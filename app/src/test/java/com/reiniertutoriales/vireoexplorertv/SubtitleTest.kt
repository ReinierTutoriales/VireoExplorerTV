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
}
