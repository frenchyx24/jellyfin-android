package org.jellyfin.mobile.nflix.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Sort options exposed by the native library browser. */
enum class LibrarySort(val label: String, val sortBy: String, val order: String) {
    Added("Ajouts récents", "DateCreated,SortName", "Descending"),
    Name("Titre", "SortName", "Ascending"),
    Year("Année", "ProductionYear,PremiereDate,SortName", "Descending"),
    Rating("Note", "CommunityRating,SortName", "Descending"),
    Played("Vu récemment", "DatePlayed,SortName", "Descending"),
    Random("Aléatoire", "Random", "Ascending"),
}

enum class LibraryFilter(val label: String) { All("Tout"), Unplayed("Non vus"), Favorites("Favoris"), Resumable("En cours") }

/** Jellyfin data access with in-memory caching. Every call is main-safe. */
class JellyfinRepository(private val api: NflixApi) {
    private val pageCache = TtlCache<ItemsPage>(CACHE_MS)
    private val itemCache = TtlCache<Item>(CACHE_MS)
    private val viewsCache = TtlCache<List<Item>>(10 * 60_000L)

    private val _userDataChanges = MutableStateFlow<Map<String, UserData>>(emptyMap())

    /** Local overrides of favorite/played state so every screen updates instantly after an action. */
    val userDataChanges: StateFlow<Map<String, UserData>> = _userDataChanges.asStateFlow()

    private val listFields = listOf(
        "PrimaryImageAspectRatio", "Overview", "Genres", "ProductionYear", "CommunityRating", "OfficialRating",
        "ParentId", "ChildCount", "MediaSourceCount", "RemoteTrailers", "Taglines",
        "Studios", "Tags", "ProductionLocations", "OriginalTitle",
    )
    private val detailFields = listFields + listOf("People", "Studios", "MediaSources", "MediaStreams", "ProviderIds", "Path", "CriticRating", "LocalTrailerCount", "DateCreated")
    private val imageTypes = "Primary,Backdrop,Logo,Thumb,Banner"

    private fun baseQuery(fields: List<String> = listFields) = mapOf<String, Any?>(
        "fields" to fields,
        "enableImageTypes" to imageTypes,
        "imageTypeLimit" to 1,
        "enableUserData" to true,
    )

    private suspend fun page(path: String, query: Map<String, Any?>, force: Boolean = false): ItemsPage {
        val key = path + query.entries.sortedBy { it.key }.joinToString("&")
        return pageCache.getOrLoad(key, force) { api.get(path, ItemsPage.serializer(), query) }
    }

    fun cachedPage(path: String, query: Map<String, Any?>): ItemsPage? =
        pageCache.peek(path + query.entries.sortedBy { it.key }.joinToString("&"))

    suspend fun me(): CurrentUser = api.get("Users/Me", CurrentUser.serializer())

    suspend fun views(force: Boolean = false): List<Item> = viewsCache.getOrLoad("views", force) {
        api.get("UserViews", ItemsPage.serializer(), mapOf("userId" to api.userId)).items
    }

    suspend fun resume(mediaTypes: String = "Video", limit: Int = 20, force: Boolean = false) =
        page("UserItems/Resume", baseQuery() + mapOf("userId" to api.userId, "limit" to limit, "mediaTypes" to mediaTypes), force).items

    suspend fun nextUp(limit: Int = 20, force: Boolean = false) =
        page("Shows/NextUp", baseQuery() + mapOf("userId" to api.userId, "limit" to limit, "enableResumable" to false, "enableRewatching" to false), force).items

    suspend fun latest(parentId: String, limit: Int = 20, includeTypes: String? = null, force: Boolean = false): List<Item> {
        val key = "latest/$parentId/$limit/$includeTypes"
        val query = baseQuery() + mapOf("userId" to api.userId, "parentId" to parentId, "limit" to limit, "includeItemTypes" to includeTypes, "groupItems" to true)
        return pageCache.getOrLoad(key, force) {
            ItemsPage(api.get("Items/Latest", ListSerializer(Item.serializer()), query))
        }.items
    }

