package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** A playable YT Music track (what the player queue holds). */
@Immutable
@Serializable
data class YtTrack(
    @SerialName("videoId") val videoId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("duration") val duration: Int = 0,
    @SerialName("thumbnail") val thumbnail: String = "",
)

/** One YouTube Music search hit: track, or album/artist suggestion (browse id). */
@Immutable
@Serializable
data class YtHit(
    @SerialName("kind") val kind: String = "track",
    @SerialName("videoId") val videoId: String = "",
    @SerialName("browseId") val browseId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("subtitle") val subtitle: String = "",
    @SerialName("thumbnail") val thumbnail: String? = null,
)

/** Full metadata of one YT track, cached server-side. */
@Immutable
@Serializable
data class YtItemInfo(
    @SerialName("videoId") val videoId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("album") val album: String = "",
    @SerialName("albumBrowseId") val albumBrowseId: String = "",
    @SerialName("channelId") val channelId: String = "",
    @SerialName("channelTitle") val channelTitle: String = "",
    @SerialName("duration") val duration: Int = 0,
    @SerialName("thumbnail") val thumbnail: String = "",
    @SerialName("downloaded") val downloaded: Boolean = false,
)

@Immutable
@Serializable
data class YtSearchResult(
    @SerialName("results") val results: List<YtHit> = emptyList(),
    @SerialName("albums") val albums: List<YtHit> = emptyList(),
    @SerialName("artists") val artists: List<YtHit> = emptyList(),
)

@Immutable
@Serializable
data class YtSearchHistoryEntry(
    @SerialName("query") val query: String = "",
    @SerialName("atUtc") val atUtc: String = "",
)

/**
 * YouTube Music through the Nflix Butler plugin. The server keeps a persistent cache
 * (yt.json): searches 24 h, item metadata forever, recents list. The app only sees this API.
 */
class YtMusicRepository(private val api: NflixApi) {
    suspend fun search(query: String, limit: Int = 12): YtSearchResult =
        api.get("Plugins/NflixButler/yt/search", YtSearchResult.serializer(), mapOf("q" to query, "limit" to limit))

    suspend fun info(videoId: String): YtItemInfo =
        api.get("Plugins/NflixButler/yt/info", YtItemInfo.serializer(), mapOf("videoId" to videoId))

    suspend fun history(limit: Int = 20): List<YtSearchHistoryEntry> =
        api.get("Plugins/NflixButler/yt/history", HistoryResponse.serializer(), mapOf("limit" to limit)).searches

    suspend fun recent(limit: Int = 20): List<YtItemInfo> =
        api.get("Plugins/NflixButler/yt/recent", RecentResponse.serializer(), mapOf("limit" to limit)).items

    suspend fun artist(name: String, limit: Int = 30): YtSearchResult =
        api.get("Plugins/NflixButler/yt/artist", YtSearchResult.serializer(), mapOf("name" to name, "limit" to limit))

    suspend fun album(browseId: String, limit: Int = 100): List<YtHit> =
        api.get("Plugins/NflixButler/yt/album", AlbumResponse.serializer(), mapOf("browseId" to browseId, "limit" to limit)).results

    /** Downloads the track into the Jellyfin music library (admin). Server answers {downloaded,path,title,artist}. */
    suspend fun download(videoId: String): JsonObject =
        api.send("POST", "Plugins/NflixButler/yt/download", body = buildJsonObject { put("videoId", JsonPrimitive(videoId)) })?.jsonObject
            ?: error("Réponse vide")

    /** Directly playable proxied audio URL (signed-in via `api_key`). */
    fun streamUrl(videoId: String): String =
        api.url("Plugins/NflixButler/yt/stream", mapOf("videoId" to videoId, "ApiKey" to api.token.orEmpty())).toString()

    @Serializable
    private data class HistoryResponse(@SerialName("searches") val searches: List<YtSearchHistoryEntry> = emptyList())

    @Serializable
    private data class RecentResponse(@SerialName("items") val items: List<YtItemInfo> = emptyList())

    @Serializable
    private data class AlbumResponse(@SerialName("results") val results: List<YtHit> = emptyList())
}
