package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
class StorageTest {
 @Test fun names() { assertTrue(validName("Movies")); listOf("", " ", ".", "..", "a/b", "a\\b", "a\u0000b").forEach { assertFalse(validName(it)) } }
}
