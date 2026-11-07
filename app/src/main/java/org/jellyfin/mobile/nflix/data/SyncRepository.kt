package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** Sync config returned by the butler plugin. */
@Immutable
@Serializable
data class SyncConfig(
    @SerialName("redirectUri") val redirectUri: String = "",
    @SerialName("deezerConfigured") val deezerConfigured: Boolean = false,
    @SerialName("deezerAppId") val deezerAppId: String = "",
    @SerialName("spotifyConfigured") val spotifyConfigured: Boolean = false,
    @SerialName("spotifyClientId") val spotifyClientId: String = "",
)

@Immutable
@Serializable
data class SyncAccountInfo(
    @SerialName("service") val service: String = "",
    @SerialName("profileName") val profileName: String = "",
    @SerialName("profileId") val profileId: String = "",
)

@Immutable
@Serializable
data class SyncPlaylist(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("tracks") val tracks: Int = 0,
)

@Immutable
@Serializable
data class SyncMatch(
    @SerialName("itemId") val itemId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
    @SerialName("localName") val localName: String = "",
)

@Immutable
@Serializable
data class SyncMissing(
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
)

@Immutable
@Serializable
data class ImportResult(
    @SerialName("total") val total: Int = 0,
    @SerialName("matched") val matched: List<SyncMatch> = emptyList(),
    @SerialName("missing") val missing: List<SyncMissing> = emptyList(),
)

@Immutable
@Serializable
data class DeezerTrack(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist") val artist: String = "",
)

/** Deezer/Spotify account linking and playlist sync, through the butler plugin. */
class SyncRepository(private val api: NflixApi) {
    suspend fun config(): SyncConfig =
        api.get("Plugins/NflixButler/sync/config", SyncConfig.serializer())

    suspend fun status(): List<SyncAccountInfo> =
        api.get("Plugins/NflixButler/sync/status", StatusResponse.serializer(), mapOf("userId" to api.userId)).accounts

    suspend fun link(service: String, code: String): JsonObject =
        api.send("POST", "Plugins/NflixButler/sync/link", body = buildJsonObject {
            put("service", JsonPrimitive(service))
            put("code", JsonPrimitive(code))
            put("userId", JsonPrimitive(api.userId))
        })?.jsonObject ?: error("Réponse vide")

    suspend fun unlink(service: String) {
        api.send("DELETE", "Plugins/NflixButler/sync/unlink", mapOf("userId" to api.userId, "service" to service))
    }

    suspend fun playlists(service: String): List<SyncPlaylist> =
        api.get("Plugins/NflixButler/sync/playlists", PlaylistsResponse.serializer(), mapOf("service" to service, "userId" to api.userId)).playlists

    suspend fun import(service: String, playlistId: String): ImportResult =
        api.get("Plugins/NflixButler/sync/import", ImportResult.serializer(), mapOf("service" to service, "playlistId" to playlistId, "userId" to api.userId))

    suspend fun deezerSearch(q: String): List<DeezerTrack> =
        api.get("Plugins/NflixButler/sync/deezer-search", SearchResponse.serializer(), mapOf("q" to q)).tracks

    suspend fun exportDeezer(name: String, tracks: List<DeezerTrack>): JsonObject =
        api.send("POST", "Plugins/NflixButler/sync/export-deezer", body = buildJsonObject {
            put("userId", JsonPrimitive(api.userId))
            put("name", JsonPrimitive(name))
            put("tracks", kotlinx.serialization.builtins.ListSerializer(DeezerTrack.serializer()).let {
                kotlinx.serialization.json.Json.encodeToJsonElement(it, tracks)
            })
        })?.jsonObject ?: error("Réponse vide")

    @Serializable
    private data class StatusResponse(@SerialName("accounts") val accounts: List<SyncAccountInfo> = emptyList())

    @Serializable
    private data class PlaylistsResponse(@SerialName("playlists") val playlists: List<SyncPlaylist> = emptyList())

    @Serializable
    private data class SearchResponse(@SerialName("tracks") val tracks: List<DeezerTrack> = emptyList())
}
