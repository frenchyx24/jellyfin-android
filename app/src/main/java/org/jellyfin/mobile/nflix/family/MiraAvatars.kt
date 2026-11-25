package org.jellyfin.mobile.nflix.family

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jellyfin.mobile.nflix.MiraProfile
import org.jellyfin.mobile.nflix.data.NflixApi

/**
 * Mira Connect profile pictures kept by the Nflix Auth plugin, one per Jellyfin user. The plugin only
 * stores a picture when that user signs in through Mira, so another family member's picture shows up
 * once they have signed in on a device; until then the Jellyfin picture or the colour initial is used.
 *
 * Read once per process from `NflixAuth/Avatars` and refreshed on every successful load; a failure is
 * silent and the app falls back to Jellyfin pictures and initials.
 */
object MiraAvatars {
    private val _pictures = MutableStateFlow<Map<String, String>>(emptyMap())
    val pictures: StateFlow<Map<String, String>> = _pictures.asStateFlow()

    @Volatile private var loading = false
    @Volatile private var lastLoad = 0L

    /** True once the server answered at least once: a success with no picture replaces the map, a failure keeps it. */
    @Volatile private var loaded = false

    /** Also set from the current session so the signed-in user's own picture shows before the map loads. */
    fun remember(userId: String?, picture: String?) {
        val key = norm(userId) ?: return
        val safe = MiraProfile.safeAvatar(picture) ?: return
        if (_pictures.value[key] == safe) return
        _pictures.value = _pictures.value + (key to safe)
    }

    /** Fetches the map from the server once ([force] reloads it, e.g. after switching profile). */
    suspend fun load(api: NflixApi, force: Boolean = false) {
        val now = System.currentTimeMillis()
        // A forced refresh (screens with avatars) runs at most once a minute.
        if (loading || (loaded && (!force || now - lastLoad < 60_000))) return
        lastLoad = now
        loading = true
        try {
            val body = api.getJson("NflixAuth/Avatars") as? JsonObject ?: return
            val next = body.mapNotNull { (id, value) ->
                val key = norm(id)?.takeIf { it.length <= 64 } ?: return@mapNotNull null
                val picture = MiraProfile.safeAvatar((value as? JsonPrimitive)?.contentOrNull) ?: return@mapNotNull null
                key to picture
            }.toMap()
            _pictures.value = next
            loaded = true
        } catch (_: Exception) {
            // Offline or plugin absent: keep what is known, the interface falls back on Jellyfin pictures.
        } finally {
            loading = false
        }
    }

    /** Mira picture of a Jellyfin user id (dashed or compact), or null. */
    fun of(userId: String?): String? = norm(userId)?.let { _pictures.value[it] }

    /** Jellyfin ids come dashed from one route and compact from another: the map keys are compact. */
    fun norm(id: String?): String? = id?.replace("-", "")?.lowercase()?.takeIf { it.isNotEmpty() }
}
