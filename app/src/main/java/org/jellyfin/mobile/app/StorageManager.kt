package org.jellyfin.mobile.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadStatus
import java.io.File

class StorageManager(
    private val context: Context,
    private val appPreferences: AppPreferences
) {
    init {
        appPreferences.storageLocation = null
    }

    private val offlineRoot: File
        get() = File(context.filesDir, "offline").apply { mkdirs() }

    /** App-private storage keeps offline media out of public file pickers and galleries. */
    fun getStorageLocation(): DocumentFile = DocumentFile.fromFile(offlineRoot)

    fun verify(download: DownloadFiles): Boolean {
        if (download.files.isEmpty()) return false

        for (file in download.files) {
            if (file.status != DownloadStatus.DOWNLOADED) return false
            val documentFile = documentFor(file.uri)
            if (documentFile == null || !documentFile.exists() || documentFile.length() != file.size) {
                return false
            }
        }

        return true
    }

    fun documentFor(uri: Uri): DocumentFile? = if (uri.scheme == "file") {
        uri.path?.let { DocumentFile.fromFile(File(it)) }
    } else {
        DocumentFile.fromSingleUri(context, uri)
    }

    companion object {
        const val NOMEDIA_FILE = ".nomedia"
    }
}
