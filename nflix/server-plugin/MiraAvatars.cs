using System.Text.Json;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>
/// Profile pictures received from Mira Connect at sign-in, kept per Jellyfin user id.
/// The web interface has no Mira token, so it reads them from here to show the same photo
/// as the phone app (profile picker, family page, account menu).
/// </summary>
public static class MiraAvatars
{
    private static readonly object Gate = new();
    private static readonly Dictionary<string, string> Map = new(StringComparer.OrdinalIgnoreCase);
    private static string? _path;

    /// <summary>Loads the stored avatars once at plugin start.</summary>
    public static void Init(string path)
    {
        _path = path;
        try
        {
            if (!File.Exists(path)) return;
            var stored = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(path));
            if (stored is null) return;
            lock (Gate)
            {
                Map.Clear();
                foreach (var entry in stored)
                {
                    if (entry.Key.Length is > 0 and <= 64 && entry.Value.Length <= 300_000) Map[entry.Key] = entry.Value;
                }
            }
        }
        catch (Exception e) when (e is IOException or JsonException or UnauthorizedAccessException)
        {
            // A corrupt file must not stop Jellyfin: the next Mira sign-in rewrites it.
        }
    }

    /// <summary>Stores the avatar of one user and rewrites the file atomically.</summary>
    public static void Save(string userId, string avatar)
    {
        var path = _path;
        if (path is null) return;
        lock (Gate)
        {
            if (Map.TryGetValue(userId, out var current) && current == avatar) return;
            Map[userId] = avatar;
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(path)!);
                var temp = path + ".tmp";
                File.WriteAllText(temp, JsonSerializer.Serialize(Map));
                File.Move(temp, path, true);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                // Keep the in-memory copy; the next sign-in retries.
            }
        }
    }

    /// <summary>Snapshot of every known avatar, keyed by user id without dashes.</summary>
    public static IReadOnlyDictionary<string, string> All()
    {
        lock (Gate) return new Dictionary<string, string>(Map, StringComparer.OrdinalIgnoreCase);
    }
}
