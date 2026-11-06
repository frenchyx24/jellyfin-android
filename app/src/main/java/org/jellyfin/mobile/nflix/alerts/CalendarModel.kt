package org.jellyfin.mobile.nflix.alerts

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Pure calendar logic, kept out of Compose so it can be tested. */
object CalendarModel {
    enum class Filter(val label: String) { All("Tout"), Series("Séries"), Anime("Animés"), Movies("Films") }

    private val FRENCH = Locale.FRANCE

    /** First and last day shown for a month grid: whole weeks, Monday first. */
    fun gridRange(month: YearMonth): Pair<LocalDate, LocalDate> {
        val first = month.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        val last = month.atEndOfMonth()
        val end = last.plusDays((DayOfWeek.SUNDAY.value - last.dayOfWeek.value).toLong())
        return start to end
    }

    /** The 35 or 42 days of the month grid. */
    fun gridDays(month: YearMonth): List<LocalDate> {
        val (start, end) = gridRange(month)
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    fun day(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

    fun matches(event: CalendarEvent, filter: Filter, followedOnly: Boolean): Boolean {
        if (followedOnly && !event.followed) return false
        return when (filter) {
            Filter.All -> true
            Filter.Series -> event.kind == "episode"
            Filter.Anime -> event.kind == "anime"
            Filter.Movies -> event.kind == "movie"
        }
    }

    /** Events grouped by the day they should be watchable on Nflix, sorted. */
    fun byDay(events: List<CalendarEvent>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<CalendarEvent>> =
        events.groupBy { day(it.nflixAt, zone) }.toSortedMap().mapValues { (_, list) -> list.sortedWith(compareBy({ it.nflixAt }, { it.title })) }

    fun confidenceLabel(event: CalendarEvent): String = when (event.confidence) {
        "available" -> "Disponible"
        "learned" -> "Estimation (d'après les épisodes précédents)"
        "estimated" -> "Estimation"
        "low" -> "Estimation lointaine"
        else -> "Date inconnue"
    }

    fun releaseLabel(event: CalendarEvent): String = when (event.releaseType) {
        "DigitalRelease" -> "Sortie numérique"
        "PhysicalRelease" -> "Sortie Blu-ray/DVD"
        "CinemaRelease" -> "Sortie cinéma"
        "Anime" -> "Diffusion (animé)"
        else -> "Diffusion"
    }

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", FRENCH)
    private val shortFormat = DateTimeFormatter.ofPattern("d MMM", FRENCH)
    private val timeFormat = DateTimeFormatter.ofPattern("HH'h'mm", FRENCH)

    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        today.minusDays(1) -> "Hier"
        else -> date.format(dayFormat).replaceFirstChar { it.titlecase(FRENCH) }
    }

    fun monthTitle(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, FRENCH).replaceFirstChar { it.titlecase(FRENCH) }} ${month.year}"

    /** "Sur Nflix vers 18h00 · Diffusion le 4 nov." */
    fun detail(event: CalendarEvent, zone: ZoneId = ZoneId.systemDefault()): String {
        val nflix = Instant.ofEpochSecond(event.nflixAt).atZone(zone)
        val release = Instant.ofEpochSecond(event.releaseAt).atZone(zone)
        val head = when {
            event.available -> "Disponible sur Nflix"
            event.estimatedAt == null -> "Date Nflix inconnue"
            else -> "Sur Nflix vers ${nflix.format(timeFormat)}"
        }
        return if (release.toLocalDate() == nflix.toLocalDate() || event.available) head
        else "$head · ${releaseLabel(event)} le ${release.format(shortFormat)}"
    }

    val weekDays: List<String> = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW_STANDALONE, FRENCH) }
}
