using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Jellyfin.Plugin.NflixAuth;

/// <summary>Validated identity extracted from a Mira Connect ID token.</summary>
public sealed record MiraIdentity(string Username, IReadOnlyList<string> Groups);

/// <summary>Minimal RS256 OIDC ID token validator backed by the issuer JWKS.</summary>
public sealed class OidcValidator
{
    private static readonly SemaphoreSlim JwksLock = new(1, 1);
    private static readonly ConcurrentDictionary<string, DateTimeOffset> UsedTokens = new();
    private static readonly ConcurrentDictionary<string, (Dictionary<string, RSAParameters> Keys, DateTimeOffset Fetched)> KeysByIssuer = new();

    private readonly IHttpClientFactory _httpClientFactory;

    /// <summary>Initializes a new instance of the <see cref="OidcValidator"/> class.</summary>
    public OidcValidator(IHttpClientFactory httpClientFactory) => _httpClientFactory = httpClientFactory;

    /// <summary>Validates the token and marks it as used (single use).</summary>
    public async Task<MiraIdentity> ValidateAsync(string idToken, PluginConfiguration config, CancellationToken cancellationToken)
    {
        var parts = idToken.Split('.');
        if (parts.Length != 3) throw new UnauthorizedAccessException("Jeton Mira invalide.");

        using var header = JsonDocument.Parse(Base64UrlDecode(parts[0]));
        if (header.RootElement.GetProperty("alg").GetString() != "RS256") throw new UnauthorizedAccessException("Algorithme de signature refusé.");
        var kid = header.RootElement.TryGetProperty("kid", out var kidElement) ? kidElement.GetString() ?? string.Empty : string.Empty;

        // Pick the trusted (issuer, client id) pair named by the unverified "iss" claim, then verify the
        // signature against that issuer's JWKS only. Untrusted issuers are rejected before any network call.
        using var payload = JsonDocument.Parse(Base64UrlDecode(parts[1]));
        var claims = payload.RootElement;
        var claimedIssuer = claims.TryGetProperty("iss", out var issElement) ? issElement.GetString() ?? string.Empty : string.Empty;
        var trusted = TrustedIssuers(config).FirstOrDefault(t => string.Equals(t.Issuer, claimedIssuer, StringComparison.Ordinal));
        if (trusted.Issuer is null) throw new UnauthorizedAccessException("Émetteur du jeton refusé.");

        var key = await GetKeyAsync(trusted.Issuer, kid, cancellationToken).ConfigureAwait(false);
        using (var rsa = RSA.Create())
        {
            rsa.ImportParameters(key);
            var signed = Encoding.ASCII.GetBytes(parts[0] + "." + parts[1]);
            if (!rsa.VerifyData(signed, Base64UrlDecode(parts[2]), HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1))
                throw new UnauthorizedAccessException("Signature du jeton Mira invalide.");
        }

        var aud = claims.GetProperty("aud");
        var audienceOk = aud.ValueKind == JsonValueKind.Array
            ? aud.EnumerateArray().Any(a => a.GetString() == trusted.ClientId)
            : aud.GetString() == trusted.ClientId;
        if (!audienceOk) throw new UnauthorizedAccessException("Jeton destiné à une autre application.");

        var now = DateTimeOffset.UtcNow;
        var exp = DateTimeOffset.FromUnixTimeSeconds(claims.GetProperty("exp").GetInt64());
        if (exp < now.AddSeconds(-30)) throw new UnauthorizedAccessException("Jeton Mira expiré. Reconnectez-vous.");
        if (claims.TryGetProperty("iat", out var iat) && DateTimeOffset.FromUnixTimeSeconds(iat.GetInt64()) > now.AddMinutes(2))
            throw new UnauthorizedAccessException("Horodatage du jeton invalide.");

        // Single use: a captured token cannot open a second session.
        foreach (var entry in UsedTokens.Where(e => e.Value < now)) UsedTokens.TryRemove(entry.Key, out _);
        var fingerprint = Convert.ToHexString(SHA256.HashData(Encoding.ASCII.GetBytes(parts[2])));
        if (!UsedTokens.TryAdd(fingerprint, exp.AddMinutes(1))) throw new UnauthorizedAccessException("Jeton déjà utilisé. Reconnectez-vous.");

        var username = claims.TryGetProperty("preferred_username", out var u) ? u.GetString() : null;
        if (string.IsNullOrWhiteSpace(username)) throw new UnauthorizedAccessException("Le jeton ne contient pas de nom d'utilisateur.");
        var groups = claims.TryGetProperty("groups", out var g) && g.ValueKind == JsonValueKind.Array
            ? g.EnumerateArray().Select(x => x.GetString() ?? string.Empty).Where(x => x.Length > 0).ToList()
            : new List<string>();
        return new MiraIdentity(username, groups);
    }

