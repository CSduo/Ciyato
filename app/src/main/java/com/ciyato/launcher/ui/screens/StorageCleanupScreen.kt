package com.ciyato.launcher.ui.screens

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * StorageCleanupScreen — real, on-device storage analysis and deletion.
 *
 * Every number on this screen comes from an actual MediaStore query or a real
 * walk of Ciyato's own cache directories — nothing here is estimated. Five
 * categories are scanned: large files, old screenshots, downloads, app cache,
 * and zero-byte files. Deletion follows the same consent flow BulkDeleteFilesScreen
 * uses (MediaStore.createDeleteRequest on API 30+, RecoverableSecurityException on
 * API 29), and app-cache items are removed directly since Ciyato owns that storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanupScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mediaRepo = remember { MediaLibraryRepository(context) }

    var hasPermission by remember { mutableStateOf(mediaRepo.hasMediaPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermission = mediaRepo.hasMediaPermission() }

    // Granting from system Settings is the only path left after a permanent
    // denial; re-check on resume so the screen updates without a restart.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = mediaRepo.hasMediaPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isScanning by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<CategoryResult>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<CleanupCategory?>(null) }
    var deviceStorage by remember { mutableStateOf<DeviceStorageOverview?>(null) }

    LaunchedEffect(hasPermission) {
        isScanning = true
        val (overview, scanned) = withContext(Dispatchers.IO) {
            val overview = readDeviceStorageOverview(context, hasPermission)
            val scanned = buildList {
                add(scanCache(context))
                // Categories backed by MediaStore can't be measured without the
                // media permission, so they simply don't appear rather than
                // showing a fake zero.
                if (hasPermission) {
                    add(scanLargeFiles(context))
                    add(scanOldScreenshots(context))
                    add(scanDownloads(context))
                    add(scanEmptyFiles(context))
                }
            }
            overview to scanned
        }
        deviceStorage = overview
        results = scanned
        isScanning = false
    }

    val openCategory = results.firstOrNull { it.category == selectedCategory }
    if (openCategory != null) {
        CleanupCategoryDetail(
            result = openCategory,
            onBack = { selectedCategory = null },
            onItemsRemoved = { removedIds ->
                results = results.map { r ->
                    if (r.category == openCategory.category) {
                        val kept = r.items.filterNot { it.id in removedIds }
                        r.copy(items = kept, totalCount = r.totalCount - removedIds.size,
                            totalBytes = r.totalBytes - (r.items.filter { it.id in removedIds }.sumOf { it.sizeBytes }))
                    } else r
                }
            },
        )
        return
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = { CiyatoTopBar(title = "Storage Cleanup", subtitle = "Real scan of this device", onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!hasPermission) {
                CleanupPermissionCard(
                    onGrant = {
                        val perms = if (Build.VERSION.SDK_INT >= 33) {
                            arrayOf(
                                android.Manifest.permission.READ_MEDIA_IMAGES,
                                android.Manifest.permission.READ_MEDIA_VIDEO,
                                android.Manifest.permission.READ_MEDIA_AUDIO,
                            )
                        } else {
                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(perms)
                    },
                )
            }

            if (isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = CiyatoGold)
                        Text("Scanning device storage…", color = CiyatoMuted, style = bodyM)
                    }
                }
            } else {
                val uniqueItems = remember(results) { results.flatMap { it.items }.distinctBy { it.id } }
                val measuredBytes = results.sumOf { it.totalBytes }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    deviceStorage?.let { overview ->
                        item { StorageBreakdownCard(overview = overview, hasPermission = hasPermission) }
                    }
                    item {
                        CleanupSummaryCard(
                            totalBytes = measuredBytes,
                            totalCount = results.sumOf { it.totalCount },
                            overlapNote = uniqueItems.size < results.sumOf { it.totalCount },
                        )
                    }
                    items(results, key = { it.category }) { result ->
                        CleanupCategoryCard(result = result, onClick = { selectedCategory = result.category })
                    }
                }
            }
        }
    }
}

