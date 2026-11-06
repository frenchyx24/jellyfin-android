package org.jellyfin.mobile.nflix

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.mobile.BuildConfig

/**
 * Nflix session metadata shared between the native login and the embedded web client.
 *
 * The access token itself lives in the upstream Room user table; this class keeps the
 * Jellyfin identifiers the web client needs to pick the session up without a second login.
 */
class NflixSession(context: Context) {
    private val prefs = context.getSharedPreferences("nflix_session", Context.MODE_PRIVATE)

    /** Access token of the signed-in user, mirrored from the Room user entity at startup. */
    @Volatile
    var accessToken: String? = null

    var serverId: String?
        get() = prefs.getString(KEY_SERVER_ID, null)
        set(value) = prefs.edit { putString(KEY_SERVER_ID, value) }

    /** Jellyfin user id exactly as returned by the server (32 hex characters, no dashes). */
    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit { putString(KEY_USER_ID, value) }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit { putString(KEY_USER_NAME, value) }

    var lastMethod: String?
        get() = prefs.getString(KEY_METHOD, null)
        set(value) = prefs.edit { putString(KEY_METHOD, value) }

    var miraName: String?
        get() = prefs.getString(KEY_MIRA_NAME, null)
        set(value) = prefs.edit { putString(KEY_MIRA_NAME, value) }

    var miraEmail: String?
        get() = prefs.getString(KEY_MIRA_EMAIL, null)
        set(value) = prefs.edit { putString(KEY_MIRA_EMAIL, value) }

    var miraAvatar: String? by mutableStateOf(prefs.getString(KEY_MIRA_AVATAR, null))
        private set

    var miraGroups: Set<String>
        get() = prefs.getStringSet(KEY_MIRA_GROUPS, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_MIRA_GROUPS, value) }

    internal fun setMiraProfile(profile: MiraProfile?) {
        miraAvatar = MiraProfile.safeAvatar(profile?.avatar)
        prefs.edit {
            putString(KEY_MIRA_NAME, profile?.name)
            putString(KEY_MIRA_EMAIL, profile?.email)
            putString(KEY_MIRA_AVATAR, miraAvatar)
            putStringSet(KEY_MIRA_GROUPS, profile?.groups.orEmpty())
        }
    }

    fun clear() {
        accessToken = null
        miraAvatar = null
        prefs.edit {
            remove(KEY_USER_ID)
            remove(KEY_SERVER_ID)
            remove(KEY_USER_NAME)
            remove(KEY_METHOD)
            remove(KEY_MIRA_NAME)
            remove(KEY_MIRA_EMAIL)
            remove(KEY_MIRA_AVATAR)
            remove(KEY_MIRA_GROUPS)
        }
    }

    /**
     * Builds the `jellyfin_credentials` local storage entry so jellyfin-web starts signed in.
     * Returns null when no native session exists.
     */
    fun webCredentials(): String? {
        val token = accessToken ?: return null
        val user = userId ?: return null
        return buildJsonObject {
            put(
                "Servers",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            serverId?.let { put("Id", it) }
                            put("Name", "Nflix")
                            put("ManualAddress", BuildConfig.NFLIX_SERVER_URL)
                            put("manualAddressOnly", true)
                            put("LastConnectionMode", 2)
                            put("DateLastAccessed", System.currentTimeMillis())
                            put("UserId", user)
                            put("AccessToken", token)
                        },
                    )
                },
            )
        }.toString()
    }

    private companion object {
        const val KEY_SERVER_ID = "server_id"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_METHOD = "method"
        const val KEY_MIRA_NAME = "mira_name"
        const val KEY_MIRA_EMAIL = "mira_email"
        const val KEY_MIRA_AVATAR = "mira_avatar"
        const val KEY_MIRA_GROUPS = "mira_groups"
    }
}
