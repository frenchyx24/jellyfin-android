using System.Security.Cryptography;
using System.Text.Json;
using MediaBrowser.Controller.Library;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>
/// Links every Nflix (Jellyfin) account to its Mira Connect (Authentik) account by user name and keeps a copy of
/// the Mira profile picture, so the web interface and the apps show it for everyone, even accounts that never
/// signed in with Mira on a phone. The directory is read with the Authentik service account, on the local network.
/// Pictures are served by this plugin: Authentik's own media links answer 404 on this server.
/// </summary>
public static class MiraDirectory
{
    private static readonly object Gate = new();
    private static readonly TimeSpan Refresh = TimeSpan.FromMinutes(30);
    private static Dictionary<string, string> _urls = new(StringComparer.OrdinalIgnoreCase);
    private static DateTimeOffset _last = DateTimeOffset.MinValue;
    private static Task? _running;
    private static string? _folder;

    /// <summary>Gets a value indicating whether a sync has finished since the plugin started or an index was loaded.</summary>
    public static bool Ready { get; private set; }

    /// <summary>Loads the last index so pictures survive restarts. Never throws.</summary>
    public static void Init(string folder)
    {
        _folder = folder;
        try
        {
            var index = Path.Combine(folder, "directory.json");
            if (!File.Exists(index)) return;
            var stored = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(index));
            if (stored is null) return;
            lock (Gate) _urls = new Dictionary<string, string>(stored, StringComparer.OrdinalIgnoreCase);
            Ready = true;
        }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException)
        {
            // Rebuilt by the next sync.
        }
    }

    /// <summary>Picture links by Jellyfin user id (no dashes).</summary>
    public static IReadOnlyDictionary<string, string> Urls()
    {
        lock (Gate) return new Dictionary<string, string>(_urls, StringComparer.OrdinalIgnoreCase);
    }

    /// <summary>Starts a sync when the last one is older than 30 minutes; returns the running sync.</summary>
    public static Task EnsureFresh(IUserManager users, IHttpClientFactory http, ILogger logger)
    {
        lock (Gate)
        {
            if (_running is { IsCompleted: false }) return _running;
            if (DateTimeOffset.UtcNow - _last < Refresh) return Task.CompletedTask;
            _last = DateTimeOffset.UtcNow;
            _running = Task.Run(async () =>
            {
                try
                {
                    await SyncAsync(users, http, logger).ConfigureAwait(false);
                }
                catch (Exception e)
                {
                    logger.LogWarning(e, "Nflix Auth: Mira picture sync failed");
                }
            });
            return _running;
        }
    }

    /// <summary>Path and content type of the stored picture of a user, or null.</summary>
    public static (string Path, string ContentType)? Image(string id)
    {
        if (_folder is null || !AvatarImage.IsUserId(id)) return null;
        foreach (var ext in new[] { "jpg", "png", "webp", "gif" })
        {
            var path = Path.Combine(_folder, "img", id + "." + ext);
            if (File.Exists(path)) return (path, AvatarImage.ContentType(ext));
        }

        return null;
    }

    private static async Task SyncAsync(IUserManager users, IHttpClientFactory http, ILogger logger)
    {
        var folder = _folder;
        if (folder is null) return;
        var config = Plugin.Instance?.Configuration ?? new PluginConfiguration();
        var tokenFile = string.IsNullOrEmpty(config.DirectoryTokenFile)
            ? Path.Combine(Path.GetDirectoryName(folder)!, "authentik-groups.token")
            : config.DirectoryTokenFile;
        if (!File.Exists(tokenFile))
        {
            logger.LogInformation("Nflix Auth: no Authentik token at {File}, Mira pictures from sign-in only", tokenFile);
            return;
        }

        var token = (await File.ReadAllTextAsync(tokenFile).ConfigureAwait(false)).Trim();
        var baseUrl = config.DirectoryUrl.TrimEnd('/');
        var client = http.CreateClient();
        client.Timeout = TimeSpan.FromSeconds(20);

        // Mira accounts: user name, e-mail and avatar attribute.
        var accounts = new List<(string Name, string Email, string Avatar)>();
        for (var page = 1; page <= 50; page++)
        {
            using var req = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v3/core/users/?page_size=100&page={page}&include_groups=false");
            req.Headers.Host = config.DirectoryHost;
            req.Headers.TryAddWithoutValidation("Authorization", "Bearer " + token);
            req.Headers.TryAddWithoutValidation("Accept", "application/json");
            using var res = await client.SendAsync(req).ConfigureAwait(false);
            if (!res.IsSuccessStatusCode)
            {
                logger.LogWarning("Nflix Auth: Authentik directory answered {Status}", (int)res.StatusCode);
                return;
            }

            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync().ConfigureAwait(false));
            foreach (var u in doc.RootElement.GetProperty("results").EnumerateArray())
            {
                var name = u.TryGetProperty("username", out var n) ? n.GetString() : null;
                if (string.IsNullOrEmpty(name)) continue;
                var email = u.TryGetProperty("email", out var m) && m.ValueKind == JsonValueKind.String ? m.GetString() ?? string.Empty : string.Empty;
                var avatar = u.TryGetProperty("attributes", out var attrs) && attrs.ValueKind == JsonValueKind.Object
                    && attrs.TryGetProperty("avatar", out var a) && a.ValueKind == JsonValueKind.String ? a.GetString() ?? string.Empty : string.Empty;
                accounts.Add((name, email.Trim(), avatar));
            }

            var next = doc.RootElement.TryGetProperty("pagination", out var p) && p.TryGetProperty("next", out var nx) && nx.ValueKind == JsonValueKind.Number ? nx.GetInt32() : 0;
            if (next <= page) break;
        }

        // A person may hold several Mira accounts with one e-mail (e.g. an old and a new one): the account
        // linked to Nflix by user name borrows the real picture of a sibling when it has none itself.
        static bool Real(string avatar) => AvatarImage.FromDataUri(avatar) is not null || AvatarImage.UploadedFileName(avatar) is not null;
        var byName = accounts.GroupBy(x => x.Name, StringComparer.OrdinalIgnoreCase).ToDictionary(g => g.Key, g => g.First(), StringComparer.OrdinalIgnoreCase);
        var byEmail = accounts.Where(x => x.Email.Length > 0 && Real(x.Avatar)).GroupBy(x => x.Email, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(g => g.Key, g => g.First().Avatar, StringComparer.OrdinalIgnoreCase);
        var found = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase); // jellyfin id -> avatar value
        foreach (var user in users.GetUsers())
        {
            string? avatar = null;
            if (byName.TryGetValue(user.Username, out var account))
            {
                avatar = Real(account.Avatar) ? account.Avatar : byEmail.GetValueOrDefault(account.Email);
            }
            else if (user.Username.Contains('@'))
            {
                avatar = byEmail.GetValueOrDefault(user.Username);
            }

            if (avatar is not null) found[user.Id.ToString("N")] = avatar;
        }

        var img = Path.Combine(folder, "img");
        Directory.CreateDirectory(img);
        var import = Path.Combine(folder, "import");
        var publicUrl = config.PublicUrl.TrimEnd('/');
        var urls = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var (id, avatar) in found)
        {
            var bytes = AvatarImage.FromDataUri(avatar) ?? await Uploaded(avatar, import, baseUrl, config.DirectoryHost, client).ConfigureAwait(false);
            var ext = bytes is null ? null : AvatarImage.Sniff(bytes);
            if (bytes is null || ext is null) continue;
            var target = Path.Combine(img, id + "." + ext);
            if (!File.Exists(target) || !(await File.ReadAllBytesAsync(target).ConfigureAwait(false)).AsSpan().SequenceEqual(bytes))
            {
                await File.WriteAllBytesAsync(target + ".tmp", bytes).ConfigureAwait(false);
                File.Move(target + ".tmp", target, true);
            }

            foreach (var other in new[] { "jpg", "png", "webp", "gif" })
            {
                if (other != ext) File.Delete(Path.Combine(img, id + "." + other));
            }

            var version = Convert.ToHexString(SHA256.HashData(bytes))[..12].ToLowerInvariant();
            urls[id] = $"{publicUrl}/NflixAuth/Avatars/{id}/Image?v={version}";
        }

        // Accounts whose Mira picture was removed fall back to their Jellyfin picture.
        foreach (var file in Directory.EnumerateFiles(img))
        {
            if (!urls.ContainsKey(Path.GetFileNameWithoutExtension(file))) File.Delete(file);
        }

        var index = Path.Combine(folder, "directory.json");
        await File.WriteAllTextAsync(index + ".tmp", JsonSerializer.Serialize(urls)).ConfigureAwait(false);
        File.Move(index + ".tmp", index, true);
        lock (Gate) _urls = urls;
        Ready = true;
        logger.LogInformation("Nflix Auth: {Count} Mira pictures linked to Nflix accounts", urls.Count);
    }

    /// <summary>Picture uploaded to Authentik: the local copy in import/, else Authentik on the local network.</summary>
    private static async Task<byte[]?> Uploaded(string avatar, string import, string baseUrl, string host, HttpClient client)
    {
        var name = AvatarImage.UploadedFileName(avatar);
        if (name is null) return null;
        var local = Path.Combine(import, name);
        if (File.Exists(local) && new FileInfo(local).Length <= AvatarImage.MaxBytes) return await File.ReadAllBytesAsync(local).ConfigureAwait(false);
        try
        {
            using var req = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/media/user-avatars/{Uri.EscapeDataString(name)}");
            req.Headers.Host = host;
            using var res = await client.SendAsync(req, HttpCompletionOption.ResponseHeadersRead).ConfigureAwait(false);
            if (!res.IsSuccessStatusCode || res.Content.Headers.ContentLength > AvatarImage.MaxBytes) return null;
            var bytes = await res.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
            return bytes.Length <= AvatarImage.MaxBytes ? bytes : null;
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException)
        {
            return null;
        }
    }
}
