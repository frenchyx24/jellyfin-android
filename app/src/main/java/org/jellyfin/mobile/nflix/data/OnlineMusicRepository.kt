package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One online catalogue track (clean metadata, square cover). [itemId] is set once it is in the library. */
@Immutable
@Serializable
data class OnlineTrack(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("artistId") val artistId: String = "",
    @SerialName("album") val album: String = "",
    @SerialName("albumId") val albumId: String = "",
    @SerialName("cover") val cover: String = "",
    @SerialName("duration") val duration: Int = 0,
    @SerialName("trackNumber") val trackNumber: Int = 0,
    @SerialName("itemId") val itemId: String? = null,
    @SerialName("state") val state: String = "idle",
)

@Immutable
@Serializable
data class OnlineAlbum(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("artistId") val artistId: String = "",
    @SerialName("cover") val cover: String = "",
    @SerialName("year") val year: String = "",
    @SerialName("type") val type: String = "album",
    @SerialName("trackCount") val trackCount: Int = 0,
) {
    val typeLabel: String get() = when (type) { "single" -> "Single"; "ep" -> "EP"; "compile" -> "Compilation"; else -> "Album" }
}

@Immutable
@Serializable
data class OnlineArtist(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("picture") val picture: String = "",
    @SerialName("fans") val fans: Int = 0,
    @SerialName("albumCount") val albumCount: Int = 0,
)

@Immutable
@Serializable
data class OnlineSearch(
    @SerialName("tracks") val tracks: List<OnlineTrack> = emptyList(),
    @SerialName("albums") val albums: List<OnlineAlbum> = emptyList(),
    @SerialName("artists") val artists: List<OnlineArtist> = emptyList(),
)

@Immutable
@Serializable
data class OnlineArtistPage(
    @SerialName("artist") val artist: OnlineArtist = OnlineArtist(),
    @SerialName("top") val top: List<OnlineTrack> = emptyList(),
    @SerialName("albums") val albums: List<OnlineAlbum> = emptyList(),
    @SerialName("related") val related: List<OnlineArtist> = emptyList(),
)

@Immutable
@Serializable
data class OnlineAlbumPage(
    @SerialName("album") val album: OnlineAlbum = OnlineAlbum(),
    @SerialName("tracks") val tracks: List<OnlineTrack> = emptyList(),
)

@Immutable
data class OnlineState(val state: String, val itemId: String?, val message: String)

/** A ready-made mix (artist radio): cover, name and its tracks. */
@Immutable
@Serializable
data class OnlineMix(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("subtitle") val subtitle: String = "",
    @SerialName("cover") val cover: String = "",
    @SerialName("tracks") val tracks: List<OnlineTrack> = emptyList(),
)

/** One row of online suggestions: tracks, albums, artists or mixes. */
@Immutable
@Serializable
data class OnlineFeedSection(
    @SerialName("key") val key: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("subtitle") val subtitle: String = "",
    @SerialName("type") val type: String = "tracks",
    @SerialName("tracks") val tracks: List<OnlineTrack> = emptyList(),
    @SerialName("albums") val albums: List<OnlineAlbum> = emptyList(),
    @SerialName("artists") val artists: List<OnlineArtist> = emptyList(),
    @SerialName("mixes") val mixes: List<OnlineMix> = emptyList(),
)

@Serializable
private data class OnlineFeed(@SerialName("sections") val sections: List<OnlineFeedSection> = emptyList())

@Immutable
@Serializable
data class OnlineLyricLine(@SerialName("startMs") val startMs: Long = -1, @SerialName("text") val text: String = "")

@Immutable
@Serializable
data class OnlineLyrics(
    @SerialName("synced") val synced: Boolean = false,
    @SerialName("instrumental") val instrumental: Boolean = false,
    @SerialName("lines") val lines: List<OnlineLyricLine> = emptyList(),
)

@Serializable
private data class PicturesResponse(@SerialName("pictures") val pictures: Map<String, String> = emptyMap())

/** Online music catalogue through the Nflix Butler plugin (music routes). */
class OnlineMusicRepository(private val api: NflixApi) {
    suspend fun search(query: String, limit: Int = 20): OnlineSearch =
        api.get("Plugins/NflixButler/music/search", OnlineSearch.serializer(), mapOf("q" to query, "limit" to limit))

    suspend fun artist(id: String): OnlineArtistPage =
        api.get("Plugins/NflixButler/music/artist", OnlineArtistPage.serializer(), mapOf("id" to id))

    suspend fun artistByName(name: String): OnlineArtistPage =
        api.get("Plugins/NflixButler/music/artist", OnlineArtistPage.serializer(), mapOf("name" to name))

    suspend fun album(id: String): OnlineAlbumPage =
        api.get("Plugins/NflixButler/music/album", OnlineAlbumPage.serializer(), mapOf("id" to id))

    /** Online suggestions (for you, mixes, releases, similar artists, trends). */
    suspend fun feed(refresh: Boolean = false): List<OnlineFeedSection> =
        api.get("Plugins/NflixButler/music/feed", OnlineFeed.serializer(), if (refresh) mapOf("refresh" to true) else emptyMap()).sections

    /** Lyrics by artist + title (synced when available); works for library and online tracks. */
    suspend fun lyrics(artist: String, title: String, album: String?, durationSeconds: Int): OnlineLyrics =
        api.get(
            "Plugins/NflixButler/music/lyrics",
            OnlineLyrics.serializer(),
            mapOf("artist" to artist, "title" to title, "album" to album, "duration" to durationSeconds.takeIf { it > 0 }),
        )

    /** Pictures for artist names that have none in the library (exact name match). */
    suspend fun pictures(names: List<String>): Map<String, String> {
        val wanted = names.filter { it.isNotBlank() && !it.contains('|') }.distinct().take(120)
        if (wanted.isEmpty()) return emptyMap()
        return api.get("Plugins/NflixButler/music/pictures", PicturesResponse.serializer(), mapOf("names" to wanted.joinToString("|"))).pictures
    }

    fun streamUrl(trackId: String): String =
        api.url("Plugins/NflixButler/music/stream", mapOf("trackId" to trackId, "ApiKey" to api.token.orEmpty())).toString()

    /** Queues tracks or a whole album for the library; returns how many were queued. */
    suspend fun download(trackIds: List<String> = emptyList(), albumId: String? = null): Int {
        val result = api.send("POST", "Plugins/NflixButler/music/download", body = buildJsonObject {
            put("trackIds", JsonArray(trackIds.map { JsonPrimitive(it) }))
            put("albumId", JsonPrimitive(albumId.orEmpty()))
        }) as? JsonObject
        return (result?.get("queued") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    }

    /** States of a list of tracks plus whether this user may add tracks to the library. */
    suspend fun states(ids: List<String>): Pair<Map<String, OnlineState>, Boolean> {
        val wanted = ids.filter { it.isNotBlank() }.distinct().take(300)
        if (wanted.isEmpty()) return emptyMap<String, OnlineState>() to false
        val root = api.getJson("Plugins/NflixButler/music/states", mapOf("ids" to wanted.joinToString(","))).jsonObject
        val states = (root["states"] as? JsonObject).orEmpty().mapNotNull { (id, element) ->
            val row = element as? JsonObject ?: return@mapNotNull null
            id to OnlineState(
                state = (row["state"] as? JsonPrimitive)?.contentOrNull ?: "idle",
                itemId = (row["itemId"] as? JsonPrimitive)?.contentOrNull,
                message = (row["message"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }.toMap()
        val can = (root["canDownload"] as? JsonPrimitive)?.contentOrNull == "true"
        return states to can
    }
}
