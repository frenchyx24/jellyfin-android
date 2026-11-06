package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe

class SearchRankingTest : FunSpec({
    fun movie(id: String, name: String, original: String? = null) = Item(id = id, name = name, originalTitle = original, type = "Movie")

    test("exact and prefix titles come first") {
        val items = listOf(movie("1", "Le Chat potté 2"), movie("2", "Chat noir, chat blanc"), movie("3", "Le Chat potté"))
        SearchRanking.rank("le chat potte", items).map { it.id } shouldBe listOf("3", "1")
    }

    test("unrelated titles sharing one word are dropped") {
        SearchRanking.score("harry potter", "Harry, un ami qui vous veut du bien") shouldBeLessThan SearchRanking.THRESHOLD
        SearchRanking.score("harry potter", "Harry Potter à l'école des sorciers") shouldBeGreaterThan 600
    }

    test("typos still match") {
        SearchRanking.score("harry poter", "Harry Potter et la Chambre des secrets") shouldBeGreaterThan SearchRanking.THRESHOLD
        SearchRanking.score("intersteller", "Interstellar") shouldBeGreaterThan SearchRanking.THRESHOLD
    }

    test("episodes need the series name") {
        val episode = Item(id = "e", name = "La Mort", type = "Episode", seriesName = "Breaking Bad")
        SearchRanking.score("mort", episode) shouldBe 0
        SearchRanking.score("breaking bad", episode) shouldBeGreaterThan SearchRanking.THRESHOLD
    }

    test("original title is searched") {
        SearchRanking.score("spirited away", movie("c", "Le Voyage de Chihiro", "Spirited Away")) shouldBeGreaterThan 900
    }

    test("fallback term for typos") {
        SearchRanking.fallbackTerm("le seigneur des anneau") shouldBe "seig"
    }
})
