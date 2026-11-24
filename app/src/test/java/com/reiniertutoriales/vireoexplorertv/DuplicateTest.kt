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
}
