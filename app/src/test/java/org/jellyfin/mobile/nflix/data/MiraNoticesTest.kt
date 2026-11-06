package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class MiraNoticesTest : FunSpec({
    val now = OffsetDateTime.parse("2026-11-04T12:00:00Z")
    fun notice(id: String, groups: List<String> = emptyList(), expires: String? = null, url: String = "") =
        Notice(id = id, title = "t", message = "m", groups = groups, expiresAt = expires, actionUrl = url, actionLabel = if (url.isEmpty()) "" else "Ouvrir", source = "mira")

    test("audience groups filter") {
        val list = listOf(notice("mira:all"), notice("mira:pro", listOf("plan_pro")), notice("mira:test", listOf("mira_notice_test")))
        MiraNotices.visible(list, setOf("plan_standard", "plan_pro"), now).map { it.id } shouldBe listOf("mira:all", "mira:pro")
        MiraNotices.visible(list, emptySet(), now).map { it.id } shouldBe listOf("mira:all")
    }

    test("expired and unprefixed notices are dropped") {
        val list = listOf(notice("mira:old", expires = "2026-11-04T11:00:00Z"), notice("mira:new", expires = "2026-11-05T00:00:00Z"), notice("abc"), notice("mira:"))
        MiraNotices.visible(list, emptySet(), now).map { it.id } shouldBe listOf("mira:new")
    }

    test("non-https links are removed") {
        val shown = MiraNotices.visible(listOf(notice("mira:a", url = "http://x.fr"), notice("mira:b", url = "https://x.fr")), emptySet(), now)
        shown.map { it.actionUrl } shouldBe listOf("", "https://x.fr")
    }
})
