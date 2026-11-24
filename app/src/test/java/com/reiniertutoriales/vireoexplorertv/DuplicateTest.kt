package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
class DuplicateTest {
    @Test fun exactComparison() {
        fun compare(a: String,b: String,n: Long) = equalStreams(a.byteInputStream(),b.byteInputStream(),n) {}
        assertTrue(compare("abc","abc",3))
        assertFalse(compare("abc","abd",3))
        assertFalse(compare("abc","ab",3))
        assertFalse(compare("abc","abc",2))
    }
    @Test fun progressivePrefixSampleDistinguishesMismatchedPrefixes() {
        val a = ByteArray(128 * 1024) { 1 }
        val b = ByteArray(128 * 1024) { 1 }.also { it[65536] = 9 }
        val c = ByteArray(128 * 1024) { 2 }
        val hashA = sampledPrefixSha256(a.inputStream(), 64 * 1024) {}
        val hashB = sampledPrefixSha256(b.inputStream(), 64 * 1024) {}
        val hashC = sampledPrefixSha256(c.inputStream(), 64 * 1024) {}
        assertEquals(hashA, hashB)
        assertNotEquals(hashA, hashC)
    }
}
