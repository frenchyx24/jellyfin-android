package org.jellyfin.mobile.nflix.family

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.TimeZone

class FamilyLogicTest : FunSpec({
    val head = SessionAccount(userId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", userName = "papa", token = "head-token", method = "mira")
    val kid = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    val teen = "cccccccccccccccccccccccccccccccc"

    test("switching from the head keeps the head account") {
        val state = ProfileSwitchLogic.afterSwitch(null, head, kid, "Léa")
        state shouldBe SwitchState(head, kid, "Léa")
    }
    test("switching between profiles keeps the first head, not the switched profile") {
        val inKid = ProfileSwitchLogic.afterSwitch(null, head, kid, "Léa")
        val kidAccount = SessionAccount(kid, "lea", "kid-token")
        ProfileSwitchLogic.afterSwitch(inKid, kidAccount, teen, "Tom")?.head shouldBe head
    }
    test("switching to the head clears the state, dashes and case ignored") {
        val inKid = ProfileSwitchLogic.afterSwitch(null, head, kid, "Léa")
        ProfileSwitchLogic.afterSwitch(inKid, SessionAccount(kid, "lea", "kid-token"), "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", "papa") shouldBe null
    }
    test("switch route is always authorized with the head token") {
        ProfileSwitchLogic.authToken(null, "head-token") shouldBe "head-token"
        ProfileSwitchLogic.authToken(SwitchState(head, kid, "Léa"), "kid-token") shouldBe "head-token"
    }
    test("members alone get no switcher; switched profiles can always go back") {
        ProfileSwitchLogic.canSwitch(isHead = false, state = null) shouldBe false
        ProfileSwitchLogic.canSwitch(isHead = true, state = null) shouldBe true
        ProfileSwitchLogic.canSwitch(isHead = false, state = SwitchState(head, kid, "Léa")) shouldBe true
    }
    test("picker shows once, only to a head on their own profile, unless disabled or skipped") {
        ProfileSwitchLogic.shouldShowPicker(true, null, enabled = true, alreadyShown = false, skipOnce = false) shouldBe true
        ProfileSwitchLogic.shouldShowPicker(false, null, enabled = true, alreadyShown = false, skipOnce = false) shouldBe false
        ProfileSwitchLogic.shouldShowPicker(true, SwitchState(head, kid, "Léa"), enabled = true, alreadyShown = false, skipOnce = false) shouldBe false
        ProfileSwitchLogic.shouldShowPicker(true, null, enabled = false, alreadyShown = false, skipOnce = false) shouldBe false
        ProfileSwitchLogic.shouldShowPicker(true, null, enabled = true, alreadyShown = true, skipOnce = false) shouldBe false
        ProfileSwitchLogic.shouldShowPicker(true, null, enabled = true, alreadyShown = false, skipOnce = true) shouldBe false
    }
    test("profile device id replaces the current user suffix") {
        ProfileSwitchLogic.profileDeviceId("devABC" + "11111111-1111-1111-1111-111111111111", "11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222") shouldBe
            "devABC22222222-2222-2222-2222-222222222222"
    }

    test("request titles and actions depend on the side") {
        val incomingRequest = JoinRequestInfo(kind = "request", direction = "incoming", userName = "lea", familyName = "Maison")
        FamilyFormat.requestTitle(incomingRequest) shouldBe "lea demande à rejoindre la famille"
        FamilyFormat.requestActions(incomingRequest) shouldBe listOf("accept", "decline")
        val outgoingRequest = incomingRequest.copy(direction = "outgoing")
        FamilyFormat.requestTitle(outgoingRequest) shouldBe "Demande envoyée à « Maison »"
        FamilyFormat.requestActions(outgoingRequest) shouldBe listOf("cancel")
        val invite = JoinRequestInfo(kind = "invite", direction = "incoming", createdByName = "papa", familyName = "Maison")
        FamilyFormat.requestTitle(invite) shouldBe "papa vous invite dans « Maison »"
        FamilyFormat.requestActions(invite.copy(state = "declined")) shouldBe emptyList()
    }
    test("state labels in French") {
        listOf("pending", "accepted", "declined", "cancelled", "expired").map(FamilyFormat::stateLabel) shouldBe
            listOf("En attente", "Acceptée", "Refusée", "Annulée", "Expirée")
    }
    test("badges") {
        FamilyFormat.badgeCount(FamilyMe(unreadMessageCount = 2, pendingRequestCount = 1, pendingFriendCount = 3)) shouldBe 6
        FamilyFormat.badgeText(0) shouldBe null
        FamilyFormat.badgeText(150) shouldBe "99+"
    }
    test("server error message is shown as is, otherwise a generic text") {
        FamilyFormat.errorMessage("""{"Message":"Code PIN incorrect."}""", 403) shouldBe "Code PIN incorrect."
        FamilyFormat.errorMessage("<html>", 429) shouldBe "Trop d’essais. Réessayez plus tard."
    }
    test("message time in local zone, .NET dates accepted") {
        val paris = TimeZone.getTimeZone("Europe/Paris")
        val now = 1_763_294_400_000L // 2025-11-16T12:00:00Z
        FamilyFormat.messageTime("2025-11-16T10:05:00.1234567Z", now, paris) shouldBe "11:05"
        FamilyFormat.messageTime("2025-11-14T10:05:00Z", now, paris) shouldBe "14/11 11:05"
        FamilyFormat.messageTime(null, now, paris) shouldBe ""
    }
})
