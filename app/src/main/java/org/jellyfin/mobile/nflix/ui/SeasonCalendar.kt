package org.jellyfin.mobile.nflix.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.TemporalAdjusters

/** The four seasons (northern hemisphere, astronomical dates rounded to the usual day). */
enum class Season(val label: String) {
    Winter("Hiver"),
    Spring("Printemps"),
    Summer("Été"),
    Autumn("Automne");

    companion object {
        fun on(date: LocalDate): Season {
            val day = MonthDay.from(date)
            return when {
                day.isBefore(MonthDay.of(3, 20)) -> Winter
                day.isBefore(MonthDay.of(6, 21)) -> Spring
                day.isBefore(MonthDay.of(9, 23)) -> Summer
                day.isBefore(MonthDay.of(12, 21)) -> Autumn
                else -> Winter
            }
        }
    }
}

/**
 * French holidays and celebrations that get their own theme. Declaration order is the priority
 * when two windows overlap and neither falls on its own day.
 */
enum class SeasonEvent(val label: String) {
    NewYear("Nouvel An"),
    Epiphany("Épiphanie"),
    Candlemas("Chandeleur"),
    Valentine("Saint-Valentin"),
    Easter("Pâques"),
    LabourDay("Fête du Travail"),
    VictoryDay("8 Mai 1945"),
    MothersDay("Fête des Mères"),
    FathersDay("Fête des Pères"),
    MusicDay("Fête de la Musique"),
    BastilleDay("14 Juillet"),
    BackToSchool("Rentrée"),
    Halloween("Halloween"),
    AllSaints("Toussaint"),
    Armistice("11 Novembre"),
    Christmas("Noël");

    fun isActive(date: LocalDate): Boolean = FrenchCalendar.window(this, date.year).contains(date) ||
        // Windows that straddle New Year belong to the previous year.
        FrenchCalendar.window(this, date.year - 1).contains(date)

    companion object {
        /** The event shown on [date]: an event on its own day wins, then declaration order. */
        fun on(date: LocalDate): SeasonEvent? {
            val active = entries.filter { it.isActive(date) }
            return active.firstOrNull { FrenchCalendar.day(it, date.year) == date || FrenchCalendar.day(it, date.year - 1) == date }
                ?: active.firstOrNull()
        }
    }
}

/** Inclusive date range. */
data class DateWindow(val start: LocalDate, val end: LocalDate) {
    fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

/** Dates of French holidays for any year (Gregorian calendar). */
object FrenchCalendar {
    /** Easter Sunday, anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun easterMonday(year: Int): LocalDate = easter(year).plusDays(1)

    /** Ascension Thursday: Easter + 39 days. */
    fun ascension(year: Int): LocalDate = easter(year).plusDays(39)

    /** Pentecost Sunday: Easter + 49 days (Whit Monday the day after). */
    fun pentecost(year: Int): LocalDate = easter(year).plusDays(49)

    /** Last Sunday of May, moved to the first Sunday of June when it falls on Pentecost. */
    fun mothersDay(year: Int): LocalDate {
        val lastSunday = LocalDate.of(year, 5, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return if (lastSunday == pentecost(year)) lastSunday.plusWeeks(1) else lastSunday
    }

    /** Third Sunday of June. */
    fun fathersDay(year: Int): LocalDate =
        LocalDate.of(year, 6, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY))

    /** The main day of [event] in [year]. */
    fun day(event: SeasonEvent, year: Int): LocalDate = when (event) {
        SeasonEvent.NewYear -> LocalDate.of(year + 1, 1, 1)
        SeasonEvent.Epiphany -> LocalDate.of(year, 1, 6)
        SeasonEvent.Candlemas -> LocalDate.of(year, 2, 2)
        SeasonEvent.Valentine -> LocalDate.of(year, 2, 14)
        SeasonEvent.Easter -> easter(year)
        SeasonEvent.LabourDay -> LocalDate.of(year, 5, 1)
        SeasonEvent.VictoryDay -> LocalDate.of(year, 5, 8)
        SeasonEvent.MothersDay -> mothersDay(year)
        SeasonEvent.FathersDay -> fathersDay(year)
        SeasonEvent.MusicDay -> LocalDate.of(year, 6, 21)
        SeasonEvent.BastilleDay -> LocalDate.of(year, 7, 14)
        SeasonEvent.BackToSchool -> LocalDate.of(year, 9, 1)
        SeasonEvent.Halloween -> LocalDate.of(year, 10, 31)
        SeasonEvent.AllSaints -> LocalDate.of(year, 11, 1)
        SeasonEvent.Armistice -> LocalDate.of(year, 11, 11)
        SeasonEvent.Christmas -> LocalDate.of(year, 12, 25)
    }

    /** Days around [event] that show its theme. New Year's window of [year] runs into January of the next year. */
    fun window(event: SeasonEvent, year: Int): DateWindow {
        val day = day(event, year)
        fun around(before: Long, after: Long) = DateWindow(day.minusDays(before), day.plusDays(after))
        return when (event) {
            SeasonEvent.NewYear -> around(2, 1) // 30 Dec – 2 Jan
            SeasonEvent.Epiphany -> around(3, 2) // 3 – 8 Jan
            SeasonEvent.Candlemas -> around(2, 1) // 31 Jan – 3 Feb
            SeasonEvent.Valentine -> around(2, 0) // 12 – 14 Feb
            SeasonEvent.Easter -> around(3, 1) // Thursday – Easter Monday
            SeasonEvent.LabourDay -> around(2, 0) // 29 Apr – 1 May
            SeasonEvent.VictoryDay -> around(0, 0) // 8 May only
            SeasonEvent.MothersDay -> around(2, 0)
            SeasonEvent.FathersDay -> around(2, 0)
            SeasonEvent.MusicDay -> around(1, 0) // 20 – 21 Jun
            SeasonEvent.BastilleDay -> around(1, 0) // 13 – 14 Jul
            SeasonEvent.BackToSchool -> around(2, 3) // 30 Aug – 4 Sep
            SeasonEvent.Halloween -> around(7, 0) // 24 – 31 Oct
            SeasonEvent.AllSaints -> around(0, 1) // 1 – 2 Nov
            SeasonEvent.Armistice -> around(1, 0) // 10 – 11 Nov
            SeasonEvent.Christmas -> DateWindow(LocalDate.of(year, 12, 1), LocalDate.of(year, 12, 26))
        }
    }

    /** Every event of [year] with its window, sorted by start date (New Year last, it ends in January). */
    fun year(year: Int): List<Pair<SeasonEvent, DateWindow>> =
        SeasonEvent.entries.map { it to window(it, year) }.sortedBy { it.second.start }
}