// ── Category detail: browse + multi-select + real delete ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupCategoryDetail(
    result: CategoryResult,
    onBack: () -> Unit,
    onItemsRemoved: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember(result.category) { mutableStateOf(result.items) }
    val bulkState = remember(items) { BulkDeleteState(items.map { it.id }) }
    val selectedBytes = items.filter { bulkState.isSelected(it.id) }.sumOf { it.sizeBytes }
    val snackbarHost = remember { SnackbarHostState() }

    var pendingConsentResume by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        pendingConsentResume?.invoke(activityResult.resultCode == Activity.RESULT_OK)
        pendingConsentResume = null
    }

    fun doDelete() {
        val batch = items.filter { bulkState.isSelected(it.id) }
        if (batch.isEmpty()) return
        val batchIds = batch.map { it.id }.toSet()
        items = items.filterNot { it.id in batchIds }
        bulkState.clearAll()
        scope.launch {
            val undone = snackbarHost.showSnackbar(
                message = "Deleting ${batch.size} · ${MediaLibraryRepository.formatBytes(batch.sumOf { it.sizeBytes })}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            ) == SnackbarResult.ActionPerformed
            if (undone) {
                items = (batch + items).sortedByDescending { it.sizeBytes }
                return@launch
            }
            val (mediaBatch, cacheBatch) = batch.partition { it.uri != null }
            val failedMedia = deleteMediaCleanupItems(context, mediaBatch) { intentSender ->
                suspendCancellableCoroutine { cont ->
                    pendingConsentResume = { granted -> cont.resume(granted) }
                    deleteConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            }
            val failedCache = withContext(Dispatchers.IO) { deleteCacheItems(cacheBatch) }
            val failed = failedMedia + failedCache
            if (failed.isNotEmpty()) {
                // Consent was denied or the delete otherwise failed: these are still
                // on the device, so put them back instead of a false "deleted" state.
                items = (failed + items).sortedByDescending { it.sizeBytes }
                snackbarHost.showSnackbar("${failed.size} of ${batch.size} could not be deleted")
            }
            onItemsRemoved(batchIds - failed.map { it.id }.toSet())
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = result.category.label,
                subtitle = if (result.items.size < result.totalCount)
                    "Showing largest ${result.items.size} of ${result.totalCount}"
                else "${result.totalCount} item${if (result.totalCount == 1) "" else "s"}",
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                Text(
                    "Nothing left to clean up here.",
                    color = CiyatoMuted,
                    style = bodyM,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(bottom = if (bulkState.selectedCount > 0) 80.dp else 0.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        CleanupItemRow(item = item, selected = bulkState.isSelected(item.id), onToggle = { bulkState.toggle(item.id) })
                    }
                }
                BulkDeleteBar(
                    selectedCount = bulkState.selectedCount,
                    totalCount = items.size,
                    selectedBytes = selectedBytes,
                    onSelectAll = { bulkState.selectAll() },
                    onClearSelection = { bulkState.clearAll() },
                    onDelete = { doDelete() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// ── UI pieces ────────────────────────────────────────────────────────────────

@Composable
private fun CleanupSummaryCard(totalBytes: Long, totalCount: Int, overlapNote: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(CiyatoShapes.large).background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("You could free up", color = CiyatoMuted, style = labelL)
        Text(MediaLibraryRepository.formatBytes(totalBytes), color = CiyatoGold, style = displaySection)
        Text(
            "$totalCount item${if (totalCount == 1) "" else "s"} across ${if (overlapNote) "overlapping " else ""}categories below",
            color = CiyatoSec,
            style = bodyM,
        )
    }
}

@Composable
private fun StorageBreakdownCard(overview: DeviceStorageOverview, hasPermission: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(CiyatoShapes.large).background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Device storage", color = CiyatoMuted, style = labelL)
            Text(
                "${MediaLibraryRepository.formatBytes(overview.usedBytes)} used of ${MediaLibraryRepository.formatBytes(overview.totalBytes)}",
                color = CiyatoGold,
                style = headingM,
            )
            Text(
                "${MediaLibraryRepository.formatBytes(overview.freeBytes)} free",
                color = CiyatoSec,
                style = bodyM,
            )
        }

        if (overview.slices.isNotEmpty()) {
            StorageBreakdownBar(slices = overview.slices)
            StorageBreakdownLegend(slices = overview.slices)
        } else if (!hasPermission) {
            Text(
                "Grant media access below to see the breakdown by category.",
                color = CiyatoMuted,
                style = bodyS,
            )
        }
    }
}

@Composable
private fun StorageBreakdownBar(slices: List<StorageBreakdownSlice>) {
    val visible = slices.filter { it.bytes > 0L }
    if (visible.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().height(10.dp).clip(CiyatoShapes.small).background(CiyatoBgEl3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visible.forEach { slice ->
            Box(
                Modifier
                    .weight(slice.bytes.toFloat())
                    .fillMaxHeight()
                    .background(slice.color),
            )
        }
    }
}

@Composable
private fun StorageBreakdownLegend(slices: List<StorageBreakdownSlice>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { slice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                Text(slice.label, color = CiyatoSec, style = bodyM, modifier = Modifier.weight(1f))
                Text(MediaLibraryRepository.formatBytes(slice.bytes), color = CiyatoWhite, style = labelL)
            }
        }
    }
}

@Composable
private fun CleanupCategoryCard(result: CategoryResult, onClick: () -> Unit) {
    CiyatoListCard(
        title = result.category.label,
        subtitle = if (result.totalCount == 0) "${result.category.description} — none found"
            else "${result.totalCount} item${if (result.totalCount == 1) "" else "s"} · ${result.category.description}",
        icon = result.category.icon,
        iconColor = result.category.accent,
        trailing = { Text(MediaLibraryRepository.formatBytes(result.totalBytes), color = CiyatoWhite, style = headingS) },
        onClick = if (result.totalCount > 0) onClick else null,
    )
}

@Composable
private fun CleanupPermissionCard(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(CiyatoShapes.large).background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.PhotoLibrary, null, tint = CiyatoGold, modifier = Modifier.size(26.dp))
        Text("Media access needed", color = CiyatoWhite, style = headingM)
        Text(
            "Ciyato needs photo, video, and audio access to scan large files, old screenshots, downloads, and empty files. App cache can already be cleared without it.",
            color = CiyatoMuted,
            style = bodyM,
        )
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
            Text("Grant Access", color = CiyatoBg, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CleanupItemRow(item: CleanupItem, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, if (selected) CiyatoGold else CiyatoSubtleBorder, CiyatoShapes.medium)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) CiyatoGold else Color.Transparent)
                .border(1.dp, if (selected) CiyatoGold else CiyatoMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Default.Check, null, tint = CiyatoBg, modifier = Modifier.size(14.dp))
        }
        Text(item.name, color = CiyatoWhite, style = bodyM, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(MediaLibraryRepository.formatBytes(item.sizeBytes), color = CiyatoMuted, style = labelL)
    }
}

