package org.jellyfin.mobile.nflix

import org.jellyfin.mobile.nflix.ui.screens.WrappedStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WrappedStatsTest {
    @Test
    fun computesHoursStreakAndTops() {
        val e = listOf(
            WrappedStats.Entry("Ep1", "Dark", listOf("Drame"), 60, LocalDate.of(2026, 3, 1)),
            WrappedStats.Entry("Ep2", "Dark", listOf("Drame"), 60, LocalDate.of(2026, 3, 2)),
            WrappedStats.Entry("Film", null, listOf("Comédie"), 120, LocalDate.of(2026, 3, 3)),
            WrappedStats.Entry("Vieux", null, listOf("Western"), 600, LocalDate.of(2025, 1, 1)),
        )
        val r = WrappedStats.compute(e, 2026)
        assertEquals(4, r.hours)
        assertEquals(3, r.longestStreak)
        assertEquals("Dark", r.topSeries.first())
        assertEquals("Drame", r.topGenres.first())
        assertEquals(1, r.movies)
    }
}
