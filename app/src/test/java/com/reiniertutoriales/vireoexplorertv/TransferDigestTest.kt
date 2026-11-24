package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
class TransferDigestTest {
    @Test fun copyAndVerify() = runBlocking {
        val bytes = ByteArray(300000) { it.toByte() }
        val output = ByteArrayOutputStream()
        val digest = transferDigest(bytes.inputStream(), output, bytes.size.toLong())
        assertArrayEquals(bytes, output.toByteArray())
        assertTrue(digest.matches(transferDigest(output.toByteArray().inputStream())))
        assertFalse(digest.matches(transferDigest(ByteArray(bytes.size).inputStream())))
    }
    @Test fun rejectsWrongSize() = runBlocking {
        for (size in listOf(1L, 3L)) {
            assertTrue(runCatching { transferDigest(byteArrayOf(1,2).inputStream(), expected = size) }.isFailure)
        }
    }
}
