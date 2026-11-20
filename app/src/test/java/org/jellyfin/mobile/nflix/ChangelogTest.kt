package org.jellyfin.mobile.nflix

import org.jellyfin.mobile.nflix.family.MessageInfo
import org.jellyfin.mobile.nflix.family.PollInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

class ChangelogTest {
    @Test
    fun `changelog knows shipped versions`() {
        assertNotNull(Changelog.forVersion("5.8.0"))
        assertNotNull(Changelog.forVersion("5.6.0"))
        assertNull(Changelog.forVersion("9.9.9"))
    }

    @Test
    fun `share link contains id and name`() {
        val link = ShareLink.forItem("abc123", "Dune")
        assertTrue(link.contains("id=abc123"))
        assertTrue(link.contains("Dune sur Nflix"))
        assertTrue(link.startsWith("https://nflix.miracloud.fr/"))
    }

    @Test
    fun `message dto parses reply and reactions`() {
        val json = Json { ignoreUnknownKeys = true }
        val m = json.decodeFromString(MessageInfo.serializer(), """{"Id":7,"Text":"Salut","ReplyToId":3,"Reactions":{"👍":2},"Read":true}""")
        assertEquals(7L, m.id)
        assertEquals(3L, m.replyToId)
        assertEquals(2, m.reactions["👍"])
    }

    @Test
    fun `poll dto parses votes and my votes`() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString(PollInfo.serializer(), """{"Id":1,"Question":"Ce soir ?","Options":["Film","Série"],"Votes":{"0":["a","b"],"1":["c"]},"MyVotes":[0]}""")
        assertEquals(2, p.options.size)
        assertEquals(3, p.totalVotes)
        assertEquals(listOf(0), p.myVotes)
    }
}
