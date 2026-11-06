using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>
/// Announcements pushed by Mira Connect's server (HMAC-signed, server to server) and the feed read by the
/// Nflix app with its normal Jellyfin session. No Mira user token ever reaches this endpoint or the phone.
/// </summary>
[ApiController]
[Route("NflixAuth/Announcements")]
public sealed class AnnouncementsController : ControllerBase
{
    private const int MaxBodyBytes = 64 * 1024;
    private static readonly SemaphoreSlim Lock = new(1, 1);
    private static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive = false };
    private readonly ILogger<AnnouncementsController> _logger;

    /// <summary>Initializes a new instance of the <see cref="AnnouncementsController"/> class.</summary>
    public AnnouncementsController(ILogger<AnnouncementsController> logger) => _logger = logger;

    private static string Directory()
    {
        var dir = Plugin.Instance?.AnnouncementsDirectory ?? throw new InvalidOperationException("Plugin not loaded");
        System.IO.Directory.CreateDirectory(dir);
        return dir;
    }

    private static string FileFor(string id) => Path.Combine(Directory(), id + ".json");

    private static string TombstoneFor(string id) => Path.Combine(Directory(), id + ".deleted");

    private static readonly Lazy<NonceCache> Nonces = new(() => new NonceCache(Path.Combine(Directory(), "nonces.cache")));

    /// <summary>Lets Mira's composer list Nflix as a compatible target app.</summary>
    [HttpGet("Capabilities")]
    [AllowAnonymous]
    public IActionResult Capabilities() => Ok(new
    {
        app = AnnouncementRules.Target,
        schemaVersion = AnnouncementRules.SchemaVersion,
        levels = AnnouncementRules.Levels,
        maxTitle = AnnouncementRules.MaxTitle,
        maxMessage = AnnouncementRules.MaxMessage,
        popup = true,
        audience = new[] { "groups" },
        enabled = Keys().Count > 0,
    });

    /// <summary>Creates or updates an announcement (idempotent on updatedAt).</summary>
    [HttpPut("{id}")]
    [AllowAnonymous]
    [RequestSizeLimit(MaxBodyBytes)]
    public async Task<IActionResult> Put([FromRoute] string id, CancellationToken cancellationToken)
    {
        var body = await ReadBody(cancellationToken).ConfigureAwait(false);
        if (body is null) return StatusCode(StatusCodes.Status413PayloadTooLarge);
        if (Authorize(body) is { } denied) return denied;
        if (!AnnouncementRules.IsValidId(id)) return UnprocessableEntity(new { errors = new[] { "id invalide" } });

        MiraAnnouncement? incoming;
        try
        {
            incoming = JsonSerializer.Deserialize<MiraAnnouncement>(body, Json);
        }
        catch (JsonException)
        {
            return BadRequest(new { errors = new[] { "JSON invalide" } });
        }

        if (incoming is null) return BadRequest(new { errors = new[] { "JSON vide" } });
        var errors = AnnouncementRules.Validate(incoming, id);
        if (errors.Count > 0) return UnprocessableEntity(new { errors });

        await Lock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var stored = await Load(id, cancellationToken).ConfigureAwait(false);
            var outcome = AnnouncementRules.Compare(stored, incoming, DeletedAt(id));
            if (outcome != UpsertOutcome.Unchanged)
            {
                var temp = FileFor(id) + ".tmp";
                await System.IO.File.WriteAllBytesAsync(temp, JsonSerializer.SerializeToUtf8Bytes(incoming, Json), cancellationToken).ConfigureAwait(false);
                System.IO.File.Move(temp, FileFor(id), true);
                System.IO.File.Delete(TombstoneFor(id));
                _logger.LogInformation("Mira announcement {Id} {Outcome}", id, outcome);
            }

            Prune();
            var status = outcome switch
            {
                UpsertOutcome.Created => "created",
                UpsertOutcome.Updated => "updated",
                _ => "unchanged",
            };
            return outcome == UpsertOutcome.Created ? StatusCode(StatusCodes.Status201Created, new { status, id }) : Ok(new { status, id });
        }
        finally
        {
            Lock.Release();
        }
    }

    /// <summary>Withdraws an announcement. Deleting an unknown id succeeds (idempotent).</summary>
    [HttpDelete("{id}")]
    [AllowAnonymous]
    public async Task<IActionResult> Delete([FromRoute] string id, CancellationToken cancellationToken)
    {
        var body = await ReadBody(cancellationToken).ConfigureAwait(false);
        if (body is null) return StatusCode(StatusCodes.Status413PayloadTooLarge);
        if (Authorize(body) is { } denied) return denied;
        if (!AnnouncementRules.IsValidId(id)) return UnprocessableEntity(new { errors = new[] { "id invalide" } });
        await Lock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var signedAt = DateTimeOffset.FromUnixTimeSeconds(long.Parse(Request.Headers["X-Mira-Timestamp"].ToString(), System.Globalization.CultureInfo.InvariantCulture));
            // A delayed DELETE never removes a version Mira changed after asking for the deletion.
            var stored = await Load(id, cancellationToken).ConfigureAwait(false);
            if (stored?.UpdatedAt > signedAt) return Ok(new { status = "unchanged", id });
            var existed = stored is not null;
            System.IO.File.Delete(FileFor(id));
            // Remember the signed deletion time so a delayed or replayed older PUT cannot recreate it.
            await System.IO.File.WriteAllTextAsync(TombstoneFor(id), signedAt.ToString("O"), cancellationToken).ConfigureAwait(false);
            if (existed) _logger.LogInformation("Mira announcement {Id} withdrawn", id);
            return Ok(new { status = existed ? "deleted" : "absent", id });
        }
        finally
        {
            Lock.Release();
        }
    }

    /// <summary>Active announcements for the signed-in Nflix user. Audience groups are filtered by the app.</summary>
    [HttpGet("Feed")]
    [Authorize]
    public async Task<IActionResult> Feed(CancellationToken cancellationToken)
    {
        var now = DateTimeOffset.UtcNow;
        var items = new List<object>();
        foreach (var file in System.IO.Directory.EnumerateFiles(Directory(), "*.json"))
        {
            MiraAnnouncement? announcement;
            try
            {
                await using var stream = System.IO.File.OpenRead(file);
                announcement = await JsonSerializer.DeserializeAsync<MiraAnnouncement>(stream, Json, cancellationToken).ConfigureAwait(false);
            }
            catch (Exception e) when (e is JsonException or IOException)
            {
                _logger.LogWarning(e, "Unreadable Mira announcement {File}", file);
                continue;
            }

            if (announcement is null || !AnnouncementRules.IsActive(announcement, now)) continue;
            items.Add(new
            {
                Id = "mira:" + announcement.Id,
                Title = announcement.Title,
                Message = announcement.Message,
                Category = announcement.Category,
                Level = announcement.Level,
                ActionLabel = announcement.ActionLabel,
                ActionUrl = AnnouncementRules.IsHttps(announcement.ActionUrl) ? announcement.ActionUrl : string.Empty,
                CreatedAt = (announcement.StartsAt ?? announcement.UpdatedAt)?.ToString("O"),
                ExpiresAt = announcement.ExpiresAt?.ToString("O"),
                Dismissible = announcement.Dismissible,
                ShowPopup = announcement.ShowPopup,
                Pinned = announcement.Pinned,
                Groups = announcement.Audience.Groups,
                Source = "mira",
            });
        }

        // Keep Jellyfin's established PascalCase feed shape expected by the Android Notice model.
        return new JsonResult(items, new JsonSerializerOptions { PropertyNamingPolicy = null });
    }

    private async Task<byte[]?> ReadBody(CancellationToken cancellationToken)
    {
        using var buffer = new MemoryStream();
        var chunk = new byte[8192];
        int read;
        while ((read = await Request.Body.ReadAsync(chunk, cancellationToken).ConfigureAwait(false)) > 0)
        {
            buffer.Write(chunk, 0, read);
            if (buffer.Length > MaxBodyBytes) return null;
        }

        return buffer.ToArray();
    }

    private static IReadOnlyDictionary<string, byte[]> Keys()
    {
        var config = Plugin.Instance?.Configuration;
        var keys = new Dictionary<string, byte[]>(StringComparer.Ordinal);
        if (config is null) return keys;
        void Add(string? keyId, string? secret)
        {
            var bytes = AnnouncementRules.ParseSecret(secret);
            if (!string.IsNullOrWhiteSpace(keyId) && bytes is not null) keys[keyId.Trim()] = bytes;
        }

        Add(config.AnnouncementKeyId, config.AnnouncementSecret);
        Add(config.AnnouncementPreviousKeyId, config.AnnouncementPreviousSecret);
        return keys;
    }

    /// <summary>Null when the request is authentic and new; otherwise the 401 to return.</summary>
    private IActionResult? Authorize(byte[] body)
    {
        var now = DateTimeOffset.UtcNow;
        var nonce = Request.Headers["X-Mira-Nonce"].ToString();
        var check = AnnouncementRules.Verify(
            Keys(),
            Request.Headers["X-Mira-Key-Id"].ToString(),
            Request.Headers["X-Mira-Timestamp"].ToString(),
            nonce,
            Request.Headers["X-Mira-Signature"].ToString(),
            Request.Method,
            Request.PathBase.Add(Request.Path).Value ?? string.Empty,
            body,
            now);
        if (check == SignatureCheck.ClockSkew)
        {
            // Authenticated caller with a wrong clock: tell it the server time so it can retry (new nonce).
            _logger.LogWarning("Mira announcement request with clock skew from {Ip}", HttpContext.Connection.RemoteIpAddress);
            return Unauthorized(new { code = "clock_skew", serverTime = now.ToUnixTimeSeconds() });
        }

        if (check == SignatureCheck.Valid && Nonces.Value.TryUse(nonce, now)) return null;
        _logger.LogWarning("Rejected Mira announcement request from {Ip}", HttpContext.Connection.RemoteIpAddress);
        return Unauthorized(new { code = "unauthorized" });
    }

    private static DateTimeOffset? DeletedAt(string id)
    {
        var file = TombstoneFor(id);
        if (!System.IO.File.Exists(file)) return null;
        return DateTimeOffset.TryParse(System.IO.File.ReadAllText(file), System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.RoundtripKind, out var at) ? at : null;
    }

    private static async Task<MiraAnnouncement?> Load(string id, CancellationToken cancellationToken)
    {
        var file = FileFor(id);
        if (!System.IO.File.Exists(file)) return null;
        await using var stream = System.IO.File.OpenRead(file);
        return await JsonSerializer.DeserializeAsync<MiraAnnouncement>(stream, Json, cancellationToken).ConfigureAwait(false);
    }

    private void Prune()
    {
        var now = DateTimeOffset.UtcNow;
        foreach (var tombstone in System.IO.Directory.EnumerateFiles(Directory(), "*.deleted"))
        {
            if (now - System.IO.File.GetLastWriteTimeUtc(tombstone) > AnnouncementRules.Retention) System.IO.File.Delete(tombstone);
        }

        foreach (var file in System.IO.Directory.EnumerateFiles(Directory(), "*.json"))
        {
            try
            {
                var announcement = JsonSerializer.Deserialize<MiraAnnouncement>(System.IO.File.ReadAllBytes(file), Json);
                if (announcement is not null && AnnouncementRules.IsStale(announcement, now)) System.IO.File.Delete(file);
            }
            catch (Exception e) when (e is JsonException or IOException)
            {
                _logger.LogDebug(e, "Skipping {File} during prune", file);
            }
        }
    }
}
