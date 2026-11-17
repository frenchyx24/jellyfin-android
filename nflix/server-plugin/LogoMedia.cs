using System.Text.Json;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>Recognised distributor logo media, by file signature. Anything else (SVG, HTML…) is refused.</summary>
public static class LogoMedia
{
    /// <summary>Static picture: PNG, JPEG or still WebP.</summary>
    public const string Image = "image";

    /// <summary>Animated picture: GIF or animated WebP.</summary>
    public const string Animated = "animated";

    /// <summary>Short looping clip: MP4 or WebM.</summary>
    public const string Video = "video";

    /// <summary>Lottie (Bodymovin) JSON animation.</summary>
    public const string Lottie = "lottie";

    /// <summary>Returns (kind, content type) for [bytes], or null when the format is not allowed.</summary>
    public static (string Kind, string ContentType)? Detect(byte[] bytes)
    {
        if (bytes.Length >= 8 && bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return (Image, "image/png");
        if (bytes.Length >= 3 && bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) return (Image, "image/jpeg");
        if (bytes.Length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') return (Animated, "image/gif");
        if (bytes.Length >= 12 && Ascii(bytes, 0, "RIFF") && Ascii(bytes, 8, "WEBP"))
        {
            return (HasWebpAnimation(bytes) ? Animated : Image, "image/webp");
        }

        // ISO base media (MP4/M4V/MOV-compatible "ftyp" box at offset 4).
        if (bytes.Length >= 12 && Ascii(bytes, 4, "ftyp")) return (Video, "video/mp4");
        // Matroska/WebM EBML header.
        if (bytes.Length >= 4 && bytes[0] == 0x1A && bytes[1] == 0x45 && bytes[2] == 0xDF && bytes[3] == 0xA3) return (Video, "video/webm");
        if (IsLottie(bytes)) return (Lottie, "application/json");
        return null;
    }

    private static bool Ascii(byte[] bytes, int offset, string text)
    {
        if (bytes.Length < offset + text.Length) return false;
        for (var i = 0; i < text.Length; i++)
        {
            if (bytes[offset + i] != text[i]) return false;
        }

        return true;
    }

    /// <summary>Extended WebP with the animation flag (VP8X) or an ANIM chunk.</summary>
    private static bool HasWebpAnimation(byte[] bytes)
    {
        if (bytes.Length >= 21 && Ascii(bytes, 12, "VP8X") && (bytes[20] & 0x02) != 0) return true;
        var limit = Math.Min(bytes.Length - 4, 4096);
        for (var i = 12; i < limit; i++)
        {
            if (Ascii(bytes, i, "ANIM")) return true;
        }

        return false;
    }

    /// <summary>A JSON object with the Lottie keys "v" (version), "fr", "w", "h" and a "layers" array.</summary>
    private static bool IsLottie(byte[] bytes)
    {
        var start = 0;
        if (bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF) start = 3;
        while (start < bytes.Length && bytes[start] is (byte)' ' or (byte)'\n' or (byte)'\r' or (byte)'\t') start++;
        if (start >= bytes.Length || bytes[start] != '{') return false;
        try
        {
            using var doc = JsonDocument.Parse(bytes.AsMemory(start), new JsonDocumentOptions { MaxDepth = 256 });
            var root = doc.RootElement;
            return root.ValueKind == JsonValueKind.Object
                && root.TryGetProperty("v", out _)
                && root.TryGetProperty("fr", out _)
                && root.TryGetProperty("w", out _)
                && root.TryGetProperty("h", out _)
                && root.TryGetProperty("layers", out var layers) && layers.ValueKind == JsonValueKind.Array;
        }
        catch (JsonException)
        {
            return false;
        }
    }

    /// <summary>Upload ceiling for [kind]: pictures stay small, clips may be larger.</summary>
    public static int MaxBytes(string kind) => kind switch
    {
        Video => 8 * 1024 * 1024,
        Animated => 6 * 1024 * 1024,
        Lottie => 2 * 1024 * 1024,
        _ => 2 * 1024 * 1024,
    };

    /// <summary>French name of [kind] for error messages.</summary>
    public static string Describe(string kind) => kind switch
    {
        Video => "vidéo",
        Animated => "image animée",
        Lottie => "animation Lottie",
        _ => "image",
    };
}
