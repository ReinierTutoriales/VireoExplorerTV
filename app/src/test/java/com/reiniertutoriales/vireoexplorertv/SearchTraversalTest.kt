package com.reiniertutoriales.vireoexplorertv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTraversalTest {
    @Test
    fun continuesAcrossPagesAndPreservesTheAuthorizedRoot() = runBlocking {
        val searched = mutableListOf<String>()
        val folders = mapOf(
            "primary" to listOf("subfolder"),
            "subfolder" to listOf("first.txt", "second.txt"),
            "usb" to listOf("third.txt")
        )
        val traversal = SearchTraversal(
            roots = listOf("primary", "usb"),
            identity = { it },
            children = {
                searched.add(it)
                folders.getValue(it)
            },
            matches = { it.endsWith(".txt") },
            descend = { it in folders }
        )

        val first = traversal.nextPage(maxResults = 1, checkActive = {})
        assertEquals(listOf(SearchHit("first.txt", listOf("primary", "subfolder"))), first.hits)
        assertTrue(first.hasMore)
        val second = traversal.nextPage(maxResults = 1, checkActive = {})
        assertEquals(listOf("second.txt"), second.hits.map { it.item })
        assertTrue(second.hasMore)
        val finalPage = traversal.nextPage(maxResults = 1, checkActive = {})
        assertEquals(listOf(SearchHit("third.txt", listOf("usb"))), finalPage.hits)
        assertFalse(finalPage.hasMore)
        assertEquals(listOf("primary", "subfolder", "usb"), searched)
    }

    @Test
    fun folderBudgetCanContinueAndReadFailuresRemainVisible() = runBlocking {
        val traversal = SearchTraversal(
            roots = listOf("root"),
            identity = { it },
            children = {
                when (it) {
                    "root" -> listOf("broken", "working")
                    "working" -> listOf("hit.txt")
                    else -> error("Unreadable")
                }
            },
            matches = { it.endsWith(".txt") },
            descend = { !it.endsWith(".txt") }
        )
        assertTrue(traversal.nextPage(maxFolders = 1, checkActive = {}).hasMore)
        val failed = traversal.nextPage(maxFolders = 1, checkActive = {})
        assertTrue(failed.hasMore)
        assertEquals(1, failed.unreadableFolders)
        val finalPage = traversal.nextPage(checkActive = {})
        assertFalse(finalPage.hasMore)
        assertEquals(1, finalPage.unreadableFolders)
        assertEquals(listOf("hit.txt"), finalPage.hits.map { it.item })
    }

    @Test
    fun reportsDepthLimitAndDoesNotRevisitCycles() = runBlocking {
        val searched = mutableListOf<String>()
        val folders = mapOf("root" to listOf("cycle"), "cycle" to listOf("root", "deep"))
        val traversal = SearchTraversal(
            roots = listOf("root"),
            identity = { it },
            children = {
                searched.add(it)
                folders[it].orEmpty()
            },
            matches = { false },
            descend = { true },
            maxDepth = 1
        )
        val page = traversal.nextPage(checkActive = {})
        assertTrue(page.limited)
        assertFalse(page.hasMore)
        assertEquals(listOf("root", "cycle"), searched)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotReportedAsUnreadable() {
        runBlocking {
            SearchTraversal(
                roots = listOf("root"),
                identity = { it },
                children = { throw CancellationException() },
                matches = { true },
                descend = { true }
            ).nextPage(checkActive = {})
        }
    }
}
