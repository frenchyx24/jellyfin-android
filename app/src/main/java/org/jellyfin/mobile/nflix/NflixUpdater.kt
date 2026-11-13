package org.jellyfin.mobile.nflix

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jellyfin.mobile.BuildConfig
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/** A newer Nflix release published on the Mira store channel. */
data class NflixRelease(val versionCode: Long, val versionName: String, val changelog: String, val sha256: String, val size: Long)

sealed interface NflixUpdateState {
    data object Idle : NflixUpdateState
    data class Available(val release: NflixRelease) : NflixUpdateState
    data class Downloading(val release: NflixRelease, val progress: Float) : NflixUpdateState
    data class Installing(val release: NflixRelease) : NflixUpdateState
    data class Failed(val release: NflixRelease, val message: String) : NflixUpdateState
}

/**
 * Checks the Mira store channel for a newer Nflix and installs it, preferably through Mira Connect
 * (which keeps update ownership and updates silently). Nflix never installs APKs itself.
 */
class NflixUpdater(private val context: Context) {
    private val prefs = context.getSharedPreferences("nflix_updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val base = BuildConfig.NFLIX_SERVER_URL.trimEnd('/') + "/NflixAuth/Store/nflix/"

    private val _state = MutableStateFlow<NflixUpdateState>(NflixUpdateState.Idle)
    val state: StateFlow<NflixUpdateState> = _state.asStateFlow()

    val miraConnectInstalled: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(MIRA_CONNECT, 0); true }.getOrDefault(false)

    /** Looks for an update; reminders for a postponed version come back after a day. */
    suspend fun check(force: Boolean = false) {
        val release = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(base + "manifest.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                    setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    val body = json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject
                    if (body["packageName"]?.jsonPrimitive?.contentOrNull != context.packageName.removeSuffix(".debug")) return@runCatching null
                    NflixRelease(
                        versionCode = body["versionCode"]?.jsonPrimitive?.longOrNull ?: return@runCatching null,
                        versionName = body["versionName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        changelog = body["changelog"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        sha256 = body["sha256"]?.jsonPrimitive?.contentOrNull.orEmpty().uppercase(),
                        size = body["size"]?.jsonPrimitive?.longOrNull ?: (body["size"]?.jsonPrimitive?.intOrNull?.toLong() ?: -1L),
                    )
                } finally {
                    connection.disconnect()
                }
            }.onFailure { Timber.w(it, "Nflix update check failed") }.getOrNull()
        } ?: return
        if (release.versionCode <= BuildConfig.VERSION_CODE) return
        val postponed = prefs.getLong(KEY_POSTPONED_CODE, 0) == release.versionCode &&
            System.currentTimeMillis() - prefs.getLong(KEY_POSTPONED_AT, 0) < REMIND_AFTER
        if (force || !postponed) _state.value = NflixUpdateState.Available(release)
    }

    fun postpone(release: NflixRelease) {
        prefs.edit {
            putLong(KEY_POSTPONED_CODE, release.versionCode)
            putLong(KEY_POSTPONED_AT, System.currentTimeMillis())
        }
        _state.value = NflixUpdateState.Idle
    }

    /** Asks Mira Connect to update Nflix; returns false when Mira Connect is unavailable. */
    fun updateWithMiraConnect(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MIRA_CONNECT)?.apply {
            putExtra(EXTRA_UPDATE_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess.also { if (it) _state.value = NflixUpdateState.Idle }
    }

    companion object {
        const val MIRA_CONNECT = "fr.miracloud.connect"
        const val EXTRA_UPDATE_PACKAGE = "fr.miracloud.connect.extra.UPDATE_PACKAGE"
        private const val KEY_POSTPONED_CODE = "postponed_code"
        private const val KEY_POSTPONED_AT = "postponed_at"
        private const val REMIND_AFTER = 24L * 60 * 60 * 1000
        private const val TIMEOUT = 15_000
    }
}
