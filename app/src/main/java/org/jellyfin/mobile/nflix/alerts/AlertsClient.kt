package org.jellyfin.mobile.nflix.alerts

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Kinds of phone notifications Nflix can post; each one has its own switch in the settings. */
enum class AlertKind(val wire: String, val label: String, val description: String) {
    Horus("horus_available", "Demandes Horus disponibles", "Quand un titre demandé est prêt et visible sur votre compte."),
    Episode("episode_available", "Nouveaux épisodes", "Quand un épisode d'une série suivie arrive sur Nflix."),
    Announced("episode_announced", "Épisodes annoncés", "Quand un nouvel épisode d'une série suivie est programmé."),
    Achievement("achievement", "Succès débloqués", "Quand vous débloquez un badge ou terminez une quête."),
    ;

    companion object {
        fun of(wire: String): AlertKind? = entries.firstOrNull { it.wire == wire }
    }
}

@Immutable
@Serializable
data class NflixAlert(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String = "",
    val itemId: String? = null,
    val createdAt: Long = 0,
)

@Serializable
private data class AlertsPage(val alerts: List<NflixAlert> = emptyList())

@Serializable
private data class FollowsPage(val follows: List<Follow> = emptyList())

@Immutable
@Serializable
data class Follow(val seriesId: String, val title: String = "", val createdAt: Long = 0)

@Immutable
@Serializable
data class CalendarEvent(
    val id: String,
    /** episode, anime or movie. */
    val kind: String,
    /** Episode, Anime, DigitalRelease, PhysicalRelease or CinemaRelease. */
    val releaseType: String = "Episode",
    val title: String = "",
    val subtitle: String = "",
    /** Broadcast or release date, epoch seconds. */
    val releaseAt: Long,
    /** Estimated availability on Nflix, epoch seconds. */
    val estimatedAt: Long? = null,
    /** available, learned, estimated, low or unknown. */
    val confidence: String = "unknown",
    val available: Boolean = false,
    val itemId: String? = null,
    val seriesId: String? = null,
    val followed: Boolean = false,
    val posterUrl: String? = null,
) {
    /** Date used to place the event in the calendar: when it should be watchable on Nflix. */
    val nflixAt: Long get() = estimatedAt ?: releaseAt
}

@Serializable
data class CalendarPage(
    val events: List<CalendarEvent> = emptyList(),
    val sourceAvailable: Boolean = true,
    val serverTime: Long = 0,
)

class AlertsHttpException(val code: Int, message: String) : IOException(message)

/**
 * Client of the Nflix alerts service (mira-annonces on Seth). The Jellyfin access token proves the
 * user's identity; the service checks it with Jellyfin and only sends it back to Jellyfin.
 */
class AlertsClient(private val client: OkHttpClient, private val baseUrl: String = BASE) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private suspend fun call(token: String, method: String, path: String, query: Map<String, String> = emptyMap(), body: String? = null): String =
        withContext(Dispatchers.IO) {
            val url = (baseUrl + path).toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val payload = body?.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url)
                .header("X-Nflix-Token", token)
                .header("Accept", "application/json")
                .method(method, payload ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AlertsHttpException(response.code, "HTTP ${response.code} $path")
                text
            }
        }

    suspend fun alerts(token: String, force: Boolean = false): List<NflixAlert> =
        json.decodeFromString(AlertsPage.serializer(), call(token, "GET", "/api/nflix/alerts", if (force) mapOf("force" to "1") else emptyMap())).alerts

    suspend fun ack(token: String, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        ids.chunked(200).forEach { chunk ->
            val body = buildJsonObject { putJsonArray("ids") { chunk.forEach { add(JsonPrimitive(it)) } } }
            call(token, "POST", "/api/nflix/alerts/ack", body = body.toString())
        }
    }

    suspend fun follows(token: String): List<Follow> =
        json.decodeFromString(FollowsPage.serializer(), call(token, "GET", "/api/nflix/follows")).follows

    suspend fun setFollow(token: String, seriesId: String, follow: Boolean) {
        require(SERIES_ID.matches(seriesId)) { "Identifiant de série invalide." }
        call(token, if (follow) "PUT" else "DELETE", "/api/nflix/follows/$seriesId")
    }

    suspend fun calendar(token: String, start: LocalDate, end: LocalDate): CalendarPage =
        json.decodeFromString(CalendarPage.serializer(), call(token, "GET", "/api/nflix/calendar", mapOf("start" to start.toString(), "end" to end.toString())))

    companion object {
        const val BASE = "https://annonces.miracloud.fr"
        private val SERIES_ID = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F-]{36}")

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }
}

/** Decides which alerts become phone notifications and which ones the server can forget. */
object AlertDispatch {
    data class Plan(val notify: List<NflixAlert>, val ack: List<Long>)

    /** Alerts older than this are dropped without a notification once they can't be shown. */
    const val STALE_SECONDS = 7 * 86_400L

    /**
     * Every alert is consumed exactly once. Disabled kinds are consumed silently. When the phone
     * cannot show notifications, alerts wait on the server (up to a week) for the user to allow them.
     * Alerts already posted (the ack failed last time) are acknowledged without a second notification.
     */
    fun plan(alerts: List<NflixAlert>, enabled: (AlertKind) -> Boolean, canNotify: Boolean, alreadyPosted: Set<Long>, now: Long): Plan {
        val notify = mutableListOf<NflixAlert>()
        val ack = mutableListOf<Long>()
        alerts.distinctBy { it.id }.forEach { alert ->
            val kind = AlertKind.of(alert.kind)
            when {
                alert.id in alreadyPosted -> ack += alert.id
                kind == null || !enabled(kind) -> ack += alert.id
                canNotify -> notify += alert
                now - alert.createdAt > STALE_SECONDS -> ack += alert.id
            }
        }
        return Plan(notify, ack)
    }
}
