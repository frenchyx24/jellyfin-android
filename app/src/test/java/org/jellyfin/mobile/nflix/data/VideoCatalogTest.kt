package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VideoCatalogTest : FunSpec({
    fun video(genres: List<String>, studios: List<String> = emptyList(), locations: List<String> = emptyList(), tags: List<String> = emptyList()) =
        Item(id = "x", type = "Series", genres = genres, studios = studios.map { NameId(it, it) }, productionLocations = locations, tags = tags)

    test("western animation is not anime") {
        VideoCatalog.isAnime(video(listOf("Animation", "Familial"), listOf("Pixar"), listOf("United States of America"))) shouldBe false
    }

    test("anime genre or tag marks anime") {
        VideoCatalog.isAnime(video(listOf("Animé", "Action"))) shouldBe true
        VideoCatalog.isAnime(video(listOf("Action"), tags = listOf("anime"))) shouldBe true
    }

    test("animation from Japan or a Japanese studio is anime") {
        VideoCatalog.isAnime(video(listOf("Animation"), locations = listOf("Japan"))) shouldBe true
        VideoCatalog.isAnime(video(listOf("Animation", "Action & Adventure"), listOf("MAPPA"))) shouldBe true
    }

    test("Japanese live action is not anime") {
        VideoCatalog.isAnime(video(listOf("Drame"), locations = listOf("Japan"))) shouldBe false
    }

    test("anime libraries are recognised, animation libraries are not") {
        VideoCatalog.isAnimeLibrary(Item(id = "a", name = "Animés")) shouldBe true
        VideoCatalog.isAnimeLibrary(Item(id = "b", name = "Films d'animation")) shouldBe false
    }

    test("genres sort popular first and drop anime labels") {
        VideoCatalog.sortGenres(listOf("Western", "Anime", "Comédie", "Action", "Biographie")) shouldBe listOf("Action", "Comédie", "Western", "Biographie")
    }
})
