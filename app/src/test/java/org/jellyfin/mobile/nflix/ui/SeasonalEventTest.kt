package org.jellyfin.mobile.nflix.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class SeasonalEventTest : FunSpec({
    test("Easter dates") {
        SeasonalEvent.easterSunday(2026) shouldBe LocalDate.of(2026, 4, 5)
        SeasonalEvent.easterSunday(2027) shouldBe LocalDate.of(2027, 3, 28)
    }

    test("French holidays resolve on their days") {
        SeasonalEvent.activeOn(LocalDate.of(2026, 7, 14)) shouldBe SeasonalEvent.Bastille
        SeasonalEvent.activeOn(LocalDate.of(2026, 10, 31)) shouldBe SeasonalEvent.Halloween
        SeasonalEvent.activeOn(LocalDate.of(2026, 12, 24)) shouldBe SeasonalEvent.Christmas
        SeasonalEvent.activeOn(LocalDate.of(2027, 1, 1)) shouldBe SeasonalEvent.NewYear
        SeasonalEvent.activeOn(LocalDate.of(2026, 11, 11)) shouldBe SeasonalEvent.Armistice
        SeasonalEvent.activeOn(LocalDate.of(2026, 4, 6)) shouldBe SeasonalEvent.Easter
        SeasonalEvent.activeOn(LocalDate.of(2026, 11, 4)) shouldBe null
    }

    test("disabled events are skipped") {
        SeasonalEvent.activeOn(LocalDate.of(2026, 12, 24), setOf("Christmas")) shouldBe null
    }
})
