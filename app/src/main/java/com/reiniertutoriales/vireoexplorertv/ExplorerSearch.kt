package com.reiniertutoriales.vireoexplorertv

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class ExplorerSearchState(
    val locations: List<String> = emptyList(),
    val page: SearchPage<Entry>? = null,
    val pageNumber: Int = 0,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val nextPage: () -> Unit
)

@Composable
internal fun rememberExplorerSearch(
    storage: Storage,
    category: FileCategory?,
    query: String,
    volumes: List<VolumeInfo>,
    directAccess: Boolean,
    authorizedFolders: List<Uri>,
    revision: Int
): ExplorerSearchState {
    val internalLabel = stringResource(R.string.explorer_internal)
    val requests = remember(storage, category, query, volumes, directAccess, authorizedFolders, revision, internalLabel) {
        Channel<Unit>(Channel.CONFLATED)
    }
    var state by remember(requests) {
        mutableStateOf(ExplorerSearchState(nextPage = { requests.trySend(Unit) }))
    }
    LaunchedEffect(requests) {
        try {
            val roots = withContext(Dispatchers.IO) {
                buildList {
                    if (directAccess) {
                        for (volume in volumes) {
                            val uri = volume.directUri ?: continue
                            add(searchRoot(uri, volume.label))
                        }
                        if (volumes.none { !it.removable && it.directUri != null }) {
                            primaryDirectStorageDir()?.let { add(searchRoot(Uri.fromFile(it), internalLabel)) }
                        }
                    }
                    for (uri in authorizedFolders) {
                        add(searchRoot(storage.root(uri), DocumentsContract.getTreeDocumentId(uri)))
                    }
                }.distinctBy { it.uri }
            }
            state = state.copy(locations = roots.map { it.name }.distinct())
            if (roots.isEmpty()) return@LaunchedEffect
            val search = storage.categorySearch(roots, category, query)
            while (true) {
                state = state.copy(loading = true)
                val result = withContext(Dispatchers.IO) {
                    val page = search.nextPage(checkActive = { ensureActive() })
                    val hits = page.hits.distinctBy { it.item.uri }.sortedWith { left, right ->
                        defaultEntryComparator.compare(left.item, right.item)
                    }
                    page.copy(hits = hits)
                }
                state = state.copy(page = result, pageNumber = state.pageNumber + 1, loading = false)
                if (!result.hasMore) break
                requests.receive()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            state = state.copy(failed = true)
        } finally {
            state = state.copy(loading = false)
        }
    }
    return state
}

private fun searchRoot(uri: Uri, label: String) =
    Entry(uri, label, DocumentsContract.Document.MIME_TYPE_DIR, -1L, 0)

@Composable
internal fun ExplorerSearchHeader(search: ExplorerSearchState, enabled: Boolean) {
    val page = search.page
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (search.locations.isNotEmpty()) {
            Text(
                stringResource(R.string.explorer_search_locations, search.locations.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall
            )
        }
        when {
            search.failed -> Text(stringResource(R.string.explorer_search_unavailable))
            search.loading -> Text(stringResource(R.string.explorer_reading_storage))
            page != null -> Text(stringResource(R.string.explorer_search_page, search.pageNumber, page.hits.size))
            else -> Text(stringResource(R.string.explorer_search_choose_folder))
        }
        if (page?.limited == true) {
            Text(stringResource(R.string.explorer_search_limited), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (page != null && page.unreadableFolders > 0) {
            Text(
                pluralStringResource(R.plurals.explorer_search_unreadable, page.unreadableFolders, page.unreadableFolders),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (page?.hasMore == true && !search.failed) {
            Button(onClick = { if (!search.loading && enabled) search.nextPage() }) {
                Text(stringResource(R.string.explorer_search_next))
            }
        }
    }
}

internal fun LazyListScope.explorerSearchResults(
    search: ExplorerSearchState,
    searchingByName: Boolean,
    revision: Int,
    enabled: Boolean,
    onOpen: (SearchHit<Entry>) -> Unit,
    onShowFolder: (SearchHit<Entry>) -> Unit
) {
    val page = search.page
    if (page != null && !page.hasMore && search.pageNumber == 1 && page.hits.isEmpty() &&
        page.unreadableFolders == 0 && !page.limited
    ) {
        item {
            val emptyLabel = if (searchingByName) R.string.explorer_no_search_results else R.string.explorer_no_category_files
            Text(stringResource(emptyLabel), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(page?.hits.orEmpty(), key = { "cat:" + it.item.uri }) { hit ->
        FolderEntry(
            entry = hit.item,
            revision = revision,
            grid = false,
            selected = false,
            enabled = enabled && !search.loading,
            onClick = { onOpen(hit) },
            onLongClick = { onShowFolder(hit) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
