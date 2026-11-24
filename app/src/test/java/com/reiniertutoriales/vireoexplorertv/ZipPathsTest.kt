package com.reiniertutoriales.vireoexplorertv

import org.junit.Test
import org.junit.Assert.*

class ZipPathsTest {
    @Test fun nested() { assertEquals(listOf("folder", "file.txt"), zipPath("folder/file.txt")) }
    @Test fun directory() { assertEquals(listOf("folder"), zipPath("folder/")) }
    @Test fun rejectsUnsafePaths() {
        for (name in listOf("../file", "a/../file", "/file", "C:/file", "a\\file", "a//file", "./file", "")) {
            assertTrue(name, runCatching { zipPath(name) }.isFailure)
        }
    }
}
