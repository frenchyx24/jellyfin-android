using System.Text.Json;
using System.Text.RegularExpressions;
using MediaBrowser.Common.Api;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>A streaming service or distributor shown as its own row on the Nflix home screen.</summary>
public sealed class Distributor
{
    /// <summary>Gets or sets the stable identifier (lowercase hex).</summary>
    public string Id { get; set; } = string.Empty;

    /// <summary>Gets or sets the display name, e.g. "Netflix".</summary>
    public string Name { get; set; } = string.Empty;

    /// <summary>Gets or sets the Jellyfin studio names whose titles belong to this row ("Netflix", "Netflix Studios").</summary>
    public List<string> Studios { get; set; } = new();

    /// <summary>Gets or sets a value indicating whether the logo is drawn on a light plate (dark logos).</summary>
    public bool LightPlate { get; set; }

    /// <summary>Gets or sets a value indicating whether the row is shown.</summary>
    public bool Enabled { get; set; } = true;

    /// <summary>Gets or sets the logo version; changes whenever a new logo is stored, so clients refresh their cache.</summary>
    public long LogoVersion { get; set; }

    /// <summary>Gets or sets the logo media kind: "image", "animated" (GIF/WebP), "video" (MP4/WebM) or "lottie".</summary>
    public string LogoKind { get; set; } = LogoMedia.Image;
}

