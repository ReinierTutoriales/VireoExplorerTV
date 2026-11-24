package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
class SmbNameTest {
    @Test fun names() {
        assertTrue(validSmbName("Movie 01.mkv"))
        for (name in listOf("", "..", "a/b", "a\\b", "a:b", "a?", "a.", "a ")) assertFalse(name, validSmbName(name))
    }
}
