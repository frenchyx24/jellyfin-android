package org.jellyfin.mobile.nflix.alerts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.mobile.nflix.NflixSession
import java.time.LocalDate

/** Follows ("M'alerter") and the release calendar, for the screens. */
class AlertsRepository(private val client: AlertsClient, private val session: NflixSession) {
    private val _follows = MutableStateFlow<Set<String>?>(null)

    /** Followed series ids, null until loaded once. */
    val follows: StateFlow<Set<String>?> = _follows.asStateFlow()
    private val lock = Mutex()
    private val calendarCache = HashMap<Pair<LocalDate, LocalDate>, Pair<Long, CalendarPage>>()

    private fun token(): String = session.accessToken ?: throw IllegalStateException("Session Nflix absente.")

    suspend fun loadFollows(force: Boolean = false): Set<String> = lock.withLock {
        _follows.value?.takeIf { !force }?.let { return it }
        val ids = client.follows(token()).mapTo(HashSet()) { normalize(it.seriesId) }
        _follows.value = ids
        ids
    }

    fun isFollowed(seriesId: String): Boolean? = _follows.value?.contains(normalize(seriesId))

    suspend fun setFollow(seriesId: String, follow: Boolean) {
        val id = normalize(seriesId)
        val before = _follows.value.orEmpty()
        _follows.value = if (follow) before + id else before - id
        try {
            client.setFollow(token(), id, follow)
            synchronized(calendarCache) { calendarCache.clear() }
        } catch (e: Exception) {
            _follows.value = before
            throw e
        }
    }

    suspend fun calendar(start: LocalDate, end: LocalDate, force: Boolean = false): CalendarPage {
        val key = start to end
        val now = System.currentTimeMillis()
        if (!force) synchronized(calendarCache) { calendarCache[key]?.takeIf { now - it.first < 5 * 60_000 }?.let { return it.second } }
        val page = client.calendar(token(), start, end)
        synchronized(calendarCache) {
            if (calendarCache.size > 12) calendarCache.clear()
            calendarCache[key] = now to page
        }
        return page
    }

    fun clear() {
        _follows.value = null
        synchronized(calendarCache) { calendarCache.clear() }
    }

    companion object {
        fun normalize(id: String) = id.replace("-", "").lowercase()
    }
}
