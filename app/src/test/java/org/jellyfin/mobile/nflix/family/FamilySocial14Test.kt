package org.jellyfin.mobile.nflix.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilySocial14Test {
    @Test
    fun followerSeeksOnlyBeyondTwoSeconds() {
        val party = PartyInfo(positionTicks = 60_000L * 10_000)
        assertNull(PartySync.seekTarget(59_000, party))
        assertEquals(60_000L, PartySync.seekTarget(57_000, party))
        assertEquals(60_000L, PartySync.seekTarget(63_000, party))
    }

    @Test
    fun hostMatchesDashedOrPlainIds() {
        val party = PartyInfo(hostId = "0123456789abcdef0123456789abcdef")
        assertTrue(PartySync.isHost(party, "01234567-89ab-cdef-0123-456789abcdef"))
        assertFalse(PartySync.isHost(party, "ffffffffffffffffffffffffffffffff"))
    }

    @Test
    fun usageIsSplitInChunksOfTenMinutes() {
        assertEquals(emptyList<Int>(), UsageMeter.chunks(59_000))
        assertEquals(listOf(1), UsageMeter.chunks(60_000))
        assertEquals(listOf(10, 10, 5), UsageMeter.chunks(25 * 60_000L + 30_000))
    }
}
