package org.jellyfin.mobile.nflix.alerts

import org.jellyfin.mobile.nflix.NflixSession
import org.jellyfin.mobile.nflix.data.AchievementHub
import org.jellyfin.mobile.nflix.data.AchievementPluginMissingException

/** Unlocked badges become phone notifications; the first check only records the cursor. */
class AchievementAlertSource(private val session: NflixSession, private val hub: () -> AchievementHub) : LocalAlertSource {
    override suspend fun poll(token: String, prefs: AlertPrefs): List<NflixAlert> {
        if (session.userId == null) return emptyList()
        // A cold worker process has no session token in memory yet.
        if (session.accessToken == null) session.accessToken = token
        val cursor = prefs.badgeCursor
        val (now, badges) = try {
            hub().unlocksSince(cursor)
        } catch (missing: AchievementPluginMissingException) {
            return emptyList()
        }
        now?.let { prefs.badgeCursor = it }
        if (cursor == null) return emptyList()
        return planBadgeAlerts(badges)
    }

    companion object {
        /** Negative, stable ids keep badge notifications apart from server alert ids. */
        fun planBadgeAlerts(badges: List<Pair<String, String>>): List<NflixAlert> = badges.distinctBy { it.first }.take(5).map { (id, title) ->
            NflixAlert(id = -((id.hashCode().toLong() and 0x3fffffff) + 1), kind = AlertKind.Achievement.wire, title = "Succès débloqué : $title", body = "Retrouvez-le dans Mes succès.")
        }
    }
}
