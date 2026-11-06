package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class SearchEngineTest : FunSpec({
    fun title(id: String, name: String, type: String = "Series", year: Int? = null, genres: List<String> = emptyList(), rating: Double? = null) =
        Item(id = id, name = name, type = type, year = year, genres = genres, communityRating = rating)

    val catalog = listOf(
        title("supra", "Supracell", year = 2024, genres = listOf("Drame", "Science-Fiction")),
        title("got", "Game of Thrones", year = 2011, genres = listOf("Drame", "Fantastique")),
        title("spider", "Spider-Man", type = "Movie", year = 2002, genres = listOf("Action")),
        title("dune1", "Dune", type = "Movie", year = 1984, genres = listOf("Science-Fiction")),
        title("dune2", "Dune", type = "Movie", year = 2021, genres = listOf("Science-Fiction"), rating = 8.0),
        title("scream", "Scream", type = "Movie", year = 1996, genres = listOf("Horreur")),
        title("br", "Blade Runner 2049", type = "Movie", year = 2017),
        title("lotr", "The Lord of the Rings", type = "Movie", year = 2001),
        title("spring", "Spring Breakers", type = "Movie", year = 2012),
    )
    val index = SearchIndex(catalog)

    test("episode codes are split from the title") {
        SearchQuery.parse("Supracell S01E03") shouldBe SearchQuery("supracell", season = 1, episode = 3)
        SearchQuery.parse("supracell 1x3") shouldBe SearchQuery("supracell", season = 1, episode = 3)
        SearchQuery.parse("One Piece saison 2 épisode 5") shouldBe SearchQuery("one piece", season = 2, episode = 5)
        SearchQuery.parse("naruto ep 12") shouldBe SearchQuery("naruto", episode = 12)
        SearchQuery.parse("the boys s2") shouldBe SearchQuery("the boys", season = 2)
    }

    test("a year narrows, but a year alone is the query") {
        SearchQuery.parse("dune 2021") shouldBe SearchQuery("dune", year = 2021)
        SearchQuery.parse("1917") shouldBe SearchQuery("1917")
        SearchEngine.local(SearchQuery.parse("dune 2021"), index).map { it.id } shouldBe listOf("dune2")
        SearchEngine.local(SearchQuery.parse("2024"), index).map { it.id } shouldBe listOf("supra")
    }

    test("fragments, acronyms and spacing find titles") {
        SearchEngine.local(SearchQuery.parse("cell"), index).map { it.id } shouldBe listOf("supra")
        SearchEngine.local(SearchQuery.parse("got"), index).first().id shouldBe "got"
        SearchEngine.local(SearchQuery.parse("spiderman"), index).first().id shouldBe "spider"
        SearchRanking.score("supracel", "Supracell") shouldBeGreaterThan SearchRanking.THRESHOLD
    }

    test("genres are recognised") {
        SearchEngine.genre(SearchQuery.parse("horreur"), catalog) shouldBe "Horreur"
        SearchEngine.genre(SearchQuery.parse("science"), catalog) shouldBe "Science-Fiction"
        SearchEngine.byGenre("Science-Fiction", catalog).first().id shouldBe "dune2"
        SearchEngine.topGenres(catalog).first() shouldBe "Science-Fiction"
    }

    test("merge keeps one entry per title, best first") {
        val server = listOf(title("supra", "Supracell"), title("x", "Supracell: Origins", type = "Movie"))
        val query = SearchQuery.parse("supracell")
        SearchEngine.merge(query, SearchEngine.local(query, index), server).map { it.id } shouldBe listOf("supra", "x")
    }

    test("numbers that belong to the title are not taken as a year filter") {
        SearchEngine.local(SearchQuery.parse("blade runner 2049"), index).first().id shouldBe "br"
        val dune = SearchQuery.parse("dune 2021")
        SearchEngine.merge(dune, emptyList(), catalog).map { it.id } shouldBe listOf("dune2")
    }

    test("fragments rank below real word matches") {
        SearchEngine.local(SearchQuery.parse("ring"), index).first().id shouldBe "lotr"
        SearchEngine.local(SearchQuery.parse("ring"), index).map { it.id }.contains("spring") shouldBe true
    }
})
