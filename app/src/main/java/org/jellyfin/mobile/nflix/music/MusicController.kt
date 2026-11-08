package org.jellyfin.mobile.nflix.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.JellyfinRepository

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val artwork: String?,
    val durationMs: Long,
)

@Immutable
data class MusicState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/** UI side of the music player: a MediaController bound to [NflixMusicService]. */
class MusicController(
    private val context: Context,
    private val repository: JellyfinRepository,
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager,
) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()
    private val offlineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let { return action(it) }
        pending += action
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, NflixMusicService::class.java))
        val built = MediaController.Builder(context, token).buildAsync()
        future = built
        built.addListener({
            val ready = runCatching { built.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = ready
            ready.addListener(listener)
            publish()
            pending.toList().forEach { it(ready) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    /** Reconnects to a session that survived the activity (music keeps playing in background). */
    fun connect() = withController { publish() }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    private fun MediaItem.toTrack(): Track = Track(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString(),
        albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID),
        artwork = mediaMetadata.artworkUri?.toString(),
        durationMs = mediaMetadata.durationMs ?: 0,
    )

    private fun publish() {
        val player = controller ?: return
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() }
        _state.value = MusicState(
            current = player.currentMediaItem?.toTrack()?.let { track ->
                if (track.durationMs <= 0 && player.duration > 0) track.copy(durationMs = player.duration) else track
            },
            isPlaying = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            queue = queue,
            index = player.currentMediaItemIndex,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: (_state.value.current?.durationMs ?: 0L)

    private fun Item.toMediaItem(localUri: Uri? = null, localArtwork: Uri? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artistItems.joinToString { it.name }.ifBlank { albumArtist ?: "" })
            .setAlbumTitle(album)
            .setArtworkUri(localArtwork ?: repository.primary(this, 600)?.let(Uri::parse))
            .setDurationMs(runTimeTicks?.div(10_000))
            .setExtras(android.os.Bundle().apply { putString(EXTRA_ALBUM_ID, albumId) })
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder().setMediaId(id).setUri(localUri ?: Uri.parse(repository.audioStreamUrl(id))).setMediaMetadata(metadata).build()
    }

    fun play(items: List<Item>, startIndex: Int = 0, shuffle: Boolean = false, startPositionMs: Long = 0L) {
        val tracks = items.filter { it.type == "Audio" || it.type == "AudioBook" }
        if (tracks.isEmpty()) return
        offlineScope.launch {
            val mediaItems = tracks.map { item ->
                val local = runCatching {
                    val id = java.util.UUID.fromString(item.id)
                    val download = downloadDao.getDownloadByItemId(id) ?: return@runCatching null
                    if (download.status != DownloadStatus.DOWNLOADED) return@runCatching null
                    val files = downloadDao.getFiles(download.id)
                    if (!storageManager.verify(DownloadFiles(download, files))) return@runCatching null
                    val audio = files.firstOrNull { it.type == DownloadFileType.ITEM }?.uri ?: return@runCatching null
                    val artwork = files.firstOrNull { it.type == DownloadFileType.IMAGE_PRIMARY }?.uri
                    audio to artwork
                }.getOrNull()
                item.toMediaItem(local?.first, local?.second)
            }
            withContext(Dispatchers.Main) {
                withController { player ->
                    player.shuffleModeEnabled = shuffle
                    player.setMediaItems(
                        mediaItems,
                        if (shuffle) (tracks.indices).random() else startIndex.coerceIn(0, tracks.lastIndex),
                        if (shuffle) 0L else startPositionMs.coerceAtLeast(0L),
                    )
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    /** Plays YouTube Music results through the butler stream proxy. */
    fun playYt(tracks: List<org.jellyfin.mobile.nflix.data.YtTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val items = tracks.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title.ifBlank { "YouTube Music" })
                .setArtist(track.artist)
                .setArtworkUri(track.thumbnail.takeIf { it.isNotBlank() }?.let(Uri::parse))
                // Unknown length (search results carry no duration): leave it unset, 0 would show a dead progress bar.
                .apply { if (track.duration > 0) setDurationMs(track.duration.toLong() * 1000L) }
                .setIsPlayable(true)
                .build()
            MediaItem.Builder()
                .setMediaId("yt:" + track.videoId)
                .setUri(Uri.parse(repository.ytStreamUrl(track.videoId)))
                .setMediaMetadata(metadata)
                .build()
        }
        withController { player ->
            player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            player.prepare()
            player.play()
        }
    }

    fun playLocal(uri: Uri, title: String) = withController { player ->
        val mediaItem = MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setIsPlayable(true)
                    .setExtras(android.os.Bundle().apply { putBoolean(NflixMusicService.EXTRA_OFFLINE_LOCAL, true) })
                    .build(),
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun enqueue(items: List<Item>, next: Boolean = false) = withController { player ->
        val mediaItems = items.filter { it.type == "Audio" }.map { it.toMediaItem() }
        if (player.mediaItemCount == 0) {
            player.setMediaItems(mediaItems)
            player.prepare()
            player.play()
        } else if (next) {
            player.addMediaItems(player.currentMediaItemIndex + 1, mediaItems)
        } else {
            player.addMediaItems(mediaItems)
        }
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipTo(index: Int) = withController { it.seekTo(index, 0) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    private companion object {
        const val EXTRA_ALBUM_ID = "albumId"
    }
}
