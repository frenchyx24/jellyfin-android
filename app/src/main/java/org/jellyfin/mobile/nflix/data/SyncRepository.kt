package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
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
    @SerialName("youtubeConfigured") val youtubeConfigured: Boolean = false,
)

/** Google device-flow code shown to the user. */
@Immutable
@Serializable
data class GoogleDeviceCode(
    @SerialName("userCode") val userCode: String = "",
    @SerialName("verificationUrl") val verificationUrl: String = "https://www.google.com/device",
    @SerialName("interval") val interval: Int = 5,
    @SerialName("expiresIn") val expiresIn: Int = 1800,
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
    @SerialName("videoId") val videoId: String = "",
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

    suspend fun status(): List<SyncAccountInfo> {
        val root = runCatching {
            api.getJson("Plugins/NflixButler/sync/status", mapOf("userId" to api.userId)).jsonObject
        }.getOrNull() ?: return emptyList()
        val accounts = root["accounts"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return accounts.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            SyncAccountInfo(
                service = row.text("service", "Service"),
                profileName = row.text("profileName", "ProfileName"),
                profileId = row.text("profileId", "ProfileId"),
            ).takeIf { it.service.isNotBlank() }
        }
    }

    suspend fun link(service: String, code: String): JsonObject =
        api.send("POST", "Plugins/NflixButler/sync/link", body = buildJsonObject {
            put("service", JsonPrimitive(service))
            put("code", JsonPrimitive(code))
            put("userId", JsonPrimitive(api.userId))
        })?.jsonObject ?: error("Réponse vide")

    suspend fun googleStart(): GoogleDeviceCode {
        val payload = api.send("POST", "Plugins/NflixButler/sync/google/start", body = buildJsonObject {
            put("userId", JsonPrimitive(api.userId))
        }) ?: error("Réponse vide")
        return api.json.decodeFromJsonElement(GoogleDeviceCode.serializer(), payload)
    }

    /** One poll: "pending", "slow_down", "linked", "denied" or "expired". */
    suspend fun googlePoll(): String {
        val payload = api.send("POST", "Plugins/NflixButler/sync/google/poll", body = buildJsonObject {
            put("userId", JsonPrimitive(api.userId))
        })?.jsonObject
        return (payload?.get("status") as? JsonPrimitive)?.contentOrNull ?: "pending"
    }

    suspend fun unlink(service: String) {
        api.send("DELETE", "Plugins/NflixButler/sync/unlink", mapOf("userId" to api.userId, "service" to service))
    }

    suspend fun playlists(service: String): List<SyncPlaylist> =
        api.get("Plugins/NflixButler/sync/playlists", PlaylistsResponse.serializer(), mapOf("service" to service, "userId" to api.userId)).playlists

    suspend fun import(service: String, playlistId: String): ImportResult {
        // The route is a POST (it may create nothing itself, but matching a whole playlist is not
        // idempotent enough for a cacheable GET).
        val payload = api.send("POST", "Plugins/NflixButler/sync/import", body = buildJsonObject {
            put("service", JsonPrimitive(service))
            put("playlistId", JsonPrimitive(playlistId))
            put("userId", JsonPrimitive(api.userId))
        }) ?: error("Réponse vide")
        return api.json.decodeFromJsonElement(ImportResult.serializer(), payload)
    }

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
    private data class PlaylistsResponse(@SerialName("playlists") val playlists: List<SyncPlaylist> = emptyList())

    @Serializable
    private data class SearchResponse(@SerialName("tracks") val tracks: List<DeezerTrack> = emptyList())

    /** Reads a string field, accepting both camelCase and the PascalCase the server used before 0.4.1. */
    private fun JsonObject.text(vararg keys: String): String =
        keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull } ?: ""
}
