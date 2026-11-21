package org.jellyfin.mobile.nflix

import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.MediaSource
import org.jellyfin.mobile.nflix.data.MediaStream
import org.jellyfin.mobile.nflix.data.QualityBadges
import org.jellyfin.mobile.nflix.playback.PlaybackMemory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackMemoryTest {

    @Test
    fun `continue watching asks after 3 chained episodes and only once`() {
        val cw = PlaybackMemory.ContinueWatching()
        assertFalse(cw.advance())
        assertFalse(cw.advance())
        assertTrue(cw.advance(), "third chained episode must ask")
        assertTrue(cw.pending)
        assertFalse(cw.advance(), "still pending: asked once")
        cw.confirm()
        assertFalse(cw.pending)
        assertEquals(0, cw.count)
    }

    @Test
    fun `continue watching disabled never asks`() {
        val cw = PlaybackMemory.ContinueWatching(enabled = false)
        repeat(10) { assertFalse(cw.advance()) }
    }

    @Test
    fun `track memory round trip per series`() {
        val store = PlaybackMemory.MapTrackStore()
        assertNull(store.get("s1"))
        store.put("s1", PlaybackMemory.TrackChoice("fre", "eng"))
        assertEquals(PlaybackMemory.TrackChoice("fre", "eng"), store.get("s1"))
        assertNull(store.get("s2"))
    }

    @Test
    fun `resume store keeps positive positions and forgets on clear`() {
        val store = PlaybackMemory.MapResumeStore()
        store.put("e1", 42_000)
        assertEquals(42_000L, store.get("e1"))
        store.put("e1", 0)
        assertEquals(42_000L, store.get("e1"), "zero must not erase")
        store.clear("e1")
        assertNull(store.get("e1"))
    }

    @Test
    fun `minutes filter keeps unknown runtime and drops long items`() {
        val short = Item(id = "a", runTimeTicks = 30 * PlaybackMemory.MinutesFilter.TICKS_PER_MINUTE)
        val long = Item(id = "b", runTimeTicks = 120 * PlaybackMemory.MinutesFilter.TICKS_PER_MINUTE)
        val unknown = Item(id = "c")
        val out = PlaybackMemory.MinutesFilter.filter(listOf(short, long, unknown), 60)
        assertEquals(listOf("a", "c"), out.map { it.id })
        assertEquals(3, PlaybackMemory.MinutesFilter.filter(listOf(short, long, unknown), 0).size)
    }

    @Test
    fun `double tap seek parses with default 10s`() {
        assertEquals(10, PlaybackMemory.DoubleTapSeek.fromKey(null).seconds)
        assertEquals(30, PlaybackMemory.DoubleTapSeek.fromKey("S30").seconds)
        assertEquals(10, PlaybackMemory.DoubleTapSeek.fromKey("bogus").seconds)
    }

    @Test
    fun `quality badges detect 4K HDR DV VF VOSTFR`() {
        fun item(streams: List<MediaStream>) = Item(id = "x", mediaSources = listOf(MediaSource(id = "m", streams = streams)))
        val uhdHdr = item(listOf(
            MediaStream(type = "Video", height = 2160, displayTitle = "HEVC HDR 10-bit"),
            MediaStream(type = "Audio", language = "eng"),
            MediaStream(type = "Subtitle", language = "fre"),
        ))
        assertEquals(listOf("4K", "HDR", "VOSTFR"), QualityBadges.of(uhdHdr))
        val dvVf = item(listOf(
            MediaStream(type = "Video", height = 2160, displayTitle = "Dolby Vision"),
            MediaStream(type = "Audio", language = "fra"),
        ))
        assertEquals(listOf("4K", "DV", "VF"), QualityBadges.of(dvVf))
        val hd = item(listOf(MediaStream(type = "Video", height = 1080), MediaStream(type = "Audio", language = "eng")))
        assertEquals(emptyList<String>(), QualityBadges.of(hd))
    }
}
