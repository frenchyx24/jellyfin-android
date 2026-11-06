package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** A distributor row ("Netflix", "Disney+"…) as stored by the Nflix Auth plugin. */
@Immutable
@Serializable
data class Distributor(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Studios") val studios: List<String> = emptyList(),
    @SerialName("LightPlate") val lightPlate: Boolean = false,
    @SerialName("Enabled") val enabled: Boolean = true,
    @SerialName("LogoVersion") val logoVersion: Long = 0,
)

/** A company found on TMDB through Horus, offered as a logo candidate. */
@Immutable
data class CompanyLogo(val id: Int, val name: String, val logoPath: String, val country: String?) {
    val previewUrl: String get() = "https://image.tmdb.org/t/p/w300$logoPath"
}

@Immutable
data class StudioMatch(val name: String, val count: Int)

@Immutable
data class AdminSession(
    val user: String,
    val device: String,
    val client: String,
    val item: String?,
    val itemId: String?,
    val progress: Float?,
    val paused: Boolean,
    val transcoding: Boolean,
    val lastActivity: String?,
)

@Immutable
data class PendingRequest(val request: HorusRequest, val requestedBy: String?)

/** Administration features. The server re-checks every right; [isAdmin] only decides what the app shows. */
class AdminRepository(private val api: NflixApi, private val jellyfin: JellyfinRepository, private val horus: HorusRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Volatile private var cached: List<Distributor>? = null
    @Volatile private var cachedAt = 0L
    @Volatile private var admin: Boolean? = null

    suspend fun isAdmin(force: Boolean = false): Boolean {
        admin?.takeIf { !force }?.let { return it }
        val me = jellyfin.me()
        val value = ((me.policy as? JsonObject)?.get("IsAdministrator") as? JsonPrimitive)?.booleanOrNull == true
        admin = value
        return value
    }

    /** Distributor rows; [all] includes hidden ones (administrators only). */
    suspend fun distributors(all: Boolean = false, force: Boolean = false): List<Distributor> {
        if (!all) cached?.takeIf { !force && System.currentTimeMillis() - cachedAt < 5 * 60_000 }?.let { return it }
        val text = api.raw("GET", api.url("NflixAuth/Studios", mapOf("all" to all.takeIf { it })))
        val list = json.decodeFromString(ListSerializer(Distributor.serializer()), text.ifBlank { "[]" })
        if (!all) { cached = list; cachedAt = System.currentTimeMillis() }
        return list
    }

    suspend fun saveDistributors(list: List<Distributor>): List<Distributor> {
        val body = json.encodeToJsonElement(ListSerializer(Distributor.serializer()), list)
        val result = api.send("PUT", "NflixAuth/Studios", body = body)
        cached = null
        return json.decodeFromJsonElement(ListSerializer(Distributor.serializer()), result ?: JsonArray(emptyList()))
    }

    suspend fun uploadLogo(id: String, bytes: ByteArray, mediaType: String) {
        api.sendBytes("PUT", "NflixAuth/Studios/$id/Logo", bytes, mediaType)
        cached = null
    }

    suspend fun logoFromTmdb(id: String, logoPath: String) {
        api.send("POST", "NflixAuth/Studios/$id/Logo/Tmdb", mapOf("path" to logoPath))
        cached = null
    }

    fun logoUrl(distributor: Distributor): String? =
        if (distributor.logoVersion > 0) api.url("NflixAuth/Studios/${distributor.id}/Logo", mapOf("v" to distributor.logoVersion)).toString() else null

    /** TMDB companies with a logo, searched through Horus. */
    suspend fun searchLogos(query: String): List<CompanyLogo> {
        if (query.isBlank()) return emptyList()
        val response = horus.api("GET", "search/company", mapOf("query" to query, "page" to 1)) as? JsonObject ?: return emptyList()
        return (response["results"] as? JsonArray).orEmpty().mapNotNull { element ->
            val company = element as? JsonObject ?: return@mapNotNull null
            val path = (company["logoPath"] as? JsonPrimitive)?.contentOrNull ?: (company["logo_path"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (!path.endsWith(".png") && !path.endsWith(".jpg")) return@mapNotNull null
            CompanyLogo(
                id = (company["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null,
                name = (company["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                logoPath = path,
                country = (company["originCountry"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
            )
        }.distinctBy { it.logoPath }
    }

    /** Jellyfin studios whose name matches, with how many movies and series carry them. */
    suspend fun matchingStudios(query: String): List<StudioMatch> {
        if (query.isBlank()) return emptyList()
        val studios = api.getJson("Studios", mapOf("searchTerm" to query, "limit" to 25, "userId" to api.userId)) as? JsonObject ?: return emptyList()
        val names = (studios["Items"] as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("Name") as? JsonPrimitive)?.contentOrNull }
        return names.map { name ->
            val count = runCatching { jellyfin.items(includeTypes = "Movie,Series", limit = 0, extra = mapOf("studios" to name)).total }.getOrDefault(0)
            StudioMatch(name, count)
        }.filter { it.count > 0 }.sortedByDescending { it.count }
    }

    /** Movies and series of a distributor, newest first. */
    suspend fun titles(distributor: Distributor, types: String = "Movie,Series", limit: Int = 24, start: Int = 0, force: Boolean = false): ItemsPage =
        if (distributor.studios.isEmpty()) ItemsPage() else jellyfin.items(
            includeTypes = types, sort = LibrarySort.Added, limit = limit, start = start, force = force,
            extra = mapOf("studios" to distributor.studios.joinToString("|")),
        )

    suspend fun pendingRequests(): List<PendingRequest> {
        val response = horus.api("GET", "request", mapOf("take" to 50, "skip" to 0, "filter" to "pending")) as? JsonObject ?: return emptyList()
        val parsed = HorusParser.requests(response)
        return parsed.map { request ->
            val enriched = if (request.type == HorusType.Movie || request.type == HorusType.Tv) {
                runCatching { request.copy(media = horus.details(request.type, request.media.id).copy(status = request.media.status)) }.getOrDefault(request)
            } else request
            PendingRequest(enriched, request.requestedBy)
        }
    }

    suspend fun decide(request: HorusRequest, approve: Boolean) {
        horus.api("POST", "request/${request.id}/${if (approve) "approve" else "decline"}")
    }

    suspend fun sessions(): List<AdminSession> {
        val list = api.getJson("Sessions", mapOf("activeWithinSeconds" to 960)) as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val session = element as? JsonObject ?: return@mapNotNull null
            fun text(obj: JsonObject?, key: String) = (obj?.get(key) as? JsonPrimitive)?.contentOrNull
            val playing = session["NowPlayingItem"] as? JsonObject
            val play = session["PlayState"] as? JsonObject
            val runtime = (playing?.get("RunTimeTicks") as? JsonPrimitive)?.longOrNull
            val position = (play?.get("PositionTicks") as? JsonPrimitive)?.longOrNull
            AdminSession(
                user = text(session, "UserName") ?: "Inconnu",
                device = text(session, "DeviceName").orEmpty(),
                client = text(session, "Client").orEmpty(),
                item = playing?.let { listOfNotNull(text(it, "SeriesName"), text(it, "Name")).joinToString(" · ") },
                itemId = text(playing, "Id"),
                progress = if (runtime != null && runtime > 0 && position != null) (position.toFloat() / runtime).coerceIn(0f, 1f) else null,
                paused = (play?.get("IsPaused") as? JsonPrimitive)?.booleanOrNull == true,
                transcoding = session["TranscodingInfo"] is JsonObject,
                lastActivity = text(session, "LastActivityDate"),
            )
        }.sortedWith(compareByDescending<AdminSession> { it.item != null }.thenBy { it.user })
    }

    suspend fun scanLibraries() {
        api.send("POST", "Library/Refresh")
    }
}
