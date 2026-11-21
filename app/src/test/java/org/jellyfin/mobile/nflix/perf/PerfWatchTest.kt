package org.jellyfin.mobile.nflix.perf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PerfWatchTest : FunSpec({
    test("median of odd and even histories") {
        PerfWatch.median(listOf(900L, 300L, 600L)) shouldBe 600L
        PerfWatch.median(listOf(400L, 800L)) shouldBe 600L
        PerfWatch.median(emptyList()) shouldBe null
    }

    test("history keeps the last ten sane values") {
        var raw = "junk,-5,999999"
        (1..12).forEach { raw = PerfWatch.append(raw, it * 100L) }
        PerfWatch.parse(raw) shouldBe (3..12).map { it * 100L }
    }
})
