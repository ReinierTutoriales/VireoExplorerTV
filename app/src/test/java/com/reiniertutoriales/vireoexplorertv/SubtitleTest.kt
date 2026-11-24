package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
import androidx.media3.common.MimeTypes
class SubtitleTest {
    @Test fun recognizesExtensions() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeForName("Movie.EN.SRT"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeForName("movie.ass"))
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeForName("movie.vtt"))
        assertEquals(MimeTypes.APPLICATION_TTML, subtitleMimeForName("movie.ttml"))
        assertNull(subtitleMimeForName("movie.exe"))
    }
    @Test fun trimsPlaybackResumeHistoryToCap() {
        val initial = (1..5).associate { "content://video/$it" to (it * 1000L) }
        val updated = trimResumePositions(initial, "content://video/6", 6000L, maxEntries = 4)
        assertEquals(listOf("content://video/3", "content://video/4", "content://video/5", "content://video/6"), updated.keys.toList())
        val cleared = trimResumePositions(updated, "content://video/4", 0L, maxEntries = 4)
        assertFalse("content://video/4" in cleared)
    }
}
