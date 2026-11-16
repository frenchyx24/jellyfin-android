package org.jellyfin.mobile.nflix.family

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.nflix.MiraProfile
import org.jellyfin.mobile.nflix.NflixLogin
import org.jellyfin.mobile.nflix.NflixSession
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * Swaps the app session between the family head and the profiles of their family.
 *
 * The head's own session is kept (encrypted) in [store] while another profile is in use, so
 * "Revenir à mon profil" needs no new sign-in. Applying a session reloads the whole app
 * ([restart]): every cache, the music player and the Horus identity start fresh for the profile.
 */
class FamilySwitcher(
    private val api: NflixApi,
    private val session: NflixSession,
    val repo: FamilyRepository,
    val store: ProfileSwitchStore,
    private val apply: suspend (login: NflixLogin, method: String) -> Unit,
    private val restart: () -> Unit,
) {
    /** Busy flag for the UI while a switch runs. */
    var busy by mutableStateOf(false)
        private set

    val state: SwitchState? get() = store.state

    val isSwitched: Boolean get() = store.state != null

    /** Switches to [member]; [pin] is required when the profile has one. Throws [FamilyException]. */
    suspend fun switchTo(member: FamilyMemberInfo, pin: String?) {
        if (ProfileSwitchLogic.sameUser(member.userId, session.userId)) return
        val current = store.state
        val headId = ProfileSwitchLogic.headUserId(current, session.userId)
        if (ProfileSwitchLogic.sameUser(member.userId, headId)) {
            switchBack()
            return
        }
        val token = ProfileSwitchLogic.authToken(current, session.accessToken) ?: throw FamilyException("Session expirée. Reconnectez-vous.")
        busy = true
        try {
            val target = member.userId.toUUIDOrNull()?.toString() ?: throw FamilyException("Profil invalide.")
            val deviceId = ProfileSwitchLogic.profileDeviceId(api.deviceId, session.userId?.toUUIDOrNull()?.toString(), target)
            val login = repo.switchTo(member.userId, pin, token, deviceId, android.os.Build.MODEL ?: "Android", BuildConfig.VERSION_NAME)
            val currentAccount = currentAccount()
            val next = ProfileSwitchLogic.afterSwitch(current, currentAccount, login.rawUserId, member.displayName)
            // A previous switched profile's session is no longer needed.
            if (current != null) repo.logoutToken(currentAccount.token)
            store.save(next)
            apply(login, "family")
            // The profile shows its own name, not the head's Mira identity; the household plan stays.
            session.setMiraProfile(MiraProfile(null, null, null, currentAccount.miraGroups.ifEmpty { session.miraGroups }))
            FamilyBadges.reset()
            restart()
        } finally {
            busy = false
        }
    }

    /** Back to the head's own profile with the stored session. Throws [FamilyException] if it expired. */
    suspend fun switchBack() {
        val current = store.state ?: return
        busy = true
        try {
            val head = current.head
            if (!repo.tokenValid(head.token)) {
                store.clear()
                throw FamilyException("La session du profil principal a expiré. Reconnectez-vous.")
            }
            session.accessToken?.let { repo.logoutToken(it) }
            val uuid = head.userId.toUUIDOrNull() ?: throw FamilyException("Profil principal invalide.")
            store.save(null)
            store.skipPickerOnce = true
            apply(NflixLogin(uuid, head.userId, head.userName, head.token, head.serverId), head.method ?: "password")
            session.setMiraProfile(MiraProfile(head.miraName, head.miraEmail, head.miraAvatar, head.miraGroups))
            FamilyBadges.reset()
            restart()
        } finally {
            busy = false
        }
    }

    /** Called on sign-out: forget the stored head session and log it out on the server. */
    suspend fun forget() {
        store.state?.head?.token?.let { repo.logoutToken(it) }
        store.clear()
        FamilyBadges.reset()
    }

    private fun currentAccount() = SessionAccount(
        userId = session.userId ?: "",
        userName = session.userName ?: "",
        token = session.accessToken ?: "",
        method = session.lastMethod,
        serverId = session.serverId,
        miraName = session.miraName,
        miraEmail = session.miraEmail,
        miraAvatar = session.miraAvatar,
        miraGroups = session.miraGroups,
    )
}
