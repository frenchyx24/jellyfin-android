package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

class AdvancedFilterTest : FunSpec({
    test("empty filter adds no query parameter") {
        AdvancedFilter().query().shouldBeEmpty()
        AdvancedFilter().active shouldBe false
    }

    test("decade expands to ten years and rating to minCommunityRating") {
        val q = AdvancedFilter(decade = 1990, minRating = 7).query()
        q["years"] shouldBe "1990,1991,1992,1993,1994,1995,1996,1997,1998,1999"
        q["minCommunityRating"] shouldBe 7
        AdvancedFilter(decade = 1990, minRating = 7).count shouldBe 2
    }

    test("studio is a query parameter but not a user-visible active filter") {
        val f = AdvancedFilter(studioId = "abc")
        f.query()["studioIds"] shouldBe "abc"
        f.active shouldBe false
    }

    test("decade labels are French") {
        AdvancedFilter.decadeLabel(1980) shouldBe "Années 80"
        AdvancedFilter.decadeLabel(2010) shouldBe "Années 2010"
    }
})
