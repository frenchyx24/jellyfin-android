package org.jellyfin.mobile.nflix.playback

/**
 * Playback memory, pure logic (the SharedPreferences-backed store lives in [PlaybackPrefs]):
 * - audio/subtitle track remembered per series,
 * - "Êtes-vous toujours là ?" after [ContinueWatching.THRESHOLD] chained episodes,
 * - exact resume position per item, so a crash or a call resumes to the second,
 * - configurable double-tap seek.
 */
object PlaybackMemory {

    /** Remembered audio/subtitle selection for one series, as ISO language codes ("fre", "eng"…). */
    data class TrackChoice(val audioLanguage: String?, val subtitleLanguage: String?)

    interface TrackStore {
        fun get(seriesId: String): TrackChoice?
        fun put(seriesId: String, choice: TrackChoice)
    }

    /** In-memory store, used by the unit tests. */
    class MapTrackStore : TrackStore {
        private val map = mutableMapOf<String, TrackChoice>()
        override fun get(seriesId: String): TrackChoice? = map[seriesId]
        override fun put(seriesId: String, choice: TrackChoice) { map[seriesId] = choice }
    }

    /**
     * Counts episodes chained without user interaction. [advance] returns true when the player
     * must pause and ask "Êtes-vous toujours là ?" instead of starting the next episode.
     */
    class ContinueWatching(val threshold: Int = THRESHOLD, var enabled: Boolean = true) {
        var count = 0
            private set
        var pending = false
            private set

        fun advance(): Boolean {
            if (!enabled || pending) return false
            count++
            if (count >= threshold) {
                pending = true
                return true
            }
            return false
        }

        /** The user answered "continue": the counter restarts. */
        fun confirm() {
            pending = false
            count = 0
        }

        /** The user answered "stop": playback ends, counter restarts. */
        fun stop() = confirm()

        /** A new playback session (manual play) resets the chain. */
        fun reset() {
            count = 0
            pending = false
        }

        companion object {
            const val THRESHOLD = 3
        }
    }

    /** Exact resume positions, in milliseconds, per item id. */
    interface ResumeStore {
        fun get(itemId: String): Long?
        fun put(itemId: String, positionMs: Long)
        fun clear(itemId: String)
    }

    class MapResumeStore : ResumeStore {
        private val map = mutableMapOf<String, Long>()
        override fun get(itemId: String): Long? = map[itemId]
        override fun put(itemId: String, positionMs: Long) { if (positionMs > 0) map[itemId] = positionMs }
        override fun clear(itemId: String) { map.remove(itemId) }
    }

    /** Double-tap seek amount, in seconds. */
    enum class DoubleTapSeek(val seconds: Int, val label: String) {
        S10(10, "10 secondes"),
        S30(30, "30 secondes");

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.name == key } ?: S10
        }
    }

    /** "J'ai X minutes" filter: keeps items whose runtime fits. */
    object MinutesFilter {
        const val TICKS_PER_MINUTE = 600_000_000L

        fun fits(itemMinutes: Long?, maxMinutes: Int): Boolean =
            maxMinutes <= 0 || itemMinutes == null || itemMinutes <= maxMinutes

        fun runtimeMinutes(ticks: Long?): Long? = ticks?.let { it / TICKS_PER_MINUTE }

        fun filter(items: List<org.jellyfin.mobile.nflix.data.Item>, maxMinutes: Int): List<org.jellyfin.mobile.nflix.data.Item> =
            if (maxMinutes <= 0) items else items.filter { fits(runtimeMinutes(it.runTimeTicks), maxMinutes) }
    }
}
