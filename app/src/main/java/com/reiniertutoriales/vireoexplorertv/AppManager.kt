package com.reiniertutoriales.vireoexplorertv

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale

private data class AppSize(val app: Long, val data: Long, val cache: Long) {
    val total get() = app + data
}

private data class InstalledApp(val name: String, val pkg: String, val system: Boolean)

private data class QuickTempScan(
    val tempFiles: List<File>,
    val emptyDirs: List<File>,
    val tempBytes: Long
) {
    val totalCount: Int get() = tempFiles.size + emptyDirs.size
}

private fun isQuickTempFileName(name: String): Boolean {
    val lower = name.lowercase(Locale.ROOT)
    if (lower.startsWith(".trashed-") || lower.startsWith("~$") || lower == "thumbs.db" || lower == ".ds_store") {
        return true
    }
    return when (lower.substringAfterLast('.', "")) {
        "tmp", "temp", "cache", "bak", "old", "orig", "log", "dmp", "crash", "part", "partial", "crdownload", "download" -> true
        else -> false
    }
}

private fun scanQuickTemporaryFiles(rootDir: File?): QuickTempScan {
    if (rootDir == null || !rootDir.exists() || !rootDir.canRead()) {
        return QuickTempScan(emptyList(), emptyList(), 0L)
    }
    val tempFiles = ArrayList<File>()
    val emptyDirs = ArrayList<File>()
    var bytes = 0L
    val queue = ArrayDeque<Pair<File, Int>>()
    queue.add(rootDir to 0)
    var visitedDirs = 0
    while (queue.isNotEmpty() && visitedDirs < 1000) {
        visitedDirs++
        val (dir, depth) = queue.removeFirst()
        val children = dir.listFiles() ?: continue
        if (children.isEmpty() && depth > 0 && dir.canWrite()) {
            emptyDirs.add(dir)
            continue
        }
        for (child in children) {
            if (child.isDirectory) {
                if (depth == 0 && child.name.equals("Android", ignoreCase = true)) continue
                if (depth < 10 && queue.size < 1000) queue.add(child to depth + 1)
            } else if (isQuickTempFileName(child.name)) {
                tempFiles.add(child)
                bytes += child.length().coerceAtLeast(0L)
            }
        }
    }
    return QuickTempScan(tempFiles, emptyDirs, bytes)
}

private fun computeDirectoryBytes(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var sum = 0L
    val stack = ArrayDeque<File>()
    stack.add(dir)
    var visited = 0
    while (stack.isNotEmpty() && visited < 2000) {
        visited++
        val current = stack.removeFirst()
        val children = current.listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory) stack.add(child)
            else sum += child.length().coerceAtLeast(0L)
        }
    }
    return sum
}

private fun clearDirectoryContents(dir: File?): Pair<Int, Long> {
    if (dir == null || !dir.exists()) return 0 to 0L
    var count = 0
    var freed = 0L
    val children = dir.listFiles() ?: return 0 to 0L
    for (child in children) {
        if (child.name.startsWith("datastore") || child.name.endsWith(".preferences_pb")) continue
        val size = if (child.isDirectory) computeDirectoryBytes(child) else child.length().coerceAtLeast(0L)
        val removed = if (child.isDirectory) child.deleteRecursively() else child.delete()
        if (removed) {
            count++
            freed += size
        }
    }
    return count to freed
}

