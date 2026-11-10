package org.jellyfin.mobile.nflix.tv

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class TvLinkTest : FunSpec({
    test("codes are exactly six ASCII digits") {
        TvLink.isValidCode("123456") shouldBe true
        TvLink.isValidCode("12345") shouldBe false
        TvLink.isValidCode("1234567") shouldBe false
        TvLink.isValidCode("12a456") shouldBe false
        TvLink.isValidCode("１２３４５６") shouldBe false // full-width digits
        TvLink.isValidCode("") shouldBe false
    }

    test("QR deep link from the TV is parsed") {
        val request = TvLink.parseLink("nflix://tv-link?code=968722&name=Salon%20TV")!!
        request.code shouldBe "968722"
        request.name shouldBe "Salon TV"
        request.source shouldBe TvRequest.Source.QrCode
    }

    test("TV name encoded with + by URLEncoder is decoded, capped and cleaned") {
        TvLink.parseLink("nflix://tv-link?code=111222&name=Chambre+parents")!!.name shouldBe "Chambre parents"
        TvLink.parseLink("nflix://tv-link?code=111222&name=" + "x".repeat(200))!!.name.length shouldBe 60
        TvLink.parseLink("nflix://tv-link?code=111222&name=%0A%0D")!!.name shouldBe "TV Nflix"
        TvLink.parseLink("nflix://tv-link?code=111222")!!.name shouldBe "TV Nflix"
    }

    test("foreign or malformed links are refused") {
        TvLink.parseLink("https://evil.example/tv-link?code=123456") shouldBe null
        TvLink.parseLink("nflix://other?code=123456") shouldBe null
        TvLink.parseLink("nflix://tv-link?code=12345") shouldBe null
        TvLink.parseLink("nflix://tv-link?code=123456789") shouldBe null
        TvLink.parseLink("nflix://tv-link") shouldBe null
        TvLink.parseLink("not a uri %%") shouldBe null
    }

    test("authorization errors are readable") {
        TvLink.authorizeError(404, null) shouldContain "expiré"
        TvLink.authorizeError(400, null) shouldContain "Code inconnu"
        TvLink.authorizeError(401, false) shouldContain "désactivée"
        TvLink.authorizeError(403, false) shouldContain "désactivée"
        TvLink.authorizeError(401, true) shouldContain "session"
        TvLink.authorizeError(null, null) shouldContain "injoignable"
        TvLink.authorizeError(500, null) shouldContain "500"
    }

    test("TV TXT record gives the code, the name and the address") {
        val tv = discoveredTv(
            serviceName = "Nflix TV Salon",
            host = "192.168.1.24",
            port = 8096,
            attributes = mapOf("code" to "968722".toByteArray(), "name" to "Salon".toByteArray()),
        )!!
        tv.code shouldBe "968722"
        tv.name shouldBe "Salon"
        tv.host shouldBe "192.168.1.24"
        tv.port shouldBe 8096
        tv.key shouldBe "salon|192.168.1.24"
    }

    test("TV without a valid six-digit code is ignored") {
        discoveredTv("Nflix TV", "10.0.0.2", 80, mapOf("code" to "12345".toByteArray())) shouldBe null
        discoveredTv("Nflix TV", "10.0.0.2", 80, mapOf("code" to "abcdef".toByteArray())) shouldBe null
        discoveredTv("Nflix TV", "10.0.0.2", 80, emptyMap()) shouldBe null
    }

    test("TV name falls back to the mDNS service name, cleaned") {
        discoveredTv("Nflix TV Chambre", "10.0.0.3", 80, mapOf("code" to "123456".toByteArray()))!!.name shouldBe "Chambre"
        discoveredTv("Nflix TV", "10.0.0.3", 80, mapOf("code" to "123456".toByteArray()))!!.name shouldBe "TV Nflix"
        discoveredTv("Nflix TV", "10.0.0.3", 80, mapOf("code" to "123456".toByteArray(), "name" to "\n".toByteArray()))!!.name shouldBe "TV Nflix"
    }

    test("discovery index keeps one entry per name and address") {
        val index = DiscoveryIndex()
        index.add("record-1", DiscoveredTv("Salon", "10.0.0.4", 80, "111111")) shouldBe true
        index.add("record-2", DiscoveredTv("Salon", "10.0.0.4", 80, "111111")) shouldBe false
        index.add("record-3", DiscoveredTv("salon", "10.0.0.4", 80, "111111")) shouldBe false
        index.values().size shouldBe 1
        index.add("record-4", DiscoveredTv("Salon", "10.0.0.5", 80, "222222")) shouldBe true
        index.values().size shouldBe 2
    }

    test("a lost mDNS record removes the TV only when no other record keeps it") {
        val index = DiscoveryIndex()
        index.add("record-1", DiscoveredTv("Salon", "10.0.0.4", 80, "111111"))
        index.add("record-2", DiscoveredTv("Salon", "10.0.0.4", 80, "111111"))
        index.removeService("record-1") shouldBe false
        index.values().size shouldBe 1
        index.removeService("record-2") shouldBe true
        index.values().isEmpty() shouldBe true
        index.removeService("unknown") shouldBe false
    }

    test("the discovery screen opens and closes") {
        TvLink.closeDiscovery()
        TvLink.discoveryOpen.value shouldBe false
        TvLink.openDiscovery()
        TvLink.discoveryOpen.value shouldBe true
        TvLink.closeDiscovery()
        TvLink.discoveryOpen.value shouldBe false
    }
})
