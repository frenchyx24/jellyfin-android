package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One YouTube Music result returned by the butler plugin (`GET .../yt/search`). */
@Immutable
@Serializable
data class YtTrack(
    @SerialName("videoId") val videoId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("duration") val duration: Int = 0,
    @SerialName("thumbnail") val thumbnail: String = "",
)

/**
 * YouTube Music search and stream proxy exposed by the Nflix Butler plugin.
 * Search answers are anonymous-object JSON (lower-case keys); the stream URL carries the
 * session token as `api_key` so ExoPlayer can play it without custom headers.
 */
class YtMusicRepository(private val api: NflixApi) {
    suspend fun search(query: String, limit: Int = 15): List<YtTrack> =
        api.get("Plugins/NflixButler/yt/search", SearchResponse.serializer(), mapOf("q" to query, "limit" to limit)).results

    /** Directly playable proxied audio URL (signed-in via `api_key`). */
    fun streamUrl(videoId: String): String =
        api.url("Plugins/NflixButler/yt/stream", mapOf("videoId" to videoId, "ApiKey" to api.token.orEmpty())).toString()

    @Serializable
    private data class SearchResponse(
        @SerialName("results") val results: List<YtTrack> = emptyList(),
    )
}
