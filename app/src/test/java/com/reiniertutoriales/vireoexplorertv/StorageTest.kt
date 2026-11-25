package com.reiniertutoriales.vireoexplorertv

import org.junit.Assert.*
import org.junit.Test

class StorageTest {
    private data class Item(val name: String, val dir: Boolean, val mime: String, val size: Long)

    @Test fun names() {
        assertTrue(validName("Movies"))
        listOf("", " ", ".", "..", "a/b", "a\\b", "a\u0000b").forEach { assertFalse(validName(it)) }
    }

    @Test fun filterAndSortKeepsFoldersFirstAndHandlesUnknownSizesAndCancellation() {
        val dirZ = Item("Zeta", true, "vnd.android.document/directory", -1L)
        val dirA = Item("alpha", true, "vnd.android.document/directory", -1L)
        val vidSmall = Item("clip.mkv", false, "video/x-matroska", 100L)
        val vidLarge = Item("Feature.MP4", false, "video/mp4", 9000L)
        val vidUnknown = Item("stream.ts", false, "video/mp2t", -1L)
        val doc = Item("notes.txt", false, "text/plain", 50L)

        val defaultOrder = compareBy<Item> { !it.dir }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.name }
        val preSorted = listOf(dirZ, vidLarge, vidSmall, doc, vidUnknown, dirA).sortedWith(defaultOrder)

        fun runFilter(
            items: List<Item>,
            category: FileCategory?,
            folderSearch: String,
            sortIndex: Int,
            alreadySorted: Boolean = true,
            checkActive: () -> Unit = {}
        ) = filterAndSortItems(
            items = items,
            directoryOf = Item::dir,
            nameOf = Item::name,
            mimeOf = Item::mime,
            sizeOf = Item::size,
            category = category,
            homeSearch = "",
            folderSearch = folderSearch,
            sortIndex = sortIndex,
            alreadyDefaultSorted = alreadySorted,
            checkActive = checkActive
        )

        // Fast-path default sort with no filters returns exact same list instance
        assertSame(preSorted, runFilter(preSorted, null, "", 0))

        // Smallest first: folders A-Z first, then known sizes ascending, unknown (-1) last
        val smallest = runFilter(preSorted, FileCategory.VIDEO, "", 3)
        assertEquals(listOf("alpha", "Zeta", "clip.mkv", "Feature.MP4", "stream.ts"), smallest.map { it.name })

        // Largest first: folders A-Z first, then sizes descending
        val largest = runFilter(preSorted, FileCategory.VIDEO, "", 2)
        assertEquals(listOf("alpha", "Zeta", "Feature.MP4", "clip.mkv", "stream.ts"), largest.map { it.name })

        // Cooperative cancellation check triggers on large batches
        val largeBatch = List(600) { idx -> Item("file_$idx.mkv", false, "video/mp4", idx.toLong()) }
        assertTrue(
            runCatching {
                runFilter(largeBatch, null, "file", 2, alreadySorted = false) {
                    throw java.util.concurrent.CancellationException("cancelled")
                }
            }.isFailure
        )
    }
}
