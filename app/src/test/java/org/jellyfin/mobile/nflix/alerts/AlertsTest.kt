package org.jellyfin.mobile.nflix.alerts

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class AlertsTest : FunSpec({
    val now = 1_800_000_000L
    fun alert(id: Long, kind: String = "horus_available", age: Long = 60) = NflixAlert(id, kind, "Titre $id", createdAt = now - age)

    test("enabled alerts are posted, disabled ones consumed silently") {
        val plan = AlertDispatch.plan(
            listOf(alert(1), alert(2, "episode_available"), alert(3, "episode_announced")),
            enabled = { it != AlertKind.Episode }, canNotify = true, alreadyPosted = emptySet(), now = now,
        )
        plan.notify.map { it.id } shouldContainExactly listOf(1L, 3L)
        plan.ack shouldContainExactly listOf(2L)
    }

    test("an alert posted before a failed ack is never shown twice") {
        val plan = AlertDispatch.plan(listOf(alert(5)), { true }, canNotify = true, alreadyPosted = setOf(5L), now = now)
        plan.notify.shouldBeEmpty()
        plan.ack shouldContainExactly listOf(5L)
    }

    test("blocked notifications keep fresh alerts on the server and drop stale ones") {
        val plan = AlertDispatch.plan(listOf(alert(6), alert(7, age = 8 * 86_400)), { true }, canNotify = false, alreadyPosted = emptySet(), now = now)
        plan.notify.shouldBeEmpty()
        plan.ack shouldContainExactly listOf(7L)
    }

    test("a backlog older than a week never floods the tray once notifications are allowed") {
        val plan = AlertDispatch.plan(listOf(alert(10, age = 9 * 86_400), alert(11)), { true }, canNotify = true, alreadyPosted = emptySet(), now = now)
        plan.notify.map { it.id } shouldContainExactly listOf(11L)
        plan.ack shouldContainExactly listOf(10L)
    }

    test("notification ids never collide with 0 and survive long ids") {
        NflixAlerts.intId(0) shouldBe 1
        (NflixAlerts.intId(1L) == NflixAlerts.intId(1L + (1L shl 32))) shouldBe false
        NflixAlerts.intId(42) shouldBe 42
    }

    test("unknown kinds and duplicates are consumed once") {
        val plan = AlertDispatch.plan(listOf(alert(8, "future_kind"), alert(9), alert(9)), { true }, canNotify = true, alreadyPosted = emptySet(), now = now)
        plan.notify.map { it.id } shouldContainExactly listOf(9L)
        plan.ack shouldContainExactly listOf(8L)
    }

    test("alerts and calendar parse partial server answers") {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val page = json.decodeFromString(
            CalendarPage.serializer(),
            """{"events":[{"id":"a","kind":"episode","releaseAt":10,"estimatedAt":null,"extra":1},{"id":"b","kind":"movie","releaseAt":20,"estimatedAt":30,"confidence":"estimated","followed":true}],"sourceAvailable":true}""",
        )
        page.events.map { it.nflixAt } shouldContainExactly listOf(10L, 30L)
        page.events[1].followed shouldBe true
        AlertKind.of("horus_available") shouldBe AlertKind.Horus
        AlertKind.of("nope") shouldBe null
    }

    test("month grid covers whole weeks, Monday first") {
        val days = CalendarModel.gridDays(YearMonth.of(2026, 11))
        days.first() shouldBe LocalDate.of(2026, 10, 26)
        days.last() shouldBe LocalDate.of(2026, 12, 6)
        (days.size % 7) shouldBe 0
        CalendarModel.gridDays(YearMonth.of(2027, 2)).size shouldBe 28
    }

    test("events land on the day they should be on Nflix and filters apply") {
        val zone = ZoneOffset.UTC
        val day = LocalDate.of(2026, 11, 10).atStartOfDay(zone).toEpochSecond()
        val late = CalendarEvent("x", "episode", releaseAt = day + 23 * 3600, estimatedAt = day + 29 * 3600, followed = true, title = "B")
        val movie = CalendarEvent("y", "movie", releaseType = "DigitalRelease", releaseAt = day, estimatedAt = day + 86_400, title = "A")
        val anime = CalendarEvent("z", "anime", releaseAt = day + 3600, title = "C")
        val grouped = CalendarModel.byDay(listOf(late, movie, anime), zone)
        grouped.keys.toList() shouldContainExactly listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11))
        grouped.getValue(LocalDate.of(2026, 11, 11)).map { it.id } shouldContainExactly listOf("y", "x")
        CalendarModel.matches(movie, CalendarModel.Filter.Movies, false) shouldBe true
        CalendarModel.matches(movie, CalendarModel.Filter.Series, false) shouldBe false
        CalendarModel.matches(anime, CalendarModel.Filter.Anime, false) shouldBe true
        CalendarModel.matches(anime, CalendarModel.Filter.All, followedOnly = true) shouldBe false
        CalendarModel.matches(late, CalendarModel.Filter.All, followedOnly = true) shouldBe true
    }

    test("labels say when a date is only an estimate") {
        val event = CalendarEvent("e", "episode", releaseAt = 0, estimatedAt = 3600, confidence = "learned")
        CalendarModel.confidenceLabel(event) shouldBe "Estimation (d'après les épisodes précédents)"
        CalendarModel.confidenceLabel(event.copy(confidence = "available", available = true)) shouldBe "Disponible"
        CalendarModel.detail(event.copy(available = true), ZoneOffset.UTC) shouldBe "Disponible sur Nflix"
        CalendarModel.detail(event.copy(estimatedAt = 2 * 86_400L), ZoneOffset.UTC) shouldBe "Sur Nflix vers 00h00 · Diffusion le 1 janv."
        CalendarModel.dayTitle(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 5)) shouldBe "Demain"
    }
})
