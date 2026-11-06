package org.jellyfin.mobile.nflix.music

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.nflix.data.NflixApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Background music playback (lock screen, notification, Bluetooth, Android Auto controls) with
 * playback reporting to Jellyfin so progress, "recently played" and plugins stay in sync.
 */
class NflixMusicService : MediaSessionService(), KoinComponent {
    private val api: NflixApi by inject()
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var reportedItem: String? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(reporter)
        val activity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_PLAYER, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setId(SESSION_ID).setSessionActivity(activity).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        reportStop()
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private val reporter = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            reportStop()
            if (mediaItem?.mediaMetadata?.extras?.getBoolean(EXTRA_OFFLINE_LOCAL) != true) {
                mediaItem?.mediaId?.let { reportStart(it) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (reportedItem == null && player.currentMediaItem?.mediaMetadata?.extras?.getBoolean(EXTRA_OFFLINE_LOCAL) != true) {
                player.currentMediaItem?.mediaId?.let { reportStart(it) }
            } else reportProgress()
        }
    }

    private fun ticks() = player.currentPosition.coerceAtLeast(0) * 10_000

    private fun report(path: String, itemId: String, positionTicks: Long, paused: Boolean) {
        scope.launch {
            runCatching {
                api.send("POST", path, body = buildJsonObject {
                    put("ItemId", itemId)
                    put("PositionTicks", positionTicks)
                    put("IsPaused", paused)
                    put("CanSeek", true)
                    put("PlayMethod", "DirectStream")
                })
            }.onFailure { Timber.d(it, "Music report %s failed", path) }
        }
    }

    private fun reportStart(itemId: String) {
        reportedItem = itemId
        report("Sessions/Playing", itemId, ticks(), !player.isPlaying)
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL)
                reportProgress()
            }
        }
    }

    private fun reportProgress() {
        val item = reportedItem ?: return
        report("Sessions/Playing/Progress", item, ticks(), !player.isPlaying)
    }

    private fun reportStop() {
        val item = reportedItem ?: return
        progressJob?.cancel()
        report("Sessions/Playing/Stopped", item, ticks(), true)
        reportedItem = null
    }

    companion object {
        const val SESSION_ID = "nflix-music"
        const val EXTRA_OPEN_PLAYER = "fr.miracloud.nflix.extra.OPEN_PLAYER"
        const val EXTRA_OFFLINE_LOCAL = "fr.miracloud.nflix.extra.OFFLINE_LOCAL"
        private const val PROGRESS_INTERVAL = 10_000L
    }
}
