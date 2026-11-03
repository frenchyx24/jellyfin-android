using System.ComponentModel.DataAnnotations;
using Jellyfin.Database.Implementations.Enums;
using MediaBrowser.Controller.Library;
using MediaBrowser.Controller.Session;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>Body of a Mira Connect sign-in request.</summary>
public sealed class MiraLoginRequest
{
    /// <summary>Gets or sets the OIDC ID token issued by Authentik.</summary>
    [Required] public string IdToken { get; set; } = string.Empty;

    /// <summary>Gets or sets the device id.</summary>
    [Required] public string DeviceId { get; set; } = string.Empty;

    /// <summary>Gets or sets the device name.</summary>
    [Required] public string DeviceName { get; set; } = string.Empty;

    /// <summary>Gets or sets the client application name.</summary>
    [Required] public string App { get; set; } = string.Empty;

    /// <summary>Gets or sets the client application version.</summary>
    [Required] public string AppVersion { get; set; } = string.Empty;
}

/// <summary>Mira Connect sign-in endpoints.</summary>
[ApiController]
[Route("NflixAuth")]
public sealed class NflixAuthController : ControllerBase
{
    private readonly ISessionManager _sessionManager;
    private readonly IUserManager _userManager;
    private readonly OidcValidator _validator;
    private readonly ILogger<NflixAuthController> _logger;

    /// <summary>Initializes a new instance of the <see cref="NflixAuthController"/> class.</summary>
    public NflixAuthController(ISessionManager sessionManager, IUserManager userManager, IHttpClientFactory httpClientFactory, ILogger<NflixAuthController> logger)
    {
        _sessionManager = sessionManager;
        _userManager = userManager;
        _validator = new OidcValidator(httpClientFactory);
        _logger = logger;
    }

    private static PluginConfiguration Config => Plugin.Instance?.Configuration ?? new PluginConfiguration();

    /// <summary>Public client configuration consumed by the Android app.</summary>
    [HttpGet("Config")]
    [AllowAnonymous]
    public IActionResult GetConfig() => Ok(new { Config.Issuer, Config.ClientId, Version = "1.1.0" });

    /// <summary>Latest Nflix Android release, consumed by Mira Connect to install and update the app.</summary>
    [HttpGet("Android/manifest.json")]
    [AllowAnonymous]
    public IActionResult AndroidManifest()
    {
        Response.Headers.CacheControl = "no-cache";
        return AndroidFile("manifest.json", "application/json; charset=utf-8", null);
    }

    /// <summary>Signed Nflix Android APK.</summary>
    [HttpGet("Android/nflix.apk")]
    [AllowAnonymous]
    public IActionResult AndroidApk() => AndroidFile("nflix.apk", "application/vnd.android.package-archive", "Nflix.apk");

    /// <summary>Nflix Android icon for store listings.</summary>
    [HttpGet("Android/icon.png")]
    [AllowAnonymous]
    public IActionResult AndroidIcon()
    {
        Response.Headers.CacheControl = "public, max-age=86400";
        return AndroidFile("icon.png", "image/png", null);
    }

    private IActionResult AndroidFile(string name, string contentType, string? downloadName)
    {
        var directory = Plugin.Instance?.AndroidDirectory;
        if (directory is null) return NotFound();
        var path = Path.Combine(directory, name);
        if (!System.IO.File.Exists(path)) return NotFound(new { Message = "Aucune version Android publiée." });
        return downloadName is null
            ? PhysicalFile(path, contentType, enableRangeProcessing: true)
            : PhysicalFile(path, contentType, downloadName, enableRangeProcessing: true);
    }

    /// <summary>Exchanges a Mira Connect ID token for a Jellyfin session.</summary>
    [HttpPost("Mira")]
    [AllowAnonymous]
    [ProducesResponseType(StatusCodes.Status200OK)]
    public async Task<IActionResult> Mira([FromBody] MiraLoginRequest request, CancellationToken cancellationToken)
    {
        var remote = HttpContext.Connection.RemoteIpAddress?.ToString() ?? string.Empty;
        MiraIdentity identity;
        try
        {
            identity = await _validator.ValidateAsync(request.IdToken, Config, cancellationToken).ConfigureAwait(false);
        }
        catch (Exception e) when (e is UnauthorizedAccessException or FormatException or System.Text.Json.JsonException or KeyNotFoundException or InvalidOperationException)
        {
            _logger.LogWarning("Nflix Auth: rejected Mira token from {Remote}: {Message}", remote, e.Message);
            return Unauthorized(new { Message = e is UnauthorizedAccessException ? e.Message : "Jeton Mira invalide." });
        }
        catch (HttpRequestException e)
        {
            _logger.LogError(e, "Nflix Auth: unable to reach the Mira issuer");
            return StatusCode(StatusCodes.Status502BadGateway, new { Message = "Mira Connect est injoignable. Réessayez." });
        }

        if (Config.RequiredGroup.Length > 0 && !identity.Groups.Contains(Config.RequiredGroup, StringComparer.OrdinalIgnoreCase))
        {
            _logger.LogWarning("Nflix Auth: {User} lacks group {Group}", identity.Username, Config.RequiredGroup);
            return StatusCode(StatusCodes.Status403Forbidden, new { Message = "Votre abonnement Mira ne donne pas accès à Nflix." });
        }

        var user = _userManager.GetUserByName(identity.Username);
        if (user is null)
        {
            return NotFound(new { Message = "Compte Nflix pas encore activé. Connectez-vous une première fois avec votre identifiant et mot de passe Mira." });
        }

        if (user.Permissions.Any(p => p.Kind == PermissionKind.IsDisabled && p.Value))
        {
            return StatusCode(StatusCodes.Status403Forbidden, new { Message = "Ce compte Nflix est désactivé." });
        }

        try
        {
            var result = await _sessionManager.AuthenticateDirect(new AuthenticationRequest
            {
                UserId = user.Id,
                Username = user.Username,
                App = request.App,
                AppVersion = request.AppVersion,
                DeviceId = request.DeviceId,
                DeviceName = request.DeviceName,
                RemoteEndPoint = remote
            }).ConfigureAwait(false);
            _logger.LogInformation("Nflix Auth: {User} signed in with Mira Connect on {Device}", user.Username, request.DeviceName);
            return Ok(result);
        }
        catch (System.Security.SecurityException e)
        {
            return StatusCode(StatusCodes.Status403Forbidden, new { e.Message });
        }
    }
}
