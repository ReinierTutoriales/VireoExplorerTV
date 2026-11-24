package com.reiniertutoriales.vireoexplorertv

import org.junit.Test
import org.junit.Assert.*

class SidecarTest {
    @Test fun matchingNames() {
        assertTrue(matchesSubtitle("Movie.mkv", "Movie.srt"))
        assertTrue(matchesSubtitle("Movie.mkv", "movie.es.forced.ass"))
        assertFalse(matchesSubtitle("Movie.mkv", "Movie2.srt"))
        assertFalse(matchesSubtitle("Movie.mkv", "Movie.txt"))
    }
}
