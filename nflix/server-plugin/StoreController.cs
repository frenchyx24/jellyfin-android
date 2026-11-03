using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>
/// Mira app store release channel: Mira Connect downloads, installs and updates apps from here.
/// Files live in &lt;data&gt;/mira-store/&lt;slug&gt;/ (manifest.json, app.apk, icon.png).
/// Publishing requires the X-Mira-Publish-Token header (token stored in the plugin configuration).
/// </summary>
[ApiController]
[Route("NflixAuth")]
public sealed partial class StoreController : ControllerBase
{
    private const long MaxApkSize = 300L * 1024 * 1024;
    private readonly ILogger<StoreController> _logger;

    /// <summary>Initializes a new instance of the <see cref="StoreController"/> class.</summary>
    public StoreController(ILogger<StoreController> logger) => _logger = logger;

    [GeneratedRegex("^[a-z0-9][a-z0-9-]{0,40}$")]
    private static partial Regex SlugRegex();

    private static string? Directory(string slug)
    {
        var root = Plugin.Instance?.StoreDirectory;
        return root is null || !SlugRegex().IsMatch(slug) ? null : Path.Combine(root, slug);
    }

    /// <summary>Legacy Nflix manifest (Mira Connect 0.4).</summary>
    [HttpGet("Android/manifest.json")]
    [AllowAnonymous]
    public IActionResult LegacyManifest() => Manifest("nflix");

    /// <summary>Legacy Nflix APK.</summary>
    [HttpGet("Android/nflix.apk")]
    [AllowAnonymous]
    public IActionResult LegacyApk() => Apk("nflix");

    /// <summary>Legacy Nflix icon.</summary>
    [HttpGet("Android/icon.png")]
    [AllowAnonymous]
    public IActionResult LegacyIcon() => Icon("nflix");

    /// <summary>Latest release manifest of an app.</summary>
    [HttpGet("Store/{slug}/manifest.json")]
    [AllowAnonymous]
    public IActionResult Manifest([FromRoute] string slug)
    {
        Response.Headers.CacheControl = "no-cache";
        return Serve(slug, "manifest.json", "application/json; charset=utf-8", null);
    }

    /// <summary>Signed APK of the latest release.</summary>
    [HttpGet("Store/{slug}/app.apk")]
    [AllowAnonymous]
    public IActionResult Apk([FromRoute] string slug) => Serve(slug, "app.apk", "application/vnd.android.package-archive", slug + ".apk");

    /// <summary>Store icon.</summary>
    [HttpGet("Store/{slug}/icon.png")]
    [AllowAnonymous]
    public IActionResult Icon([FromRoute] string slug)
    {
        Response.Headers.CacheControl = "public, max-age=3600";
        return Serve(slug, "icon.png", "image/png", null);
    }

    /// <summary>Uploads the APK of the next release (raw body). It is only served once its manifest is published.</summary>
    [HttpPut("Store/{slug}/app.apk")]
    [AllowAnonymous]
    [DisableRequestSizeLimit]
    public async Task<IActionResult> UploadApk([FromRoute] string slug, CancellationToken cancellationToken)
    {
        if (!Authorized()) return Unauthorized();
        var dir = Directory(slug);
        if (dir is null) return BadRequest(new { Message = "Identifiant d'application invalide." });
        System.IO.Directory.CreateDirectory(dir);
        var staging = Path.Combine(dir, "app.apk.upload");
        await using (var file = System.IO.File.Create(staging))
        {
            var buffer = new byte[81920];
            long total = 0;
            int read;
            while ((read = await Request.Body.ReadAsync(buffer, cancellationToken).ConfigureAwait(false)) > 0)
            {
                total += read;
                if (total > MaxApkSize) return StatusCode(StatusCodes.Status413PayloadTooLarge);
                await file.WriteAsync(buffer.AsMemory(0, read), cancellationToken).ConfigureAwait(false);
            }
        }

        return Ok(new { Sha256 = await Sha256Async(staging, cancellationToken).ConfigureAwait(false) });
    }

