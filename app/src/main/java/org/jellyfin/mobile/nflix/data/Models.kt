package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Immutable
@Serializable
data class ItemsPage(
    @SerialName("Items") val items: List<Item> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0,
)

@Immutable
@Serializable
data class UserData(
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("PlaybackPositionTicks") val positionTicks: Long = 0,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val favorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayed: Int? = null,
)

@Immutable
@Serializable
data class Person(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val imageTag: String? = null,
)

@Immutable
@Serializable
data class MediaStream(
    @SerialName("Type") val type: String = "",
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Index") val index: Int = 0,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("Height") val height: Int? = null,
)

@Immutable
@Serializable
data class MediaSource(
    @SerialName("Id") val id: String = "",
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("MediaStreams") val streams: List<MediaStream> = emptyList(),
)

@Immutable
@Serializable
data class UrlLink(@SerialName("Url") val url: String = "", @SerialName("Name") val name: String? = null)

@Immutable
@Serializable
data class NameId(@SerialName("Id") val id: String = "", @SerialName("Name") val name: String = "")

@Immutable
@Serializable
data class Item(
    @SerialName("Id") val id: String,
    /** YouTube Music result thumbnail URL for synthetic `yt:` items; never serialized. */
    @kotlinx.serialization.Transient val ytThumb: String? = null,
    @SerialName("Name") val name: String = "",
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Type") val type: String = "",
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("CriticRating") val criticRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropTags: List<String> = emptyList(),
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropTags: List<String> = emptyList(),
    @SerialName("ParentLogoItemId") val parentLogoItemId: String? = null,
    @SerialName("ParentLogoImageTag") val parentLogoTag: String? = null,
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbTag: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryTag: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("UserData") val userData: UserData? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Studios") val studios: List<NameId> = emptyList(),
    @SerialName("Tags") val tags: List<String> = emptyList(),
    @SerialName("ProductionLocations") val productionLocations: List<String> = emptyList(),
    @SerialName("People") val people: List<Person> = emptyList(),
    @SerialName("MediaStreams") val mediaStreams: List<MediaStream> = emptyList(),
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList(),
    @SerialName("RemoteTrailers") val trailers: List<UrlLink> = emptyList(),
    @SerialName("ProviderIds") val providerIds: Map<String, String?> = emptyMap(),
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("ArtistItems") val artistItems: List<NameId> = emptyList(),
    @SerialName("AlbumArtists") val albumArtists: List<NameId> = emptyList(),
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryTag: String? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("SongCount") val songCount: Int? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryAspectRatio: Double? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("PlaylistItemId") val playlistItemId: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("LocalTrailerCount") val localTrailerCount: Int? = null,
    @SerialName("AirDays") val airDays: List<String> = emptyList(),
    @SerialName("AirTime") val airTime: String? = null,
) {
    val isFolderLike: Boolean get() = type in setOf("Series", "Season", "BoxSet", "MusicAlbum", "MusicArtist", "Playlist", "CollectionFolder", "Folder")
    val isPlayableVideo: Boolean get() = type in setOf("Movie", "Episode", "Video", "MusicVideo", "Trailer")
    val isBook: Boolean get() = type == "Book"
    val isAudioBook: Boolean get() = type == "AudioBook"
    val progress: Float get() = ((userData?.playedPercentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val runtimeMinutes: Int? get() = runTimeTicks?.let { (it / 600_000_000L).toInt() }?.takeIf { it > 0 }
    val audioLanguages: List<String>
        get() = (mediaSources.firstOrNull()?.streams ?: mediaStreams).filter { it.type == "Audio" }
            .mapNotNull { it.language?.uppercase() }.distinct()
    val maxHeight: Int? get() = (mediaSources.firstOrNull()?.streams ?: mediaStreams).filter { it.type == "Video" }.mapNotNull { it.height }.maxOrNull()
    val episodeLabel: String?
        get() = if (type == "Episode") {
            buildString {
                parentIndexNumber?.let { append("S").append(it) }
                indexNumber?.let { append(if (isNotEmpty()) " · É" else "É").append(it) }
            }.ifEmpty { null }
        } else null
    val tmdbId: String? get() = providerIds["Tmdb"]
}

@Immutable
@Serializable
data class LyricLine(@SerialName("Text") val text: String = "", @SerialName("Start") val start: Long? = null)

@Immutable
@Serializable
data class Lyrics(@SerialName("Lyrics") val lines: List<LyricLine> = emptyList())

@Immutable
@Serializable
data class CurrentUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("PrimaryImageTag") val imageTag: String? = null,
    @SerialName("Policy") val policy: JsonElement? = null,
)

/** NflixNotice plugin announcement. */
@Immutable
@Serializable
data class Notice(
    @SerialName("Id") val id: String,
    @SerialName("Title") val title: String = "",
    @SerialName("Message") val message: String = "",
    @SerialName("Category") val category: String = "announcement",
    @SerialName("Level") val level: String = "info",
    @SerialName("Version") val version: String = "",
    @SerialName("ActionLabel") val actionLabel: String = "",
    @SerialName("ActionUrl") val actionUrl: String = "",
    @SerialName("CreatedAt") val createdAt: String = "",
    @SerialName("Dismissible") val dismissible: Boolean = true,
    @SerialName("ShowPopup") val showPopup: Boolean = false,
    @SerialName("Pinned") val pinned: Boolean = false,
    @SerialName("ExpiresAt") val expiresAt: String? = null,
    /** Mira audience groups (plan_standard, plan_pro, plan_dieu…); empty = everyone. */
    @SerialName("Groups") val groups: List<String> = emptyList(),
    @SerialName("Source") val source: String = "",
)

/** Subset of the Media Bar Enhanced plugin configuration used by the native hero carousel. */
@Serializable
data class MediaBarConfig(
    @SerialName("IsEnabled") val enabled: Boolean = true,
    @SerialName("ShuffleInterval") val shuffleInterval: Long = 7000,
    @SerialName("MaxMovies") val maxMovies: Int = 15,
    @SerialName("MaxTvShows") val maxTvShows: Int = 15,
    @SerialName("MaxItems") val maxItems: Int = 15,
    @SerialName("MaxDaysRecent") val maxDaysRecent: Int = 0,
    @SerialName("SortBy") val sortBy: String = "PremiereDate",
    @SerialName("SortOrder") val sortOrder: String = "Descending",
    @SerialName("IncludeWatchedContent") val includeWatched: Boolean = true,
    @SerialName("CustomMediaIds") val customMediaIds: String = "",
    @SerialName("EnableCustomMediaIds") val enableCustomIds: Boolean = false,
    @SerialName("MaxPlotLength") val maxPlotLength: Int = 360,
)

/** Subset of the Jellyfin Enhanced public configuration mirrored natively. */
@Serializable
data class EnhancedConfig(
    @SerialName("ShowWatchProgress") val showWatchProgress: Boolean = true,
    @SerialName("ShowAudioLanguages") val showAudioLanguages: Boolean = true,
    @SerialName("RemoveContinueWatchingEnabled") val removeContinueWatching: Boolean = true,
    @SerialName("ShowFileSizes") val showFileSizes: Boolean = false,
)

/** Home screen sections defined by the Home Screen Sections plugin (when enabled by the admin). */
@Serializable
data class HomeSectionsMeta(@SerialName("Enabled") val enabled: Boolean = false)

@Serializable
data class HomeSection(
    @SerialName("Section") val section: String = "",
    @SerialName("DisplayText") val displayText: String = "",
    @SerialName("AdditionalData") val additionalData: String? = null,
)

@Serializable
data class HomeSectionsPage(@SerialName("Items") val items: List<HomeSection> = emptyList())

/** Moonfin resolved settings (synced with the Moonfin web/TV clients). */
@Serializable
data class MoonfinSettings(
    val mergeContinueWatchingNextUp: Boolean = true,
    val defaultAudioLanguage: String? = null,
    val defaultSubtitleLanguage: String? = null,
    val subtitleMode: String? = null,
    val mediaBarItemCount: Int = 20,
)
