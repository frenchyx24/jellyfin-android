using MediaBrowser.Common.Configuration;
using MediaBrowser.Common.Plugins;
using MediaBrowser.Model.Plugins;
using MediaBrowser.Model.Serialization;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>Plugin configuration for Mira Connect (Authentik) sign-in.</summary>
public sealed class PluginConfiguration : BasePluginConfiguration
{
    /// <summary>Gets or sets the OIDC issuer (must end with a slash).</summary>
    public string Issuer { get; set; } = "https://auth.miracloud.fr/application/o/nflix-android/";

    /// <summary>Gets or sets the public OIDC client id used by the Nflix Android app.</summary>
    public string ClientId { get; set; } = "C20bjJuhP51n0vq4j4lsKsuIeWkJGA4hnchvz99V";

    /// <summary>Gets or sets the group required in the token (mirrors the LDAP search filter). Empty disables the check.</summary>
    public string RequiredGroup { get; set; } = "plan_standard";
}

/// <summary>Nflix Auth plugin: exchanges a Mira Connect ID token for a Jellyfin session.</summary>
public sealed class Plugin : BasePlugin<PluginConfiguration>
{
    /// <summary>Initializes a new instance of the <see cref="Plugin"/> class.</summary>
    public Plugin(IApplicationPaths paths, IXmlSerializer serializer) : base(paths, serializer)
    {
        Instance = this;
        AndroidDirectory = Path.Combine(paths.DataPath, "nflix-android");
    }

    /// <summary>Gets the directory holding the published Android release (manifest.json, nflix.apk, icon.png).</summary>
    public string AndroidDirectory { get; }

    /// <summary>Gets the running instance.</summary>
    public static Plugin? Instance { get; private set; }

    /// <inheritdoc />
    public override string Name => "Nflix Auth";

    /// <inheritdoc />
    public override string Description => "Connexion Mira Connect (Authentik OIDC) pour l'application Nflix Android.";

    /// <inheritdoc />
    public override Guid Id => Guid.Parse("5d1b6a1e-9f0c-4f7e-8a51-6e1f4b7c2a90");
}
