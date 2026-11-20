package org.jellyfin.mobile.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

/** Reversible fake: XOR with a fixed byte plus a 1-byte tag, so tampering is detected. */
private class FakeCipher(var broken: Boolean = false) : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        check(!broken) { "keystore unavailable" }
        return byteArrayOf(TAG) + plain.map { (it.toInt() xor 0x5A).toByte() }
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        check(!broken) { "keystore unavailable" }
        check(sealed.isNotEmpty() && sealed[0] == TAG) { "bad tag" }
        return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    private companion object {
        const val TAG: Byte = 0x42
    }
}

class TokenCodecTest : FunSpec({
    val token = "0123456789abcdef0123456789abcdef"

    test("encode seals with enc1 prefix and decode round-trips") {
        val codec = TokenCodec(FakeCipher())
        val stored = codec.encode(token)!!
        stored shouldStartWith TokenCodec.PREFIX
        stored shouldNotContain token
        codec.decode(stored) shouldBe TokenCodec.Decoded(token, stored, needsRewrite = false)
    }

    test("null and blank tokens are stored as null") {
        val codec = TokenCodec(FakeCipher())
        codec.encode(null) shouldBe null
        codec.encode("") shouldBe null
        codec.decode(null) shouldBe TokenCodec.Decoded(null, null, needsRewrite = false)
    }

    test("plaintext is migrated on first read") {
        val codec = TokenCodec(FakeCipher())
        var persisted: String? = "untouched"
        codec.read(token) { persisted = it } shouldBe token
        persisted shouldNotBe "untouched"
        persisted!! shouldStartWith TokenCodec.PREFIX
        // Second read of the migrated value needs no rewrite.
        var again = false
        codec.read(persisted) { again = true } shouldBe token
        again shouldBe false
    }

    test("broken keystore on read signs out and clears the stored token") {
        val cipher = FakeCipher()
        val codec = TokenCodec(cipher)
        val stored = codec.encode(token)
        cipher.broken = true
        var persisted: String? = "untouched"
        codec.read(stored) { persisted = it } shouldBe null
        persisted shouldBe null
    }

    test("broken keystore during migration signs out without crashing") {
        val codec = TokenCodec(FakeCipher(broken = true))
        var persisted: String? = "untouched"
        codec.read(token) { persisted = it } shouldBe null
        persisted shouldBe null
        codec.encode(token) shouldBe null
    }

    test("corrupted sealed value is treated as signed out") {
        val codec = TokenCodec(FakeCipher())
        codec.decode("enc1:zz").token shouldBe null
        codec.decode("enc1:00ff").needsRewrite shouldBe true
    }

    test("persist failure does not crash the read") {
        val codec = TokenCodec(FakeCipher())
        codec.read(token) { error("db locked") } shouldBe token
    }

    test("hex helpers round-trip all byte values") {
        val bytes = ByteArray(256) { it.toByte() }
        TokenCodec.fromHex(TokenCodec.toHex(bytes)).toList() shouldBe bytes.toList()
    }
})