    suspend fun items(
        parentId: String? = null,
        includeTypes: String? = null,
        sort: LibrarySort = LibrarySort.Added,
        filter: LibraryFilter = LibraryFilter.All,
        start: Int = 0,
        limit: Int = 60,
        genre: String? = null,
        recursive: Boolean = true,
        searchTerm: String? = null,
        personId: String? = null,
        ids: List<String>? = null,
        extra: Map<String, Any?> = emptyMap(),
        force: Boolean = false,
    ): ItemsPage = page(
        "Items",
        baseQuery() + mapOf(
            "userId" to api.userId,
            "parentId" to parentId,
            "includeItemTypes" to includeTypes,
            "sortBy" to sort.sortBy,
            "sortOrder" to sort.order,
            "startIndex" to start,
            "limit" to limit,
            "recursive" to recursive,
            "genres" to genre,
            "searchTerm" to searchTerm,
            "personIds" to personId,
            "ids" to ids,
            "isPlayed" to if (filter == LibraryFilter.Unplayed) false else null,
            "isFavorite" to if (filter == LibraryFilter.Favorites) true else null,
            "filters" to if (filter == LibraryFilter.Resumable) "IsResumable" else null,
        ) + extra,
        force,
    )

    suspend fun genres(parentId: String?): List<String> = runCatching {
        api.get("Genres", ItemsPage.serializer(), mapOf("userId" to api.userId, "parentId" to parentId, "sortBy" to "SortName")).items.map { it.name }
    }.getOrDefault(emptyList())

    fun cachedItem(id: String): Item? = itemCache.peek(id)

    suspend fun item(id: String, force: Boolean = false): Item = itemCache.getOrLoad(id, force) {
        api.get("Items/$id", Item.serializer(), baseQuery(detailFields) + mapOf("userId" to api.userId))
    }

    /** Next episode to watch in one series, or null when caught up. */
    suspend fun nextUpFor(seriesId: String): Item? = runCatching {
        api.get("Shows/NextUp", ItemsPage.serializer(), baseQuery() + mapOf("userId" to api.userId, "seriesId" to seriesId, "limit" to 1, "enableResumable" to true)).items.firstOrNull()
    }.getOrNull()

    fun trailerStreamUrl(trailer: Item) = api.streamUrl("Videos/${trailer.id}/stream", mapOf("static" to true))

    suspend fun seasons(seriesId: String) =
        page("Shows/$seriesId/Seasons", baseQuery() + mapOf("userId" to api.userId)).items

    suspend fun episodes(seriesId: String, seasonId: String) =
        page("Shows/$seriesId/Episodes", baseQuery(listFields + "MediaSources") + mapOf("userId" to api.userId, "seasonId" to seasonId)).items

    suspend fun children(parentId: String, sortBy: String = "SortName", includeTypes: String? = null) =
        page("Items", baseQuery() + mapOf("userId" to api.userId, "parentId" to parentId, "sortBy" to sortBy, "includeItemTypes" to includeTypes)).items

    suspend fun similar(id: String) = runCatching {
        page("Items/$id/Similar", baseQuery() + mapOf("userId" to api.userId, "limit" to 16)).items
    }.getOrDefault(emptyList())

    suspend fun specialFeatures(id: String) = runCatching {
        api.get("Items/$id/SpecialFeatures", ListSerializer(Item.serializer()), mapOf("userId" to api.userId))
    }.getOrDefault(emptyList())

