package org.jellyfin.mobile.nflix.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.nflix.NflixSession
import org.jellyfin.mobile.nflix.NflixUpdater
import org.jellyfin.mobile.nflix.data.HorusRepository
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.JellyfinRepository
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.data.PluginRepository
import org.jellyfin.mobile.nflix.music.MusicController
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

/** Everything screens need, provided once through a composition local. */
@Stable
class AppDeps(
    val api: NflixApi,
    val jellyfin: JellyfinRepository,
    val plugins: PluginRepository,
    val horus: HorusRepository,
    val music: MusicController,
    val events: ActivityEventHandler,
    val session: NflixSession,
    val updater: NflixUpdater,
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager,
    val onSignOut: () -> Unit,
    val onOpenWeb: () -> Unit,
) {
    /** Administration tools; the server enforces rights, the app only hides what a member cannot use. */
    val admin: org.jellyfin.mobile.nflix.data.AdminRepository by lazy { org.jellyfin.mobile.nflix.data.AdminRepository(api, jellyfin, horus) }

    /** Starts the right native player for any item. */
    fun play(scope: CoroutineScope, item: Item, fromStart: Boolean = false, navigator: Navigator? = null) {
        scope.launch {
            runCatching {
                when (item.type) {
                    "Movie", "Episode", "Video", "MusicVideo", "Trailer" -> playVideos(scope, listOf(item.id), 0, if (fromStart) null else item.userData?.positionTicks)
                    "Series" -> {
                        val next = jellyfin.items(parentId = item.id, includeTypes = "Episode", extra = mapOf("sortBy" to "ParentIndexNumber,IndexNumber", "sortOrder" to "Ascending", "isPlayed" to false), limit = 1).items.firstOrNull()
                            ?: jellyfin.items(parentId = item.id, includeTypes = "Episode", extra = mapOf("sortBy" to "ParentIndexNumber,IndexNumber", "sortOrder" to "Ascending"), limit = 1).items.firstOrNull()
                        next?.let { playVideos(scope, listOf(it.id), 0, if (fromStart) null else it.userData?.positionTicks) }
                    }
                    "Season" -> {
                        val episodes = jellyfin.episodes(item.seriesId ?: return@runCatching, item.id)
                        val start = episodes.indexOfFirst { it.userData?.played != true }.coerceAtLeast(0)
                        playVideos(scope, episodes.map { it.id }, start, episodes.getOrNull(start)?.userData?.positionTicks)
                    }
                    "Audio", "AudioBook" -> music.play(listOf(item))
                    "MusicAlbum" -> music.play(jellyfin.albumTracks(item.id))
                    "MusicArtist" -> music.play(jellyfin.instantMix(item.id))
                    "Playlist" -> {
                        val items = jellyfin.playlistItems(item.id)
                        if (items.any { it.type == "Audio" }) music.play(items) else playVideos(scope, items.filter { it.isPlayableVideo }.map { it.id }, 0, null)
                    }
                    "BoxSet" -> {
                        val items = jellyfin.children(item.id, "ProductionYear,SortName").filter { it.isPlayableVideo }
                        playVideos(scope, items.map { it.id }, 0, null)
                    }
                    "Book" -> navigator?.open(Route.Reader(item.id))
                    else -> Unit
                }
            }.onFailure { Timber.e(it, "Unable to play %s", item.id) }
        }
    }

    fun playVideos(scope: CoroutineScope, ids: List<String>, startIndex: Int, resumeTicks: Long?) {
        val uuids = ids.mapNotNull { it.toUUIDOrNull() }
        if (uuids.isEmpty()) return
        val selectedId = uuids.getOrNull(startIndex.coerceIn(0, uuids.lastIndex)) ?: uuids.first()
        scope.launch {
            val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val download = downloadDao.getDownloadByItemId(selectedId) ?: return@withContext false
                if (download.status != DownloadStatus.DOWNLOADED) return@withContext false
                storageManager.verify(DownloadFiles(download, downloadDao.getFiles(download.id)))
            }
            events.emit(
                ActivityEvent.LaunchNativePlayer(
                    PlayOptions(
                        ids = if (local) listOf(selectedId) else uuids,
                        mediaSourceId = selectedId.takeIf { local }?.toString(),
                        startIndex = if (local) 0 else startIndex,
                        startPosition = resumeTicks?.takeIf { it > 0 }?.ticks,
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        playFromDownloads = local,
                    ),
                ),
            )
        }
    }

    fun download(item: Item) {
        item.id.toUUIDOrNull()?.let { events.emit(ActivityEvent.DownloadItems(listOf(it))) }
    }

    suspend fun downloadComplete(item: Item) {
        val items = when (item.type) {
            "Series" -> jellyfin.items(parentId = item.id, includeTypes = "Episode", limit = 10_000).items
            "Season" -> item.seriesId?.let { jellyfin.episodes(it, item.id) }.orEmpty()
            "MusicAlbum" -> jellyfin.albumTracks(item.id)
            "MusicArtist" -> jellyfin.artistAlbums(item.id).flatMap { jellyfin.albumTracks(it.id) }
            "Playlist" -> jellyfin.playlistItems(item.id)
            else -> listOf(item)
        }
        downloadItems(items.map { it.id })
    }

    fun downloadItems(ids: Collection<String>) {
        ids.mapNotNull { it.toUUIDOrNull() }.distinct().takeIf { it.isNotEmpty() }
            ?.let { events.emit(ActivityEvent.DownloadItems(it)) }
    }

    fun openUrl(url: String) = events.emit(ActivityEvent.OpenUrl(url))
    fun openDownloads() = events.emit(ActivityEvent.OpenDownloads)
    fun openSettings() = events.emit(ActivityEvent.OpenSettings)
}

val LocalDeps = staticCompositionLocalOf<AppDeps> { error("No AppDeps") }