// ── Data model ───────────────────────────────────────────────────────────────

private enum class CleanupCategory(val label: String, val description: String, val icon: ImageVector, val accent: Color) {
    LARGE_FILES("Large Files", "Over 50 MB each", Icons.Default.Storage, CiyatoBlue),
    OLD_SCREENSHOTS("Old Screenshots", "Older than 30 days", Icons.Default.Screenshot, CiyatoPurple),
    DOWNLOADS("Downloads", "Everything in Downloads", Icons.Default.Download, CiyatoGreen),
    CACHE("App Cache", "Ciyato's own temporary data", Icons.Default.Memory, CiyatoAmber),
    EMPTY_FILES("Empty Files", "Zero-byte entries", Icons.Default.DeleteSweep, CiyatoRed),
}

private data class CleanupItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val uri: Uri? = null,
    val file: java.io.File? = null,
)

private data class CategoryResult(
    val category: CleanupCategory,
    val totalBytes: Long,
    val totalCount: Int,
    val items: List<CleanupItem>,
)

/** One real, measured slice of used storage for the breakdown bar/legend. */
private data class StorageBreakdownSlice(
    val label: String,
    val bytes: Long,
    val color: Color,
)

/**
 * Whole-device storage snapshot. [totalBytes]/[usedBytes]/[freeBytes] come from
 * StatFs and need no permission; [slices] is only populated when media
 * permission is granted, since it's built from MediaStore aggregate queries.
 */
private data class DeviceStorageOverview(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val slices: List<StorageBreakdownSlice>,
)

// ── Real scanning (MediaStore + app cache) ──────────────────────────────────

private const val LARGE_FILE_THRESHOLD = 50L * 1024 * 1024 // 50 MB
private const val OLD_SCREENSHOT_DAYS = 30L
private const val DISPLAY_LIMIT = 250

/** MIME types counted as "Documents" in the storage breakdown below. */
private val DOCUMENT_MIMES = listOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "text/plain",
)

private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

