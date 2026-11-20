package org.jellyfin.mobile.nflix.family

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A signed-in account as the app needs it to restore a session. */
@Serializable
data class SessionAccount(
    val userId: String,
    val userName: String,
    val token: String,
    val method: String? = null,
    val serverId: String? = null,
    val miraName: String? = null,
    val miraEmail: String? = null,
    val miraAvatar: String? = null,
    val miraGroups: Set<String> = emptySet(),
)

/** While a head uses another profile: the head's own account, and the profile in use. */
@Serializable
data class SwitchState(val head: SessionAccount, val activeUserId: String, val activeName: String)

/** Pure rules of the profile switch (unit-tested). */
object ProfileSwitchLogic {
    fun sameUser(a: String?, b: String?): Boolean =
        a != null && b != null && a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)

    /** "Changer de profil" is offered to a head, or to anyone currently inside a switched profile. */
    fun canSwitch(isHead: Boolean, state: SwitchState?): Boolean = isHead || state != null

    /** The "Qui regarde ?" screen shows once per app start, only to a head using their own profile. */
    fun shouldShowPicker(isHead: Boolean, state: SwitchState?, enabled: Boolean, alreadyShown: Boolean, skipOnce: Boolean): Boolean =
        isHead && state == null && enabled && !alreadyShown && !skipOnce

    /** The Switch route is always authorized with the head's token, never the switched profile's. */
    fun authToken(state: SwitchState?, currentToken: String?): String? = state?.head?.token ?: currentToken

    /** The account that owns the switcher: the stored head, or the current account. */
    fun headUserId(state: SwitchState?, currentUserId: String?): String? = state?.head?.userId ?: currentUserId

    /**
     * New state after switching to [targetUserId]. Switching to the head (or to oneself while not
     * switched) clears the state; otherwise the head stays the one saved first.
     */
    fun afterSwitch(state: SwitchState?, current: SessionAccount, targetUserId: String, targetName: String): SwitchState? {
        val head = state?.head ?: current
        return if (sameUser(head.userId, targetUserId)) null else SwitchState(head, targetUserId, targetName)
    }

    /** Device id for a profile: the app's base device id plus the profile's user id (as the SDK does). */
    fun profileDeviceId(currentDeviceId: String, currentUserUuid: String?, targetUserUuid: String): String {
        val base = currentUserUuid?.let { currentDeviceId.removeSuffix(it) } ?: currentDeviceId
        return base + targetUserUuid
    }
}

/**
 * Persists the [SwitchState]. The head token is encrypted with an AES-GCM key kept in the Android
 * Keystore (never exported), so it cannot be read from a backup or from the preferences file.
 */
class ProfileSwitchStore(context: Context) {
    private val prefs = context.getSharedPreferences("nflix_family", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var state: SwitchState? = load()
        private set

    /** User setting: show "Qui regarde ?" at start (heads only). */
    var pickerEnabled: Boolean
        get() = prefs.getBoolean(KEY_PICKER, true)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_PICKER, value) }

    /** Last known head flag, so the picker can show before the network answers. */
    var lastKnownHead: Boolean
        get() = prefs.getBoolean(KEY_HEAD, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_HEAD, value) }

    /** Set when coming back to the head profile, so the picker does not pop up right away. */
    var skipPickerOnce: Boolean
        get() = prefs.getBoolean(KEY_SKIP, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_SKIP, value) }

    fun save(newState: SwitchState?) {
        state = newState
        if (newState == null) {
            prefs.edit(commit = true) { remove(KEY_STATE) }
            return
        }
        // A broken keystore must not crash the caller; the in-memory state still applies.
        runCatching {
            val plain = json.encodeToString(SwitchState.serializer(), newState).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.iv + cipher.doFinal(plain)
            prefs.edit(commit = true) { putString(KEY_STATE, Base64.encodeToString(sealed, Base64.NO_WRAP)) }
        }
    }

    fun clear() {
        save(null)
        prefs.edit(commit = true) { remove(KEY_HEAD); remove(KEY_SKIP) }
    }

    private fun load(): SwitchState? {
        val stored = prefs.getString(KEY_STATE, null) ?: return null
        return runCatching {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
            }
            val plain = cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE)
            json.decodeFromString(SwitchState.serializer(), plain.toString(Charsets.UTF_8))
        }.getOrElse {
            prefs.edit(commit = true) { remove(KEY_STATE) }
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val KEY_ALIAS = "nflix_family_head"
        const val KEY_STATE = "switch_state"
        const val KEY_PICKER = "picker_enabled"
        const val KEY_HEAD = "last_known_head"
        const val KEY_SKIP = "skip_picker_once"
    }
}

/** Live counts for the badges on the Famille entry and the home avatar. */
object FamilyBadges {
    var unread by mutableIntStateOf(0)
        private set
    var pending by mutableIntStateOf(0)
        private set
    var me by mutableStateOf<FamilyMe?>(null)
        private set

    val total: Int get() = unread + pending

    fun update(value: FamilyMe) {
        me = value
        unread = value.unreadMessageCount
        pending = value.pendingRequestCount + value.pendingFriendCount
    }

    fun reset() {
        me = null
        unread = 0
        pending = 0
    }
}
