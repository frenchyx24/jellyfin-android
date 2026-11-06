package org.jellyfin.mobile.nflix.data

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.nflix.MiraAccountBridge

/** Sends a ticket to Mira; returns the HTTP status, or 0 when no Mira account is linked. */
class SupportRepository(private val api: NflixApi, private val context: Context) {
    suspend fun report(item: Item, kind: String, message: String, context: String?): Int = withContext(Dispatchers.IO) {
        // The bridge is a cross-process ContentProvider call: keep it off the main thread.
        val token = MiraAccountBridge(this@SupportRepository.context).account()?.idToken ?: return@withContext 0
        val body = buildJsonObject {
            put("kind", if (kind == "improvement") "improvement" else "incident")
            put("title", listOfNotNull(item.seriesName?.takeIf { item.type in setOf("Episode", "Season") }, item.name).joinToString(" · ").take(100) + " (${item.type})")
            put("message", message)
            put("app", "nflix")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("mediaType", item.type)
            put("mediaId", item.id)
            put("mediaName", item.name)
            put("mediaYear", item.year)
            put("season", if (item.type == "Episode") item.parentIndexNumber else if (item.type == "Season") item.indexNumber else null)
            put("episode", if (item.type == "Episode") item.indexNumber else null)
            put("context", context)
        }.toString()
        val request = Request.Builder()
            .url("https://annonces.miracloud.fr/tickets".toHttpUrl())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        api.client.newCall(request).execute().use { it.code }
    }
}