/// <summary>
/// Distributor rows ("Netflix", "Disney+"…) configured by Nflix administrators.
/// Everyone signed in can read them; only Jellyfin administrators can change them.
/// </summary>
[ApiController]
[Route("NflixAuth/Studios")]
public sealed partial class StudiosController : ControllerBase
{
    private const int MaxLogoBytes = 2 * 1024 * 1024;
    private const int MaxUploadBytes = 8 * 1024 * 1024;
    private const int MaxDistributors = 40;
    private static readonly SemaphoreSlim Lock = new(1, 1);
    private static readonly JsonSerializerOptions Json = new() { WriteIndented = true, PropertyNamingPolicy = null };
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(15) };
    private readonly ILogger<StudiosController> _logger;

    /// <summary>Initializes a new instance of the <see cref="StudiosController"/> class.</summary>
    public StudiosController(ILogger<StudiosController> logger) => _logger = logger;

    [GeneratedRegex("^[a-f0-9]{8,32}$")]
    private static partial Regex IdRegex();

    // TMDB image paths look like "/tyHnxjQJLH6h4iDQKhN5iqebWmX.png".
    [GeneratedRegex(@"^/[A-Za-z0-9]{10,64}\.(png|jpg|jpeg)$")]
    private static partial Regex TmdbPathRegex();

    private static string Root => Plugin.Instance?.StudiosDirectory ?? throw new InvalidOperationException("Plugin Nflix Auth non initialisé.");

    private static string ListFile => Path.Combine(Root, "distributors.json");

    private static string LogoFile(string id) => Path.Combine(Root, id + ".logo");

    /// <summary>Distributor rows, in display order. Administrators can ask for hidden rows too.</summary>
    [HttpGet]
    [Authorize]
    public async Task<ActionResult<List<Distributor>>> List([FromQuery] bool all = false)
    {
        var list = await LoadAsync().ConfigureAwait(false);
        var admin = User.IsInRole("Administrator");
        return all && admin ? list : list.Where(d => d.Enabled).ToList();
    }

    /// <summary>Replaces the whole list (order, names, studios, plate, visibility). Logos of removed rows are deleted.</summary>
    [HttpPut]
    [Authorize(Policy = Policies.RequiresElevation)]
    public async Task<ActionResult<List<Distributor>>> Save([FromBody] List<Distributor> incoming)
    {
        if (incoming is null || incoming.Count > MaxDistributors) return BadRequest(new { Message = $"Liste invalide ({MaxDistributors} distributeurs maximum)." });
        await Lock.WaitAsync().ConfigureAwait(false);
        try
        {
            var current = await LoadUnlockedAsync().ConfigureAwait(false);
            var cleaned = new List<Distributor>();
            foreach (var d in incoming)
            {
                var name = (d.Name ?? string.Empty).Trim();
                if (name.Length is 0 or > 60) return BadRequest(new { Message = "Chaque distributeur a besoin d'un nom (60 caractères maximum)." });
                var id = string.IsNullOrEmpty(d.Id) ? Convert.ToHexString(System.Security.Cryptography.RandomNumberGenerator.GetBytes(8)).ToLowerInvariant() : d.Id.ToLowerInvariant();
                if (!IdRegex().IsMatch(id) || cleaned.Any(c => c.Id == id)) return BadRequest(new { Message = "Identifiant de distributeur invalide." });
                var studios = (d.Studios ?? new()).Select(s => s.Trim()).Where(s => s.Length is > 0 and <= 120).Distinct(StringComparer.OrdinalIgnoreCase).Take(20).ToList();
                var previous = current.FirstOrDefault(c => c.Id == id);
                cleaned.Add(new Distributor { Id = id, Name = name, Studios = studios, LightPlate = d.LightPlate, Enabled = d.Enabled, LogoVersion = previous?.LogoVersion ?? 0, LogoKind = previous?.LogoKind ?? LogoMedia.Image });
            }

            foreach (var removed in current.Where(c => cleaned.All(n => n.Id != c.Id)))
            {
                var file = LogoFile(removed.Id);
                if (System.IO.File.Exists(file)) System.IO.File.Delete(file);
            }

            await SaveUnlockedAsync(cleaned).ConfigureAwait(false);
            _logger.LogInformation("Nflix: {Count} distributor rows saved by {User}", cleaned.Count, User.Identity?.Name);
            return cleaned;
        }
        finally
        {
            Lock.Release();
        }
    }

    /// <summary>Logo image of a distributor (public: it is artwork, not personal data).</summary>
    [HttpGet("{id}/Logo")]
    [AllowAnonymous]
    public IActionResult Logo([FromRoute] string id)
    {
        if (!IdRegex().IsMatch(id)) return NotFound();
        var file = LogoFile(id);
        if (!System.IO.File.Exists(file)) return NotFound();
        var bytes = System.IO.File.ReadAllBytes(file);
        Response.Headers.CacheControl = "public, max-age=86400";
        Response.Headers.XContentTypeOptions = "nosniff";
        // Range requests let video players seek and buffer.
        return PhysicalFile(file, LogoMedia.Detect(bytes)?.ContentType ?? "application/octet-stream", enableRangeProcessing: true);
    }

    /// <summary>
    /// Stores a logo uploaded by an administrator: PNG/JPEG/WebP (2 MB), GIF or animated WebP (6 MB),
    /// MP4/WebM clip (8 MB) or Lottie JSON (2 MB).
    /// </summary>
    [HttpPut("{id}/Logo")]
    [Authorize(Policy = Policies.RequiresElevation)]
    [RequestSizeLimit(MaxUploadBytes + 1024)]
    public async Task<IActionResult> UploadLogo([FromRoute] string id, CancellationToken cancellationToken)
    {
        var bytes = await ReadLimitedAsync(Request.Body, MaxUploadBytes, cancellationToken).ConfigureAwait(false);
        if (bytes is null) return BadRequest(new { Message = "Le fichier dépasse 8 Mo." });
        return await StoreLogoAsync(id, bytes).ConfigureAwait(false);
    }

    /// <summary>Fetches a logo found on TMDB (through Horus) and keeps a copy on the server. Only image.tmdb.org is contacted.</summary>
    [HttpPost("{id}/Logo/Tmdb")]
    [Authorize(Policy = Policies.RequiresElevation)]
    public async Task<IActionResult> LogoFromTmdb([FromRoute] string id, [FromQuery] string path, CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(path) || !TmdbPathRegex().IsMatch(path)) return BadRequest(new { Message = "Chemin de logo TMDB invalide." });
        byte[] bytes;
        try
        {
            using var response = await Http.GetAsync(new Uri("https://image.tmdb.org/t/p/w500" + path), HttpCompletionOption.ResponseHeadersRead, cancellationToken).ConfigureAwait(false);
            if (!response.IsSuccessStatusCode) return StatusCode(StatusCodes.Status502BadGateway, new { Message = $"TMDB a répondu {(int)response.StatusCode}." });
            if (response.Content.Headers.ContentLength > MaxLogoBytes) return BadRequest(new { Message = "Logo trop volumineux." });
            await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
            var bounded = await ReadLimitedAsync(stream, MaxLogoBytes, cancellationToken).ConfigureAwait(false);
            if (bounded is null) return BadRequest(new { Message = "Logo trop volumineux." });
            bytes = bounded;
        }
        catch (HttpRequestException error)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { Message = "TMDB est injoignable : " + error.Message });
        }

        return await StoreLogoAsync(id, bytes).ConfigureAwait(false);
    }

    /// <summary>Animated logo candidates from GIPHY (stickers first: they often have a transparent background).</summary>
    [HttpGet("Animated/Search")]
    [Authorize(Policy = Policies.RequiresElevation)]
    public async Task<ActionResult<List<AnimatedCandidate>>> SearchAnimated([FromQuery] string q, CancellationToken cancellationToken)
    {
        var key = Plugin.Instance?.Configuration.GiphyApiKey;
        if (string.IsNullOrWhiteSpace(key)) return StatusCode(StatusCodes.Status503ServiceUnavailable, new { Message = "Recherche GIPHY non configurée sur le serveur." });
        q = (q ?? string.Empty).Trim();
        if (q.Length is < 2 or > 60) return BadRequest(new { Message = "Recherche invalide." });
        var results = new List<AnimatedCandidate>();
        foreach (var kind in new[] { "stickers", "gifs" })
        {
            var url = $"https://api.giphy.com/v1/{kind}/search?api_key={Uri.EscapeDataString(key)}&q={Uri.EscapeDataString(q)}&limit=15&rating=g&lang=fr";
            try
            {
                using var response = await Http.GetAsync(new Uri(url), cancellationToken).ConfigureAwait(false);
                if (!response.IsSuccessStatusCode) continue;
                await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
                using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken).ConfigureAwait(false);
                foreach (var gif in doc.RootElement.GetProperty("data").EnumerateArray())
                {
                    var id = gif.GetProperty("id").GetString();
                    if (id is null || !GiphyIdRegex().IsMatch(id)) continue;
                    var preview = gif.GetProperty("images").GetProperty("fixed_height_small");
                    var previewUrl = preview.TryGetProperty("webp", out var w) ? w.GetString() : preview.GetProperty("url").GetString();
                    if (previewUrl is null || !IsGiphyMedia(previewUrl)) continue;
                    results.Add(new AnimatedCandidate { Id = id, Title = gif.TryGetProperty("title", out var t) ? t.GetString() ?? string.Empty : string.Empty, PreviewUrl = previewUrl, Sticker = kind == "stickers" });
                }
            }
            catch (HttpRequestException)
            {
                // One source down still leaves the other.
            }
        }

        return results;
    }

    /// <summary>Downloads a GIPHY animation (animated WebP, or MP4 when asked) and stores it as the logo. Only giphy.com hosts are contacted.</summary>
    [HttpPost("{id}/Logo/Giphy")]
    [Authorize(Policy = Policies.RequiresElevation)]
    public async Task<IActionResult> LogoFromGiphy([FromRoute] string id, [FromQuery] string gifId, [FromQuery] bool video = false, CancellationToken cancellationToken = default)
    {
        var key = Plugin.Instance?.Configuration.GiphyApiKey;
        if (string.IsNullOrWhiteSpace(key)) return StatusCode(StatusCodes.Status503ServiceUnavailable, new { Message = "GIPHY non configuré sur le serveur." });
        if (string.IsNullOrEmpty(gifId) || !GiphyIdRegex().IsMatch(gifId)) return BadRequest(new { Message = "Animation GIPHY invalide." });
        byte[]? bytes = null;
        try
        {
            using var meta = await Http.GetAsync(new Uri($"https://api.giphy.com/v1/gifs/{gifId}?api_key={Uri.EscapeDataString(key)}"), cancellationToken).ConfigureAwait(false);
            if (!meta.IsSuccessStatusCode) return StatusCode(StatusCodes.Status502BadGateway, new { Message = $"GIPHY a répondu {(int)meta.StatusCode}." });
            await using var metaStream = await meta.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
            using var doc = await JsonDocument.ParseAsync(metaStream, cancellationToken: cancellationToken).ConfigureAwait(false);
            var images = doc.RootElement.GetProperty("data").GetProperty("images");
            var field = video ? "mp4" : "webp";
            var limit = LogoMedia.MaxBytes(video ? LogoMedia.Video : LogoMedia.Animated);
            // Best quality that fits under the limit.
            foreach (var rendition in new[] { "original", "fixed_width", "fixed_height", "downsized" })
            {
                if (!images.TryGetProperty(rendition, out var r) || !r.TryGetProperty(field, out var u)) continue;
                var url = u.GetString();
                if (url is null || !IsGiphyMedia(url)) continue;
                using var response = await Http.GetAsync(new Uri(url), HttpCompletionOption.ResponseHeadersRead, cancellationToken).ConfigureAwait(false);
                if (!response.IsSuccessStatusCode || response.Content.Headers.ContentLength > limit) continue;
                await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
                bytes = await ReadLimitedAsync(stream, limit, cancellationToken).ConfigureAwait(false);
                if (bytes is not null) break;
            }
        }
        catch (HttpRequestException error)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { Message = "GIPHY est injoignable : " + error.Message });
        }
        catch (KeyNotFoundException)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { Message = "Réponse GIPHY inattendue." });
        }

        if (bytes is null) return BadRequest(new { Message = "Animation trop lourde ou introuvable." });
        return await StoreLogoAsync(id, bytes).ConfigureAwait(false);
    }

    [GeneratedRegex("^[A-Za-z0-9]{6,40}$")]
    private static partial Regex GiphyIdRegex();

    private static bool IsGiphyMedia(string url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps
        && (uri.Host == "giphy.com" || uri.Host.EndsWith(".giphy.com", StringComparison.Ordinal));

    private async Task<IActionResult> StoreLogoAsync(string id, byte[] bytes)
    {
        if (!IdRegex().IsMatch(id)) return BadRequest(new { Message = "Identifiant invalide." });
        var media = LogoMedia.Detect(bytes);
        if (bytes.Length == 0 || media is null) return BadRequest(new { Message = "Format refusé : PNG, JPEG, WebP, GIF, MP4, WebM ou Lottie (JSON)." });
        var kind = media.Value.Kind;
        if (bytes.Length > LogoMedia.MaxBytes(kind)) return BadRequest(new { Message = $"Fichier trop lourd pour une {LogoMedia.Describe(kind)} ({LogoMedia.MaxBytes(kind) / (1024 * 1024)} Mo maximum)." });
        await Lock.WaitAsync().ConfigureAwait(false);
        try
        {
            var list = await LoadUnlockedAsync().ConfigureAwait(false);
            var row = list.FirstOrDefault(d => d.Id == id);
            if (row is null) return NotFound(new { Message = "Enregistrez d'abord le distributeur." });
            System.IO.Directory.CreateDirectory(Root);
            var temp = LogoFile(id) + ".upload";
            await System.IO.File.WriteAllBytesAsync(temp, bytes).ConfigureAwait(false);
            System.IO.File.Move(temp, LogoFile(id), true);
            row.LogoVersion = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            row.LogoKind = kind;
            await SaveUnlockedAsync(list).ConfigureAwait(false);
            return Ok(row);
        }
        finally
        {
            Lock.Release();
        }
    }

    private static async Task<byte[]?> ReadLimitedAsync(Stream input, int maximumBytes, CancellationToken cancellationToken)
    {
        using var buffer = new MemoryStream(Math.Min(maximumBytes, 64 * 1024));
        var chunk = new byte[16 * 1024];
        while (true)
        {
            var read = await input.ReadAsync(chunk, cancellationToken).ConfigureAwait(false);
            if (read == 0) return buffer.ToArray();
            if (buffer.Length + read > maximumBytes) return null;
            await buffer.WriteAsync(chunk.AsMemory(0, read), cancellationToken).ConfigureAwait(false);
        }
    }

    private static async Task<List<Distributor>> LoadAsync()
    {
        await Lock.WaitAsync().ConfigureAwait(false);
        try
        {
            return await LoadUnlockedAsync().ConfigureAwait(false);
        }
        finally
        {
            Lock.Release();
        }
    }

    private static async Task<List<Distributor>> LoadUnlockedAsync()
    {
        if (!System.IO.File.Exists(ListFile)) return new();
        await using var stream = System.IO.File.OpenRead(ListFile);
        return await JsonSerializer.DeserializeAsync<List<Distributor>>(stream, Json).ConfigureAwait(false) ?? new();
    }

    private static async Task SaveUnlockedAsync(List<Distributor> list)
    {
        System.IO.Directory.CreateDirectory(Root);
        var temp = ListFile + ".upload";
        await System.IO.File.WriteAllTextAsync(temp, JsonSerializer.Serialize(list, Json)).ConfigureAwait(false);
        System.IO.File.Move(temp, ListFile, true);
    }
}

/// <summary>An animated logo candidate found on GIPHY.</summary>
public sealed class AnimatedCandidate
{
    /// <summary>Gets or sets the GIPHY id.</summary>
    public string Id { get; set; } = string.Empty;

    /// <summary>Gets or sets the title.</summary>
    public string Title { get; set; } = string.Empty;

    /// <summary>Gets or sets the small animated preview (media.giphy.com).</summary>
    public string PreviewUrl { get; set; } = string.Empty;

    /// <summary>Gets or sets a value indicating whether it is a sticker (transparent background).</summary>
    public bool Sticker { get; set; }
}
