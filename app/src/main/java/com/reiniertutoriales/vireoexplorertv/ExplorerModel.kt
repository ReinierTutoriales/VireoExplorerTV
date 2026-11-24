package com.reiniertutoriales.vireoexplorertv

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ExplorerModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private var currentPath by mutableStateOf(restorePath(application))
    var path: List<Pair<Uri, String>>
        get() = currentPath
        set(value) {
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
    var transferJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
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
    private var loadedParent: Uri? = null
    private var loadedRevision = -1
    private var loadGeneration = 0L
    private var loadJob: kotlinx.coroutines.Job? = null

    fun load(parent: Uri?) {
        if (loadedParent == parent && loadedRevision == revision) return
        val generation = ++loadGeneration
        loadJob?.cancel()
        val changed = loadedParent != parent
        loadedParent = parent
        loadedRevision = revision
        freeBytes = null
        if (changed) search = ""
        multi = false
        marked = emptySet()
        entries = emptyList()
        selected = null
        busy = parent != null
        if (parent == null) return
        loadJob = viewModelScope.launch {
            try {
                val result = storage.children(parent)
                if (generation != loadGeneration) return@launch
                entries = result
                val available = storage.availableBytes(parent)
                if (generation == loadGeneration) freeBytes = available
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == loadGeneration) error = e.localizedMessage ?: "Cannot read folder" }
            finally { if (generation == loadGeneration) busy = false }
        }
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
                                for (entry in pending.first) {
                                    mutableProgress.value = 0
                                    try {
                                        val names = storage.children(destination).map { it.name }
                                        val name = resolveConflict(entry.name, entry.directory, names, policy)
                                        if (name == null) skipped++
                                        else if (entry.directory && pending.second) {
                                            check(name == entry.name) { "Native folder move cannot rename on conflict" }
                                            storage.moveDirectory(entry, checkNotNull(clipboardParent), destination)
                                        } else if (entry.directory) storage.copyDirectory(entry, destination, name, report)
                                        else storage.transfer(entry, destination, pending.second, name, report)
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
