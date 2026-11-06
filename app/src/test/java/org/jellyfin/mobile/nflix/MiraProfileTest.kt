package org.jellyfin.mobile.nflix

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class MiraProfileTest : FunSpec({
    fun token(payload: String) = "header.${payload.encodeUtf8().base64Url().trimEnd('=')}.signature"

    test("accepted token provides display profile") {
        val profile = MiraProfile.fromAcceptedToken(token("""{"name":"Élodie","email":"test@example.invalid","picture":"https://auth.example.invalid/photo.png","groups":["plan_standard"]}"""))
        profile?.name shouldBe "Élodie"
        profile?.email shouldBe "test@example.invalid"
        profile?.avatar shouldBe "https://auth.example.invalid/photo.png"
        profile?.groups shouldBe setOf("plan_standard")
    }
    test("missing name falls back to username and old fields are not retained") {
        MiraProfile.fromAcceptedToken(token("""{"preferred_username":"alice"}""")) shouldBe MiraProfile("alice", null, null, emptySet())
    }
    test("avatar prefers Authentik attributes then falls back to accepted token picture") {
        val attributes = MiraProfile.fromAcceptedToken(token("""{"attributes":{"avatar":"https://auth.example.invalid/custom.png"},"picture":"https://auth.example.invalid/picture.png"}"""))
        attributes?.avatar shouldBe "https://auth.example.invalid/custom.png"
        val picture = MiraProfile.fromAcceptedToken(token("""{"picture":"https://auth.example.invalid/picture.png"}"""))
        picture?.avatar shouldBe "https://auth.example.invalid/picture.png"
    }
    test("malformed tokens and claims do not crash login") {
        listOf("", "a.b", "a.!.c", token("[]"), token("{bad}")).forEach {
            MiraProfile.fromAcceptedToken(it) shouldBe null
        }
    }
    test("unsafe avatars are rejected") {
        listOf("http://example.invalid/a", "javascript:alert(1)", "/photo.png", "https://user:password@example.invalid/a", "https://").forEach {
            MiraProfile.safeAvatar(it) shouldBe null
        }
    }
    test("small inline avatars are accepted, oversized or foreign ones are not") {
        val jpeg = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(ByteArray(2048) { it.toByte() })
        MiraProfile.safeAvatar(jpeg) shouldBe jpeg
        MiraProfile.inlineBytes(jpeg)?.size shouldBe 2048
        val huge = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(ByteArray(MiraProfile.MAX_INLINE_AVATAR + 1))
        MiraProfile.safeAvatar(huge) shouldBe null
        listOf("data:image/svg+xml;base64,PHN2Zz4=", "data:text/html;base64,PGgxPg==", "data:image/png;base64,").forEach {
            MiraProfile.safeAvatar(it) shouldBe null
        }
    }
    test("initials follow Authentik: first letters of the first two words") {
        MiraProfile.initials("Noah") shouldBe "N"
        MiraProfile.initials("  élodie   martin dupont") shouldBe "ÉM"
        MiraProfile.initials(null) shouldBe "?"
        MiraProfile.initials("   ") shouldBe "?"
    }
    test("absent profile claims remain optional") {
        MiraProfile.fromAcceptedToken(token("{}")) shouldBe MiraProfile(null, null, null, emptySet())
    }
})
