package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LogoMediaTest : FunSpec({
    fun bytes(vararg v: Int) = (v.map { it.toByte() } + List(16) { 0.toByte() }).toByteArray()
    fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1) + ByteArray(16)

    test("pictures, animations, clips and Lottie are recognised") {
        LogoMedia.sniff(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) shouldBe ("image" to "image/png")
        LogoMedia.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0)) shouldBe ("image" to "image/jpeg")
        LogoMedia.sniff(ascii("GIF89a")) shouldBe ("animated" to "image/gif")
        LogoMedia.sniff(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")) shouldBe ("image" to "image/webp")
        LogoMedia.sniff(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8X\u0000\u0000\u0000\u0000\u0002")) shouldBe ("animated" to "image/webp")
        LogoMedia.sniff(ascii("\u0000\u0000\u0000\u0018ftypmp42")) shouldBe ("video" to "video/mp4")
        LogoMedia.sniff(bytes(0x1A, 0x45, 0xDF, 0xA3)) shouldBe ("video" to "video/webm")
        LogoMedia.sniff("{\"v\":\"5.7\",\"fr\":30,\"w\":10,\"h\":10,\"layers\":[]}".toByteArray()) shouldBe ("lottie" to "application/json")
    }

    test("markup and unknown files are refused") {
        LogoMedia.sniff("<svg></svg>".toByteArray()) shouldBe null
        LogoMedia.sniff("{\"hello\":1}".toByteArray()) shouldBe null
        LogoMedia.sniff(ByteArray(0)) shouldBe null
    }

    test("limits match the server") {
        LogoMedia.maxBytes("video") shouldBe 8 * 1024 * 1024
        LogoMedia.maxBytes("animated") shouldBe 6 * 1024 * 1024
        LogoMedia.maxBytes("image") shouldBe 2 * 1024 * 1024
    }
})
