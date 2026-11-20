package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/** 5.6-5.8.1 crashed at launch: bare base64 pins made CertificatePinner.Builder.add throw. */
class PinPolicyPinnerTest : FunSpec({
    test("every baked pin is accepted by OkHttp") {
        val pinner = PinPolicy.Pins.pinner()
        pinner.pins.size shouldBe PinPolicy.Pins.hosts.size * PinPolicy.Pins.accepted.size
    }

    test("pin prefix added once") {
        PinPolicy.Pins.pin("abc=") shouldBe "sha256/abc="
        PinPolicy.Pins.pin("sha256/abc=") shouldBe "sha256/abc="
        PinPolicy.Pins.pin(PinPolicy.Pins.WE1_INTERMEDIATE) shouldStartWith "sha256/"
    }
})
