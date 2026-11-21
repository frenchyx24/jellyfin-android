package org.jellyfin.mobile.data

/** Authenticated symmetric cipher used to seal access tokens at rest. Implementations may throw. */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * Encodes Jellyfin access tokens for storage as `enc1:<hex>` and decodes them back.
 *
 * Never throws: a broken cipher (e.g. a wiped Android Keystore) makes the token unreadable,
 * which callers treat as "signed out". Legacy plaintext values are re-sealed on first read.
 */
class TokenCodec(private val cipher: TokenCipher) {

    /** Result of reading a stored value: the usable [token] and, when [needsRewrite], what to store instead. */
    data class Decoded(val token: String?, val storeAs: String?, val needsRewrite: Boolean)

    /** Seals [token] for storage, or returns null when it is null/blank or the cipher fails. */
    fun encode(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return runCatching { PREFIX + toHex(cipher.encrypt(token.toByteArray(Charsets.UTF_8))) }.getOrNull()
    }

    fun decode(stored: String?): Decoded {
        if (stored.isNullOrBlank()) return Decoded(token = null, storeAs = stored, needsRewrite = false)
        if (stored.startsWith(PREFIX)) {
            val token = runCatching {
                cipher.decrypt(fromHex(stored.substring(PREFIX.length))).toString(Charsets.UTF_8)
            }.getOrNull()
            return if (token.isNullOrBlank()) {
                // Unreadable token: drop it so the user signs in again.
                Decoded(token = null, storeAs = null, needsRewrite = true)
            } else {
                Decoded(token = token, storeAs = stored, needsRewrite = false)
            }
        }
        // Legacy plaintext: migrate. If sealing fails the keystore is unusable, so sign out.
        val sealed = encode(stored)
        return if (sealed == null) {
            Decoded(token = null, storeAs = null, needsRewrite = true)
        } else {
            Decoded(token = stored, storeAs = sealed, needsRewrite = true)
        }
    }

    /** Decodes [stored] and calls [persist] with the replacement value when it must be rewritten. */
    fun read(stored: String?, persist: (String?) -> Unit): String? {
        val decoded = decode(stored)
        if (decoded.needsRewrite) runCatching { persist(decoded.storeAs) }
        return decoded.token
    }

    companion object {
        const val PREFIX = "enc1:"
        private const val HEX = "0123456789abcdef"

        internal fun toHex(bytes: ByteArray): String {
            val out = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
            }
            return out.toString()
        }

        internal fun fromHex(text: String): ByteArray {
            require(text.length % 2 == 0) { "odd hex length" }
            return ByteArray(text.length / 2) { i ->
                val hi = Character.digit(text[i * 2], 16)
                val lo = Character.digit(text[i * 2 + 1], 16)
                require(hi >= 0 && lo >= 0) { "invalid hex" }
                ((hi shl 4) or lo).toByte()
            }
        }
    }
}
