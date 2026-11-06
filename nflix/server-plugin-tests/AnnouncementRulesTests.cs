using System.Text;
using Jellyfin.Plugin.NflixAuth;
using Xunit;

public class AnnouncementRulesTests
{
    private static readonly byte[] Secret = Enumerable.Range(1, 32).Select(i => (byte)i).ToArray();
    private static readonly DateTimeOffset Now = DateTimeOffset.FromUnixTimeSeconds(1_800_000_000);
    private const string Path = "/NflixAuth/Announcements/abc";
    private const string Nonce = "n0nce-0123456789abcdef";

    private static MiraAnnouncement Valid(string id = "abc") => new()
    {
        Id = id,
        Targets = new() { "mira", "nflix" },
        Title = "Maintenance",
        Message = "Coupure ce soir.",
        Level = "info",
        ActionUrl = "https://miracloud.fr/statut",
        UpdatedAt = Now,
        Audience = new() { Groups = new() { "plan_pro" } },
    };

    private static Dictionary<string, byte[]> Keys() => new() { ["nflix-1"] = Secret };

    private static string Signature(string keyId = "nflix-1", string? ts = null, string nonce = Nonce, string method = "PUT", string path = Path, string body = "{}") =>
        "v1=" + AnnouncementRules.Sign(Secret, keyId, ts ?? Now.ToUnixTimeSeconds().ToString(), nonce, method, path, Encoding.UTF8.GetBytes(body));

    [Fact]
    public void Valid_announcement_passes() => Assert.Empty(AnnouncementRules.Validate(Valid(), "abc"));

    [Fact]
    public void Announcement_without_nflix_target_is_refused()
    {
        var a = Valid();
        a.Targets = new() { "mira" };
        Assert.Contains("cible nflix absente", AnnouncementRules.Validate(a, "abc"));
    }

    [Theory]
    [InlineData("http://miracloud.fr")]
    [InlineData("javascript:alert(1)")]
    [InlineData("intent://x")]
    [InlineData("https://")]
    public void Non_https_links_are_refused(string url)
    {
        var a = Valid();
        a.ActionUrl = url;
        Assert.Contains("actionUrl doit être en https", AnnouncementRules.Validate(a, "abc"));
    }

    [Fact]
    public void Id_must_match_route_and_shape()
    {
        Assert.Contains("id différent de l'URL", AnnouncementRules.Validate(Valid(), "other"));
        Assert.False(AnnouncementRules.IsValidId("../etc/passwd"));
        Assert.False(AnnouncementRules.IsValidId("mira:abc"));
    }

    [Fact]
    public void Expiry_must_follow_start_and_updatedAt_is_required()
    {
        var a = Valid();
        a.StartsAt = Now;
        a.ExpiresAt = Now;
        a.UpdatedAt = null;
        var errors = AnnouncementRules.Validate(a, "abc");
        Assert.Contains("expiresAt doit suivre startsAt", errors);
        Assert.Contains("updatedAt obligatoire", errors);
    }

    [Fact]
    public void Active_window_and_retention()
    {
        var a = Valid();
        a.StartsAt = Now.AddHours(1);
        Assert.False(AnnouncementRules.IsActive(a, Now));
        a.StartsAt = null;
        a.ExpiresAt = Now.AddMinutes(-1);
        Assert.False(AnnouncementRules.IsActive(a, Now));
        Assert.False(AnnouncementRules.IsStale(a, Now));
        Assert.True(AnnouncementRules.IsStale(a, Now.AddDays(31)));
    }

    [Fact]
    public void Upsert_is_idempotent_on_updatedAt()
    {
        var stored = Valid();
        Assert.Equal(UpsertOutcome.Created, AnnouncementRules.Compare(null, Valid()));
        Assert.Equal(UpsertOutcome.Unchanged, AnnouncementRules.Compare(stored, Valid()));
        var older = Valid();
        older.UpdatedAt = Now.AddMinutes(-5);
        Assert.Equal(UpsertOutcome.Unchanged, AnnouncementRules.Compare(stored, older));
        var newer = Valid();
        newer.UpdatedAt = Now.AddMinutes(5);
        Assert.Equal(UpsertOutcome.Updated, AnnouncementRules.Compare(stored, newer));
    }

    [Fact]
    public void Older_put_cannot_recreate_a_deleted_announcement()
    {
        Assert.Equal(UpsertOutcome.Unchanged, AnnouncementRules.Compare(null, Valid(), deletedAt: Now.AddSeconds(10)));
        var reAdded = Valid();
        reAdded.UpdatedAt = Now.AddMinutes(1);
        Assert.Equal(UpsertOutcome.Created, AnnouncementRules.Compare(null, reAdded, deletedAt: Now.AddSeconds(10)));
    }

