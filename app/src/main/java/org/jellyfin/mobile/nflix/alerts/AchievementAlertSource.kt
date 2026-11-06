package org.jellyfin.mobile.nflix.alerts

import org.jellyfin.mobile.nflix.NflixSession
import org.jellyfin.mobile.nflix.data.AchievementHub
import org.jellyfin.mobile.nflix.data.AchievementPluginMissingException

/** Unlocked badges become phone notifications; the first check only records the cursor. */
class AchievementAlertSource(
    private val session: NflixSession,
    private val deviceId: () -> String?,
    private val hub: (userId: String, token: String) -> AchievementHub,
) : LocalAlertSource {
    override suspend fun poll(token: String, prefs: AlertPrefs): List<NflixAlert> {
        val userId = session.userId ?: return emptyList()
        val cursor = prefs.badgeCursor
        val (now, badges) = try {
            hub(userId, token).unlocksSince(cursor, deviceId())
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
