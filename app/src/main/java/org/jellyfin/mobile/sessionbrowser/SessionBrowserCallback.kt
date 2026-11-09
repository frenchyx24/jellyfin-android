package org.jellyfin.mobile.sessionbrowser

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.serialization.json.Json
import org.jellyfin.mobile.R
import org.jellyfin.mobile.sessionbrowser.page.AlbumLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.AlbumsAlphaLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.AlbumsLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.ArtistLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.ArtistsAlphaLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.ArtistsLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.AudioBooksAlphaLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.AudioBooksLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.FavoritesLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.GenreLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.GenresLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.OnlineAlbumLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.OnlineLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.OnlineMusic
import org.jellyfin.mobile.sessionbrowser.page.PlaylistLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.PlaylistsLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.RecentLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.RootLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.SearchLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.SuggestedLibraryPage
import org.jellyfin.mobile.sessionbrowser.page.UserViewLibraryPage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber

@Suppress("InjectDispatcher")
class SessionBrowserCallback(
    private val context: Context,
    private val api: ApiClient,
) : MediaLibrarySession.Callback {
    companion object {
        const val MAX_PAGE_SIZE = 250
        const val MEDIA_ITEM_EXTRA_START_POSITION = "start_position"
    }

    val pages = listOf(
        RootLibraryPage(api),
        UserViewLibraryPage(context),
        AlbumsLibraryPage(api),
        AlbumsAlphaLibraryPage,
        AlbumLibraryPage(api),
        AudioBooksLibraryPage(api),
        AudioBooksAlphaLibraryPage,
        ArtistsLibraryPage(api),
        ArtistsAlphaLibraryPage,
        ArtistLibraryPage(api),
        FavoritesLibraryPage(api),
        GenresLibraryPage(api),
        GenreLibraryPage(api),
        PlaylistsLibraryPage(api),
        PlaylistLibraryPage(api),
        RecentLibraryPage(api),
        SuggestedLibraryPage(api),
        SearchLibraryPage(context, api),
        OnlineLibraryPage(api),
        OnlineAlbumLibraryPage(api),
    )

    private val LibraryRoute.page get() = pages.firstOrNull { page -> page.route == this::class }

    private fun LibraryPageElement.Item.toMediaItem(
        route: LibraryRoute,
        groupTitle: String? = null,
    ): MediaItem = MediaItem.Builder().apply {
        val extras = bundleOf()
        groupTitle?.let {
            extras.putString(androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it)
        }

        if (action is LibraryItemAction.Navigate) {
            val page = action.route.page

            val contentStyle = when (page?.grid == true) {
                true -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                false -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            }
            extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, contentStyle)
            extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, contentStyle)
            setMediaId(Json.encodeToString<LibraryMediaId>(LibraryMediaId.Route(action.route)))
        } else if (action is LibraryItemAction.PlayOnline) {
            setMediaId(Json.encodeToString<LibraryMediaId>(LibraryMediaId.Online(action.kind, action.id, action.title, action.artist, action.album, action.cover, route)))
        } else if (action is LibraryItemAction.Play) {
            setMediaId(Json.encodeToString<LibraryMediaId>(LibraryMediaId.Item(action.item.id, route)))
            action.item.userData?.playbackPositionTicks?.ticks?.inWholeMilliseconds?.let {
                extras.putLong(MEDIA_ITEM_EXTRA_START_POSITION, it)
            }
        }

        setMediaMetadata(
            MediaMetadata.Builder().apply {
                setTitle(title)
                setArtist(artist)
                setAlbumTitle(album)
                setIsBrowsable(action is LibraryItemAction.Navigate)
                setIsPlayable(action is LibraryItemAction.Play || action is LibraryItemAction.PlayOnline)

                if (image != null) {
                    setArtworkUri(image)
                } else if (iconRes != null) {
                    setArtworkUri(iconRes.asResourceUri())
                }

                setExtras(extras)
            }.build(),
        )
    }.build()

    private fun Int.asResourceUri() = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(this))
        .appendPath(context.resources.getResourceTypeName(this))
        .appendPath(context.resources.getResourceEntryName(this))
        .build()

    private fun List<LibraryPageElement>.toMediaItems(route: LibraryRoute): List<MediaItem> = flatMap { element ->
        when (element) {
            is LibraryPageElement.Group -> element.items.map { item ->
                item.toMediaItem(
                    route = route,
                    groupTitle = element.title,
                )
            }

            is LibraryPageElement.Item -> listOf(element.toMediaItem(route))
        }
    }

    private fun createPageMediaItem(route: LibraryRoute, page: LibraryPage<*> = route.page!!) = MediaItem.Builder().apply {
        val extras = Bundle()
        val contentStyle = when (page.grid) {
            true -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            false -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        }
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, contentStyle)
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, contentStyle)

        setMediaId(Json.encodeToString<LibraryMediaId>(LibraryMediaId.Route(route)))
        setMediaMetadata(
            MediaMetadata.Builder().apply {
                setIsBrowsable(true)
                setIsPlayable(false)
                setExtras(extras)
            }.build(),
        )
    }.build()

    private suspend fun createPageContentResult(
        route: LibraryRoute,
        params: LibraryParams? = null,
        pageIndex: Int,
        pageSize: Int,
    ): LibraryResult<ImmutableList<MediaItem>> {
        val page = route.page ?: return LibraryResult.ofError(
            SessionError(
                SessionError.ERROR_BAD_VALUE,
                context.getString(R.string.media_service_unknown_page)
            )
        )

        if (api.baseUrl == null || api.accessToken == null) {
            return LibraryResult.ofError(
                SessionError(
                    SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
                    context.getString(R.string.media_service_auth_expired),
                ),
            )
        }

        @Suppress("UNCHECKED_CAST")
        val items = (page as? LibraryPage<LibraryRoute>)?.getContent(
            route,
            pageIndex * pageSize,
            minOf(pageSize, MAX_PAGE_SIZE),
        )?.toMediaItems(route)

        return if (items == null) {
            LibraryResult.ofError(
                SessionError(SessionError.ERROR_BAD_VALUE, context.getString(R.string.media_service_no_items)),
            )
        } else {
            LibraryResult.ofItemList(items, params)
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = CoroutineScope(Dispatchers.IO).future {
        val route = when {
            params?.isRecent == true -> LibraryRoute.Recent()
            params?.isSuggested == true -> LibraryRoute.Suggested
            else -> LibraryRoute.Root
        }

        Timber.d("onGetLibraryRoot $session $browser $params $route")

        val page = route.page

        if (page == null) {
            LibraryResult.ofError(
                SessionError(SessionError.ERROR_NOT_SUPPORTED, context.getString(R.string.media_service_unknown_page)),
                params ?: LibraryParams.Builder().build(),
            )
        } else {
            LibraryResult.ofItem(createPageMediaItem(route, page), params)
        }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = CoroutineScope(Dispatchers.IO).future {
        Timber.d("onGetChildren $parentId $page $pageSize $params")

        val libraryMediaId = runCatching { Json.decodeFromString<LibraryMediaId>(parentId) }.getOrNull()
        Timber.d("onGetChildren $libraryMediaId")

        if (libraryMediaId !is LibraryMediaId.Route) {
            LibraryResult.ofError(
                SessionError(SessionError.ERROR_BAD_VALUE, context.getString(R.string.media_service_unknown_page)),
            )
        } else {
            createPageContentResult(libraryMediaId.route, params, page, pageSize)
        }
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = CoroutineScope(Dispatchers.IO).future {
        Timber.d("onSearch $query $params")

        // Add fake item count because we have not actually searched yet
        session.notifySearchResultChanged(browser, query, 1, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = CoroutineScope(Dispatchers.IO).future {
        Timber.d("onGetSearchResult $query $page $pageSize $params")
        createPageContentResult(LibraryRoute.Search(query), params, page, pageSize)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = CoroutineScope(Dispatchers.IO).future {
        Timber.d("onGetItem $session $browser $mediaId")

        val libraryMediaId = runCatching { Json.decodeFromString<LibraryMediaId>(mediaId) }.getOrNull()
        when (libraryMediaId) {
            is LibraryMediaId.Item -> {
                val item by api.userLibraryApi.getItem(itemId = libraryMediaId.itemId)
                LibraryResult.ofItem(LibraryPageElement.baseItem(api, item).toMediaItem(libraryMediaId.route), null)
            }

            is LibraryMediaId.Online -> LibraryResult.ofItem(
                LibraryPageElement.Item(
                    title = libraryMediaId.title,
                    artist = libraryMediaId.artist,
                    album = libraryMediaId.album,
                    image = libraryMediaId.cover?.let(Uri::parse),
                    action = LibraryItemAction.PlayOnline(libraryMediaId.kind, libraryMediaId.id, libraryMediaId.title, libraryMediaId.artist, libraryMediaId.album, libraryMediaId.cover),
                ).toMediaItem(libraryMediaId.route),
                null,
            )

            is LibraryMediaId.Route -> {
                val page = libraryMediaId.route.page

                if (page == null) {
                    LibraryResult.ofError(
                        SessionError(
                            SessionError.ERROR_NOT_SUPPORTED,
                            context.getString(R.string.media_service_unknown_page),
                        ),
                    )
                } else {
                    LibraryResult.ofItem(createPageMediaItem(libraryMediaId.route, page), null)
                }
            }

            null -> LibraryResult.ofError(
                SessionError(SessionError.ERROR_BAD_VALUE, context.getString(R.string.media_service_invalid_id)),
            )
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> = CoroutineScope(Dispatchers.IO).future {
        Timber.d("onAddMediaItems $mediaSession $controller $mediaItems")

        mediaItems.mapNotNull {
            val libraryMediaId = runCatching { Json.decodeFromString<LibraryMediaId>(it.mediaId) }.getOrNull()
            if (libraryMediaId is LibraryMediaId.Online) {
                val uri = OnlineMusic.streamUrl(api, libraryMediaId.kind, libraryMediaId.id) ?: return@mapNotNull null
                return@mapNotNull it.buildUpon().setUri(uri).setMediaMetadata(
                    it.mediaMetadata.buildUpon()
                        .setTitle(libraryMediaId.title)
                        .setArtist(libraryMediaId.artist)
                        .setAlbumTitle(libraryMediaId.album)
                        .setArtworkUri(libraryMediaId.cover?.let(Uri::parse))
                        .setIsPlayable(true)
                        .build(),
                ).build()
            }
            if (libraryMediaId !is LibraryMediaId.Item) return@mapNotNull null

            val playbackUri = api.universalAudioApi.getUniversalAudioStreamUrl(
                itemId = libraryMediaId.itemId,
                deviceId = api.deviceInfo.id,
                maxStreamingBitrate = 140000000,
                container = listOf(
                    "opus",
                    "mp3|mp3",
                    "aac",
                    "m4a",
                    "m4b|aac",
                    "flac",
                    "webma",
                    "webm",
                    "wav",
                    "ogg",
                ),
                transcodingProtocol = MediaStreamProtocol.HLS,
                transcodingContainer = "ts",
                audioCodec = "aac",
                enableRemoteMedia = true,
            )

            it.buildUpon().apply {
                setUri(playbackUri + "&ApiKey=${api.accessToken}")
            }.build()
        }
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = CoroutineScope(Dispatchers.IO).future {
        Timber.d("onSetMediaItems $mediaSession $controller $mediaItems $startIndex $startPositionMs")

        var expandedItems = mediaItems
        var newStartIndex = startIndex

        // Modify playlist when a single item is requested
        if (mediaItems.size == 1) {
            val firstItem = mediaItems.first()

            // Expand media item to full playlist
            val libraryMediaId = runCatching {
                Json.decodeFromString<LibraryMediaId>(firstItem.mediaId)
            }.getOrNull()

            when {
                // Play via search
                firstItem.mediaId == MediaItem.DEFAULT_MEDIA_ID && firstItem.requestMetadata.mediaUri == null -> {
                    val search = firstItem.requestMetadata.searchQuery
                    Timber.d("Search requested $search")

                    val route = if (search.isNullOrEmpty()) LibraryRoute.Suggested
                    else LibraryRoute.Search(search)

                    @Suppress("UNCHECKED_CAST")
                    expandedItems = (route.page as? LibraryPage<LibraryRoute>)
                        ?.getContent(route, 0, MAX_PAGE_SIZE)
                        ?.toMediaItems(route)
                        .orEmpty()

                    // Android Auto does not support session error reporting
                    // but Google Play requires feedback on all search requests
                    // so fallback to random item playback if no search results are found
                    if (expandedItems.isEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        expandedItems = (LibraryRoute.Suggested.page as LibraryPage<LibraryRoute>)
                            .getContent(LibraryRoute.Suggested, 0, MAX_PAGE_SIZE)
                            .toMediaItems(route)
                    }

                    newStartIndex = 0
                }

                // Online track: play its whole row (section, mix or album) from that track.
                libraryMediaId is LibraryMediaId.Online -> {
                    @Suppress("UNCHECKED_CAST")
                    val siblings = (libraryMediaId.route.page as? LibraryPage<LibraryRoute>)
                        ?.getContent(libraryMediaId.route, 0, MAX_PAGE_SIZE)
                        ?.let { elements ->
                            // Only the group holding the requested track, in its order.
                            elements.firstOrNull { e ->
                                e is LibraryPageElement.Group && e.items.any { (it.action as? LibraryItemAction.PlayOnline)?.id == libraryMediaId.id }
                            }?.let { listOf(it) } ?: elements
                        }
                        ?.toMediaItems(libraryMediaId.route)
                        ?.filter { it.mediaMetadata.isPlayable == true }
                        .orEmpty()
                    expandedItems = siblings.ifEmpty { mediaItems }
                    newStartIndex = expandedItems.indexOfFirst {
                        (runCatching { Json.decodeFromString<LibraryMediaId>(it.mediaId) }.getOrNull() as? LibraryMediaId.Online)?.id == libraryMediaId.id
                    }.coerceAtLeast(0)
                }

                // Expand media item to full playlist
                libraryMediaId is LibraryMediaId.Item -> {
                    Timber.d("Expanding item $libraryMediaId")
                    val page = libraryMediaId.route.page

                    @Suppress("UNCHECKED_CAST")
                    expandedItems = (page as? LibraryPage<LibraryRoute>)
                        ?.getContent(libraryMediaId.route, startIndex, MAX_PAGE_SIZE)
                        ?.toMediaItems(libraryMediaId.route)
                        .orEmpty()

                    newStartIndex = expandedItems
                        .indexOfFirst {
                            (Json.decodeFromString<LibraryMediaId>(it.mediaId) as? LibraryMediaId.Item)?.itemId == libraryMediaId.itemId
                        }
                        .coerceAtLeast(0)
                }

                libraryMediaId is LibraryMediaId.Route -> {
                    val route = libraryMediaId.route
                    if (route is LibraryRoute.Artist) {
                        val result by api.itemsApi.getItems(
                            artistIds = listOf(route.artistId),
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            imageTypeLimit = 1,
                            enableImageTypes = listOf(ImageType.PRIMARY),
                            recursive = true,
                            limit = MAX_PAGE_SIZE,
                        )
                        expandedItems = result.items.map { item ->
                            LibraryPageElement.baseItem(api, item, action = LibraryItemAction.Play(item))
                                .toMediaItem(route)
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        expandedItems = (route.page as? LibraryPage<LibraryRoute>)
                            ?.getContent(route, 0, MAX_PAGE_SIZE)
                            ?.toMediaItems(route)
                            .orEmpty()
                            .filter { it.mediaMetadata.isPlayable == true }
                    }
                    newStartIndex = 0
                }
            }
        }

        // Use the server-side stored position if there is no requested start position
        val newStartPositionMs = when (startPositionMs) {
            0L, C.TIME_UNSET -> expandedItems.firstOrNull()?.mediaMetadata?.extras?.getLong(
                MEDIA_ITEM_EXTRA_START_POSITION,
                startPositionMs
            ) ?: startPositionMs

            else -> startPositionMs
        }

        expandedItems = onAddMediaItems(mediaSession, controller, expandedItems).await()

        Timber.d("onSetMediaItems resulted in ${expandedItems.size} items")
        if (expandedItems.isEmpty()) {
            mediaSession.sendError(
                controller,
                SessionError(SessionError.ERROR_BAD_VALUE, context.getString(R.string.media_service_no_items))
            )
        }
        MediaSession.MediaItemsWithStartPosition(expandedItems, newStartIndex, newStartPositionMs)
    }
}
