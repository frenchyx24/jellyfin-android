using System.Text.RegularExpressions;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>Pure helpers for Mira Connect profile pictures (no Jellyfin dependency, unit tested).</summary>
public static partial class AvatarImage
{
    /// <summary>Largest picture kept, in bytes.</summary>
    public const int MaxBytes = 3 * 1024 * 1024;

    /// <summary>File extension of a supported picture, from its first bytes, or null.</summary>
    public static string? Sniff(ReadOnlySpan<byte> b)
    {
        if (b.Length >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) return "jpg";
        if (b.Length >= 8 && b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "png";
        if (b.Length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b.Length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    /// <summary>Content type of an extension returned by <see cref="Sniff"/>.</summary>
    public static string ContentType(string ext) => ext switch
    {
        "jpg" => "image/jpeg",
        "png" => "image/png",
        "gif" => "image/gif",
        _ => "image/webp",
    };

    /// <summary>Bytes of an inline "data:image/...;base64," picture, or null when it is not one or is invalid.</summary>
    public static byte[]? FromDataUri(string value)
    {
        var m = DataUri().Match(value);
        if (!m.Success || value.Length > MaxBytes * 4 / 3 + 64) return null;
        try
        {
            var bytes = Convert.FromBase64String(m.Groups[1].Value);
            return bytes.Length is > 0 and <= MaxBytes && Sniff(bytes) is not null ? bytes : null;
        }
        catch (FormatException)
        {
            return null;
        }
    }

    /// <summary>
    /// File name of a picture uploaded to the Authentik media folder ("…/user-avatars/&lt;name&gt;"), or null.
    /// Authentik's generic placeholders return null: the clients then show the profile colour and initial.
    /// </summary>
    public static string? UploadedFileName(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || (uri.Scheme != "https" && uri.Scheme != "http")) return null;
        var m = Uploaded().Match(uri.AbsolutePath);
        if (!m.Success) return null;
        var name = m.Groups[1].Value;
        return name.StartsWith("default", StringComparison.OrdinalIgnoreCase) ? null : name;
    }

    /// <summary>True for a Jellyfin user id written as 32 hex digits.</summary>
    public static bool IsUserId(string id) => UserId().IsMatch(id);

    [GeneratedRegex("^data:image/(?:jpeg|jpg|png|webp|gif);base64,([A-Za-z0-9+/=]+)$", RegexOptions.IgnoreCase)]
    private static partial Regex DataUri();

    [GeneratedRegex("/user-avatars/([A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.(?:jpe?g|png|gif|webp))$", RegexOptions.IgnoreCase)]
    private static partial Regex Uploaded();

    [GeneratedRegex("^[0-9a-f]{32}$")]
    private static partial Regex UserId();
}