    [Fact]
    public void Valid_signature_is_accepted()
    {
        var ts = Now.ToUnixTimeSeconds().ToString();
        Assert.Equal(SignatureCheck.Valid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, Nonce, Signature(), "PUT", Path, Encoding.UTF8.GetBytes("{}"), Now));
    }

    [Fact]
    public void Tampered_body_path_method_nonce_or_key_id_is_rejected()
    {
        var ts = Now.ToUnixTimeSeconds().ToString();
        var body = Encoding.UTF8.GetBytes("{}");
        var sig = Signature();
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, Nonce, sig, "PUT", Path, Encoding.UTF8.GetBytes("{ }"), Now));
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, Nonce, sig, "PUT", "/NflixAuth/Announcements/xyz", body, Now));
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, Nonce, sig, "DELETE", Path, body, Now));
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, "another-nonce-0123456", sig, "PUT", Path, body, Now));
        // Same secret under another key id: the key id is signed, so the signature no longer matches.
        var rotated = new Dictionary<string, byte[]> { ["nflix-1"] = Secret, ["nflix-2"] = Secret };
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(rotated, "nflix-2", ts, Nonce, sig, "PUT", Path, body, Now));
    }

    [Fact]
    public void Clock_skew_unknown_key_missing_nonce_and_no_configuration_fail_closed()
    {
        var body = Encoding.UTF8.GetBytes("{}");
        var old = Now.AddSeconds(-301).ToUnixTimeSeconds().ToString();
        Assert.Equal(SignatureCheck.ClockSkew, AnnouncementRules.Verify(Keys(), "nflix-1", old, Nonce, Signature(ts: old), "PUT", Path, body, Now));
        // A wrong signature with a stale clock is never reported as clock skew.
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", old, Nonce, Signature(), "PUT", Path, body, Now));
        var ts = Now.ToUnixTimeSeconds().ToString();
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-9", ts, Nonce, Signature(), "PUT", Path, body, Now));
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, null, Signature(), "PUT", Path, body, Now));
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, "short", Signature(nonce: "short"), "PUT", Path, body, Now));
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(new Dictionary<string, byte[]>(), "nflix-1", ts, Nonce, Signature(), "PUT", Path, body, Now));
        Assert.Equal(SignatureCheck.Invalid, AnnouncementRules.Verify(Keys(), "nflix-1", ts, Nonce, Signature().Replace("v1=", "v2="), "PUT", Path, body, Now));
    }

    [Fact]
    public void Rotation_accepts_both_keys()
    {
        var second = Enumerable.Range(100, 32).Select(i => (byte)i).ToArray();
        var keys = new Dictionary<string, byte[]> { ["nflix-1"] = Secret, ["nflix-2"] = second };
        var ts = Now.ToUnixTimeSeconds().ToString();
        var body = Encoding.UTF8.GetBytes("{}");
        var sig2 = "v1=" + AnnouncementRules.Sign(second, "nflix-2", ts, Nonce, "PUT", Path, body);
        Assert.Equal(SignatureCheck.Valid, AnnouncementRules.Verify(keys, "nflix-1", ts, Nonce, Signature(), "PUT", Path, body, Now));
        Assert.Equal(SignatureCheck.Valid, AnnouncementRules.Verify(keys, "nflix-2", ts, Nonce, sig2, "PUT", Path, body, Now));
    }

    [Fact]
    public void Short_or_malformed_secrets_are_ignored()
    {
        Assert.Null(AnnouncementRules.ParseSecret(""));
        Assert.Null(AnnouncementRules.ParseSecret("abcd"));
        Assert.Null(AnnouncementRules.ParseSecret(new string('z', 64)));
        Assert.NotNull(AnnouncementRules.ParseSecret(Convert.ToHexString(Secret)));
    }

    [Fact]
    public void Nonce_is_single_use_within_ttl_and_survives_restart()
    {
        var file = System.IO.Path.GetTempFileName();
        try
        {
            var cache = new NonceCache(file);
            Assert.True(cache.TryUse(Nonce, Now));
            Assert.False(cache.TryUse(Nonce, Now.AddMinutes(9)));
            var restarted = new NonceCache(file);
            Assert.False(restarted.TryUse(Nonce, Now.AddMinutes(10)));
            Assert.True(restarted.TryUse(Nonce, Now.AddMinutes(16)));
        }
        finally
        {
            File.Delete(file);
        }
    }
}
