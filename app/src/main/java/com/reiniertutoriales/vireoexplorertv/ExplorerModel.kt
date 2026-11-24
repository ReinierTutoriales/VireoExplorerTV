package com.reiniertutoriales.vireoexplorertv

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*

internal class ExplorerModel(application: Application) : AndroidViewModel(application) {
    var path by mutableStateOf<List<Pair<Uri,String>>>(emptyList())
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
    var transferred by mutableLongStateOf(0L)
    var search by mutableStateOf("")
    var scanOpen by mutableStateOf(false)
    var networkOpen by mutableStateOf(false)
    var appsOpen by mutableStateOf(false)
    var mutating by mutableStateOf(false)

    val storage = Storage(application.contentResolver)
}
