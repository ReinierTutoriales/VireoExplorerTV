package com.reiniertutoriales.vireoexplorertv

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal val entryNameComparator: Comparator<Entry> =
    compareBy<Entry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name }

internal val defaultEntryComparator: Comparator<Entry> =
    compareBy<Entry> { !it.directory }.then(entryNameComparator)

internal fun <T> filterAndSortItems(
    items: List<T>,
    directoryOf: (T) -> Boolean,
    nameOf: (T) -> String,
    mimeOf: (T) -> String,
    sizeOf: (T) -> Long,
    category: FileCategory?,
    homeSearch: String,
    folderSearch: String,
    sortIndex: Int,
    alreadyDefaultSorted: Boolean = true,
    checkActive: () -> Unit = {}
): List<T> {
    if (items.isEmpty()) return emptyList()
    val hasCategory = category != null
    val hasHomeSearch = homeSearch.isNotEmpty()
    val hasFolderSearch = folderSearch.isNotEmpty()
    var steps = 0
    val filtered = if (!hasCategory && !hasHomeSearch && !hasFolderSearch) {
        if (sortIndex == 0 && alreadyDefaultSorted) return items
        ArrayList(items)
    } else {
        val out = ArrayList<T>(items.size)
        for (i in items.indices) {
            if ((++steps and 0xFF) == 0) checkActive()
            val item = items[i]
            val dir = directoryOf(item)
            val name = nameOf(item)
            if ((!hasCategory || category.matches(dir, name, mimeOf(item))) &&
                (!hasHomeSearch || dir || name.contains(homeSearch, ignoreCase = true)) &&
                (!hasFolderSearch || name.contains(folderSearch, ignoreCase = true))
            ) {
                out.add(item)
            }
        }
        out
    }
    if (filtered.size <= 1 || (sortIndex == 0 && alreadyDefaultSorted)) return filtered
    val names = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER, nameOf).thenBy(nameOf)
    val order = when (sortIndex) {
        1 -> names.reversed()
        2 -> compareByDescending(sizeOf).then(names)
        3 -> compareBy<T> { val sz = sizeOf(it); if (sz < 0) Long.MAX_VALUE else sz }.then(names)
        else -> names
    }
    val comparator = compareBy<T> { !directoryOf(it) }.then(order)
    filtered.sortWith { left, right ->
        if ((++steps and 0xFF) == 0) checkActive()
        comparator.compare(left, right)
    }
    return filtered
}

internal fun filterAndSortEntries(
    entries: List<Entry>,
    category: FileCategory?,
    homeSearch: String,
    folderSearch: String,
    sortIndex: Int,
    alreadyDefaultSorted: Boolean = true,
    checkActive: () -> Unit = {}
): List<Entry> = filterAndSortItems(
    items = entries,
    directoryOf = Entry::directory,
    nameOf = Entry::name,
    mimeOf = Entry::mime,
    sizeOf = Entry::size,
    category = category,
    homeSearch = homeSearch,
    folderSearch = folderSearch,
    sortIndex = sortIndex,
    alreadyDefaultSorted = alreadyDefaultSorted,
    checkActive = checkActive
)

private class ListingSpec(
    val parent: Uri,
    val entries: List<Entry>,
    val category: FileCategory?,
    val homeSearch: String,
    val folderSearch: String,
    val sortIndex: Int
) {
    fun matches(
        otherParent: Uri,
        otherEntries: List<Entry>,
        otherCategory: FileCategory?,
        otherHomeSearch: String,
        otherFolderSearch: String,
        otherSortIndex: Int
    ): Boolean =
        parent == otherParent &&
            entries === otherEntries &&
            category == otherCategory &&
            homeSearch == otherHomeSearch &&
            folderSearch == otherFolderSearch &&
            sortIndex == otherSortIndex
}

