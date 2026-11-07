package org.jellyfin.mobile.nflix

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.mobile.nflix.data.NflixApi
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last uncaught exception so a crash that the developer cannot reproduce still leaves a
 * trace. The handler writes one small file synchronously, then hands control back to the platform.
 * On the next start the trace is sent to Jellyfin (`POST /ClientLog/Document`), which stores it in
 * the server log folder, and the file is deleted.
 */
object CrashLog {
    private var file: File? = null
    private var pending: String? = null

    /** Installs the handler and reads the trace left by a previous crash. Call from `Application`. */
    fun install(context: Context) {
        val target = File(context.filesDir, "nflix-crash.txt")
        file = target
        pending = runCatching {
            target.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val buffer = StringWriter()
                error.printStackTrace(PrintWriter(buffer))
                target.writeText("thread=${thread.name}\n\n$buffer")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Sends the stored trace once, then forgets it. Safe to call on every start; no-op when empty. */
    suspend fun upload(api: NflixApi) {
        val text = pending ?: return
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("clientName", "Nflix")
                .addFormDataPart("deviceId", api.deviceId)
                .addFormDataPart("version", "4.5.4")
                .addFormDataPart("name", "nflix-crash.txt")
                .addFormDataPart("file", "nflix-crash.txt", text.toRequestBody("text/plain".toMediaType()))
                .build()

            val request = Request.Builder()
                .url(api.baseUrl.trimEnd('/') + "/ClientLog/Document")
                .header("Authorization", api.authorization())
                .post(body)
                .build()

            val response = api.client.newCall(request).execute()
            response.use { if (!it.isSuccessful) error("HTTP ${it.code}") }
            pending = null
            file?.delete()
        }.onFailure {
            Timber.w(it, "Crash log upload failed")
        }
    }
}
