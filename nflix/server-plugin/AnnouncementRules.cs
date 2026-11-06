using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>Announcement pushed by Mira (server to server). Field names follow the agreed JSON contract.</summary>
public sealed class MiraAnnouncement
{
    /// <summary>Gets or sets the Mira id (no "mira:" prefix; the app adds it).</summary>
    [JsonPropertyName("id")] public string Id { get; set; } = string.Empty;

    /// <summary>Gets or sets the apps this announcement targets. Must contain "nflix".</summary>
    [JsonPropertyName("targets")] public List<string> Targets { get; set; } = new();

    /// <summary>Gets or sets the title.</summary>
    [JsonPropertyName("title")] public string Title { get; set; } = string.Empty;

    /// <summary>Gets or sets the plain-text message.</summary>
    [JsonPropertyName("message")] public string Message { get; set; } = string.Empty;

    /// <summary>Gets or sets the level: info, warning or urgent.</summary>
    [JsonPropertyName("level")] public string Level { get; set; } = "info";

    /// <summary>Gets or sets the category.</summary>
    [JsonPropertyName("category")] public string Category { get; set; } = "announcement";

    /// <summary>Gets or sets the action button label.</summary>
    [JsonPropertyName("actionLabel")] public string ActionLabel { get; set; } = string.Empty;

    /// <summary>Gets or sets the action URL (HTTPS only, or empty).</summary>
    [JsonPropertyName("actionUrl")] public string ActionUrl { get; set; } = string.Empty;

    /// <summary>Gets or sets when the announcement starts showing.</summary>
    [JsonPropertyName("startsAt")] public DateTimeOffset? StartsAt { get; set; }

    /// <summary>Gets or sets when the announcement stops showing.</summary>
    [JsonPropertyName("expiresAt")] public DateTimeOffset? ExpiresAt { get; set; }

    /// <summary>Gets or sets a value indicating whether the announcement is pinned on top.</summary>
    [JsonPropertyName("pinned")] public bool Pinned { get; set; }

    /// <summary>Gets or sets a value indicating whether the app shows a popup.</summary>
    [JsonPropertyName("showPopup")] public bool ShowPopup { get; set; }

    /// <summary>Gets or sets a value indicating whether users can dismiss it.</summary>
    [JsonPropertyName("dismissible")] public bool Dismissible { get; set; } = true;

    /// <summary>Gets or sets the audience; empty groups means everyone.</summary>
    [JsonPropertyName("audience")] public MiraAudience Audience { get; set; } = new();

    /// <summary>Gets or sets the last change on the Mira side; used for idempotence.</summary>
    [JsonPropertyName("updatedAt")] public DateTimeOffset? UpdatedAt { get; set; }
}

/// <summary>Who sees an announcement.</summary>
public sealed class MiraAudience
{
    /// <summary>Gets or sets the Mira groups (e.g. plan_pro). Empty = everyone.</summary>
    [JsonPropertyName("groups")] public List<string> Groups { get; set; } = new();
}

/// <summary>Result of checking a signed request.</summary>
public enum SignatureCheck
{
    /// <summary>Valid signature, fresh timestamp.</summary>
    Valid,

    /// <summary>
    /// Valid signature but the timestamp is outside the allowed window. Only reported after the signature
    /// matched, so it reveals nothing to an unauthenticated caller; Mira may retry with a new nonce.
    /// </summary>
    ClockSkew,

    /// <summary>Anything else: unknown key, bad nonce, wrong signature, no key configured.</summary>
    Invalid,
}

/// <summary>Outcome of storing a pushed announcement.</summary>
public enum UpsertOutcome
{
    /// <summary>New announcement stored.</summary>
    Created,

    /// <summary>Existing announcement replaced by a newer version.</summary>
    Updated,

    /// <summary>Same or older version received again; nothing changed.</summary>
    Unchanged,
}

/// <summary>Pure rules of the Mira → Nflix announcement contract (no Jellyfin dependency, unit tested).</summary>
public static class AnnouncementRules
{
    /// <summary>Contract version exposed by the capabilities endpoint.</summary>
    public const int SchemaVersion = 1;

    /// <summary>Target name that must be present for Nflix to accept an announcement.</summary>
    public const string Target = "nflix";

    /// <summary>Maximum title length.</summary>
    public const int MaxTitle = 120;

    /// <summary>Maximum message length.</summary>
    public const int MaxMessage = 4000;

    /// <summary>Maximum accepted clock difference for signed requests.</summary>
    public static readonly TimeSpan MaxClockSkew = TimeSpan.FromSeconds(300);

    /// <summary>Expired announcements are deleted after this delay.</summary>
    public static readonly TimeSpan Retention = TimeSpan.FromDays(30);

