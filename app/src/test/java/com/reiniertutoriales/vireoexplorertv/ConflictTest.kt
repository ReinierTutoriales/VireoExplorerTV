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
    @Test fun batchResolveConflictInSetTracksAddedNames() {
        val occupied = hashSetOf("photo.jpg")
        val first = resolveConflictInSet("Photo.JPG", false, occupied, ConflictPolicy.KEEP_BOTH)
        assertEquals("Photo (1).JPG", first)
        occupied.add(first!!.lowercase(java.util.Locale.ROOT))
        val second = resolveConflictInSet("photo.jpg", false, occupied, ConflictPolicy.KEEP_BOTH)
        assertEquals("photo (2).jpg", second)
    }
}
