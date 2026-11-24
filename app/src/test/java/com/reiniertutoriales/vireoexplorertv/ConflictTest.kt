package com.reiniertutoriales.vireoexplorertv

import org.junit.Test
import org.junit.Assert.*

class ConflictTest {
    @Test fun keepBothPreservesExtension() {
        assertEquals("movie (2).mkv", resolveConflict("movie.mkv", false, listOf("MOVIE.MKV", "movie (1).mkv"), ConflictPolicy.KEEP_BOTH))
    }
    @Test fun skipConflict() {
        assertNull(resolveConflict("folder", true, listOf("folder"), ConflictPolicy.SKIP))
    }
    @Test fun directoryNameKeepsDots() {
        assertEquals("a.b (1)", resolveConflict("a.b", true, listOf("a.b"), ConflictPolicy.KEEP_BOTH))
    }
}
