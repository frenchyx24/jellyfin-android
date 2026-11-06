package org.jellyfin.mobile.nflix.cast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jellyfin.mobile.nflix.data.Item

class CastProtocolTest : FunSpec({
    val credentials = CastProtocol.Credentials("user", "device", "sensitive-token", "https://jellyfin.example", "server", "Living room")

    test("receiver auth stays in message body, never URL") {
        val message = CastProtocol.message("Identify", credentials)
        val json = Json.parseToJsonElement(message).jsonObject
        json["accessToken"]?.toString() shouldBe "\"sensitive-token\""
        json["serverAddress"]?.toString() shouldBe "\"https://jellyfin.example\""
        message.contains("?ApiKey=") shouldBe false
    }

    test("play options carry resume and selected tracks") {
        val options = CastProtocol.playOptions(listOf(Item(id = "episode", name = "Pilot", type = "Episode")), "server", 42_000_000, 2, 4)
        options["startPositionTicks"].toString() shouldBe "42000000"
        options["audioStreamIndex"].toString() shouldBe "2"
        options["subtitleStreamIndex"].toString() shouldBe "4"
        options["items"]?.toString()?.contains("episode") shouldBe true
    }

    test("remote state updates position, volume and tracks") {
        val target = CastTarget("screen", "TV", CastTarget.Kind.Jellyfin)
        val initial = CastStatus(target, idle = true)
        val state = Json.parseToJsonElement("""{"NowPlayingItem":{"Id":"item","Name":"Film","RunTimeTicks":900},"PlayState":{"PositionTicks":123,"IsPaused":true,"VolumeLevel":70,"AudioStreamIndex":1,"SubtitleStreamIndex":2}}""").jsonObject
        val result = CastProtocol.applyState(initial, state)
        result.itemId shouldBe "item"
        result.title shouldBe "Film"
        result.positionTicks shouldBe 123
        result.durationTicks shouldBe 900
        result.paused shouldBe true
        result.volume shouldBe 0.7f
        result.audioIndex shouldBe 1
        result.subtitleIndex shouldBe 2
    }
})
