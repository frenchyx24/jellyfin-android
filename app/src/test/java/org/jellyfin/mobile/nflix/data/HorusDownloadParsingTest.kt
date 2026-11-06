package org.jellyfin.mobile.nflix.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jellyfin.mobile.nflix.ui.screens.formatBytes
import org.jellyfin.mobile.nflix.ui.screens.formatDuration
import org.jellyfin.mobile.nflix.ui.screens.parseEta
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.Duration

class HorusDownloadParsingTest : FunSpec({
    // Shape produced by Horus' download tracker (Sonarr queue item mapped by downloadtracker.js).
    val response = """
        {"results":[{"id":7,"status":2,"type":"tv","is4k":false,"createdAt":"2026-11-03T10:15:22.000Z","updatedAt":"2026-11-03T10:16:00.000Z",
          "requestedBy":{"displayName":"Frenchyx"},
          "seasons":[{"seasonNumber":2,"status":2},{"seasonNumber":1,"status":2},{"seasonNumber":0,"status":2}],
          "media":{"tmdbId":96677,"mediaType":"tv","status":3,"status4k":1,
            "downloadStatus":[
              {"title":"Show.S01E02","size":2000,"sizeLeft":500,"status":"downloading","timeLeft":"00:12:30","episode":{"seasonNumber":1,"episodeNumber":2,"title":"Chapitre 2"}},
              {"title":"Show.S01E01","size":1000,"sizeLeft":0,"status":"completed","timeLeft":"00:00:00","episode":{"seasonNumber":1,"episodeNumber":1}}
            ],
            "downloadStatus4k":[]}}]}
    """.trimIndent()

    test("parses queue items, seasons and weighted progress") {
        val request = HorusParser.requests(Json.parseToJsonElement(response).jsonObject).single()
        request.seasons.map { it.number } shouldBe listOf(1, 2)
        request.requestedBy shouldBe "Frenchyx"
        request.downloads.size shouldBe 2
        // Sorted by season then episode.
        request.downloads.first().episode shouldBe 1
        request.downloads[1].episodeTitle shouldBe "Chapitre 2"
        request.downloads[1].progress!!.toDouble() shouldBe (0.75 plusOrMinus 0.001)
        // (1000 + 1500) / 3000
        request.progress!!.toDouble() shouldBe (2500.0 / 3000.0 plusOrMinus 0.001)
        request.downloading shouldBe true
    }

    test("no queue means no progress") {
        val idle = response.replace(Regex("\"downloadStatus\":\\[.*?\\],\\s*\"downloadStatus4k\"", RegexOption.DOT_MATCHES_ALL), "\"downloadStatus\":[],\"downloadStatus4k\"")
        val request = HorusParser.requests(Json.parseToJsonElement(idle).jsonObject).single()
        request.progress shouldBe null
        request.downloading shouldBe false
    }

    test("eta falls back to arr time left") {
        val download = HorusDownload("x", 10, 5, "downloading", "1.02:03:04", null, null, null, null)
        parseEta(download) shouldBe Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4)
        formatDuration(parseEta(download)!!) shouldBe "1 j 2 h"
        formatDuration(Duration.ofMinutes(12)) shouldBe "12 min"
    }

    test("formats sizes in french units") {
        formatBytes(512) shouldBe "512 o"
        formatBytes(1_610_612_736) shouldBe "1,5 Go"
    }
})
