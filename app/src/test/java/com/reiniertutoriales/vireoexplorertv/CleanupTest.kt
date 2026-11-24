package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
class CleanupTest {
    @Test fun candidatesAreExplicit() {
        assertNotNull(cleanupReason("download.CRDOWNLOAD"))
        assertNotNull(cleanupReason("installer.apk"))
        assertNotNull(cleanupReason("cache.tmp"))
        assertNull(cleanupReason("family.jpg"))
        assertNull(cleanupReason("temporary"))
    }
}
