package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Decodes real server responses (local fixtures, not committed) with the app models. */
class ParsingTest : FunSpec({
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun fixture(name: String): String? = javaClass.getResource("/nflix/$name.json")?.readText()

    test("items page") {
        val text = fixture("items") ?: return@test
        val page = json.decodeFromString(ItemsPage.serializer(), text)
        page.items.shouldNotBeEmpty()
        System.err.println("items: " + page.items.groupingBy { it.type }.eachCount())
        page.items.forEach { it.id.length shouldBe 32 }
    }

    test("latest list") {
        val text = fixture("latest") ?: return@test
        json.decodeFromString(ListSerializer(Item.serializer()), text).shouldNotBeEmpty()
    }

    test("lyrics") {
        val text = fixture("lyrics") ?: return@test
        val lyrics = json.decodeFromString(Lyrics.serializer(), text)
        lyrics.lines.shouldNotBeEmpty()
        System.err.println("lyrics synced: " + lyrics.lines.any { it.start != null })
    }

    test("plugins") {
        fixture("notices")?.let { json.decodeFromString(ListSerializer(Notice.serializer()), it).shouldNotBeEmpty() }
        fixture("mediabar")?.let { json.decodeFromString(MediaBarConfig.serializer(), it).enabled shouldBe true }
        fixture("moonfin")?.let { json.decodeFromString(MoonfinSettings.serializer(), it) }
        fixture("enhanced")?.let { json.decodeFromString(EnhancedConfig.serializer(), it) }
        fixture("me")?.let { json.decodeFromString(CurrentUser.serializer(), it).id.length shouldBe 32 }
    }

    test("horus") {
        fixture("horus_trending")?.let {
            val page = HorusParser.page(json.parseToJsonElement(it).jsonObject)
            page.results.shouldNotBeEmpty()
            page.results.first().poster shouldNotBe null
            System.err.println("horus trending: " + page.results.take(3).map { m -> "${m.type}:${m.title}:${m.status}" })
        }
        fixture("horus_music")?.let {
            val page = HorusParser.page(json.parseToJsonElement(it).jsonObject, HorusType.Music)
            page.results.first().type shouldBe HorusType.Music
        }
        fixture("horus_book")?.let {
            val book = HorusParser.media(json.parseToJsonElement(it).jsonObject, HorusType.Book)!!
            book.title shouldBe "Dune"
        }
        fixture("horus_tv")?.let {
            val tv = HorusParser.media(json.parseToJsonElement(it).jsonObject, HorusType.Tv)!!
            tv.seasons.shouldNotBeEmpty()
            System.err.println("tv seasons: " + tv.seasons.size + " status " + tv.status)
        }
        fixture("horus_requests")?.let {
            val requests = HorusParser.requests(json.parseToJsonElement(it).jsonObject)
            requests.shouldNotBeEmpty()
            System.err.println("requests: " + requests.take(3).map { r -> "${r.type}:${r.media.id}:${r.statusLabel}" })
        }
    }
})
