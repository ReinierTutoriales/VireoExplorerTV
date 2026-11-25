package com.reiniertutoriales.vireoexplorertv

import kotlinx.coroutines.CancellationException

internal data class SearchHit<T>(val item: T, val parents: List<T>)

internal data class SearchPage<T>(
    val hits: List<SearchHit<T>>,
    val hasMore: Boolean,
    val unreadableFolders: Int,
    val limited: Boolean
)

/** A bounded traversal that retains its position between pages. */
internal class SearchTraversal<T>(
    roots: List<T>,
    private val identity: (T) -> String,
    private val children: suspend (T) -> List<T>,
    private val matches: (T) -> Boolean,
    private val descend: (T) -> Boolean,
    private val maxDepth: Int = 32,
    private val maxVisited: Int = 100_000
) {
    private class Frame<T>(val items: Iterator<T>, val parents: List<T>)

    private val frames = ArrayDeque<Frame<T>>().apply { add(Frame(roots.iterator(), emptyList())) }
    private val visited = HashSet<String>()
    private var pendingFolder: SearchHit<T>? = null
    private var unreadableFolders = 0
    private var limited = false

    suspend fun nextPage(
        maxResults: Int = 600,
        maxFolders: Int = 1_500,
        checkActive: () -> Unit
    ): SearchPage<T> {
        require(maxResults > 0 && maxFolders > 0)
        val hits = ArrayList<SearchHit<T>>()
        var folders = 0
        while (hits.size < maxResults && folders < maxFolders) {
            checkActive()
            val pending = pendingFolder
            if (pending != null) {
                pendingFolder = null
                val key = identity(pending.item)
                if (key in visited) continue
                if (visited.size == maxVisited) {
                    limited = true
                    continue
                }
                visited.add(key)
                folders++
                val items = try {
                    children(pending.item)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    unreadableFolders++
                    continue
                }
                if (items.isNotEmpty()) frames.add(Frame(items.iterator(), pending.parents + pending.item))
                continue
            }
            val frame = nextFrame() ?: break
            val item = frame.items.next()
            val hit = SearchHit(item, frame.parents)
            if (frame.parents.isEmpty()) {
                pendingFolder = hit
                continue
            }
            if (descend(item)) {
                if (frame.parents.size <= maxDepth) pendingFolder = hit
                else limited = true
            }
            if (matches(item)) hits.add(hit)
        }
        return SearchPage(hits, pendingFolder != null || nextFrame() != null, unreadableFolders, limited)
    }

    private fun nextFrame(): Frame<T>? {
        while (frames.isNotEmpty() && !frames.last().items.hasNext()) frames.removeLast()
        return frames.lastOrNull()
    }
}
