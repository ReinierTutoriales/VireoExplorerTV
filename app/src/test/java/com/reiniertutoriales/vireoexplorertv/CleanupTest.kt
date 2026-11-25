package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
class CleanupTest {
    @Test fun candidatesAreExplicit() {
        assertNotNull(cleanupReason("download.CRDOWNLOAD"))
        assertNotNull(cleanupReason("installer.apk"))
        assertNotNull(cleanupReason("cache.tmp"))
        assertNotNull(cleanupReason("backup.bak"))
        assertNotNull(cleanupReason("debug.log"))
        assertNotNull(cleanupReason(".trashed-1234-video.mp4"))
        assertNull(cleanupReason("family.jpg"))
        assertNull(cleanupReason("temporary"))
    }
}