    /**
     * Global search. Each family is its own request so one slow or rejected query (persons, artists)
     * never blanks the others; it fails only when every request fails.
     */
    suspend fun search(term: String, limit: Int = 40): List<Item> = coroutineScope {
        fun items(types: String) = async {
            runCatching {
                page("Items", baseQuery() + mapOf("userId" to api.userId, "searchTerm" to term, "recursive" to true, "limit" to limit, "includeItemTypes" to types)).items
            }
        }
        val requests = listOf(
            items("Movie,Series,BoxSet"),
            items("Episode"),
            items("MusicAlbum,Audio,Playlist"),
            items("Book,AudioBook"),
            async { runCatching { page("Artists", baseQuery() + mapOf("userId" to api.userId, "searchTerm" to term, "limit" to 20)).items.map { it.copy(type = "MusicArtist") } } },
            async { runCatching { page("Persons", mapOf("userId" to api.userId, "searchTerm" to term, "limit" to 20, "enableImageTypes" to "Primary", "imageTypeLimit" to 1)).items.map { it.copy(type = "Person") } } },
        ).map { it.await() }
        if (requests.all { it.isFailure }) throw requests.first().exceptionOrNull() ?: IllegalStateException("Recherche indisponible")
        requests.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }
    }

    /**
     * Every film, series and collection with just what search needs: one request, cached, so search
     * answers while typing, offline from typos, word fragments and acronyms.
     */
    suspend fun searchCatalog(force: Boolean = false): List<Item> = page(
        "Items",
        baseQuery(listOf("Genres", "ProductionYear", "OriginalTitle", "Tags", "ProductionLocations", "Studios", "CommunityRating", "PrimaryImageAspectRatio")) +
            mapOf("userId" to api.userId, "recursive" to true, "includeItemTypes" to "Movie,Series,BoxSet", "sortBy" to "SortName", "limit" to 10_000),
        force,
    ).items

    suspend fun genres(parentId: String?, includeTypes: String?): List<String> = runCatching {
        api.get("Genres", ItemsPage.serializer(), mapOf("userId" to api.userId, "parentId" to parentId, "includeItemTypes" to includeTypes, "sortBy" to "SortName", "recursive" to true)).items.map { it.name }
    }.getOrDefault(emptyList())

    suspend fun localTrailers(id: String): List<Item> =
        api.get("Items/$id/LocalTrailers", ListSerializer(Item.serializer()), mapOf("userId" to api.userId))

    suspend fun artists(parentId: String?, start: Int = 0, limit: Int = 60, sortBy: String = "SortName", descending: Boolean = false) = page(
        "Artists/AlbumArtists",
        baseQuery() + mapOf("userId" to api.userId, "parentId" to parentId, "startIndex" to start, "limit" to limit, "sortBy" to sortBy, "sortOrder" to if (descending) "Descending" else "Ascending"),
    )

    suspend fun albumTracks(albumId: String) = page(
        "Items",
        baseQuery(listFields + "MediaSources") + mapOf("userId" to api.userId, "parentId" to albumId, "sortBy" to "ParentIndexNumber,IndexNumber,SortName", "includeItemTypes" to "Audio"),
    ).items

    suspend fun artistAlbums(artistId: String) = page(
        "Items",
        baseQuery() + mapOf("userId" to api.userId, "albumArtistIds" to artistId, "includeItemTypes" to "MusicAlbum", "recursive" to true, "sortBy" to "ProductionYear,SortName", "sortOrder" to "Descending"),
    ).items

    suspend fun artistTopTracks(artistId: String) = page(
        "Items",
        baseQuery() + mapOf("userId" to api.userId, "artistIds" to artistId, "includeItemTypes" to "Audio", "recursive" to true, "sortBy" to "PlayCount,SortName", "sortOrder" to "Descending", "limit" to 10),
    ).items

    suspend fun playlistItems(playlistId: String) = page(
        "Playlists/$playlistId/Items",
        baseQuery() + mapOf("userId" to api.userId),
    ).items

    suspend fun instantMix(itemId: String) = runCatching {
        page("Items/$itemId/InstantMix", baseQuery() + mapOf("userId" to api.userId, "limit" to 50)).items
    }.getOrDefault(emptyList())

    suspend fun lyrics(itemId: String): Lyrics? = runCatching { api.get("Audio/$itemId/Lyrics", Lyrics.serializer()) }.getOrNull()

    fun effectiveUserData(item: Item): UserData? = _userDataChanges.value[item.id] ?: item.userData

    suspend fun setFavorite(item: Item, favorite: Boolean) {
        val current = effectiveUserData(item) ?: UserData()
        _userDataChanges.update { it + (item.id to current.copy(favorite = favorite)) }
        runCatching {
            api.send(if (favorite) "POST" else "DELETE", "UserFavoriteItems/${item.id}", mapOf("userId" to api.userId))
        }.onFailure { _userDataChanges.update { map -> map + (item.id to current) } }
        pageCache.invalidate()
    }

    suspend fun setPlayed(item: Item, played: Boolean) {
        val current = effectiveUserData(item) ?: UserData()
        _userDataChanges.update { it + (item.id to current.copy(played = played, playedPercentage = if (played) null else current.playedPercentage)) }
        runCatching {
            api.send(if (played) "POST" else "DELETE", "UserPlayedItems/${item.id}", mapOf("userId" to api.userId))
        }.onFailure { _userDataChanges.update { map -> map + (item.id to current) } }
        pageCache.invalidate()
        itemCache.invalidate(item.id)
    }

    /** Jellyfin Enhanced "remove from continue watching": reset the resume position. */
    suspend fun removeFromResume(item: Item) {
        val current = effectiveUserData(item) ?: UserData()
        _userDataChanges.update { it + (item.id to current.copy(positionTicks = 0, playedPercentage = null)) }
        runCatching {
            api.send("POST", "UserItems/${item.id}/UserData", mapOf("userId" to api.userId),
                kotlinx.serialization.json.buildJsonObject { put("PlaybackPositionTicks", kotlinx.serialization.json.JsonPrimitive(0)) })
        }
        pageCache.invalidate("UserItems/Resume")
    }

    fun invalidateAll() {
        pageCache.invalidate()
        itemCache.invalidate()
    }

    /** Audio playlists the user can open: their own, plus any shared with them. */
    suspend fun audioPlaylists(force: Boolean = false): List<Item> =
        page("Items", baseQuery() + mapOf("userId" to api.userId, "includeItemTypes" to "Playlist", "recursive" to true, "sortBy" to "SortName", "mediaTypes" to "Audio"), force).items

    /**
     * Creates a private playlist: Jellyfin shows a non-public playlist only to its owner and the users it is
     * shared with, so no plugin is needed for per-user music playlists.
     */
    suspend fun createPlaylist(name: String, itemIds: List<String>): String? {
        val result = api.send(
            "POST", "Playlists",
            body = kotlinx.serialization.json.buildJsonObject {
                put("Name", kotlinx.serialization.json.JsonPrimitive(name))
                put("UserId", kotlinx.serialization.json.JsonPrimitive(api.userId))
                put("MediaType", kotlinx.serialization.json.JsonPrimitive("Audio"))
                put("IsPublic", kotlinx.serialization.json.JsonPrimitive(false))
                put("Ids", kotlinx.serialization.json.JsonArray(itemIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            },
        )
        pageCache.invalidate()
        return ((result as? kotlinx.serialization.json.JsonObject)?.get("Id") as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

    suspend fun addToPlaylist(playlistId: String, itemIds: List<String>) {
        api.send("POST", "Playlists/$playlistId/Items", mapOf("ids" to itemIds, "userId" to api.userId))
        pageCache.invalidate()
    }

    /** [entryIds] are the PlaylistItemId values of the entries, not the media ids. */
    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>) {
        api.send("DELETE", "Playlists/$playlistId/Items", mapOf("entryIds" to entryIds))
        pageCache.invalidate()
    }

    suspend fun renamePlaylist(playlistId: String, name: String) {
        api.send("POST", "Playlists/$playlistId", body = kotlinx.serialization.json.buildJsonObject { put("Name", kotlinx.serialization.json.JsonPrimitive(name)) })
        pageCache.invalidate()
        itemCache.invalidate(playlistId)
    }

    suspend fun deletePlaylist(playlistId: String) {
        api.send("DELETE", "Items/$playlistId")
        pageCache.invalidate()
        itemCache.invalidate(playlistId)
    }

    suspend fun <A, B> both(a: suspend () -> A, b: suspend () -> B): Pair<A, B> = coroutineScope {
        val x = async { a() }
        val y = async { b() }
        x.await() to y.await()
    }

    fun primary(item: Item, width: Int = 400): String? = when {
        item.imageTags["Primary"] != null -> api.imageUrl(item.id, "Primary", item.imageTags["Primary"], width)
        item.albumId != null && item.albumPrimaryTag != null -> api.imageUrl(item.albumId, "Primary", item.albumPrimaryTag, width)
        item.seriesId != null && item.seriesPrimaryTag != null -> api.imageUrl(item.seriesId, "Primary", item.seriesPrimaryTag, width)
        else -> null
    }

    fun backdrop(item: Item, width: Int = 1280): String? = when {
        item.backdropTags.isNotEmpty() -> api.imageUrl(item.id, "Backdrop", item.backdropTags.first(), width, 0)
        item.parentBackdropItemId != null && item.parentBackdropTags.isNotEmpty() ->
            api.imageUrl(item.parentBackdropItemId, "Backdrop", item.parentBackdropTags.first(), width, 0)
        item.imageTags["Thumb"] != null -> api.imageUrl(item.id, "Thumb", item.imageTags["Thumb"], width)
        else -> primary(item, width)
    }

    fun thumb(item: Item, width: Int = 640): String? = when {
        item.imageTags["Thumb"] != null -> api.imageUrl(item.id, "Thumb", item.imageTags["Thumb"], width)
        item.type == "Episode" && item.imageTags["Primary"] != null -> api.imageUrl(item.id, "Primary", item.imageTags["Primary"], width)
        item.parentThumbItemId != null && item.parentThumbTag != null -> api.imageUrl(item.parentThumbItemId, "Thumb", item.parentThumbTag, width)
        else -> backdrop(item, width)
    }

    fun logo(item: Item, width: Int = 600): String? = when {
        item.imageTags["Logo"] != null -> api.imageUrl(item.id, "Logo", item.imageTags["Logo"], width)
        item.parentLogoItemId != null && item.parentLogoTag != null -> api.imageUrl(item.parentLogoItemId, "Logo", item.parentLogoTag, width)
        else -> null
    }

    fun personImage(person: Person, width: Int = 240) = api.imageUrl(person.id, "Primary", person.imageTag, width)

    fun downloadUrl(item: Item) = api.streamUrl("Items/${item.id}/Download", emptyMap())

    /** Proxied YouTube Music audio (`yt/stream`) playable with the session api_key. */
    fun ytStreamUrl(videoId: String) = api.url(
        "Plugins/NflixButler/yt/stream",
        mapOf("videoId" to videoId, "ApiKey" to api.token.orEmpty()),
    ).toString()

    fun audioStreamUrl(itemId: String) = api.streamUrl(
        "Audio/$itemId/universal",
        mapOf(
            "userId" to api.userId,
            "deviceId" to "nflix-music",
            "container" to "opus,webm|opus,mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg",
            "transcodingContainer" to "mp3",
            "transcodingProtocol" to "http",
            "audioCodec" to "mp3",
            "maxStreamingBitrate" to 140_000_000,
        ),
    )

    companion object {
        private const val CACHE_MS = 3 * 60_000L

        fun parseNoticeLevel(json: JsonObject?, key: String) = json?.get(key)?.jsonPrimitive?.contentOrNull
    }
}
