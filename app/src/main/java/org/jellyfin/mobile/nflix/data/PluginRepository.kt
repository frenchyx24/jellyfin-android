package org.jellyfin.mobile.nflix.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.time.LocalDate

/**
 * Native integration of the server plugins used by the Nflix web client, so both stay in sync:
 * Media Bar Enhanced (hero), Home Screen Sections, NflixNotice / Internal Notifications (announcements),
 * Jellyfin Enhanced (progress, languages, continue watching), Moonfin (synced preferences), LogoSwap (logo).
 */
class PluginRepository(context: Context, private val api: NflixApi, private val jellyfin: JellyfinRepository) {
    private val prefs = context.getSharedPreferences("nflix_plugins", Context.MODE_PRIVATE)
    private val heroCache = TtlCache<List<Item>>(10 * 60_000L)

    private val _notices = MutableStateFlow<List<Notice>>(emptyList())
    val notices: StateFlow<List<Notice>> = _notices.asStateFlow()

    private val _dismissed = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    var enhanced: EnhancedConfig = EnhancedConfig()
        private set
    var moonfin: MoonfinSettings = MoonfinSettings()
        private set
    var mediaBar: MediaBarConfig = MediaBarConfig()
        private set

    /** Logo served by the LogoSwap plugin (the same one the web client shows). */
    val serverLogoUrl: String get() = api.url("logoswap/image").toString()

    suspend fun refreshConfigs() {
        runCatching { enhanced = api.get("JellyfinEnhanced/public-config", EnhancedConfig.serializer()) }
            .onFailure { Timber.d(it, "Jellyfin Enhanced config unavailable") }
        runCatching { moonfin = api.get("Moonfin/Settings/Resolved/mobile", MoonfinSettings.serializer()) }
            .onFailure { Timber.d(it, "Moonfin settings unavailable") }
        runCatching { mediaBar = api.get("MediaBarEnhanced/Config", MediaBarConfig.serializer()) }
            .onFailure { Timber.d(it, "Media Bar config unavailable") }
    }

    /** Hero carousel built with the Media Bar Enhanced rules. */
    suspend fun heroItems(force: Boolean = false): List<Item> = heroCache.getOrLoad("hero", force) {
        val config = mediaBar
        if (!config.enabled) return@getOrLoad emptyList()
        val customIds = config.customMediaIds.split(',', '\n', ' ').map { it.trim() }.filter { it.length >= 32 }
        val items = if (config.enableCustomIds && customIds.isNotEmpty()) {
            jellyfin.items(ids = customIds, limit = customIds.size, extra = mapOf("sortBy" to null)).items
        } else {
            val extra = buildMap<String, Any?> {
                put("hasOverview", true)
                put("imageTypes", "Backdrop")
                if (!config.includeWatched) put("isPlayed", false)
                if (config.maxDaysRecent > 0) put("minPremiereDate", LocalDate.now().minusDays(config.maxDaysRecent.toLong()).toString())
                put("sortBy", if (config.sortBy == "Random") "Random" else "${config.sortBy},SortName")
                put("sortOrder", config.sortOrder)
            }
            val (movies, shows) = jellyfin.both(
                { jellyfin.items(includeTypes = "Movie", limit = config.maxMovies, extra = extra).items },
                { jellyfin.items(includeTypes = "Series", limit = config.maxTvShows, extra = extra).items },
            )
            (movies.zip(shows) { a, b -> listOf(a, b) }.flatten() + movies.drop(shows.size) + shows.drop(movies.size))
        }
        items.distinctBy { it.id }.filter { it.backdropTags.isNotEmpty() || it.parentBackdropTags.isNotEmpty() }
            .take(config.maxItems.coerceIn(3, moonfin.mediaBarItemCount.coerceAtLeast(3)))
    }

    /** Sections chosen by the admin in the Home Screen Sections plugin, when it is enabled. */
    suspend fun homeSections(): List<HomeSection> = runCatching {
        val meta = api.get("HomeScreen/Meta", HomeSectionsMeta.serializer())
        if (!meta.enabled) emptyList() else api.get("HomeScreen/Sections", HomeSectionsPage.serializer(), mapOf("userId" to api.userId)).items
    }.getOrDefault(emptyList())

    suspend fun homeSectionItems(section: HomeSection): List<Item> = runCatching {
        api.get(
            "HomeScreen/Section/${section.section}", ItemsPage.serializer(),
            mapOf("userId" to api.userId, "additionalData" to section.additionalData),
        ).items
    }.getOrDefault(emptyList())

    /** Announcements from NflixNotice and Internal Notifications. */
    suspend fun refreshNotices() {
        val notices = mutableListOf<Notice>()
        runCatching { notices += api.get("Plugins/NflixNotice/Feed", ListSerializer(Notice.serializer())) }
            .onFailure { Timber.d(it, "NflixNotice unavailable") }
        runCatching {
            val config = api.getJson("InternalNotifications/config").obj()
            (config?.get("Messages") as? JsonArray)?.forEachIndexed { index, element ->
                val message = element as? JsonObject ?: return@forEachIndexed
                val text = (message["Message"] ?: message["Texte"] ?: message["Text"]) as? JsonPrimitive
                val title = (message["Title"] ?: message["Titre"]) as? JsonPrimitive
                val id = (message["Id"] as? JsonPrimitive)?.contentOrNull ?: "internal-$index-${text?.contentOrNull?.hashCode()}"
                if (text?.contentOrNull != null) notices += Notice(id = id, title = title?.contentOrNull ?: "Message", message = text.content)
            }
        }
        _notices.value = notices.sortedWith(compareByDescending<Notice> { it.pinned }.thenByDescending { it.createdAt })
    }

    fun dismiss(notice: Notice) {
        val next = _dismissed.value + notice.id
        _dismissed.value = next
        prefs.edit { putStringSet(KEY_DISMISSED, next) }
    }

    private companion object {
        const val KEY_DISMISSED = "dismissed_notices"
    }
}