    /// <summary>Uploads the store icon (raw PNG body).</summary>
    [HttpPut("Store/{slug}/icon.png")]
    [AllowAnonymous]
    public async Task<IActionResult> UploadIcon([FromRoute] string slug, CancellationToken cancellationToken)
    {
        if (!Authorized()) return Unauthorized();
        var dir = Directory(slug);
        if (dir is null) return BadRequest();
        System.IO.Directory.CreateDirectory(dir);
        var temp = Path.Combine(dir, "icon.png.upload");
        await using (var file = System.IO.File.Create(temp))
        {
            await Request.Body.CopyToAsync(file, cancellationToken).ConfigureAwait(false);
        }

        System.IO.File.Move(temp, Path.Combine(dir, "icon.png"), true);
        return NoContent();
    }

    /// <summary>Publishes a release: checks the uploaded APK against the manifest SHA-256, then swaps it in atomically.</summary>
    [HttpPut("Store/{slug}/manifest.json")]
    [AllowAnonymous]
    public async Task<IActionResult> Publish([FromRoute] string slug, CancellationToken cancellationToken)
    {
        if (!Authorized()) return Unauthorized();
        var dir = Directory(slug);
        if (dir is null) return BadRequest();
        JsonObject manifest;
        try
        {
            manifest = JsonNode.Parse(await new StreamReader(Request.Body, Encoding.UTF8).ReadToEndAsync(cancellationToken).ConfigureAwait(false))?.AsObject()
                ?? throw new JsonException();
        }
        catch (JsonException)
        {
            return BadRequest(new { Message = "Manifest JSON invalide." });
        }

        foreach (var key in new[] { "packageName", "versionCode", "versionName", "sha256" })
        {
            if (manifest[key] is null) return BadRequest(new { Message = $"Champ {key} manquant." });
        }

        var staging = Path.Combine(dir, "app.apk.upload");
        var live = Path.Combine(dir, "app.apk");
        var expected = manifest["sha256"]!.GetValue<string>().ToUpperInvariant();
        var source = System.IO.File.Exists(staging) ? staging : live;
        if (!System.IO.File.Exists(source)) return BadRequest(new { Message = "Aucun APK envoyé." });
        var actual = await Sha256Async(source, cancellationToken).ConfigureAwait(false);
        if (actual != expected) return BadRequest(new { Message = "L'empreinte SHA-256 ne correspond pas à l'APK envoyé.", Actual = actual });

        if (source == staging) System.IO.File.Move(staging, live, true);
        manifest["sha256"] = actual;
        manifest["size"] = new FileInfo(live).Length;
        manifest["apkUrl"] = $"/NflixAuth/Store/{slug}/app.apk";
        manifest["iconUrl"] = $"/NflixAuth/Store/{slug}/icon.png";
        manifest["publishedAt"] = DateTimeOffset.UtcNow.ToString("O");
        var temp = Path.Combine(dir, "manifest.json.upload");
        await System.IO.File.WriteAllTextAsync(temp, manifest.ToJsonString(new JsonSerializerOptions { WriteIndented = true }), cancellationToken).ConfigureAwait(false);
        System.IO.File.Move(temp, Path.Combine(dir, "manifest.json"), true);
        _logger.LogInformation("Mira store: published {Slug} {Version} ({Code})", slug, manifest["versionName"], manifest["versionCode"]);
        return Ok(manifest);
    }

    private bool Authorized()
    {
        var expected = Plugin.Instance?.Configuration.PublishToken;
        var given = Request.Headers["X-Mira-Publish-Token"].ToString();
        if (string.IsNullOrEmpty(expected) || string.IsNullOrEmpty(given)) return false;
        return CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(given));
    }

    private IActionResult Serve(string slug, string name, string contentType, string? downloadName)
    {
        var dir = Directory(slug);
        if (dir is null) return NotFound();
        var path = Path.Combine(dir, name);
        if (!System.IO.File.Exists(path)) return NotFound(new { Message = "Aucune version publiée." });
        return downloadName is null
            ? PhysicalFile(path, contentType, enableRangeProcessing: true)
            : PhysicalFile(path, contentType, downloadName, enableRangeProcessing: true);
    }

    private static async Task<string> Sha256Async(string path, CancellationToken cancellationToken)
    {
        await using var stream = System.IO.File.OpenRead(path);
        return Convert.ToHexString(await SHA256.HashDataAsync(stream, cancellationToken).ConfigureAwait(false));
    }
}