/**
 * Column used for folder/path matching. RELATIVE_PATH only exists in the
 * MediaStore schema from API 29; on Android 8–9 (minSdk 26) referencing it in a
 * selection throws, so the legacy DATA (absolute path) column is used instead.
 */
@Suppress("DEPRECATION")
private val pathColumn: String =
    if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.FileColumns.RELATIVE_PATH
    else MediaStore.Files.FileColumns.DATA

private fun scanLargeFiles(context: Context): CategoryResult {
    val selection = "${MediaStore.Files.FileColumns.SIZE} >= ?"
    val args = arrayOf(LARGE_FILE_THRESHOLD.toString())
    val (count, bytes) = summarize(context, selection, args)
    val items = queryCleanupItems(context, selection, args, "${MediaStore.Files.FileColumns.SIZE} DESC")
    return CategoryResult(CleanupCategory.LARGE_FILES, bytes, count, items)
}

private fun scanOldScreenshots(context: Context): CategoryResult {
    val cutoffSeconds = (System.currentTimeMillis() - OLD_SCREENSHOT_DAYS * 24 * 60 * 60 * 1000L) / 1000L
    val media = MediaStore.Files.FileColumns.MEDIA_TYPE
    val selection = "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND $pathColumn LIKE ? AND " +
        "${MediaStore.Files.FileColumns.DATE_MODIFIED} < ?"
    val args = arrayOf("%Screenshot%", cutoffSeconds.toString())
    val (count, bytes) = summarize(context, selection, args)
    val items = queryCleanupItems(context, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC")
    return CategoryResult(CleanupCategory.OLD_SCREENSHOTS, bytes, count, items)
}

private fun scanDownloads(context: Context): CategoryResult {
    val selection = "$pathColumn LIKE ?"
    val args = arrayOf("%Download%")
    val (count, bytes) = summarize(context, selection, args)
    val items = queryCleanupItems(context, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
    return CategoryResult(CleanupCategory.DOWNLOADS, bytes, count, items)
}

private fun scanEmptyFiles(context: Context): CategoryResult {
    val selection = "${MediaStore.Files.FileColumns.SIZE} = 0"
    val (count, bytes) = summarize(context, selection, null)
    val items = queryCleanupItems(context, selection, null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
    return CategoryResult(CleanupCategory.EMPTY_FILES, bytes, count, items)
}

private fun scanCache(context: Context): CategoryResult {
    val roots = buildList {
        context.cacheDir?.let { add(it) }
        runCatching { context.externalCacheDirs?.filterNotNull() }.getOrNull()?.let { addAll(it) }
    }
    val entries = roots.flatMap { root -> root.listFiles()?.toList() ?: emptyList() }
    val items = entries.map { entry ->
        CleanupItem(id = "cache_${entry.absolutePath}", name = entry.name, sizeBytes = folderSize(entry), file = entry)
    }.sortedByDescending { it.sizeBytes }
    return CategoryResult(CleanupCategory.CACHE, items.sumOf { it.sizeBytes }, items.size, items)
}

private fun folderSize(file: java.io.File): Long = when {
    file.isFile -> file.length()
    file.isDirectory -> file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    else -> 0L
}

/**
 * Real, on-device storage breakdown: total/used/free from [StatFs] (no permission
 * needed), plus a per-category split of used space from MediaStore aggregate
 * queries (needs media permission — omitted entirely when it's not granted,
 * rather than showing a guessed split).
 */
@Suppress("DEPRECATION")
private fun readDeviceStorageOverview(context: Context, hasPermission: Boolean): DeviceStorageOverview {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    val totalBytes = stat.blockCountLong * stat.blockSizeLong
    val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

    val slices = if (hasPermission) {
        val media = MediaStore.Files.FileColumns.MEDIA_TYPE
        val mime = MediaStore.Files.FileColumns.MIME_TYPE

        val (_, imageBytes) = summarize(context, "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}", null)
        val (_, videoBytes) = summarize(context, "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}", null)
        val (_, audioBytes) = summarize(context, "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}", null)

        // Documents/Downloads = recognized document MIME types, plus anything
        // sitting in the Downloads folder — excluding image/video/audio there
        // so it isn't double-counted against the categories above.
        val docSelection = "$mime IN (${DOCUMENT_MIMES.joinToString(",") { "?" }}) OR " +
            "($pathColumn LIKE ? AND $media NOT IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}, " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}))"
        val docArgs = (DOCUMENT_MIMES + "%Download%").toTypedArray()
        val (_, docBytes) = summarize(context, docSelection, docArgs)

        // Whatever's left of used space that wasn't measured above — system
        // files, app installs/data, and anything MediaStore doesn't expose.
        val otherBytes = (usedBytes - (imageBytes + videoBytes + audioBytes + docBytes)).coerceAtLeast(0L)

        listOf(
            StorageBreakdownSlice("Images", imageBytes, CiyatoBlue),
            StorageBreakdownSlice("Videos", videoBytes, CiyatoPurple),
            StorageBreakdownSlice("Audio", audioBytes, CiyatoGreen),
            StorageBreakdownSlice("Documents & Downloads", docBytes, CiyatoAmber),
            StorageBreakdownSlice("Other / app data", otherBytes, CiyatoMuted),
        )
    } else {
        emptyList()
    }

    return DeviceStorageOverview(totalBytes = totalBytes, usedBytes = usedBytes, freeBytes = freeBytes, slices = slices)
}

