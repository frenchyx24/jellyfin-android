package org.jellyfin.mobile.nflix.ui.tutorial

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

private class MemoryStorage(var value: Int = 0) : TutorialStorage {
    var writes = 0
    override fun seenVersion() = value
    override fun saveSeenVersion(version: Int) {
        value = version
        writes++
    }
}

class TutorialTest : FunSpec({
    test("shown until seen, then never again") {
        val storage = MemoryStorage()
        val progress = TutorialProgress(storage)
        progress.shouldShow() shouldBe true
        progress.markSeen()
        progress.shouldShow() shouldBe false
        storage.value shouldBe TutorialProgress.VERSION
        // A new instance (app restart, new sign-in) reads the same preference.
        TutorialProgress(storage).shouldShow() shouldBe false
    }

    test("markSeen is idempotent and never downgrades a newer version") {
        val storage = MemoryStorage(TutorialProgress.VERSION + 1)
        val progress = TutorialProgress(storage)
        progress.shouldShow() shouldBe false
        progress.markSeen()
        storage.value shouldBe TutorialProgress.VERSION + 1
        storage.writes shouldBe 0
    }

    test("an older tutorial version shows the current one again") {
        TutorialProgress(MemoryStorage(TutorialProgress.VERSION - 1)).shouldShow() shouldBe true
    }

    test("administration page only for administrators, before the last page") {
        val member = TutorialPages.pages(isAdmin = false)
        val admin = TutorialPages.pages(isAdmin = true)
        member.map { it.topic } shouldNotContain TutorialTopic.Admin
        admin.map { it.topic } shouldContain TutorialTopic.Admin
        admin.size shouldBe member.size + 1
        admin[admin.lastIndex - 1].topic shouldBe TutorialTopic.Admin
        member.first().topic shouldBe TutorialTopic.Welcome
        member.last().topic shouldBe TutorialTopic.Done
        admin.last().topic shouldBe TutorialTopic.Done
    }

    test("every feature area has a page with a title and unique topic") {
        val pages = TutorialPages.pages(isAdmin = true)
        pages.map { it.topic }.toSet() shouldBe TutorialTopic.entries.toSet()
        pages.all { it.title.isNotBlank() && it.body.isNotBlank() } shouldBe true
        pages.map { it.topic }.distinct().size shouldBe pages.size
    }

    test("profile page tells how to see the tutorial again") {
        val profile = TutorialPages.pages(isAdmin = false).single { it.topic == TutorialTopic.Profile }
        profile.tips.any { it.startsWith("Didacticiel") } shouldBe true
    }

    test("late administrator answer never moves the page on screen") {
        val member = TutorialPages.pages(isAdmin = false)
        val admin = TutorialPages.pages(isAdmin = true)
        val adminIndex = admin.indexOfFirst { it.topic == TutorialTopic.Admin }
        // Allowed only while the page on screen keeps its index once the admin page is inserted.
        for (page in member.indices) {
            val allowed = TutorialPages.canInsertAdmin(page)
            allowed shouldBe (page < adminIndex)
            if (allowed) admin[page] shouldBe member[page]
        }
        TutorialPages.canInsertAdmin(member.lastIndex) shouldBe false
    }

    test("navigation stays within bounds") {
        val count = TutorialPages.pages(isAdmin = false).size
        TutorialFlow.next(0, count) shouldBe 1
        TutorialFlow.next(count - 1, count) shouldBe count - 1
        TutorialFlow.previous(0) shouldBe 0
        TutorialFlow.previous(3) shouldBe 2
        TutorialFlow.next(0, 1) shouldBe 0
    }

    test("button labels and back behaviour") {
        TutorialFlow.primaryLabel(0, 5) shouldBe "Suivant"
        TutorialFlow.primaryLabel(4, 5) shouldBe "C'est parti"
        TutorialFlow.isLast(4, 5) shouldBe true
        TutorialFlow.isLast(3, 5) shouldBe false
        TutorialFlow.backCloses(0) shouldBe true
        TutorialFlow.backCloses(2) shouldBe false
        TutorialFlow.pageDescription(1, 12) shouldBe "Page 2 sur 12"
    }
})
