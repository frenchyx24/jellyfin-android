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
                cleaned.Add(new Distributor { Id = id, Name = name, Studios = studios, LightPlate = d.LightPlate, Enabled = d.Enabled, LogoVersion = previous?.LogoVersion ?? 0 });
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
        var head = new byte[12];
        using (var stream = System.IO.File.OpenRead(file)) _ = stream.Read(head, 0, head.Length);
        Response.Headers.CacheControl = "public, max-age=86400";
        return PhysicalFile(file, ImageType(head) ?? "application/octet-stream");
    }

    /// <summary>Stores a logo uploaded by an administrator (PNG, JPEG or WebP, 2 MB maximum).</summary>
    [HttpPut("{id}/Logo")]
    [Authorize(Policy = Policies.RequiresElevation)]
    [RequestSizeLimit(MaxLogoBytes + 1024)]
    public async Task<IActionResult> UploadLogo([FromRoute] string id, CancellationToken cancellationToken)
    {
        var bytes = await ReadLimitedAsync(Request.Body, MaxLogoBytes, cancellationToken).ConfigureAwait(false);
        if (bytes is null) return BadRequest(new { Message = "Le logo dépasse 2 Mo." });
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

    private async Task<IActionResult> StoreLogoAsync(string id, byte[] bytes)
    {
        if (!IdRegex().IsMatch(id)) return BadRequest(new { Message = "Identifiant invalide." });
        if (bytes.Length is 0 or > MaxLogoBytes) return BadRequest(new { Message = "Le logo doit faire entre 1 octet et 2 Mo." });
        if (ImageType(bytes) is null) return BadRequest(new { Message = "Format refusé : utilisez une image PNG, JPEG ou WebP." });
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
            await SaveUnlockedAsync(list).ConfigureAwait(false);
            return Ok(row);
        }
        finally
        {
            Lock.Release();
        }
    }

    /// <summary>Recognises PNG, JPEG and WebP by their signature; anything else (SVG, HTML…) is refused.</summary>
    internal static string? ImageType(byte[] head)
    {
        if (head.Length >= 8 && head[0] == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) return "image/png";
        if (head.Length >= 3 && head[0] == 0xFF && head[1] == 0xD8 && head[2] == 0xFF) return "image/jpeg";
        if (head.Length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F' && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return "image/webp";
        return null;
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
