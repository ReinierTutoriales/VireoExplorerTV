package com.reiniertutoriales.vireoexplorertv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAccessStateTest {
    @Test
    fun restoresAccessFromCurrentPermissions() {
        val readOnly = StorageAccessState.fromPermissions(read = true, write = false)
        assertEquals(StorageAccessState.READ_ONLY, readOnly)
        assertTrue(readOnly.canRead)
        assertFalse(readOnly.canWrite)
        val restored = StorageAccessState.fromPermissions(true, true, StorageAccessState.UNAVAILABLE)
        assertEquals(StorageAccessState.GRANTED, restored)
    }

    @Test
    fun distinguishesMissingSettingsFromRevokedGrants() {
        assertEquals(StorageAccessState.NEEDS_CONSENT, StorageAccessState.fromPermissions(false, false))
        assertEquals(StorageAccessState.REVOKED, StorageAccessState.fromPermissions(false, false, StorageAccessState.READ_ONLY))
        assertEquals(StorageAccessState.UNAVAILABLE, StorageAccessState.fromPermissions(false, false, StorageAccessState.UNAVAILABLE))
    }
}
