package org.jellyfin.mobile.nflix

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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
 * (which keeps update ownership and updates silently), otherwise directly.
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

    fun canInstallDirectly(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads, verifies the SHA-256 and installs the release with the system installer. */
    suspend fun installDirectly(release: NflixRelease) {
        val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, "nflix-${release.versionCode}.apk")
        try {
            _state.value = NflixUpdateState.Downloading(release, 0f)
            val digest = withContext(Dispatchers.IO) { download(release, file) }
            if (!digest.equals(release.sha256, ignoreCase = true)) throw IOException("Fichier corrompu : empreinte invalide.")
            _state.value = NflixUpdateState.Installing(release)
            withContext(Dispatchers.IO) { commit(file) }
        } catch (e: Exception) {
            Timber.e(e, "Nflix self update failed")
            file.delete()
            _state.value = NflixUpdateState.Failed(release, e.message ?: "Mise à jour impossible.")
        }
    }

    private fun download(release: NflixRelease, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = (URL(base + "app.apk").openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT
            readTimeout = 60_000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Téléchargement refusé (${connection.responseCode}).")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
            var read = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        read += count
                        if (total > 0) _state.value = NflixUpdateState.Downloading(release, (read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    private fun commit(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output, BUFFER)
                    session.fsync(output)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, sessionId, Intent(context, NflixUpdateReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallFailed(message: String) {
        val release = when (val current = _state.value) {
            is NflixUpdateState.Installing -> current.release
            is NflixUpdateState.Downloading -> current.release
            is NflixUpdateState.Available -> current.release
            is NflixUpdateState.Failed -> current.release
            NflixUpdateState.Idle -> return
        }
        _state.value = NflixUpdateState.Failed(release, message)
    }

    companion object {
        const val MIRA_CONNECT = "fr.miracloud.connect"
        const val EXTRA_UPDATE_PACKAGE = "fr.miracloud.connect.extra.UPDATE_PACKAGE"
        private const val KEY_POSTPONED_CODE = "postponed_code"
        private const val KEY_POSTPONED_AT = "postponed_at"
        private const val REMIND_AFTER = 24L * 60 * 60 * 1000
        private const val TIMEOUT = 15_000
        private const val BUFFER = 64 * 1024
    }
}

/** Shows the system confirmation for direct updates and reports failures. */
class NflixUpdateReceiver : BroadcastReceiver(), org.koin.core.component.KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = getKoin().get<NflixUpdater>()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is restarted on the new version
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.onInstallFailed("Mise à jour annulée.")
            PackageInstaller.STATUS_FAILURE_CONFLICT -> updater.onInstallFailed("Signature différente : désinstallez puis réinstallez Nflix.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> updater.onInstallFailed("Espace de stockage insuffisant.")
            else -> updater.onInstallFailed("Installation impossible.")
        }
    }
}
