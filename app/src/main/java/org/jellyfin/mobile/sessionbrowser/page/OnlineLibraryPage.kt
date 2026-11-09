package org.jellyfin.mobile.sessionbrowser.page

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jellyfin.mobile.R
import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute
import org.jellyfin.mobile.sessionbrowser.libraryPage
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online music for the car: suggestions, mixes, recent online listens, online albums and search
 * results, all through the Nflix server (same routes as the app). Playback goes through the
 * server audio proxy, so the car only ever talks to the Nflix server.
 */
internal object OnlineMusic {
    private val json = Json { ignoreUnknownKeys = true }
    private val idRx = Regex("^[0-9]{1,20}$")
    private val videoRx = Regex("^[A-Za-z0-9_-]{11}$")

    private fun base(api: ApiClient): String? = api.baseUrl?.trimEnd('/')

    suspend fun get(api: ApiClient, path: String, query: Map<String, String> = emptyMap()): JsonElement? = withContext(Dispatchers.IO) {
        val root = base(api) ?: return@withContext null
        val token = api.accessToken ?: return@withContext null
        val q = query.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        runCatching {
            val connection = URL("$root/$path" + if (q.isNotEmpty()) "?$q" else "").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "MediaBrowser Token=\"$token\"")
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode !in 200..299) null
                else json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }.onFailure { Timber.d(it, "online music %s failed", path) }.getOrNull()
    }

    fun streamUrl(api: ApiClient, kind: String, id: String): String? {
        val root = base(api) ?: return null
        val token = api.accessToken ?: return null
        return when {
            kind == "dz" && idRx.matches(id) -> "$root/Plugins/NflixButler/music/stream?trackId=$id&ApiKey=$token"
            kind == "yt" && videoRx.matches(id) -> "$root/Plugins/NflixButler/yt/stream?videoId=$id&ApiKey=$token"
            else -> null
        }
    }

    private fun JsonElement?.str(name: String): String? = ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonElement?.arr(name: String): List<JsonElement> = ((this as? JsonObject)?.get(name) as? JsonArray).orEmpty()

    /** A catalogue track; one already in the library still streams through the same route. */
    fun track(element: JsonElement): LibraryPageElement.Item? {
        val id = element.str("id") ?: return null
        val title = element.str("title") ?: return null
        val artist = element.str("artist").orEmpty()
        val album = element.str("album")
        val cover = element.str("cover")
        return LibraryPageElement.Item(
            title = title, artist = artist, album = album, image = cover?.let(Uri::parse),
            action = LibraryItemAction.PlayOnline("dz", id, title, artist, album, cover),
        )
    }

    /** A recent online listen (audio id). */
    fun recent(element: JsonElement): LibraryPageElement.Item? {
        val id = element.str("videoId") ?: return null
        val title = element.str("title") ?: return null
        val artist = element.str("artist").orEmpty()
        val cover = element.str("thumbnail")
        return LibraryPageElement.Item(
            title = title, artist = artist, album = element.str("album"), image = cover?.let(Uri::parse),
            action = LibraryItemAction.PlayOnline("yt", id, title, artist, element.str("album"), cover),
        )
    }

    fun album(element: JsonElement): LibraryPageElement.Item? {
        val id = element.str("id") ?: return null
        return LibraryPageElement.Item(
            title = element.str("title") ?: return null,
            artist = element.str("artist"),
            image = element.str("cover")?.let(Uri::parse),
            iconRes = R.drawable.ic_notification,
            action = LibraryItemAction.Navigate(LibraryRoute.OnlineAlbum(id)),
        )
    }

    suspend fun feedGroups(api: ApiClient): List<LibraryPageElement> = coroutineScope {
        val feed = async { get(api, "Plugins/NflixButler/music/feed") }
        val recent = async { get(api, "Plugins/NflixButler/yt/recent", mapOf("limit" to "20")) }
        val groups = mutableListOf<LibraryPageElement>()
        for (section in feed.await().arr("sections")) {
            val title = section.str("title") ?: continue
            when (section.str("type")) {
                "tracks" -> section.arr("tracks").mapNotNull(::track).takeIf { it.isNotEmpty() }?.let { groups += LibraryPageElement.Group(title, it) }
                "mixes" -> section.arr("mixes").forEach { mix ->
                    val name = mix.str("title") ?: return@forEach
                    mix.arr("tracks").mapNotNull(::track).take(25).takeIf { it.isNotEmpty() }?.let { groups += LibraryPageElement.Group(name, it) }
                }
                "albums" -> section.arr("albums").mapNotNull(::album).takeIf { it.isNotEmpty() }?.let { groups += LibraryPageElement.Group(title, it) }
            }
        }
        recent.await().arr("items").mapNotNull(::recent).takeIf { it.isNotEmpty() }?.let {
            groups.add(minOf(1, groups.size), LibraryPageElement.Group("Écoutés récemment", it))
        }
        groups
    }

    suspend fun searchTracks(api: ApiClient, query: String): List<LibraryPageElement.Item> =
        get(api, "Plugins/NflixButler/music/search", mapOf("q" to query, "limit" to "20")).arr("tracks").mapNotNull(::track)
}

/** Paging over groups: only the slice [offset, offset + limit) of all their items. */
internal fun List<LibraryPageElement>.pageOf(offset: Int, limit: Int): List<LibraryPageElement> {
    var index = 0
    return mapNotNull { element ->
        when (element) {
            is LibraryPageElement.Group -> {
                val start = index
                index += element.items.size
                element.items.filterIndexed { i, _ -> start + i in offset until offset + limit }.takeIf { it.isNotEmpty() }?.let { element.copy(items = it) }
            }
            is LibraryPageElement.Item -> (if (index in offset until offset + limit) element else null).also { index++ }
        }
    }
}

val OnlineLibraryPage = { api: ApiClient ->
    libraryPage<LibraryRoute.Online>(grid = true) { _, offset, limit ->
        OnlineMusic.feedGroups(api).pageOf(offset, limit)
    }
}

val OnlineAlbumLibraryPage = { api: ApiClient ->
    libraryPage<LibraryRoute.OnlineAlbum> { route, offset, limit ->
        val tracks = OnlineMusic.get(api, "Plugins/NflixButler/music/album", mapOf("id" to route.albumId))
            .let { ((it as? JsonObject)?.get("tracks") as? JsonArray).orEmpty() }
            .mapNotNull(OnlineMusic::track)
        tracks.drop(offset).take(limit)
    }
}
