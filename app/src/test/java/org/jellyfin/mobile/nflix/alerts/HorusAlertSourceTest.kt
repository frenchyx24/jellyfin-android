package org.jellyfin.mobile.nflix.alerts

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.nflix.data.HorusMedia
import org.jellyfin.mobile.nflix.data.HorusRequest
import org.jellyfin.mobile.nflix.data.HorusStatus
import org.jellyfin.mobile.nflix.data.HorusType

class HorusAlertSourceTest : FunSpec({
    fun request(id: String, status: HorusStatus, jellyfin: String?, type: HorusType = HorusType.Movie) = HorusRequest(
        id = id.hashCode(), type = type, status = 2, createdAt = "", is4k = false,
        media = HorusMedia(id, type, "Titre $id", null, null, null, null, null, null, status, HorusStatus.Unknown, jellyfin),
    )

    test("first run records available requests without posting") {
        var stored: Set<String> = emptySet()
        HorusAlertSource.plan(listOf(request("1", HorusStatus.Available, "abc")), null) { stored = it }.shouldBeEmpty()
        stored shouldBe setOf("movie-1")
    }

    test("a request that becomes available is posted once") {
        var stored: Set<String> = setOf("movie-1")
        val list = listOf(request("1", HorusStatus.Available, "abc"), request("2", HorusStatus.Available, "def"), request("3", HorusStatus.Processing, null))
        val alerts = HorusAlertSource.plan(list, stored) { stored = it }
        alerts.size shouldBe 1
        alerts[0].title shouldBe "« Titre 2 » est disponible"
        alerts[0].itemId shouldBe "def"
        HorusAlertSource.plan(list, stored) { stored = it }.shouldBeEmpty()
    }

    test("partial series count, partial movies and unlinked items do not") {
        HorusAlertSource.isReady(request("4", HorusStatus.Partial, "x", HorusType.Tv)) shouldBe true
        HorusAlertSource.isReady(request("5", HorusStatus.Partial, "x")) shouldBe false
        HorusAlertSource.isReady(request("6", HorusStatus.Available, null)) shouldBe false
    }
})
