package org.jellyfin.mobile.nflix.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.ApiClientController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Per-kind switches and the small local ledger of notifications already posted. */
class AlertPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nflix_alerts", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER, true)
        set(value) { prefs.edit { putBoolean(KEY_MASTER, value) }; _version.value++ }

    fun isEnabled(kind: AlertKind): Boolean = masterEnabled && prefs.getBoolean("kind_${kind.wire}", true)

    fun kindSwitch(kind: AlertKind): Boolean = prefs.getBoolean("kind_${kind.wire}", true)

    fun setEnabled(kind: AlertKind, enabled: Boolean) {
        prefs.edit { putBoolean("kind_${kind.wire}", enabled) }
        _version.value++
    }

    /** Ids posted on this phone; trimmed to the most recent ones. */
    fun posted(): Set<Long> = prefs.getStringSet(KEY_POSTED, emptySet()).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

    fun markPosted(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val all = (posted() + ids).sortedDescending().take(400)
        prefs.edit { putStringSet(KEY_POSTED, all.mapTo(HashSet()) { it.toString() }) }
    }

    fun forgetPosted(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val left = posted() - ids.toSet()
        prefs.edit { putStringSet(KEY_POSTED, left.mapTo(HashSet()) { it.toString() }) }
    }

    /** Badges already reported as unlocked, so only new ones notify. */
    fun knownBadges(): Set<String>? = prefs.getStringSet(KEY_BADGES, null)

    fun setKnownBadges(ids: Set<String>) = prefs.edit { putStringSet(KEY_BADGES, ids) }

    var lastRun: Long
        get() = prefs.getLong(KEY_LAST_RUN, 0)
        set(value) = prefs.edit { putLong(KEY_LAST_RUN, value) }

    fun clearAccount() = prefs.edit { remove(KEY_POSTED); remove(KEY_BADGES); remove(KEY_LAST_RUN) }

    private companion object {
        const val KEY_MASTER = "master"
        const val KEY_POSTED = "posted"
        const val KEY_BADGES = "known_badges"
        const val KEY_LAST_RUN = "last_run"
    }
}

/** Android side of the alerts: channel, notifications and the periodic worker. */
object NflixAlerts {
    const val CHANNEL = "nflix_alerts"
    const val EXTRA_OPEN_ITEM = "org.jellyfin.mobile.nflix.OPEN_ITEM"
    private const val WORK = "nflix-alerts"
    private const val WORK_NOW = "nflix-alerts-now"
    private val ITEM_ID = Regex("[0-9a-fA-F]{32}")

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alertes Nflix", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Demandes Horus disponibles, nouveaux épisodes des séries suivies et succès."
            },
        )
    }

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canNotify(context: Context): Boolean {
        if (!permissionGranted(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Opens the title page of [itemId] when tapped; falls back to the app home. */
    fun post(context: Context, alert: NflixAlert): Boolean {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        alert.itemId?.takeIf { ITEM_ID.matches(it) }?.let { intent.putExtra(EXTRA_OPEN_ITEM, it) }
        val open = PendingIntent.getActivity(context, alert.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setColor(0xFFE50914.toInt())
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setGroup("nflix-alerts")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        return runCatching { NotificationManagerCompat.from(context).notify("nflix-alert", alert.id.toInt(), notification) }
            .onFailure { Timber.w(it, "Alert notification refused") }
            .isSuccess
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NflixAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One immediate pass, e.g. right after the user allowed notifications. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<NflixAlertWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).run {
            cancelUniqueWork(WORK)
            cancelUniqueWork(WORK_NOW)
        }
        AlertPrefs(context).clearAccount()
    }
}

/**
 * Hook for alerts produced on the phone itself (achievement unlocks): returns the alerts to post.
 * Registered by the app; absent in a cold worker process it simply yields nothing.
 */
fun interface LocalAlertSource {
    suspend fun poll(token: String, prefs: AlertPrefs): List<NflixAlert>
}

/** Koin holder: generic lists cannot be told apart by type. */
class LocalAlertSources(val sources: List<LocalAlertSource>)

/** Polls the alerts service, posts notifications and acknowledges what was handled. */
class NflixAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val controller: ApiClientController by inject()
    private val client: AlertsClient by inject()
    private val localSources: LocalAlertSources by inject()

    override suspend fun doWork(): Result {
        val token = runCatching { controller.loadSavedUser()?.accessToken }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val context = applicationContext
        val prefs = AlertPrefs(context)
        if (!prefs.masterEnabled) return Result.success()
        NflixAlerts.createChannel(context)
        val canNotify = NflixAlerts.canNotify(context)
        val now = System.currentTimeMillis() / 1000
        val remote = try {
            client.alerts(token)
        } catch (e: AlertsHttpException) {
            Timber.w("Alerts service answered %d", e.code)
            return if (e.code == 401) Result.success() else Result.retry()
        } catch (e: java.io.IOException) {
            return Result.retry()
        }
        val plan = AlertDispatch.plan(remote, prefs::isEnabled, canNotify, prefs.posted(), now)
        val posted = plan.notify.filter { NflixAlerts.post(context, it) }.map { it.id }
        prefs.markPosted(posted)
        val handled = plan.ack + posted
        runCatching { client.ack(token, handled) }
            .onSuccess { prefs.forgetPosted(handled) }
            .onFailure { Timber.w("Alerts ack failed, retried next run") }
        if (canNotify) {
            localSources.sources.forEach { source ->
                runCatching { source.poll(token, prefs) }.getOrDefault(emptyList())
                    .filter { prefs.isEnabled(AlertKind.of(it.kind) ?: return@filter false) }
                    .forEach { NflixAlerts.post(context, it) }
            }
        }
        prefs.lastRun = now
        return Result.success()
    }
}
