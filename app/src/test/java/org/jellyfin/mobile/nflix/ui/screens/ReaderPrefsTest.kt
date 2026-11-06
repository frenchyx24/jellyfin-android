package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ReaderPrefsTest : FunSpec({
    test("theme colours override every element of the book, publisher styles included") {
        val css = ReaderPrefs(theme = ReaderTheme.Night).css()
        css shouldContain "background:#14141A!important"
        // The bug: books set p{color:#000}; the universal rule must carry !important.
        css shouldContain "*{color:#DADAE0!important"
    }
    test("custom text colour and day theme") {
        val prefs = ReaderPrefs(theme = ReaderTheme.Day, textColor = Color(0xFFB8D8FF))
        prefs.text shouldBe Color(0xFFB8D8FF)
        prefs.css() shouldContain "color:#B8D8FF!important"
        prefs.css() shouldContain "background:#FFFFFF!important"
    }
    test("layout settings reach the stylesheet") {
        val css = ReaderPrefs(size = 140, lineHeight = 1.8f, margin = 40, justify = false, font = ReaderFont.Sans).css()
        css shouldContain "font-size:140%"
        css shouldContain "line-height:1.8"
        css shouldContain "padding:72px 40px 120px 40px"
        css shouldContain "text-align:left"
        css shouldContain "sans-serif"
    }
})
