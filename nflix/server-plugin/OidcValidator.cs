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
    private static Dictionary<string, RSAParameters> _keys = new();
    private static DateTimeOffset _keysFetched = DateTimeOffset.MinValue;
    private static string _keysIssuer = string.Empty;

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

        var key = await GetKeyAsync(config.Issuer, kid, cancellationToken).ConfigureAwait(false);
        using (var rsa = RSA.Create())
        {
            rsa.ImportParameters(key);
            var signed = Encoding.ASCII.GetBytes(parts[0] + "." + parts[1]);
            if (!rsa.VerifyData(signed, Base64UrlDecode(parts[2]), HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1))
                throw new UnauthorizedAccessException("Signature du jeton Mira invalide.");
        }

        using var payload = JsonDocument.Parse(Base64UrlDecode(parts[1]));
        var claims = payload.RootElement;
        if (claims.GetProperty("iss").GetString() != config.Issuer) throw new UnauthorizedAccessException("Émetteur du jeton refusé.");
        var aud = claims.GetProperty("aud");
        var audienceOk = aud.ValueKind == JsonValueKind.Array
            ? aud.EnumerateArray().Any(a => a.GetString() == config.ClientId)
            : aud.GetString() == config.ClientId;
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

    private async Task<RSAParameters> GetKeyAsync(string issuer, string kid, CancellationToken cancellationToken)
    {
        if (_keysIssuer == issuer && _keysFetched > DateTimeOffset.UtcNow.AddHours(-6) && TryGet(kid, out var cached)) return cached;
        await JwksLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            // Refresh at most once per minute to absorb unknown kids without hammering the issuer.
            if (_keysIssuer != issuer || _keysFetched < DateTimeOffset.UtcNow.AddMinutes(-1) || !TryGet(kid, out _))
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

                _keys = keys;
                _keysIssuer = issuer;
                _keysFetched = DateTimeOffset.UtcNow;
            }

            return TryGet(kid, out var key) ? key : throw new UnauthorizedAccessException("Clé de signature Mira inconnue.");
        }
        finally
        {
            JwksLock.Release();
        }
    }

    private static bool TryGet(string kid, out RSAParameters key)
    {
        if (_keys.TryGetValue(kid, out key)) return true;
        if (kid.Length == 0 && _keys.Count == 1) { key = _keys.Values.First(); return true; }
        return false;
    }

    private static byte[] Base64UrlDecode(string value)
    {
        var s = value.Replace('-', '+').Replace('_', '/');
        s = s.PadRight(s.Length + ((4 - (s.Length % 4)) % 4), '=');
        return Convert.FromBase64String(s);
    }
}
