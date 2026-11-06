package org.jellyfin.mobile.nflix.ui.screens

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.MediaStream

class TitleHeroTest : FunSpec({
    fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    test("accent follows the dominant vivid hue, ignoring greys") {
        val pixels = IntArray(400) { i -> if (i < 300) argb(40, 40, 40) else argb(200, 30, 30) }
        val accent = AccentColor.from(pixels)!!
        (accent.red > accent.green && accent.red > accent.blue) shouldBe true
        AccentColor.from(IntArray(100) { argb(128, 128, 128) }) shouldBe null
        AccentColor.from(IntArray(0)) shouldBe null
    }

    test("dark accents are lifted to stay readable on black") {
        val accent = AccentColor.from(IntArray(100) { argb(20, 0, 60) })
        accent shouldNotBe null
        (androidx.compose.ui.graphics.Color(accent!!.value).let { it.red + it.green + it.blue } > 0.3f) shouldBe true
    }

    test("anime detection, versions and schedule") {
        val anime = Item(
            id = "a", type = "Series", genres = listOf("Anime"), status = "Continuing",
            airDays = listOf("Saturday"), airTime = "17:30",
            mediaStreams = listOf(MediaStream(type = "Audio", language = "jpn"), MediaStream(type = "Subtitle", language = "fre"), MediaStream(type = "Audio", language = "fre")),
        )
        AnimeInfo.isAnime(anime) shouldBe true
        AnimeInfo.versions(anime) shouldContainExactly listOf("VF", "VOSTFR")
        AnimeInfo.schedule(anime) shouldBe "Chaque samedi à 17h30"
        AnimeInfo.schedule(anime.copy(status = "Ended")) shouldBe null
        AnimeInfo.isAnime(Item(id = "b", genres = listOf("Drame"))) shouldBe false
    }
})
