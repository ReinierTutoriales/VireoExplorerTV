package com.reiniertutoriales.vireoexplorertv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SmbRetryTest {
    @Test fun mutationNeverReplays() = runBlocking {
        val original = IOException("connection lost")
        var reconnects = 0
        var calls = 0
        try {
            retrySmbReadOnly(false, original, { reconnects++; "share" }) { calls++ }
            fail("Expected original failure")
        } catch (e: IOException) { assertSame(original, e) }
        assertEquals(0, reconnects)
        assertEquals(0, calls)
    }

    @Test fun readOnlyReplaysOnce() = runBlocking {
        var reconnects = 0
        var calls = 0
        val result = retrySmbReadOnly(true, IOException("lost"), { reconnects++; "share" }) {
            calls++
            it
        }
        assertEquals("share", result)
        assertEquals(1, reconnects)
        assertEquals(1, calls)
    }

    @Test fun cancellationNeverReconnects() = runBlocking {
        val original = CancellationException("cancelled")
        var reconnects = 0
        try {
            retrySmbReadOnly(true, original, { reconnects++; "share" }) { it }
            fail("Expected cancellation")
        } catch (e: CancellationException) { assertSame(original, e) }
        assertEquals(0, reconnects)
    }

    @Test fun connectedFailureIsNotReplayed() = runBlocking {
        val original = IOException("access denied")
        try {
            retrySmbReadOnly<String, String>(true, original, { null }) { it }
            fail("Expected original failure")
        } catch (e: IOException) { assertSame(original, e) }
    }
}