/** Accurate count + total bytes for a selection, scanning every matching row (never capped). */
private fun summarize(context: Context, selection: String, args: Array<String>?): Pair<Int, Long> {
    var count = 0
    var bytes = 0L
    runCatching {
        context.contentResolver.query(filesUri, arrayOf(MediaStore.Files.FileColumns.SIZE), selection, args, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    count++
                    bytes += cursor.getLong(0)
                }
            }
    }
    return count to bytes
}

/** Capped item list for browsing/selecting in the UI. May be fewer than the real total. */
private fun queryCleanupItems(context: Context, selection: String, args: Array<String>?, sortOrder: String): List<CleanupItem> =
    buildList {
        runCatching {
            context.contentResolver.query(
                filesUri,
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE),
                selection,
                args,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (cursor.moveToNext() && size < DISPLAY_LIMIT) {
                    val id = cursor.getLong(idCol)
                    add(
                        CleanupItem(
                            id = "media_$id",
                            name = cursor.getString(nameCol) ?: "file_$id",
                            sizeBytes = cursor.getLong(sizeCol),
                            uri = ContentUris.withAppendedId(filesUri, id),
                        ),
                    )
                }
            }
        }
    }

// ── Real deletion ────────────────────────────────────────────────────────────

/**
 * Deletes MediaStore-backed [items], requesting the user's consent through the
 * system dialog when Android requires it. Returns the items that could NOT be
 * deleted so the caller can restore them instead of leaving a false "deleted" state.
 */
private suspend fun deleteMediaCleanupItems(
    context: Context,
    items: List<CleanupItem>,
    requestConsent: suspend (IntentSender) -> Boolean,
): List<CleanupItem> = withContext(Dispatchers.IO) {
    val failed = mutableListOf<CleanupItem>()
    when {
        items.isEmpty() -> Unit
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            val uris = items.mapNotNull { it.uri }
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
            val granted = withContext(Dispatchers.Main) { requestConsent(pendingIntent.intentSender) }
            if (!granted) failed += items
        }
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
            items.forEach { item ->
                val uri = item.uri
                if (uri == null) {
                    failed += item
                    return@forEach
                }
                try {
                    if (context.contentResolver.delete(uri, null, null) <= 0) failed += item
                } catch (security: RecoverableSecurityException) {
                    val granted = withContext(Dispatchers.Main) { requestConsent(security.userAction.actionIntent.intentSender) }
                    val deleted = granted && runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
                    if (!deleted) failed += item
                } catch (_: Exception) {
                    failed += item
                }
            }
        }
        else -> {
            items.forEach { item ->
                val uri = item.uri
                if (uri == null) {
                    failed += item
                    return@forEach
                }
                try {
                    if (context.contentResolver.delete(uri, null, null) <= 0) failed += item
                } catch (_: Exception) {
                    failed += item
                }
            }
        }
    }
    failed
}

/** Deletes app-owned cache entries directly — no MediaStore consent applies to our own storage. */
private fun deleteCacheItems(items: List<CleanupItem>): List<CleanupItem> {
    val failed = mutableListOf<CleanupItem>()
    items.forEach { item ->
        val file = item.file
        val ok = file != null && runCatching { if (file.isDirectory) file.deleteRecursively() else file.delete() }.getOrDefault(false)
        if (!ok) failed += item
    }
    return failed
}
