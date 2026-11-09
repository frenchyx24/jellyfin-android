package org.jellyfin.mobile.sessionbrowser.page

import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute
import org.jellyfin.mobile.sessionbrowser.libraryPage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType

private val collectionTypes = setOf(CollectionType.MUSIC, CollectionType.BOOKS, CollectionType.PLAYLISTS)

/**
 * Root library page that returns the available libraries (user views) for Android Auto playback.
 * Note that this should ideally not exceed 4 items.
 */
val RootLibraryPage = { api: ApiClient ->
    libraryPage<LibraryRoute.Root>(grid = true) { _, offset, limit ->
        val userViews by api.userViewsApi.getUserViews()
        val views = userViews
            .items
            .filter { collectionTypes.contains(it.collectionType) }
            .map {
                LibraryPageElement.baseItem(
                    api = api,
                    item = it,
                    image = null,
                    iconRes = null,
                    action = when (it.collectionType) {
                        CollectionType.PLAYLISTS -> LibraryItemAction.Navigate(LibraryRoute.Playlists)
                        else -> LibraryItemAction.Navigate(LibraryRoute.Library(it.id, it.collectionType))
                    },
                )
            }
        // Online suggestions sit right after the music library.
        val online = LibraryPageElement.Item(title = "Pour toi", iconRes = org.jellyfin.mobile.R.drawable.ic_notification, action = LibraryItemAction.Navigate(LibraryRoute.Online))
        val musicIndex = userViews.items.filter { collectionTypes.contains(it.collectionType) }.indexOfFirst { it.collectionType == CollectionType.MUSIC }
        val all = views.toMutableList<LibraryPageElement>().apply { add(if (musicIndex >= 0) musicIndex + 1 else size, online) }
        all.drop(offset).take(limit)
    }
}
