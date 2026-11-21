package org.jellyfin.mobile.nflix

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.nflix.widget.ResumeWidget

class SettingsBackupTest : FunSpec({
    test("comfort settings travel") {
        SettingsBackup.keep("data_saver") shouldBe true
        SettingsBackup.keep("dialogue_boost") shouldBe true
    }

    test("secrets and per-title state never leave the device") {
        listOf("access_token", "parentalPin", "pin_hash", "last_id", "resume:abc", "chat_cursor", "session_user", "Password").forEach {
            SettingsBackup.keep(it) shouldBe false
        }
    }

    test("widget label") {
        ResumeWidget.label(null) shouldBe "Ouvrir Nflix"
        ResumeWidget.label("Dune") shouldBe "Reprendre « Dune »"
    }
})
