package org.jellyfin.mobile.nflix.family

import org.jellyfin.mobile.nflix.alerts.ChatAlertSource
import org.jellyfin.mobile.nflix.ui.screens.FamilyNightFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilySocial15Test {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun inviteLinkRoundTrip() {
        val link = SocialLinks.inviteLink("https://nflix.example/", token)
        assertEquals(token, SocialLinks.parseInvite(link))
        assertEquals(token, SocialLinks.parseInvite(" $token "))
        assertNull(SocialLinks.parseInvite("https://x/?invite=../../etc"))
    }

    @Test
    fun avatarsAreStableAndTwoColoured() {
        val a = GeneratedAvatar.spec("user-1")
        assertEquals(a, GeneratedAvatar.spec("user-1"))
        assertNotEquals(a.background, a.accent)
    }

    @Test
    fun chatAlertsSkipFirstSightOwnAndRead() {
        val store = HashMap<String, Long>()
        val conv = ConversationInfo(id = "c", title = "Famille", unread = 1, lastMessage = MessageInfo(id = 5, senderName = "Léa", text = "Salut"))
        assertTrue(ChatAlertSource.plan(listOf(conv), { store[it] ?: -1 }, { k, v -> store[k] = v }).isEmpty())
        val next = conv.copy(lastMessage = MessageInfo(id = 6, senderName = "Léa", text = "Ce soir ?"))
        val alerts = ChatAlertSource.plan(listOf(next), { store[it] ?: -1 }, { k, v -> store[k] = v })
        assertEquals("Léa : Ce soir ?", alerts.single().body)
        assertTrue(ChatAlertSource.plan(listOf(next), { store[it] ?: -1 }, { k, v -> store[k] = v }).isEmpty())
        val mine = conv.copy(lastMessage = MessageInfo(id = 7, mine = true))
        assertTrue(ChatAlertSource.plan(listOf(mine), { store[it] ?: -1 }, { k, v -> store[k] = v }).isEmpty())
        assertTrue(ChatAlertSource.isQuietHour(23) && ChatAlertSource.isQuietHour(7) && !ChatAlertSource.isQuietHour(20))
    }

    @Test
    fun movieNightStartIsInTheFuture() {
        val now = java.time.ZonedDateTime.of(2026, 11, 20, 21, 30, 0, 0, java.time.ZoneId.of("Europe/Paris"))
        assertEquals("2026-11-21T19:00Z", FamilyNightFormat.startIso(0, 20, now))
        assertEquals("2026-11-21T19:00Z", FamilyNightFormat.startIso(1, 20, now))
    }
}