internal class ExplorerModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private var currentPath by mutableStateOf(restorePath(application))
    var path: List<Pair<Uri, String>>
        get() = currentPath
        set(value) {
            if (currentPath.lastOrNull()?.first != value.lastOrNull()?.first) error = null
            currentPath = value
            savedState["pathUris"] = ArrayList(value.map { it.first.toString() })
            savedState["pathLabels"] = ArrayList(value.map { it.second })
        }

    private fun restorePath(application: Application): List<Pair<Uri, String>> {
        val uris = savedState.get<ArrayList<String>>("pathUris") ?: return emptyList()
        val labels = savedState.get<ArrayList<String>>("pathLabels") ?: return emptyList()
        if (uris.isEmpty() || uris.size != labels.size || uris.size > 128) return emptyList()
        return try {
            val restored = uris.map(Uri::parse)
            val root = restored.first()
            if (root.scheme == "file") {
                if (!hasDirectStorageAccess(application)) return emptyList()
                val rootFile = java.io.File(root.path ?: return emptyList())
                if (!rootFile.exists() || restored.any { it.scheme != "file" || !(java.io.File(it.path ?: "").exists()) }) {
                    return emptyList()
                }
                return restored.zip(labels)
            }
            val grants = application.contentResolver.persistedUriPermissions
            val storage = Storage(application.contentResolver)
            if (grants.none { it.isReadPermission && storage.root(it.uri) == root }) return emptyList()
            if (restored.any { it.authority != root.authority || android.provider.DocumentsContract.getTreeDocumentId(it) != android.provider.DocumentsContract.getTreeDocumentId(root) }) return emptyList()
            restored.zip(labels)
        } catch (_: IllegalArgumentException) { emptyList() }
        catch (_: SecurityException) { emptyList() }
    }
    var roots by mutableStateOf(application.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri })
    var entries by mutableStateOf<List<Entry>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var selected by mutableStateOf<Entry?>(null)
    var viewing by mutableStateOf<Entry?>(null)
    var clipboard by mutableStateOf<Pair<List<Entry>, Boolean>?>(null)
    var conflictPolicy by mutableStateOf(ConflictPolicy.STOP)
    var clipboardParent by mutableStateOf<Uri?>(null)
    var multi by mutableStateOf(false)
    var marked by mutableStateOf<Set<Uri>>(emptySet())
    var transferJob by mutableStateOf<Job?>(null)
    private val mutableProgress = MutableStateFlow(0L)
    val progress = mutableProgress.asStateFlow()
    var search by mutableStateOf("")
    var scanOpen by mutableStateOf(false)
    var networkOpen by mutableStateOf(false)
    var appsOpen by mutableStateOf(false)
    var mutating by mutableStateOf(false)

    val storage = Storage(application.contentResolver)

    var freeBytes by mutableStateOf<Long?>(null)
        private set
    private var loadedParent by mutableStateOf<Uri?>(null)
    private var loadedRevision = -1
    private var loadGeneration = 0L
    private var loadJob: Job? = null

    private var visibleParent by mutableStateOf<Uri?>(null)
    var visibleEntries by mutableStateOf<List<Entry>>(emptyList())
        private set
    var listingPending by mutableStateOf(false)
        private set
    private var completedListing: ListingSpec? = null
    private var activeListing: ListingSpec? = null
    private var listingGeneration = 0L
    private var listingJob: Job? = null

    fun entriesFor(parent: Uri): List<Entry> = if (loadedParent == parent) entries else emptyList()

    fun visibleFor(parent: Uri): List<Entry> = if (visibleParent == parent) visibleEntries else emptyList()

    fun syncListing(
        parent: Uri?,
        category: FileCategory?,
        homeSearch: String,
        sortIndex: Int
    ) {
        if (parent == null) {
            listingGeneration++
            listingJob?.cancel()
            listingJob = null
            activeListing = null
            completedListing = null
            visibleParent = null
            visibleEntries = emptyList()
            listingPending = false
            return
        }
        val source = entriesFor(parent)
        val folderSearch = search
        if (visibleParent != parent) {
            listingGeneration++
            listingJob?.cancel()
            listingJob = null
            activeListing = null
            completedListing = null
            visibleParent = parent
            visibleEntries = emptyList()
        }
        if (completedListing?.matches(parent, source, category, homeSearch, folderSearch, sortIndex) == true) {
            listingGeneration++
            listingJob?.cancel()
            listingJob = null
            activeListing = null
            listingPending = false
            return
        }
        if (activeListing?.matches(parent, source, category, homeSearch, folderSearch, sortIndex) == true) {
            return
        }
        // Fast synchronous path when source is empty or default A-Z sort with no active filters.
        if (source.isEmpty() || (category == null && homeSearch.isEmpty() && folderSearch.isEmpty() && sortIndex == 0)) {
            listingGeneration++
            listingJob?.cancel()
            listingJob = null
            activeListing = null
            val spec = ListingSpec(parent, source, category, homeSearch, folderSearch, sortIndex)
            completedListing = spec
            visibleEntries = source
            listingPending = false
            return
        }
        val spec = ListingSpec(parent, source, category, homeSearch, folderSearch, sortIndex)
        activeListing = spec
        listingPending = true
        val generation = ++listingGeneration
        listingJob?.cancel()
        listingJob = viewModelScope.launch {
            try {
                val computed = withContext(Dispatchers.Default) {
                    val ctx = coroutineContext
                    filterAndSortEntries(
                        entries = source,
                        category = category,
                        homeSearch = homeSearch,
                        folderSearch = folderSearch,
                        sortIndex = sortIndex,
                        alreadyDefaultSorted = true,
                        checkActive = { ctx.ensureActive() }
                    )
                }
                if (generation == listingGeneration && visibleParent == parent) {
                    visibleEntries = computed
                    completedListing = spec
                    activeListing = null
                    listingPending = false
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun load(parent: Uri?) {
        if (loadedParent == parent && loadedRevision == revision) return
        val generation = ++loadGeneration
        loadJob?.cancel()
        val changed = loadedParent != parent
        loadedParent = parent
        loadedRevision = revision
        if (changed) {
            freeBytes = null
            search = ""
            multi = false
            marked = emptySet()
            entries = emptyList()
            selected = null
            listingJob?.cancel()
            listingJob = null
            activeListing = null
            completedListing = null
            visibleParent = parent
            visibleEntries = emptyList()
            listingPending = false
        }
        busy = parent != null
        if (parent == null) return
        loadJob = viewModelScope.launch {
            try {
                val result = storage.children(parent)
                if (generation != loadGeneration) return@launch
                entries = result
                if (marked.isNotEmpty()) {
                    marked = result.mapNotNullTo(mutableSetOf()) { if (it.uri in marked) it.uri else null }
                }
                selected = selected?.let { saved -> result.firstOrNull { it.uri == saved.uri } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == loadGeneration) {
                    listingGeneration++
                    listingJob?.cancel()
                    listingJob = null
                    entries = emptyList()
                    visibleEntries = emptyList()
                    completedListing = null
                    activeListing = null
                    listingPending = false
                    marked = emptySet()
                    multi = false
                    selected = null
                    freeBytes = null
                    error = e.localizedMessage ?: "Cannot read folder"
                }
            }
            finally { if (generation == loadGeneration) busy = false }
        }
    }

    suspend fun loadAvailableSpace(parent: Uri) {
        freeBytes = null
        val available = try {
            storage.availableBytes(parent)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (path.lastOrNull()?.first == parent) freeBytes = available
    }

    fun mutate(action: suspend () -> Unit) {
        if (mutating) return
        mutating = true
        viewModelScope.launch {
            try { action(); selected = null; revision++ }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "Operation failed" }
            finally { mutating = false }
        }
    }

    fun archive(action: suspend ((Long) -> Unit) -> Unit) {
        if (mutating) return
        mutating = true
        mutableProgress.value = 0
        error = null
        transferJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try { action { mutableProgress.value = it }; selected = null }
            catch (e: CancellationException) { error = "Archive operation cancelled; partial output may remain"; throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "Archive operation failed" }
            finally { mutating = false; transferJob = null; revision++ }
        }
        transferJob?.start()
    }

    fun paste(destination: Uri) {
        if (mutating) return
        val pending = clipboard ?: return
        val policy = conflictPolicy
        mutating = true
        mutableProgress.value = 0
        transferJob = viewModelScope.launch {
            try {
                val report: (Long) -> Unit = { bytes -> mutableProgress.value = bytes }
                var completed = 0
                var skipped = 0
                val occupied = storage.childNames(destination).mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
                for (entry in pending.first) {
                    mutableProgress.value = 0
                    try {
                        val name = resolveConflictInSet(entry.name, entry.directory, occupied, policy)
                        if (name == null) skipped++
                        else {
                            if (entry.directory && pending.second) {
                                check(name == entry.name) { "Native folder move cannot rename on conflict" }
                                storage.moveDirectory(entry, checkNotNull(clipboardParent), destination)
                            } else if (entry.directory) storage.copyDirectory(entry, destination, name, report)
                            else storage.transfer(entry, destination, pending.second, name, report)
                            occupied.add(name.lowercase(Locale.ROOT))
                        }
                        completed++
                        clipboard = pending.first.drop(completed).takeIf { it.isNotEmpty() }?.let { it to pending.second }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { throw IllegalStateException("$completed of ${pending.first.size} items completed: ${e.localizedMessage}", e) }
                }
                clipboard = null
                if (skipped > 0) error = "$skipped conflicting items skipped; originals preserved"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "Transfer failed" }
            finally { mutating = false; transferJob = null; revision++ }
        }
    }
}
