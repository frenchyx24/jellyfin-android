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

    /// <summary>Gets or sets the OIDC issuer of the Mira Connect app, whose tokens Nflix receives through the account bridge.</summary>
    public string MiraConnectIssuer { get; set; } = "https://auth.miracloud.fr/application/o/mira-connect-android/";

    /// <summary>Gets or sets the public OIDC client id of the Mira Connect app.</summary>
    public string MiraConnectClientId { get; set; } = "Y17SA3fNpxTeqXTUHRITcsYMaCqgbAVE3TxjNlf5";

    /// <summary>Gets or sets the group required in the token (mirrors the LDAP search filter). Empty disables the check.</summary>
    public string RequiredGroup { get; set; } = "plan_standard";

    /// <summary>Gets or sets the secret required to publish store releases (generated on first start).</summary>
    public string PublishToken { get; set; } = string.Empty;

    /// <summary>Gets or sets the key id of the Mira announcement secret (e.g. nflix-1). Set by the admin.</summary>
    public string AnnouncementKeyId { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the dedicated server-to-server HMAC secret (hex, 32+ bytes) shared with Mira's announcement
    /// server. Provisioned by the admin out of band; never generated here, never returned by any API.
    /// Empty disables pushes (fail closed).
    /// </summary>
    public string AnnouncementSecret { get; set; } = string.Empty;

    /// <summary>Gets or sets the previous key id, accepted during a rotation.</summary>
    public string AnnouncementPreviousKeyId { get; set; } = string.Empty;

    /// <summary>Gets or sets the previous secret, accepted during a rotation.</summary>
    public string AnnouncementPreviousSecret { get; set; } = string.Empty;
}

/// <summary>Nflix Auth plugin: exchanges a Mira Connect ID token for a Jellyfin session.</summary>
public sealed class Plugin : BasePlugin<PluginConfiguration>
{
    /// <summary>Initializes a new instance of the <see cref="Plugin"/> class.</summary>
    public Plugin(IApplicationPaths paths, IXmlSerializer serializer) : base(paths, serializer)
    {
        Instance = this;
        StoreDirectory = Path.Combine(paths.DataPath, "mira-store");
        StudiosDirectory = Path.Combine(paths.DataPath, "nflix-studios");
        AnnouncementsDirectory = Path.Combine(paths.DataPath, "nflix-announcements");
        if (string.IsNullOrEmpty(Configuration.PublishToken))
        {
            Configuration.PublishToken = Convert.ToHexString(System.Security.Cryptography.RandomNumberGenerator.GetBytes(32));
            SaveConfiguration();
        }
    }

    /// <summary>Gets the root directory of the Mira store release channels.</summary>
    public string StoreDirectory { get; }

    /// <summary>Gets the directory holding the distributor rows and their logos.</summary>
    public string StudiosDirectory { get; }

    /// <summary>Gets the directory holding Mira announcements, tombstones and the nonce cache.</summary>
    public string AnnouncementsDirectory { get; }

    /// <summary>Gets the running instance.</summary>
    public static Plugin? Instance { get; private set; }

    /// <inheritdoc />
    public override string Name => "Nflix Auth";

    /// <inheritdoc />
    public override string Description => "Connexion Mira Connect (Authentik OIDC) pour l'application Nflix Android.";

    /// <inheritdoc />
    public override Guid Id => Guid.Parse("5d1b6a1e-9f0c-4f7e-8a51-6e1f4b7c2a90");
}
