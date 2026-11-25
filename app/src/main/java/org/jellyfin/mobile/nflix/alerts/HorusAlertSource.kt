package org.jellyfin.mobile.nflix.alerts

import org.jellyfin.mobile.nflix.data.HorusRepository
import org.jellyfin.mobile.nflix.data.HorusRequest
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.HorusType

/**
 * The user's Horus requests that became available on Nflix become notifications, checked from the
 * phone with its own Horus session. The first run only records what is already available.
 */
class HorusAlertSource(private val horus: () -> HorusRepository?, private val userName: () -> String?) : LocalAlertSource {
    override suspend fun poll(token: String, prefs: AlertPrefs): List<NflixAlert> {
        if (!prefs.isEnabled(AlertKind.Horus)) return emptyList()
        val repo = horus() ?: return emptyList()
        if (repo.userName == null) repo.userName = userName()
        val requests = repo.myRequests(force = true)
        return plan(requests, prefs.horusSeen(), System.currentTimeMillis(), prefs::setHorusSeen)
    }

    companion object {
        fun key(request: HorusRequest) = "${request.type.api}-${request.media.id}"

        fun isReady(request: HorusRequest): Boolean =
            request.media.jellyfinId != null && (request.media.status == HorusStatus.Available ||
                (request.media.status == HorusStatus.Partial && request.type == HorusType.Tv))

        private fun epochMs(iso: String): Long? = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()

        /**
         * [seen] null = first run: only requests that became available in the last 24 h are posted,
         * older ones are just remembered.
         */
        fun plan(requests: List<HorusRequest>, seen: Set<String>?, nowMs: Long, store: (Set<String>) -> Unit): List<NflixAlert> {
            val ready = requests.filter(::isReady)
            val keys = ready.map(::key).toSet()
            store(((seen ?: emptySet()) + keys).toList().takeLast(400).toSet())
            return ready.filter { r ->
                if (seen != null) key(r) !in seen
                else epochMs(r.updatedAt)?.let { nowMs - it in 0..86_400_000L } == true
            }.distinctBy(::key).take(5).map { r ->
                val name = r.media.title.ifBlank { "Votre demande" }
                NflixAlert(
                    id = -(0x20000000L + (key(r).hashCode().toLong() and 0x1fffffff)),
                    kind = AlertKind.Horus.wire,
                    title = "« ${name.take(120)} » est disponible",
                    body = "Votre demande Horus est prête sur Nflix.",
                    itemId = r.media.jellyfinId,
                )
            }
        }
    }
}
