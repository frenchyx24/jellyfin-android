package org.jellyfin.mobile.nflix.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class SeasonalEventTest : FunSpec({
    test("seasons follow the calendar") {
        Season.on(LocalDate.of(2026, 1, 15)) shouldBe Season.Winter
        Season.on(LocalDate.of(2026, 4, 6)) shouldBe Season.Spring
        Season.on(LocalDate.of(2026, 7, 14)) shouldBe Season.Summer
        Season.on(LocalDate.of(2026, 10, 31)) shouldBe Season.Autumn
        Season.on(LocalDate.of(2026, 12, 24)) shouldBe Season.Winter
    }

    test("events tint their days") {
        SeasonEvent.on(LocalDate.of(2026, 10, 31)) shouldBe SeasonEvent.Halloween
        SeasonEvent.on(LocalDate.of(2026, 12, 24)) shouldBe SeasonEvent.Christmas
        SeasonEvent.on(LocalDate.of(2027, 1, 1)) shouldBe SeasonEvent.NewYear
        SeasonEvent.on(LocalDate.of(2026, 11, 4)) shouldBe null
    }

    test("theme resolution: forced key, event, season") {
        NflixSeasonTheme.byKey("winter") shouldBe NflixSeasonTheme.Winter
        NflixSeasonTheme.byKey("nope") shouldBe null
        NflixSeasonTheme.resolve("auto", LocalDate.of(2026, 10, 31), events = true).key shouldBe "halloween"
        NflixSeasonTheme.resolve("auto", LocalDate.of(2026, 10, 31), events = false).key shouldBe "autumn"
        NflixSeasonTheme.resolve("auto", LocalDate.of(2026, 7, 14), events = true).key shouldBe "summer"
    }
})
