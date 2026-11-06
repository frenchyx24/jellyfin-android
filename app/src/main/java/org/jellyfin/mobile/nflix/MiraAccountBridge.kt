package org.jellyfin.mobile.nflix

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.security.MessageDigest

/** Result of a trusted account exchange with the Mira Connect app. */
data class MiraAccount(
    val idToken: String,
    val expiresAt: Long,
    val name: String,
    val email: String,
    val avatar: String?,
    val groups: List<String>,
)

/**
 * Reads one freshly refreshed identity token from Mira Connect.
 *
 * The provider is exported, so the caller proves it talks to the real Mira app
 * (package name, single signer) before sending any challenge. Only a short-lived
 * ID token and display metadata cross the boundary: never access or refresh tokens.
 */
class MiraAccountBridge(private val context: Context) {
    fun account(): MiraAccount? = runCatching {
        if (!isMiraTrusted()) return null
        val uri = Uri.parse("content://$AUTHORITY")
        val nonce = challenge()
        val extras = Bundle().apply { putString(EXTRA_CHALLENGE, nonce) }
        val reply = context.contentResolver.call(uri, METHOD_SESSION, null, extras) ?: return null
        if (reply.getString(EXTRA_CHALLENGE) != nonce) return null
        val token = reply.getString(EXTRA_ID_TOKEN) ?: return null
        val expiresAt = reply.getLong(EXTRA_EXPIRES_AT, 0L)
        if (token.isBlank() || expiresAt <= System.currentTimeMillis() / 1000L + 5L) return null
        MiraAccount(
            idToken = token,
            expiresAt = expiresAt,
            name = reply.getString(EXTRA_NAME).orEmpty(),
            email = reply.getString(EXTRA_EMAIL).orEmpty(),
            avatar = reply.getString(EXTRA_AVATAR),
            groups = reply.getStringArrayList(EXTRA_GROUPS).orEmpty(),
        )
    }.getOrNull()

    /** Silent sign-in must never open a browser or prompt the user. */
    fun isMiraInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(MIRA_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private fun isMiraTrusted(): Boolean = runCatching {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val info = pm.getPackageInfo(MIRA_PACKAGE, flags)
        val provider = pm.resolveContentProvider(AUTHORITY, 0) ?: return false
        val expectedPackage = info.packageName == MIRA_PACKAGE && provider.packageName == MIRA_PACKAGE && provider.applicationInfo.uid == info.applicationInfo?.uid
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
        val uniqueSigner = signers?.size == 1 && signers[0].let { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { byte -> "%02X".format(byte) } == MIRA_SIGNER
        }
        expectedPackage && uniqueSigner
    }.getOrDefault(false)

    private fun challenge(): String {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }

    private companion object {
        const val AUTHORITY = "fr.miracloud.connect.nflix.account"
        const val METHOD_SESSION = "session"
        const val EXTRA_CHALLENGE = "challenge"
        const val EXTRA_ID_TOKEN = "id_token"
        const val EXTRA_EXPIRES_AT = "expires_at"
        const val EXTRA_NAME = "name"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_AVATAR = "avatar"
        const val EXTRA_GROUPS = "groups"
        const val MIRA_PACKAGE = "fr.miracloud.connect"
        const val MIRA_SIGNER = "3C7E2866A82ADEA668857EE07B46995975C0096EA1642D590689A9EC1B320A64"
    }
}
