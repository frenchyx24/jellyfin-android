package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.model.api.MediaType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadsViewModel : ViewModel(), KoinComponent {

    private val downloadDao: DownloadDao by inject()
    private val downloadManager: DownloadManager by inject()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val storageManager: StorageManager by inject()

    val downloads: StateFlow<List<DownloadFiles>> = downloadDao
        .getAllDownloadsWithFiles()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun openDownload(download: DownloadEntity) {
        when (download.item.mediaType) {
            MediaType.VIDEO -> {
                val playOptions = PlayOptions(
                    ids = listOf(download.itemId),
                    mediaSourceId = download.itemId.toString(),
                    startIndex = 0,
                    startPosition = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    playFromDownloads = true,
                )
                activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
            }

            MediaType.AUDIO,
            MediaType.PHOTO,
            MediaType.UNKNOWN -> {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadDao.getFiles(download.id)
                            .firstOrNull { it.type == DownloadFileType.ITEM }
                            ?.uri
                            ?.takeIf { storageManager.documentFor(it)?.exists() == true }
                    }?.let {
                        if (download.item.mediaType == MediaType.AUDIO) {
                            activityEventHandler.emit(ActivityEvent.PlayOfflineAudio(it.toString(), download.item.name.orEmpty()))
                        } else {
                            activityEventHandler.emit(ActivityEvent.OpenUrl(it.toString(), true))
                        }
                    }
                }
            }

            MediaType.BOOK -> activityEventHandler.emit(ActivityEvent.OpenOfflineBook(download.itemId.toString()))
        }
    }

    fun download(download: DownloadEntity) {
        viewModelScope.launch {
            downloadManager.resume(download)
        }
    }

    fun removeDownload(download: DownloadEntity, deleteFiles: Boolean) {
        viewModelScope.launch {
            downloadManager.delete(download.id, deleteFiles)
        }
    }

}
