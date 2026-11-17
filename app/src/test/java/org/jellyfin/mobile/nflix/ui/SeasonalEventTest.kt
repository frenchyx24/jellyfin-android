package org.jellyfin.mobile.nflix.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class SeasonalEventTest : FunSpec({
    fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    test("seasons follow the calendar") {
        Season.on(d(2026, 1, 15)) shouldBe Season.Winter
        Season.on(d(2026, 4, 6)) shouldBe Season.Spring
        Season.on(d(2026, 7, 14)) shouldBe Season.Summer
        Season.on(d(2026, 10, 31)) shouldBe Season.Autumn
        Season.on(d(2026, 12, 24)) shouldBe Season.Winter
    }

    test("Easter Sunday for known years") {
        mapOf(
            2008 to d(2008, 3, 23), 2019 to d(2019, 4, 21), 2020 to d(2020, 4, 12), 2024 to d(2024, 3, 31),
            2025 to d(2025, 4, 20), 2026 to d(2026, 4, 5), 2027 to d(2027, 3, 28), 2028 to d(2028, 4, 16),
            2038 to d(2038, 4, 25), 2285 to d(2285, 3, 22),
        ).forEach { (year, easter) -> FrenchCalendar.easter(year) shouldBe easter }
    }

    test("movable feasts derive from Easter") {
        FrenchCalendar.easterMonday(2026) shouldBe d(2026, 4, 6)
        FrenchCalendar.ascension(2026) shouldBe d(2026, 5, 14)
        FrenchCalendar.pentecost(2026) shouldBe d(2026, 5, 24)
        FrenchCalendar.ascension(2025) shouldBe d(2025, 5, 29)
        FrenchCalendar.pentecost(2025) shouldBe d(2025, 6, 8)
    }

    test("Mother's Day: last Sunday of May, first Sunday of June when it is Pentecost") {
        FrenchCalendar.mothersDay(2024) shouldBe d(2024, 5, 26)
        FrenchCalendar.mothersDay(2025) shouldBe d(2025, 5, 25)
        FrenchCalendar.mothersDay(2026) shouldBe d(2026, 5, 31)
        // Pentecost 31 May 2020 pushed it to 7 June.
        FrenchCalendar.pentecost(2020) shouldBe d(2020, 5, 31)
        FrenchCalendar.mothersDay(2020) shouldBe d(2020, 6, 7)
    }

    test("Father's Day is the third Sunday of June") {
        FrenchCalendar.fathersDay(2024) shouldBe d(2024, 6, 16)
        FrenchCalendar.fathersDay(2025) shouldBe d(2025, 6, 15)
        FrenchCalendar.fathersDay(2026) shouldBe d(2026, 6, 21)
    }

    test("fixed holidays and their windows") {
        SeasonEvent.on(d(2026, 10, 24)) shouldBe SeasonEvent.Halloween
        SeasonEvent.on(d(2026, 10, 31)) shouldBe SeasonEvent.Halloween
        SeasonEvent.on(d(2026, 10, 23)) shouldBe null
        SeasonEvent.on(d(2026, 11, 1)) shouldBe SeasonEvent.AllSaints
        SeasonEvent.on(d(2026, 11, 11)) shouldBe SeasonEvent.Armistice
        SeasonEvent.on(d(2026, 11, 4)) shouldBe null
        SeasonEvent.on(d(2026, 12, 24)) shouldBe SeasonEvent.Christmas
        SeasonEvent.on(d(2026, 12, 27)) shouldBe null
        SeasonEvent.on(d(2026, 12, 31)) shouldBe SeasonEvent.NewYear
        SeasonEvent.on(d(2027, 1, 1)) shouldBe SeasonEvent.NewYear
        SeasonEvent.on(d(2027, 1, 2)) shouldBe SeasonEvent.NewYear
        SeasonEvent.on(d(2027, 1, 6)) shouldBe SeasonEvent.Epiphany
        SeasonEvent.on(d(2027, 2, 2)) shouldBe SeasonEvent.Candlemas
        SeasonEvent.on(d(2027, 2, 14)) shouldBe SeasonEvent.Valentine
        SeasonEvent.on(d(2026, 5, 1)) shouldBe SeasonEvent.LabourDay
        SeasonEvent.on(d(2026, 5, 8)) shouldBe SeasonEvent.VictoryDay
        SeasonEvent.on(d(2026, 5, 9)) shouldBe null
        SeasonEvent.on(d(2026, 7, 13)) shouldBe SeasonEvent.BastilleDay
        SeasonEvent.on(d(2026, 7, 14)) shouldBe SeasonEvent.BastilleDay
        SeasonEvent.on(d(2026, 9, 1)) shouldBe SeasonEvent.BackToSchool
    }

    test("movable holidays") {
        SeasonEvent.on(d(2026, 4, 5)) shouldBe SeasonEvent.Easter
        SeasonEvent.on(d(2026, 4, 6)) shouldBe SeasonEvent.Easter
        SeasonEvent.on(d(2026, 4, 7)) shouldBe null
        SeasonEvent.on(d(2026, 5, 31)) shouldBe SeasonEvent.MothersDay
        SeasonEvent.on(d(2020, 6, 7)) shouldBe SeasonEvent.MothersDay
        SeasonEvent.on(d(2020, 5, 31)) shouldBe null
    }

    test("an event on its own day wins an overlap") {
        // 2026: Father's Day and Fête de la Musique both fall on 21 June.
        SeasonEvent.on(d(2026, 6, 20)) shouldBe SeasonEvent.FathersDay
        SeasonEvent.on(d(2026, 6, 21)) shouldBe SeasonEvent.FathersDay
        // 2025: Father's Day 15 June, Fête de la Musique 21 June.
        SeasonEvent.on(d(2025, 6, 21)) shouldBe SeasonEvent.MusicDay
        SeasonEvent.on(d(2025, 6, 15)) shouldBe SeasonEvent.FathersDay
    }

    test("every event has a theme and every year lists all events") {
        SeasonEvent.entries.forEach { NflixSeasonTheme.forEvent(it).event shouldBe it }
        FrenchCalendar.year(2026).size shouldBe SeasonEvent.entries.size
        NflixSeasonTheme.selectable.map { it.key }.toSet().size shouldBe NflixSeasonTheme.selectable.size
    }

    test("theme resolution: holiday beats season, forced key, off") {
        NflixSeasonTheme.byKey("Winter") shouldBe NflixSeasonTheme.Winter
        NflixSeasonTheme.byKey("Nope") shouldBe null
        NflixSeasonTheme.resolve("auto", d(2026, 10, 31), events = true).key shouldBe "Halloween"
        NflixSeasonTheme.resolve("auto", d(2026, 10, 31), events = false).key shouldBe "Autumn"
        NflixSeasonTheme.resolve("auto", d(2026, 7, 14), events = true).key shouldBe "BastilleDay"
        NflixSeasonTheme.resolve("auto", d(2026, 7, 20), events = true).key shouldBe "Summer"
        NflixSeasonTheme.resolve("auto", d(2026, 12, 20), events = true).key shouldBe "Christmas"
        NflixSeasonTheme.resolve("Christmas", d(2026, 7, 20), events = true).key shouldBe "Christmas"
        NflixSeasonTheme.resolve("off", d(2026, 12, 25), events = true).key shouldBe "brand"
        NflixSeasonTheme.resolve("Nope", d(2026, 8, 2), events = true).key shouldBe "Summer"
    }

    test("commemorations stay sober") {
        listOf(NflixSeasonTheme.VictoryDay, NflixSeasonTheme.Armistice).forEach { theme ->
            theme.decor.sober shouldBe true
            theme.decor.particles.none { it.motion == Motion.Confetti } shouldBe true
            (theme.decor.feature == Feature.Fireworks) shouldBe false
        }
    }
})