@Composable
internal fun AppManager(
    onClose: () -> Unit,
    onAnalyze: (Uri) -> Unit,
    onRequestStorageAccess: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var capacity by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var ownCacheBytes by remember { mutableLongStateOf(0L) }
    var quickTempScan by remember { mutableStateOf<QuickTempScan?>(null) }
    var applicationsPage by rememberSaveable { mutableStateOf(true) }
    var chosenApp by remember { mutableStateOf<InstalledApp?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sizes by remember { mutableStateOf<Map<String, AppSize>>(emptyMap()) }
    var lastUsed by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var measuring by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var statsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val directGranted = remember(revision) { hasDirectStorageAccess(context) }
    val directInternalDir = remember(revision, directGranted) {
        if (directGranted) primaryDirectStorageDir() else null
    }
    val directInternalUri = remember(directInternalDir) {
        directInternalDir?.let { Uri.fromFile(it) }
    }
    val safRoots = remember(revision) {
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
    }

    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: Exception) { error = "System action unavailable on this TV" }
    }

    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(applicationsPage, loading, showSystem) {
        if (!applicationsPage || !loading) primaryFocus.requestFocus()
    }
    BackHandler(onBack = onClose)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        statsJob?.cancel()
        measuring = false
        revision++
    }

    LaunchedEffect(revision, directGranted) {
        loading = true
        error = null
        try {
            apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0).map {
                    InstalledApp(
                        pm.getApplicationLabel(it).toString(),
                        it.packageName,
                        it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    )
                }.sortedBy { it.name.lowercase(Locale.ROOT) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Unable to list visible applications" }
        finally { loading = false }
    }

    LaunchedEffect(revision, directGranted, applicationsPage) {
        if (applicationsPage) return@LaunchedEffect
        quickTempScan = null
        try {
            val (cap, cacheBytes, tempReport) = withContext(Dispatchers.IO) {
                val stat = try {
                    val targetPath = primaryDirectStorageDir()?.absolutePath ?: context.filesDir.absolutePath
                    val fs = StatFs(targetPath)
                    fs.totalBytes to fs.availableBytes
                } catch (_: IllegalArgumentException) { null }
                val ownCache = computeDirectoryBytes(context.cacheDir) + computeDirectoryBytes(context.externalCacheDir)
                val quickScan = if (directGranted) scanQuickTemporaryFiles(primaryDirectStorageDir()) else null
                Triple(stat, ownCache, quickScan)
            }
            capacity = cap
            ownCacheBytes = cacheBytes
            quickTempScan = tempReport
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Storage analysis unavailable"
        }
    }

    val sortLabels = listOf(
        stringResource(R.string.cleaner_sort_name),
        stringResource(R.string.cleaner_sort_size),
        stringResource(R.string.cleaner_sort_cache),
        stringResource(R.string.cleaner_sort_recent)
    )
    val visibleApps = remember(apps, showSystem, sort, sizes, lastUsed) {
        apps.filter { showSystem || !it.system }.let { list ->
            when (sort) {
                1 -> list.sortedByDescending { sizes[it.pkg]?.total ?: -1L }
                2 -> list.sortedByDescending { sizes[it.pkg]?.cache ?: -1L }
                3 -> list.sortedByDescending { lastUsed[it.pkg] ?: -1L }
                else -> list
            }
        }
    }
    val restingBorder = Border(BorderStroke(1.dp, colors.borderVariant), shape = VireoCardShape)
    val focusBorder = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExplorerOption(
                selected = false,
                onClick = onClose
            ) {
                Text(stringResource(R.string.explorer_back))
            }
            Text(
                stringResource(R.string.cleaner_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ExplorerOption(
                selected = false,
                onClick = { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
                leadingIcon = { ExplorerIcon(R.drawable.symbol_hard_drive) }
            ) {
                Text(stringResource(R.string.cleaner_system_storage))
            }
            ExplorerOption(
                selected = false,
                onClick = { open(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) },
                leadingIcon = { ExplorerIcon(R.drawable.symbol_android) }
            ) {
                Text(stringResource(R.string.cleaner_manage_apps))
            }
        }

        error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
        statusNotice?.let { Text(it, color = colors.primary, style = MaterialTheme.typography.bodyMedium) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExplorerOption(selected = applicationsPage, onClick = { applicationsPage = true },
                leadingIcon = { ExplorerIcon(R.drawable.symbol_android) }) {
                Text(stringResource(R.string.manager_applications))
            }
            ExplorerOption(selected = !applicationsPage, onClick = { applicationsPage = false },
                leadingIcon = { ExplorerIcon(R.drawable.symbol_cleaning_services) }) {
                Text(stringResource(R.string.manager_cleanup))
            }
        }
        val launchUnavailable = stringResource(R.string.manager_launch_unavailable)
        chosenApp?.let { app ->
            AppActionsDialog(
                name = app.name,
                packageName = app.pkg,
                onOpen = {
                    val pm = context.packageManager
                    val intent = pm.getLeanbackLaunchIntentForPackage(app.pkg)
                        ?: pm.getLaunchIntentForPackage(app.pkg)
                    if (intent == null) error = launchUnavailable
                    else open(intent)
                    chosenApp = null
                },
                onSettings = {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)))
                    chosenApp = null
                },
                onDismiss = { chosenApp = null }
            )
        }

        key(applicationsPage) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (!applicationsPage) {
                    item {
                        capacity?.let { (total, free) ->
                            val used = (total - free).coerceAtLeast(0L)
                            val fraction = if (total > 0L) (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
                            Surface(
                                shape = VireoCardShape,
                                colors = SurfaceDefaults.colors(
                                    containerColor = colors.surface,
                                    contentColor = colors.onSurface
                                ),
                                border = restingBorder,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.cleaner_internal_card_title),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "${(fraction * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.primary
                                        )
                                    }
                                    VireoProgressBar(progress = fraction)
                                    Text(
                                        stringResource(
                                            R.string.cleaner_storage_summary,
                                            Formatter.formatFileSize(context, used),
                                            Formatter.formatFileSize(context, total),
                                            Formatter.formatFileSize(context, free)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val tempSummaryText = when {
                            !directGranted -> stringResource(R.string.explorer_grant_storage_subtitle)
                            quickTempScan == null -> stringResource(R.string.cleaner_quick_temp_scanning)
                            else -> stringResource(
                                R.string.cleaner_quick_temp_ready,
                                quickTempScan!!.totalCount,
                                Formatter.formatFileSize(context, quickTempScan!!.tempBytes),
                                Formatter.formatFileSize(context, ownCacheBytes)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                onClick = {
                                    if (!directGranted) {
                                        onRequestStorageAccess()
                                    } else {
                                        scope.launch {
                                            trimThumbnailCache(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
                                            val (deletedCount, freedBytes) = withContext(Dispatchers.IO) {
                                                var count = 0
                                                var freed = 0L
                                                val (c1, b1) = clearDirectoryContents(context.cacheDir)
                                                val (c2, b2) = clearDirectoryContents(context.externalCacheDir)
                                                count += c1 + c2
                                                freed += b1 + b2
                                                val currentScan = scanQuickTemporaryFiles(primaryDirectStorageDir())
                                                for (file in currentScan.tempFiles) {
                                                    val sz = file.length().coerceAtLeast(0L)
                                                    if (file.delete()) {
                                                        count++
                                                        freed += sz
                                                    }
                                                }
                                                for (dir in currentScan.emptyDirs) {
                                                    if (dir.listFiles()?.isEmpty() == true && dir.delete()) {
                                                        count++
                                                    }
                                                }
                                                count to freed
                                            }
                                            statusNotice = "$deletedCount items cleaned (${Formatter.formatFileSize(context, freedBytes)})"
                                            revision++
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).focusRequester(primaryFocus),
                                shape = CardDefaults.shape(VireoCardShape),
                                colors = CardDefaults.colors(
                                    containerColor = colors.surface,
                                    contentColor = colors.onSurface,
                                    focusedContainerColor = colors.primaryContainer,
                                    focusedContentColor = colors.onPrimaryContainer
                                ),
                                border = CardDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                                scale = CardDefaults.scale(focusedScale = 1.02f)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(48.dp).background(colors.surfaceVariant, VireoPillShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ExplorerIcon(R.drawable.symbol_cleaning_services)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            if (directGranted) stringResource(R.string.cleaner_quick_temp_title)
                                            else stringResource(R.string.explorer_grant_storage_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            tempSummaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Card(
                                onClick = {
                                    if (directInternalUri != null) {
                                        onAnalyze(directInternalUri)
                                    } else {
                                        onRequestStorageAccess()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = CardDefaults.shape(VireoCardShape),
                                colors = CardDefaults.colors(
                                    containerColor = colors.surface,
                                    contentColor = colors.onSurface,
                                    focusedContainerColor = colors.primaryContainer,
                                    focusedContentColor = colors.onPrimaryContainer
                                ),
                                border = CardDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                                scale = CardDefaults.scale(focusedScale = 1.02f)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(48.dp).background(colors.surfaceVariant, VireoPillShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ExplorerIcon(R.drawable.symbol_hard_drive)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            stringResource(R.string.cleaner_scan_internal),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            stringResource(R.string.cleaner_scan_internal_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (safRoots.isNotEmpty()) {
                        items(safRoots, key = { "cleanup:$it" }) { root ->
                            ListItem(
                                selected = false,
                                onClick = { onAnalyze(root) },
                                leadingContent = {
                                    Box(
                                        Modifier.size(40.dp).background(colors.surfaceVariant, VireoPillShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ExplorerIcon(R.drawable.symbol_folder)
                                    }
                                },
                                headlineContent = {
                                    Text(DocumentsContract.getTreeDocumentId(root), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.explorer_analyze_folder), color = colors.onSurfaceVariant)
                                },
                                shape = ListItemDefaults.shape(VireoCardShape),
                                border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                            )
                        }
                    }

                }
                if (applicationsPage) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                stringResource(R.string.cleaner_apps_section, visibleApps.size),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge
                            )
                            ExplorerOption(
                                selected = showSystem,
                                onClick = { showSystem = !showSystem }
                            ) {
                                Text(stringResource(R.string.cleaner_show_system))
                            }
                            ExplorerOption(
                                selected = false,
                                onClick = { sort = (sort + 1) % 4 }
                            ) {
                                Text(sortLabels[sort])
                            }
                            if (Build.VERSION.SDK_INT >= 26) {
                                ExplorerOption(
                                    selected = measuring,
                                    enabled = !loading && !measuring,
                                    onClick = {
                                        statsJob?.cancel()
                                        measuring = true
                                        sizes = emptyMap()
                                        lastUsed = emptyMap()
                                        val snapshot = apps
                                        statsJob = scope.launch {
                                            try {
                                                sizes = withContext(Dispatchers.IO) {
                                                    buildMap {
                                                        for (app in snapshot) {
                                                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                                            try {
                                                                put(app.pkg, applicationStorage(context, app.pkg))
                                                            } catch (e: CancellationException) {
                                                                throw e
                                                            } catch (_: Exception) {
                                                            }
                                                        }
                                                    }
                                                }
                                                lastUsed = withContext(Dispatchers.IO) {
                                                    try {
                                                        val manager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
                                                        val now = System.currentTimeMillis()
                                                        manager?.queryAndAggregateUsageStats(now - 30L * 24 * 60 * 60 * 1000, now)
                                                            .orEmpty().mapValues { it.value.lastTimeUsed }.filterValues { it > 0L }
                                                    } catch (_: SecurityException) {
                                                        emptyMap()
                                                    }
                                                }
                                                if (sizes.isEmpty()) {
                                                    error = "Enable usage access to calculate app & cache sizes"
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                error = "Enable usage access to calculate app & cache sizes"
                                            } finally {
                                                measuring = false
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.cleaner_measure_apps))
                                }
                            }
                            ExplorerOption(
                                selected = false,
                                onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                            ) {
                                Text(stringResource(R.string.cleaner_usage_access))
                            }
                        }
                    }

                    if (loading) item { Text(stringResource(R.string.manager_loading)) }
                    if (!loading && visibleApps.isEmpty()) item {
                        OutlinedButton(modifier = Modifier.focusRequester(primaryFocus),
                            onClick = { showSystem = !showSystem }) {
                            Text(stringResource(R.string.cleaner_show_system))
                        }
                    }
                    items(visibleApps, key = { it.pkg }) { app ->
                        val appSize = sizes[app.pkg]
                        val usedDate = lastUsed[app.pkg]?.let {
                            DateFormat.getDateFormat(context).format(Date(it))
                        }
                        ListItem(
                            selected = false,
                            onClick = {
                                chosenApp = app
                            },
                            modifier = Modifier.then(
                                if (app == visibleApps.firstOrNull()) Modifier.focusRequester(primaryFocus) else Modifier
                            ),
                            leadingContent = { InstalledAppIcon(app.pkg, revision) },
                            headlineContent = {
                                Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                val sub = if (usedDate != null) "${app.pkg} · $usedDate" else app.pkg
                                Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                            },
                            trailingContent = if (appSize != null) {
                                {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            Formatter.formatFileSize(context, appSize.total),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.primary
                                        )
                                        if (appSize.cache > 0L) {
                                            Text(
                                                "Cache: " + Formatter.formatFileSize(context, appSize.cache),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else null,
                            shape = ListItemDefaults.shape(VireoCardShape),
                            colors = ListItemDefaults.colors(
                                containerColor = colors.surface,
                                contentColor = colors.onSurface,
                                focusedContainerColor = colors.surfaceVariant,
                                focusedContentColor = colors.onSurface
                            ),
                            border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                            scale = ListItemDefaults.scale(focusedScale = 1.01f)
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(26)
private fun applicationStorage(context: android.content.Context, pkg: String): AppSize {
    val info = context.packageManager.getApplicationInfo(pkg, 0)
    val manager = requireNotNull(context.getSystemService(android.app.usage.StorageStatsManager::class.java))
    val stats = manager.queryStatsForPackage(info.storageUuid, pkg, android.os.Process.myUserHandle())
    return AppSize(stats.appBytes, stats.dataBytes, stats.cacheBytes)
}
