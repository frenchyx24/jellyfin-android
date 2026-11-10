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
})
