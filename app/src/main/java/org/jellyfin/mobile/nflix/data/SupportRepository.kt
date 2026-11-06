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

    /** The signed-in Mira user's own tickets, newest first; null when no Mira account is linked or the server refuses. */
    suspend fun mine(): List<SupportTicket>? = withContext(Dispatchers.IO) {
        val token = MiraAccountBridge(this@SupportRepository.context).account()?.idToken ?: return@withContext null
        val request = Request.Builder()
            .url("https://annonces.miracloud.fr/tickets/mine?limit=50".toHttpUrl())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        api.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = kotlinx.serialization.json.Json.parseToJsonElement(response.body?.string().orEmpty())
            val list = (root as? kotlinx.serialization.json.JsonObject)?.get("Tickets") as? kotlinx.serialization.json.JsonArray ?: return@use emptyList()
            list.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.let(SupportTicket::from) }
        }
    }
}

@androidx.compose.runtime.Immutable
data class SupportTicket(
    val id: Long,
    val kind: String,
    val title: String,
    val message: String,
    val status: String,
    val adminNote: String?,
    val mediaName: String?,
    val season: Int?,
    val episode: Int?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        fun from(o: kotlinx.serialization.json.JsonObject): SupportTicket? {
            fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.takeIf { it.isNotBlank() }
            fun num(k: String) = str(k)?.toDoubleOrNull()
            return SupportTicket(
                id = num("Id")?.toLong() ?: return null,
                kind = str("Kind") ?: "incident",
                title = str("Title").orEmpty(),
                message = str("Message").orEmpty(),
                status = str("Status") ?: "open",
                adminNote = str("AdminNote"),
                mediaName = str("MediaName"),
                season = num("Season")?.toInt(),
                episode = num("Episode")?.toInt(),
                createdAt = num("CreatedAt")?.toLong() ?: 0L,
                updatedAt = num("UpdatedAt")?.toLong() ?: 0L,
            )
        }
    }
}
