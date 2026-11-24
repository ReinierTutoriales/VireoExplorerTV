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
    @Test fun reconstructsChronologicalResumeOrderFromUnorderedMap() {
        val unordered = hashMapOf(
            "content://video/c" to 3000L,
            "content://video/a" to 1000L,
            "content://video/b" to 2000L
        )
        val encodedOrder = "content://video/b\ncontent://video/a\ncontent://video/c"
        val ordered = reconstructOrderedResumeMap(unordered, encodedOrder)
        assertEquals(listOf("content://video/b", "content://video/a", "content://video/c"), ordered.keys.toList())
        val trimmed = trimResumePositions(ordered, "content://video/d", 4000L, maxEntries = 3)
        assertEquals(listOf("content://video/a", "content://video/c", "content://video/d"), trimmed.keys.toList())
    }
}