    /// <summary>Accepted levels.</summary>
    public static readonly IReadOnlyList<string> Levels = new[] { "info", "warning", "urgent" };

    private static readonly Regex IdPattern = new("^[A-Za-z0-9_-]{1,64}$", RegexOptions.Compiled | RegexOptions.CultureInvariant);
    private static readonly Regex GroupPattern = new("^[A-Za-z0-9_.-]{1,64}$", RegexOptions.Compiled | RegexOptions.CultureInvariant);

    /// <summary>Gets a value indicating whether an id is acceptable.</summary>
    public static bool IsValidId(string? id) => id is not null && IdPattern.IsMatch(id);

    /// <summary>Returns the list of problems; empty means valid.</summary>
    public static List<string> Validate(MiraAnnouncement announcement, string routeId)
    {
        var errors = new List<string>();
        if (!IsValidId(announcement.Id)) errors.Add("id invalide");
        if (!string.Equals(announcement.Id, routeId, StringComparison.Ordinal)) errors.Add("id différent de l'URL");
        if (!announcement.Targets.Any(t => string.Equals(t?.Trim(), Target, StringComparison.OrdinalIgnoreCase))) errors.Add("cible nflix absente");
        if (string.IsNullOrWhiteSpace(announcement.Title) && string.IsNullOrWhiteSpace(announcement.Message)) errors.Add("titre et message vides");
        if (announcement.Title.Length > MaxTitle) errors.Add("titre trop long");
        if (announcement.Message.Length > MaxMessage) errors.Add("message trop long");
        if (!Levels.Contains(announcement.Level)) errors.Add("niveau inconnu");
        if (announcement.ActionLabel.Length > 40) errors.Add("libellé d'action trop long");
        if (!string.IsNullOrEmpty(announcement.ActionUrl) && !IsHttps(announcement.ActionUrl)) errors.Add("actionUrl doit être en https");
        if (announcement.UpdatedAt is null) errors.Add("updatedAt obligatoire");
        if (announcement.StartsAt is { } start && announcement.ExpiresAt is { } end && end <= start) errors.Add("expiresAt doit suivre startsAt");
        if (announcement.Audience.Groups.Any(g => g is null || !GroupPattern.IsMatch(g))) errors.Add("groupe d'audience invalide");
        return errors;
    }

