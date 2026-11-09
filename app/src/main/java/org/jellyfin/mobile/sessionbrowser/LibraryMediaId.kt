@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.mobile.sessionbrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
sealed interface LibraryMediaId {
    @Serializable
    @SerialName("item")
    data class Item(val itemId: UUID, val route: LibraryRoute) : LibraryMediaId

    @Serializable
    @SerialName("route")
    data class Route(val route: LibraryRoute) : LibraryMediaId

    /** A playable online track; carries its metadata so playback needs no extra lookup. */
    @Serializable
    @SerialName("online")
    data class Online(
        val kind: String,
        val id: String,
        val title: String,
        val artist: String,
        val album: String? = null,
        val cover: String? = null,
        val route: LibraryRoute,
    ) : LibraryMediaId
}