    /// <summary>
    /// Issuers whose ID tokens open a Nflix session: the Nflix app's own OIDC client (browser sign-in) and the
    /// Mira Connect app (silent sign-in through its account bridge, used on a fresh install).
    /// </summary>
    private static IEnumerable<(string Issuer, string ClientId)> TrustedIssuers(PluginConfiguration config)
    {
        if (config.Issuer.Length > 0 && config.ClientId.Length > 0) yield return (config.Issuer, config.ClientId);
        if (config.MiraConnectIssuer.Length > 0 && config.MiraConnectClientId.Length > 0) yield return (config.MiraConnectIssuer, config.MiraConnectClientId);
    }

    private async Task<RSAParameters> GetKeyAsync(string issuer, string kid, CancellationToken cancellationToken)
    {
        if (KeysByIssuer.TryGetValue(issuer, out var cached) && cached.Fetched > DateTimeOffset.UtcNow.AddHours(-6) && TryGet(cached.Keys, kid, out var hit)) return hit;
        await JwksLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            KeysByIssuer.TryGetValue(issuer, out var entry);
            // Refresh at most once per minute to absorb unknown kids without hammering the issuer.
            if (entry.Keys is null || entry.Fetched < DateTimeOffset.UtcNow.AddMinutes(-1) || !TryGet(entry.Keys, kid, out _))
            {
                if (!issuer.StartsWith("https://", StringComparison.Ordinal)) throw new UnauthorizedAccessException("Émetteur non HTTPS refusé.");
                using var client = _httpClientFactory.CreateClient();
                client.Timeout = TimeSpan.FromSeconds(15);
                using var stream = await client.GetStreamAsync(new Uri(issuer + "jwks/"), cancellationToken).ConfigureAwait(false);
                using var jwks = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken).ConfigureAwait(false);
                var keys = new Dictionary<string, RSAParameters>();
                foreach (var k in jwks.RootElement.GetProperty("keys").EnumerateArray())
                {
                    if (k.GetProperty("kty").GetString() != "RSA") continue;
                    var id = k.TryGetProperty("kid", out var kidProp) ? kidProp.GetString() ?? string.Empty : string.Empty;
                    keys[id] = new RSAParameters
                    {
                        Modulus = Base64UrlDecode(k.GetProperty("n").GetString()!),
                        Exponent = Base64UrlDecode(k.GetProperty("e").GetString()!)
                    };
                }

                entry = (keys, DateTimeOffset.UtcNow);
                KeysByIssuer[issuer] = entry;
            }

            return TryGet(entry.Keys, kid, out var key) ? key : throw new UnauthorizedAccessException("Clé de signature Mira inconnue.");
        }
        finally
        {
            JwksLock.Release();
        }
    }

    private static bool TryGet(Dictionary<string, RSAParameters> keys, string kid, out RSAParameters key)
    {
        if (keys.TryGetValue(kid, out key)) return true;
        if (kid.Length == 0 && keys.Count == 1) { key = keys.Values.First(); return true; }
        return false;
    }

    private static byte[] Base64UrlDecode(string value)
    {
        var s = value.Replace('-', '+').Replace('_', '/');
        s = s.PadRight(s.Length + ((4 - (s.Length % 4)) % 4), '=');
        return Convert.FromBase64String(s);
    }
}
