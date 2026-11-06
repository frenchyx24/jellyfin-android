package org.jellyfin.mobile.nflix

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64

internal data class MiraProfile(
    val name: String?,
    val email: String?,
    val avatar: String?,
    val groups: Set<String>,
) {
    companion object {
        // Display metadata only. Call after the server has accepted the ID token; never use these claims for permissions.
        fun fromAcceptedToken(token: String): MiraProfile? = runCatching {
            require(token.length <= 128 * 1024)
            val parts = token.split('.')
            require(parts.size == 3)
            val payload = parts[1].decodeBase64()?.utf8() ?: return null
            val claims = Json.parseToJsonElement(payload) as? JsonObject ?: return null
            fun text(key: String) = (claims[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            val attributes = claims["attributes"] as? JsonObject
            MiraProfile(
                name = text("name") ?: text("preferred_username"),
                email = text("email"),
                avatar = safeAvatar((attributes?.get("avatar") as? JsonPrimitive)?.contentOrNull) ?: safeAvatar(text("picture")),
                groups = (claims["groups"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                }?.toSet().orEmpty(),
            )
        }.getOrNull()

        /** Authentik "initials" avatar text: first letters of the first two words of the name. */
        fun initials(name: String?): String = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.take(2)
            ?.mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }?.joinToString("")?.ifBlank { null } ?: "?"

        /** Largest inline avatar accepted (decoded bytes); Mira sends a downscaled JPEG well below this. */
        const val MAX_INLINE_AVATAR = 200 * 1024
        private val inlineAvatar = Regex("^data:image/(jpeg|png|webp);base64,([A-Za-z0-9+/=]+)$")

        /** HTTPS URL without credentials, or a small inline JPEG/PNG/WebP data URI. Anything else is dropped. */
        fun safeAvatar(value: String?): String? {
            if (value == null) return null
            if (value.startsWith("data:")) return value.takeIf { inlineBytes(it) != null }
            val url = value.toHttpUrlOrNull() ?: return null
            return url.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }?.toString()
        }

        /** Decoded image bytes of an accepted inline avatar, or null. */
        fun inlineBytes(value: String): ByteArray? {
            if (value.length > MAX_INLINE_AVATAR * 4 / 3 + 64) return null
            val data = inlineAvatar.matchEntire(value)?.groupValues?.get(2) ?: return null
            return data.decodeBase64()?.toByteArray()?.takeIf { it.isNotEmpty() && it.size <= MAX_INLINE_AVATAR }
        }
    }
}
