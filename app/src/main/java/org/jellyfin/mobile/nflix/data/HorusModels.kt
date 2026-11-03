package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class HorusType(val api: String, val label: String) {
    Movie("movie", "Film"), Tv("tv", "Série"), Music("music", "Album"), Book("book", "Livre"), Person("person", "Personne");

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.api == value }
    }
}

/** Availability of a title on Nflix, as tracked by Horus (Seerr media status). */
enum class HorusStatus(val label: String) {
    Unknown(""), Pending("En attente"), Processing("En cours"), Partial("Partiellement disponible"),
    Available("Disponible"), Blocked("Bloqué"), Deleted("Supprimé");

    companion object {
        fun of(code: Int?) = when (code) {
            2 -> Pending
            3 -> Processing
            4 -> Partial
            5 -> Available
            6 -> Blocked
            7 -> Deleted
            else -> Unknown
        }
    }
}

@Immutable
data class HorusSeason(val number: Int, val name: String, val episodes: Int, val status: HorusStatus)

@Immutable
data class HorusTrack(val position: Int, val title: String, val lengthMs: Long?)

@Immutable
data class HorusMedia(
    val id: String,
    val type: HorusType,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val year: String?,
    val poster: String?,
    val backdrop: String?,
    val rating: Double?,
    val status: HorusStatus,
    val status4k: HorusStatus,
    val jellyfinId: String?,
    val genres: List<String> = emptyList(),
    val seasons: List<HorusSeason> = emptyList(),
    val tracks: List<HorusTrack> = emptyList(),
    val runtime: Int? = null,
    val tagline: String? = null,
) {
    val requestable: Boolean get() = type != HorusType.Person && status in setOf(HorusStatus.Unknown, HorusStatus.Deleted, HorusStatus.Partial)
}

@Immutable
data class HorusRequest(
    val id: Int,
    val type: HorusType,
    val status: Int,
    val media: HorusMedia,
    val createdAt: String,
    val is4k: Boolean,
) {
    val statusLabel: String
        get() = when (status) {
            1 -> "En attente de validation"
            2 -> if (media.status == HorusStatus.Available) "Disponible" else "Approuvée"
            3 -> "Refusée"
            4 -> "Échec"
            5 -> "Terminée"
            else -> ""
        }
}

@Immutable
data class HorusPage(val results: List<HorusMedia>, val page: Int, val totalPages: Int)

internal object HorusParser {
    private const val TMDB = "https://image.tmdb.org/t/p/"

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.dbl(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.arr(key: String) = this[key] as? JsonArray

    private fun image(path: String?, size: String): String? = when {
        path == null -> null
        path.startsWith("http") -> path
        path.startsWith("/") -> TMDB + size + path
        else -> null
    }

    fun media(json: JsonObject, fallbackType: HorusType? = null): HorusMedia? {
        val type = HorusType.of(json.str("mediaType")) ?: fallbackType ?: return null
        val id = json.str("id") ?: return null
        val info = json.obj("mediaInfo")
        val statusInfo = info?.arr("seasons")?.associate { (it.jsonObject.int("seasonNumber") ?: 0) to HorusStatus.of(it.jsonObject.int("status")) }.orEmpty()
        return HorusMedia(
            id = id,
            type = type,
            title = json.str("title") ?: json.str("name") ?: json.str("originalTitle") ?: "",
            subtitle = when (type) {
                HorusType.Music -> json.str("artistName")
                HorusType.Book -> json.str("authorName")
                else -> null
            },
            overview = json.str("overview"),
            year = (json.str("releaseDate") ?: json.str("firstAirDate") ?: json.str("firstPublishYear"))?.take(4),
            poster = image(json.str("posterPath") ?: json.str("profilePath"), "w342"),
            backdrop = image(json.str("backdropPath"), "w1280"),
            rating = json.dbl("voteAverage")?.takeIf { it > 0 } ?: json.obj("rating")?.dbl("value")?.let { it * 2 },
            status = HorusStatus.of(info?.int("status")),
            status4k = HorusStatus.of(info?.int("status4k")),
            jellyfinId = info?.str("jellyfinMediaId"),
            genres = json.arr("genres")?.mapNotNull { g ->
                (g as? JsonObject)?.str("name") ?: (g as? JsonPrimitive)?.contentOrNull
            }.orEmpty(),
            seasons = json.arr("seasons")?.mapNotNull { s ->
                val season = s as? JsonObject ?: return@mapNotNull null
                val number = season.int("seasonNumber") ?: return@mapNotNull null
                if (number == 0) return@mapNotNull null
                HorusSeason(number, season.str("name") ?: "Saison $number", season.int("episodeCount") ?: 0, statusInfo[number] ?: HorusStatus.Unknown)
            }.orEmpty(),
            tracks = json.arr("tracks")?.mapNotNull { t ->
                val track = t as? JsonObject ?: return@mapNotNull null
                HorusTrack(track.int("position") ?: 0, track.str("title") ?: "", (track["length"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull())
            }.orEmpty(),
            runtime = json.int("runtime"),
            tagline = json.str("tagline"),
        )
    }

    fun page(json: JsonObject, fallbackType: HorusType? = null): HorusPage = HorusPage(
        results = json.arr("results")?.mapNotNull { (it as? JsonObject)?.let { o -> media(o, fallbackType) } }
            ?.filter { it.type != HorusType.Person }.orEmpty(),
        page = json.int("page") ?: 1,
        totalPages = json.int("totalPages") ?: 1,
    )

    fun requests(json: JsonObject): List<HorusRequest> = json.arr("results")?.mapNotNull { r ->
        val request = r as? JsonObject ?: return@mapNotNull null
        val mediaJson = request.obj("media") ?: return@mapNotNull null
        val type = HorusType.of(request.str("type")) ?: HorusType.of(mediaJson.str("mediaType")) ?: return@mapNotNull null
        val title = mediaJson.str("metadataTitle")
        val media = HorusMedia(
            id = mediaJson.str("tmdbId") ?: mediaJson.str("mbId") ?: mediaJson.str("openLibraryId") ?: mediaJson.str("id") ?: "",
            type = type,
            title = title ?: "",
            subtitle = mediaJson.str("metadataCreator"),
            overview = null,
            year = mediaJson.str("metadataYear"),
            poster = image(mediaJson.str("metadataImage"), "w342"),
            backdrop = null,
            rating = null,
            status = HorusStatus.of(mediaJson.int("status")),
            status4k = HorusStatus.of(mediaJson.int("status4k")),
            jellyfinId = mediaJson.str("jellyfinMediaId"),
        )
        HorusRequest(
            id = request.int("id") ?: 0,
            type = type,
            status = request.int("status") ?: 0,
            media = media,
            createdAt = request.str("createdAt").orEmpty(),
            is4k = (request["is4k"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }.orEmpty()

    @Suppress("unused")
    fun firstArray(json: JsonObject, key: String) = json[key]?.jsonArray
}
