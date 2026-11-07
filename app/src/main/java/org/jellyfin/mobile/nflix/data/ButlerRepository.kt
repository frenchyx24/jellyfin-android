package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Libraries the butler files into (`libraries` member of the status). */
@Immutable
@Serializable
data class ButlerLibraries(
    @SerialName("movie") val movie: String = "",
    @SerialName("series") val series: String = "",
    @SerialName("anime") val anime: String = "",
)

/** Status of the Nflix Butler server plugin (`GET Plugins/NflixButler/api/status`). */
@Immutable
@Serializable
data class ButlerStatus(
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("running") val running: Boolean = false,
    @SerialName("lastRunUtc") val lastRunUtc: String? = null,
    @SerialName("startedUtc") val startedUtc: String? = null,
    @SerialName("runs") val runs: Int = 0,
    @SerialName("filed") val filed: Int = 0,
    @SerialName("repaired") val repaired: Int = 0,
    @SerialName("pending") val pending: Int = 0,
    @SerialName("pollSeconds") val pollSeconds: Int = 0,
    @SerialName("fixUnmatched") val fixUnmatched: Boolean = false,
    @SerialName("roots") val roots: List<String> = emptyList(),
    @SerialName("libraries") val libraries: ButlerLibraries = ButlerLibraries(),
    @SerialName("lastError") val lastError: String? = null,
)

/** One journal entry (`GET .../api/entries`). */
// The server serializes IntakeEntry in PascalCase (Jellyfin 12.4 default), while the
// anonymous wrappers ("entries", "items", "started") keep their lower-case member names.
@Immutable
@Serializable
data class ButlerEntry(
    @SerialName("Id") val id: String = "",
    @SerialName("Path") val path: String = "",
    @SerialName("Destination") val destination: String? = null,
    @SerialName("Kind") val kind: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("Year") val year: Int? = null,
    @SerialName("Season") val season: Int = 0,
    @SerialName("Episode") val episode: Int = 0,
    @SerialName("Library") val library: String = "",
    @SerialName("Action") val action: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Message") val message: String = "",
    @SerialName("ItemId") val itemId: String? = null,
    @SerialName("SizeBytes") val sizeBytes: Long = 0,
    @SerialName("AtUtc") val atUtc: String? = null,
) {
    /** "Titre S01E02" for episodes, the title alone otherwise. */
    val label: String
        get() = if (kind == "episode") {
            "$title S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        } else {
            title
        }
}

/** An item Jellyfin could not match to a metadata provider (`GET .../api/unmatched`). */
@Immutable
@Serializable
data class ButlerUnmatched(
    @SerialName("itemId") val itemId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("path") val path: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("year") val year: Int? = null,
)

/**
 * Client for the Nflix Butler plugin. Every route is `[Authorize(Policy = "RequiresElevation")]`
 * server-side; this class only calls them, the app hides the screen from non-administrators.
 */
class ButlerRepository(private val api: NflixApi) {
    suspend fun status(): ButlerStatus =
        api.get("Plugins/NflixButler/api/status", ButlerStatus.serializer())

    suspend fun entries(limit: Int = 100): List<ButlerEntry> =
        api.get("Plugins/NflixButler/api/entries", EntriesResponse.serializer(), mapOf("limit" to limit)).entries

    suspend fun unmatched(limit: Int = 50): List<ButlerUnmatched> =
        api.get("Plugins/NflixButler/api/unmatched", UnmatchedResponse.serializer(), mapOf("limit" to limit)).items

    /** One pass now (`POST .../api/run-now`). The server answers `{started:true}`. */
    suspend fun runNow(): Boolean {
        val result = api.send("POST", "Plugins/NflixButler/api/run-now")?.jsonObject ?: return true
        return result["started"]?.jsonPrimitive?.booleanOrNull ?: true
    }

    /** Repairs one item's identification (`POST .../api/fix/{itemId}`). Returns the resolved name when fixed. */
    suspend fun fix(itemId: String): String? {
        val result = api.send("POST", "Plugins/NflixButler/api/fix/$itemId")?.jsonObject ?: return null
        val fixed = result["fixed"]?.jsonPrimitive?.booleanOrNull == true
        return if (fixed) result["name"]?.jsonPrimitive?.contentOrNull else null
    }

    /** Targeted re-read of one item's folder (`POST .../api/refresh/{itemId}`). */
    suspend fun refresh(itemId: String) {
        api.send("POST", "Plugins/NflixButler/api/refresh/$itemId")
    }

    /** Plugin settings (`GET/POST .../api/settings`), kept raw so unknown keys survive a round trip. */
    suspend fun settings(): JsonObject = api.getJson("Plugins/NflixButler/api/settings").jsonObject

    suspend fun saveSettings(settings: JsonObject) {
        api.send("POST", "Plugins/NflixButler/api/settings", body = settings)
    }

    @Serializable
    private data class EntriesResponse(
        @SerialName("entries") val entries: List<ButlerEntry> = emptyList(),
    )

    @Serializable
    private data class UnmatchedResponse(
        @SerialName("items") val items: List<ButlerUnmatched> = emptyList(),
    )
}
