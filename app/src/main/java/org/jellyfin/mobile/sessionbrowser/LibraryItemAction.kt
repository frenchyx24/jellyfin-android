package org.jellyfin.mobile.sessionbrowser

import org.jellyfin.sdk.model.api.BaseItemDto

sealed interface LibraryItemAction {
    data class Play(
        val item: BaseItemDto,
    ) : LibraryItemAction

    data class Navigate(val route: LibraryRoute) : LibraryItemAction

    /** Online track: "dz" (catalogue id) or "yt" (audio id), streamed through the server proxy. */
    data class PlayOnline(val kind: String, val id: String, val title: String, val artist: String, val album: String?, val cover: String?) : LibraryItemAction
}
