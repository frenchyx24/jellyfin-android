package org.jellyfin.mobile.nflix.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Announcement category shown as a badge on each card. */
enum class NoticeCategory(val label: String) {
    Info("Info"),
    Maintenance("Maintenance"),
    New("Nouveauté"),
}

/** Pure helpers for the announcements tab: category, unread count and French dates. */
object NoticeFormat {
    private val maintenance = setOf("maintenance", "outage", "incident", "panne", "downtime")
    private val news = setOf("feature", "new", "news", "nouveaute", "nouveauté", "release", "update", "changelog", "nouveautes", "nouveautés")
    private val longDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
    private val shortDate = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)
    private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRENCH)

    fun category(notice: Notice): NoticeCategory {
        val category = notice.category.trim().lowercase()
        val level = notice.level.trim().lowercase()
        return when {
            category in maintenance || level in maintenance -> NoticeCategory.Maintenance
            category in news || notice.version.isNotBlank() -> NoticeCategory.New
            else -> NoticeCategory.Info
        }
    }

    fun unread(notices: List<Notice>, read: Set<String>, dismissed: Set<String>): Int =
        notices.count { it.id !in read && it.id !in dismissed }

    /** Parses the server date (offset, UTC instant, local date-time or date); null when unreadable. */
    fun parse(raw: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? {
        val text = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        return runCatching { OffsetDateTime.parse(text).atZoneSameInstant(zone) }
            .recoverCatching { Instant.parse(text).atZone(zone) }
            .recoverCatching { LocalDateTime.parse(text).atZone(zone) }
            .recoverCatching { LocalDate.parse(text.take(10)).atStartOfDay(zone) }
            .getOrNull()
    }

    /** "à l'instant", "il y a 5 min", "il y a 3 h", "hier", "il y a 4 jours", "il y a 2 semaines", then the date. */
    fun relative(raw: String, now: ZonedDateTime = ZonedDateTime.now()): String {
        val date = parse(raw, now.zone) ?: return ""
        val elapsed = Duration.between(date, now)
        val minutes = elapsed.toMinutes()
        val days = ChronoUnit.DAYS.between(date.toLocalDate(), now.toLocalDate())
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            elapsed.toHours() < 24 -> "il y a ${elapsed.toHours()} h"
            days <= 1 -> "hier"
            days < 7 -> "il y a $days jours"
            days < 28 -> (days / 7).let { if (it == 1L) "il y a 1 semaine" else "il y a $it semaines" }
            date.year == now.year -> date.format(shortDate)
            else -> date.format(longDate)
        }
    }

    /** "mardi 4 novembre 2026 à 12:00" for the detail view. */
    fun full(raw: String, zone: ZoneId = ZoneId.systemDefault()): String = parse(raw, zone)?.format(fullDate).orEmpty()
}
