package com.reiniertutoriales.vireoexplorertv

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

private data class AppSize(val app: Long, val data: Long, val cache: Long) {
    val total get() = app + data
}

private data class InstalledApp(val name: String, val pkg: String, val system: Boolean)

@Composable
internal fun AppManager(onClose: () -> Unit, onAnalyze: (Uri) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var capacity by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sizes by remember { mutableStateOf<Map<String, AppSize>>(emptyMap()) }
    var measuring by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var statsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val roots = remember(revision) { context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri } }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: Exception) { error = "System action unavailable on this device" }
    }
    BackHandler(onBack = onClose)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        statsJob?.cancel()
        sizes = emptyMap()
        measuring = false
        revision++
    }
    LaunchedEffect(revision) {
        loading = true
        error = null
        capacity = withContext(Dispatchers.IO) {
            try {
                val fs = android.os.StatFs(context.filesDir.absolutePath)
                fs.totalBytes to fs.availableBytes
            } catch (_: IllegalArgumentException) { null }
        }
        try {
            apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0).map {
                    InstalledApp(pm.getApplicationLabel(it).toString(), it.packageName, it.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Unable to list visible applications" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Applications and system storage")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClose) { Text("Back") }
            Button(onClick = { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }) { Text("System storage") }
            Button(onClick = { open(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }) { Text("Manage apps") }
            Button(onClick = { showSystem = !showSystem }) { Text(if (showSystem) "Hide system" else "Show system") }
            Button(enabled = !measuring, onClick = { sizes = emptyMap(); revision++ }) { Text("Refresh") }
        }
        Button(onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Authorize usage statistics") }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !loading && !measuring && android.os.Build.VERSION.SDK_INT >= 26, onClick = {
                statsJob?.cancel()
                measuring = true
                sizes = emptyMap()
                val snapshot = apps
                statsJob = scope.launch {
                    try {
                        sizes = withContext(Dispatchers.IO) {
                            buildMap {
                                for (app in snapshot) {
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    try { put(app.pkg, applicationStorage(context, app.pkg)) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { /* unavailable for this package */ }
                                }
                            }
                        }
                        if (sizes.size < snapshot.size) error = "Some statistics unavailable; check usage access"
                    } finally { measuring = false }
                }
            }) { Text("Measure applications") }
            Button(onClick = { sort = (sort + 1) % 3 }) { Text(listOf("Name", "Largest", "Cache")[sort]) }
            if (measuring) Button(onClick = { statsJob?.cancel() }) { Text("Cancel") }
        }

        if (measuring) Text("Measuring storage…")
        Text("Visible applications only. Clear cache, clear data and uninstall through Android settings.")
        error?.let { Text(it) }
        if (loading) Text("Loading applications…")
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                fun size(bytes: Long) = android.text.format.Formatter.formatFileSize(context, bytes)
                capacity?.let { (total, free) ->
                    Text("Internal data volume: ${size(total)} total · ${size(free)} available")
                    Text("Used or reserved: ${size((total - free).coerceAtLeast(0L))}")
                }
                if (sizes.isNotEmpty()) {
                    Text("Measured: ${sizes.size} / ${apps.size} visible apps")
                    Text("Reported app sizes: ${size(sizes.values.sumOf { it.total })} · cache: ${size(sizes.values.sumOf { it.cache })}")
                    Text("Package totals may include shared code; cache is not guaranteed recoverable.")
                }
            }
            item { Text("Cleanup — authorized folders") }
            items(roots, key = { "cleanup:$it" }) { root ->
                Button(onClick = { onAnalyze(root) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Analyze: " + android.provider.DocumentsContract.getTreeDocumentId(root))
                }
            }
            item { Text("Applications") }
            items(apps.filter { showSystem || !it.system }.let { visible ->
                when (sort) {
                    1 -> visible.sortedByDescending { sizes[it.pkg]?.total ?: -1L }
                    2 -> visible.sortedByDescending { sizes[it.pkg]?.cache ?: -1L }
                    else -> visible
                }
            }, key = { it.pkg }) { app ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))) }, modifier = Modifier.weight(1f)) {
                    Column { Text(app.name); Text(app.pkg) }
                }
                sizes[app.pkg]?.let { size ->
                    Column {
                        Text("Total: " + android.text.format.Formatter.formatFileSize(context, size.total))
                        Text("Cache: " + android.text.format.Formatter.formatFileSize(context, size.cache))
                    }
                }

                }
            }
        }
    }
}

private fun applicationStorage(context: android.content.Context, pkg: String): AppSize {
    check(android.os.Build.VERSION.SDK_INT >= 26)
    val info = context.packageManager.getApplicationInfo(pkg, 0)
    val manager = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
    val stats = manager.queryStatsForPackage(info.storageUuid, pkg, android.os.Process.myUserHandle())
    return AppSize(stats.appBytes, stats.dataBytes, stats.cacheBytes)
}