    /// <summary>HTTPS absolute URL with a host, nothing else (no javascript:, intent:, http:).</summary>
    public static bool IsHttps(string url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps && !string.IsNullOrEmpty(uri.Host);

    /// <summary>
    /// Decides what to do with a pushed version against the stored one. <paramref name="deletedAt"/> is the
    /// signed time of a previous DELETE: an older PUT (replayed or delayed) never brings it back.
    /// </summary>
    public static UpsertOutcome Compare(MiraAnnouncement? stored, MiraAnnouncement incoming, DateTimeOffset? deletedAt = null)
    {
        if (deletedAt is { } removed && incoming.UpdatedAt <= removed) return UpsertOutcome.Unchanged;
        if (stored is null) return UpsertOutcome.Created;
        return incoming.UpdatedAt > stored.UpdatedAt ? UpsertOutcome.Updated : UpsertOutcome.Unchanged;
    }

    /// <summary>Gets a value indicating whether the announcement is visible at <paramref name="now"/>.</summary>
    public static bool IsActive(MiraAnnouncement announcement, DateTimeOffset now) =>
        (announcement.StartsAt is null || announcement.StartsAt <= now) && (announcement.ExpiresAt is null || announcement.ExpiresAt > now);

    /// <summary>Gets a value indicating whether the stored announcement can be deleted for good.</summary>
    public static bool IsStale(MiraAnnouncement announcement, DateTimeOffset now) =>
        announcement.ExpiresAt is { } end && now - end > Retention;

    /// <summary>Nonce lifetime in the anti-replay cache (longer than twice the clock skew).</summary>
    public static readonly TimeSpan NonceTtl = TimeSpan.FromMinutes(15);

    private static readonly Regex NoncePattern = new("^[A-Za-z0-9_-]{16,128}$", RegexOptions.Compiled | RegexOptions.CultureInvariant);

    /// <summary>Gets a value indicating whether a nonce has an acceptable shape.</summary>
    public static bool IsValidNonce(string? nonce) => nonce is not null && NoncePattern.IsMatch(nonce);

    /// <summary>String signed by Mira: key id, timestamp, nonce, method, path and body hash, one per line.</summary>
    public static string CanonicalString(string keyId, string timestamp, string nonce, string method, string path, byte[] body) =>
        string.Join('\n', keyId, timestamp, nonce, method.ToUpperInvariant(), path, Convert.ToHexString(SHA256.HashData(body)).ToLowerInvariant());

    /// <summary>Hex HMAC-SHA256 of the canonical string, lower case.</summary>
    public static string Sign(byte[] secret, string keyId, string timestamp, string nonce, string method, string path, byte[] body) =>
        Convert.ToHexString(HMACSHA256.HashData(secret, Encoding.UTF8.GetBytes(CanonicalString(keyId, timestamp, nonce, method, path, body)))).ToLowerInvariant();

    /// <summary>
    /// Checks a signed request (signature, key, clock). <paramref name="keys"/> maps key ids to secrets
    /// (two during rotation). Fails closed when no key is configured. Replay is checked separately by
    /// <see cref="NonceCache"/>, only after the signature is valid.
    /// </summary>
    public static SignatureCheck Verify(
        IReadOnlyDictionary<string, byte[]> keys,
        string? keyId,
        string? timestamp,
        string? nonce,
        string? signatureHeader,
        string method,
        string path,
        byte[] body,
        DateTimeOffset now)
    {
        if (keys.Count == 0 || string.IsNullOrEmpty(keyId) || string.IsNullOrEmpty(timestamp) || string.IsNullOrEmpty(signatureHeader)) return SignatureCheck.Invalid;
        if (!IsValidNonce(nonce)) return SignatureCheck.Invalid;
        if (!keys.TryGetValue(keyId, out var secret) || secret.Length < 32) return SignatureCheck.Invalid;
        if (!long.TryParse(timestamp, NumberStyles.None, CultureInfo.InvariantCulture, out var seconds)) return SignatureCheck.Invalid;
        if (!signatureHeader.StartsWith("v1=", StringComparison.Ordinal)) return SignatureCheck.Invalid;
        var expected = Encoding.ASCII.GetBytes(Sign(secret, keyId, timestamp, nonce!, method, path, body));
        var given = Encoding.ASCII.GetBytes(signatureHeader[3..].Trim().ToLowerInvariant());
        if (!CryptographicOperations.FixedTimeEquals(expected, given)) return SignatureCheck.Invalid;
        // Clock checked only once the caller has proven it holds the secret.
        if (seconds > DateTimeOffset.MaxValue.ToUnixTimeSeconds()) return SignatureCheck.ClockSkew;
        var sent = DateTimeOffset.FromUnixTimeSeconds(seconds);
        return (now - sent).Duration() > MaxClockSkew ? SignatureCheck.ClockSkew : SignatureCheck.Valid;
    }

    /// <summary>Parses a hex secret; null when malformed or shorter than 32 bytes.</summary>
    public static byte[]? ParseSecret(string? hex)
    {
        if (string.IsNullOrWhiteSpace(hex)) return null;
        try
        {
            var bytes = Convert.FromHexString(hex.Trim());
            return bytes.Length >= 32 ? bytes : null;
        }
        catch (FormatException)
        {
            return null;
        }
    }
}

/// <summary>
/// Anti-replay cache: a nonce is accepted once within <see cref="AnnouncementRules.NonceTtl"/>.
/// Persisted so a restart does not reopen the replay window.
/// </summary>
public sealed class NonceCache
{
    private readonly Dictionary<string, DateTimeOffset> _seen = new(StringComparer.Ordinal);
    private readonly object _gate = new();
    private readonly string? _file;

    /// <summary>Initializes a new instance of the <see cref="NonceCache"/> class.</summary>
    /// <param name="file">Where to persist nonces; null keeps them in memory (tests).</param>
    public NonceCache(string? file = null)
    {
        _file = file;
        if (file is null || !File.Exists(file)) return;
        try
        {
            foreach (var line in File.ReadAllLines(file))
            {
                var parts = line.Split(' ', 2);
                if (parts.Length == 2 && long.TryParse(parts[1], NumberStyles.None, CultureInfo.InvariantCulture, out var seconds))
                {
                    _seen[parts[0]] = DateTimeOffset.FromUnixTimeSeconds(seconds);
                }
            }
        }
        catch (IOException)
        {
            // A missing cache only shortens protection until timestamps age out; never block requests on it.
        }
    }

    /// <summary>Records the nonce; false when it was already used (replay).</summary>
    public bool TryUse(string nonce, DateTimeOffset now)
    {
        lock (_gate)
        {
            foreach (var expired in _seen.Where(e => now - e.Value > AnnouncementRules.NonceTtl).Select(e => e.Key).ToList())
            {
                _seen.Remove(expired);
            }

            if (_seen.ContainsKey(nonce)) return false;
            _seen[nonce] = now;
            if (_file is not null)
            {
                var temp = _file + ".tmp";
                File.WriteAllLines(temp, _seen.Select(e => e.Key + " " + e.Value.ToUnixTimeSeconds().ToString(CultureInfo.InvariantCulture)));
                File.Move(temp, _file, true);
            }

            return true;
        }
    }
}
