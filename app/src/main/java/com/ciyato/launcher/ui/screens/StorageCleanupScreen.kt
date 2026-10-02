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
import com.ciyato.launcher.data.PhotoDeviceLibrary
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import androidx.compose.ui.res.pluralStringResource
import com.ciyato.launcher.R
import androidx.compose.runtime.derivedStateOf
import com.ciyato.launcher.data.MediaAccess
import com.ciyato.launcher.ui.components.openSystemScreen
import com.ciyato.launcher.ui.components.GlyphChip
import com.ciyato.launcher.ui.components.FilePreview
import com.ciyato.launcher.data.FileAccess
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import android.provider.Settings

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

    // The access LEVEL, not a boolean: a partial grant answers true to
    // "do we have permission?" while showing only a hand-picked subset (F-115).
    var access by remember { mutableStateOf(MediaAccess.of(context)) }
    val hasPermission by remember { derivedStateOf { access.canSeeAnything } }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { access = MediaAccess.of(context) }

    // Granting from system Settings is the only path left after a permanent
    // denial; re-check on resume so the screen updates without a restart.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                access = MediaAccess.of(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isScanning by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<CategoryResult>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<CleanupCategory?>(null) }
    var deviceStorage by remember { mutableStateOf<DeviceStorageOverview?>(null) }

    LaunchedEffect(access) {
        isScanning = true
        val (overview, scanned) = withContext(Dispatchers.IO) {
            val overview = readDeviceStorageOverview(context, access)
            val scanned = buildList {
                add(scanCache(context))
                // Needs Usage access, not media access, so it is measured either way.
                add(scanAppCaches(context))
                // Categories backed by MediaStore can't be measured without the
                // media permission, so they simply don't appear rather than
                // showing a fake zero.
                if (hasPermission) {
                    // Trashed photos are the one category that is pure win:
                    // they are already deleted as far as the person is
                    // concerned, and still occupying the disk until something
                    // clears them. Emptying the trash is what actually frees
                    // that space, so the cleanup agent owns that job.
                    add(scanTrash(context))
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
    if (openCategory != null && openCategory.category == CleanupCategory.APP_CACHES) {
        AppCacheDetail(result = openCategory, onBack = { selectedCategory = null })
        return
    }
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

    Column(Modifier.fillMaxSize().background(CiyatoBg)) {
        CleanupHeader(scanning = isScanning, onBack = onBack)

        if (!hasPermission) {
            Box(Modifier.padding(horizontal = 16.dp)) {
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
        }

        if (isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = CiyatoWhite, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
                    Text("Scanning your storage\u2026", color = CiyatoMuted, style = bodyM)
                }
            }
        } else {
            val uniqueItems = remember(results) { results.flatMap { it.items }.distinctBy { it.id } }
            // Unique bytes, not the sum of category totals: one file can sit in
            // several categories - a 400 MB video in Downloads is also a Large File -
            // and summing totals promised more space than the phone had (F-113).
            val measuredBytes = remember(uniqueItems) { uniqueItems.sumOf { it.sizeBytes } }
            val overlapping = remember(uniqueItems, results) { uniqueItems.size < results.sumOf { it.totalCount } }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                deviceStorage?.let { overview ->
                    item(key = "donut") { StorageDonutCard(overview = overview) }
                }
                item(key = "found") {
                    CleanupSummaryCard(totalBytes = measuredBytes, totalCount = uniqueItems.size, overlapNote = overlapping)
                }
                // Grouped by how much judgement each needs, safest first (F-118).
                CleanupTier.entries.forEach { tier ->
                    val inTier = results.filter { it.category.tier == tier }
                    if (inTier.isEmpty()) return@forEach
                    item(key = "tier_${tier.name}") {
                        Column(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                tier.title.uppercase(),
                                color = CiyatoMuted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                letterSpacing = 1.3.sp,
                            )
                            Text(tier.blurb, color = CiyatoMuted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    items(inTier, key = { it.category }) { result ->
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
                else pluralStringResource(R.plurals.count_items, result.totalCount, result.totalCount),
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
        Modifier
            .fillMaxWidth()
            .clip(CleanupCardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF14201D), Color(0xFF0F1115))))
            .border(1.dp, CleanupEdge, CleanupCardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Framed as found, not as guaranteed reclaim: these are worth LOOKING at, and
        // some will be worth keeping (F-113, F-114).
        Text("READY TO REVIEW", color = CiyatoMuted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
        Text(
            MediaLibraryRepository.formatBytes(totalBytes),
            style = TextStyle(
                brush = Brush.linearGradient(listOf(Color(0xFF6EE7B7), Color(0xFF5EEAD4), Color(0xFF7DD3FC))),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
            ),
        )
        Text(
            buildString {
                append("$totalCount item")
                if (totalCount != 1) append("s")
                append(if (overlapNote) ", each counted once even where it sits in two categories" else " across the categories below")
            },
            color = CiyatoSec,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

/** Colour for a breakdown slice, by what it measures - the same hues as the categories. */
private fun sliceColor(label: String): Color = when {
    label.startsWith("Image") -> Color(0xFFC084FC)
    label.startsWith("Video") -> Color(0xFFFB7185)
    label.startsWith("Audio") -> Color(0xFF34D399)
    label.startsWith("Document") -> Color(0xFFFBBF24)
    else -> Color(0xFF64748B)
}

/**
 * What is using the phone's storage, as a donut.
 *
 * The whole ring is the device; each arc is a measured slice; the empty track is
 * free space. On most phones "Apps & system" is most of the ring, and the chart is
 * honest about that rather than inflating the media slices to look interesting.
 */
@Composable
private fun StorageDonutCard(overview: DeviceStorageOverview) {
    val visible = overview.slices.filter { it.bytes > 0L }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CleanupCardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF171B22), Color(0xFF0F1115))))
            .border(1.dp, CleanupEdge, CleanupCardShape)
            .padding(20.dp),
    ) {
        Text("STORAGE", color = CiyatoMuted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val stroke = size.minDimension * 0.12f
                    val inset = stroke / 2f
                    val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Color.White.copy(alpha = 0.07f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = Stroke(width = stroke),
                    )
                    val total = overview.totalBytes.coerceAtLeast(1L).toFloat()
                    var start = -90f
                    val gap = if (visible.size > 1) 2.4f else 0f
                    visible.forEach { slice ->
                        val sweep = 360f * slice.bytes / total
                        if (sweep > gap + 0.4f) {
                            drawArc(
                                color = sliceColor(slice.label),
                                startAngle = start + gap / 2f,
                                sweepAngle = sweep - gap,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arc,
                                style = Stroke(width = stroke, cap = StrokeCap.Butt),
                            )
                        }
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        MediaLibraryRepository.formatBytes(overview.usedBytes),
                        color = CiyatoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        letterSpacing = (-0.4).sp,
                    )
                    Text("of ${MediaLibraryRepository.formatBytes(overview.totalBytes)}", color = CiyatoMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                visible.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(sliceColor(slice.label)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            slice.label.replace("Other / app data", "Apps & system"),
                            color = CiyatoSec,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(MediaLibraryRepository.formatBytes(slice.bytes), color = CiyatoWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("Free", color = CiyatoSec, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(MediaLibraryRepository.formatBytes(overview.freeBytes), color = CiyatoWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (visible.isNotEmpty() && !overview.access.totalsAreComplete) {
            Spacer(Modifier.height(14.dp))
            // Said beside the chart: under a partial grant the media slices are real
            // but incomplete, and a chart that does not say so misleads (F-115).
            Text(
                "Ciyato can only see the photos and videos you selected, so these sizes are partial. " +
                    "Allow access to all photos for a complete breakdown.",
                color = CiyatoMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        } else if (!overview.access.canSeeAnything) {
            Spacer(Modifier.height(14.dp))
            Text("Grant media access below to see what's using the space.", color = CiyatoMuted, fontSize = 12.sp)
        }
    }
}





/** Each category's chip colours - the Organizer's palette, so the screens agree. */
private fun glyphFor(category: CleanupCategory): Triple<ImageVector, Color, Color> = when (category) {
    CleanupCategory.CACHE -> Triple(Icons.Rounded.Memory, Color(0xFF94A3B8), Color(0xFF1E293B))
    CleanupCategory.APP_CACHES -> Triple(Icons.Rounded.Apps, Color(0xFF5EEAD4), Color(0xFF134E4A))
    CleanupCategory.TRASH -> Triple(Icons.Rounded.Delete, Color(0xFFFB7185), Color(0xFF881337))
    CleanupCategory.OLD_SCREENSHOTS -> Triple(Icons.Rounded.Screenshot, Color(0xFF60A5FA), Color(0xFF1E3A8A))
    CleanupCategory.EMPTY_FILES -> Triple(Icons.AutoMirrored.Rounded.InsertDriveFile, Color(0xFFCBD5E1), Color(0xFF334155))
    CleanupCategory.LARGE_FILES -> Triple(Icons.Rounded.Inventory2, Color(0xFFFBBF24), Color(0xFF92400E))
    CleanupCategory.DOWNLOADS -> Triple(Icons.Rounded.Download, Color(0xFF818CF8), Color(0xFF312E81))
}

@Composable
private fun CleanupCategoryCard(result: CategoryResult, onClick: () -> Unit) {
    val (icon, light, deep) = glyphFor(result.category)
    val empty = result.totalCount == 0
    // Zero-byte files report their COUNT. "0 B" beside them read as "nothing here",
    // which was the truth about the bytes and a falsehood about the files.
    val trailing = when {
        empty -> null
        result.category == CleanupCategory.EMPTY_FILES -> "${result.totalCount}"
        else -> MediaLibraryRepository.formatBytes(result.totalBytes)
    }
    val subtitle = when {
        result.note != null && empty -> result.note
        empty -> "None found"
        result.category == CleanupCategory.APP_CACHES ->
            "${result.totalCount} ${if (result.totalCount == 1) "app" else "apps"} \u00B7 ${result.category.description}"
        else -> pluralStringResource(R.plurals.count_items, result.totalCount, result.totalCount) +
            " \u00B7 ${result.category.description}"
    }
    // A few of the files themselves, so a category reads as what it contains.
    val previews = remember(result.items) {
        if (result.category in setOf(CleanupCategory.OLD_SCREENSHOTS, CleanupCategory.LARGE_FILES, CleanupCategory.DOWNLOADS, CleanupCategory.TRASH)) {
            result.items.filter { it.uri != null || it.file != null }.take(4)
        } else emptyList()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(CleanupCardShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF15181D), Color(0xFF0E1013))))
            .border(1.dp, CleanupEdge, CleanupCardShape)
            .clickable(enabled = !empty, onClickLabel = "Review ${result.category.label}", role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(icon, light, deep, side = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(result.category.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = CiyatoMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(trailing, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (result.category == CleanupCategory.EMPTY_FILES) {
                        Text(if (result.totalCount == 1) "file" else "files", color = CiyatoMuted, fontSize = 11.sp)
                    }
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (previews.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                previews.forEach { item ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                        FilePreview(
                            name = item.name,
                            mimeType = FileAccess.mimeTypeOf(item.name),
                            uri = item.uri ?: Uri.fromFile(item.file),
                            file = item.file,
                            compact = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                repeat(4 - previews.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Other apps' caches, largest first, each opening that app's own settings.
 *
 * Android does not let one app clear another's cache - that permission is reserved for
 * the system - so the honest version of this list routes the person to the one place
 * that can, rather than offering a button that cannot work.
 */
@Composable
private fun AppCacheDetail(result: CategoryResult, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(CiyatoBg)) {
        CleanupHeader(scanning = false, onBack = onBack, title = "Apps' cache")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "intro") {
                Text(
                    "${MediaLibraryRepository.formatBytes(result.totalBytes)} across ${result.totalCount} apps. " +
                        "Android only lets an app's own settings clear its cache - tap one to open it, then Storage \u2192 Clear cache.",
                    color = CiyatoMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(result.items, key = { it.id }) { item ->
                val icon = remember(item.packageName) {
                    runCatching { context.packageManager.getApplicationIcon(item.packageName!!).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CiyatoShapes.medium)
                        .clickable(onClickLabel = "Open ${item.name} settings", role = Role.Button) {
                            openSystemScreen(
                                context,
                                android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", item.packageName, null)),
                                "find ${item.name} in Settings > Apps",
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(CiyatoBgEl))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(item.name, color = CiyatoWhite, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(MediaLibraryRepository.formatBytes(item.sizeBytes), color = CiyatoSec, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun CleanupHeader(scanning: Boolean, onBack: () -> Unit, title: String = "Cleanup") {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = CiyatoWhite)
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(title, color = CiyatoWhite, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (scanning) "Scanning\\u2026" else "Scanned just now", color = CiyatoMuted, fontSize = 12.sp)
        }
    }
}

private val CleanupCardShape = RoundedCornerShape(22.dp)
private val CleanupEdge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.03f)))

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
            .background(if (selected) Color(0xFF1C2027) else CiyatoBgEl)
            .border(1.dp, if (selected) CiyatoWhite.copy(alpha = 0.6f) else CiyatoSubtleBorder, CiyatoShapes.medium)
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The file itself, not just its name.
        if (item.uri != null || item.file != null) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))) {
                FilePreview(
                    name = item.name,
                    mimeType = FileAccess.mimeTypeOf(item.name),
                    uri = item.uri ?: Uri.fromFile(item.file),
                    file = item.file,
                    compact = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(item.name, color = CiyatoWhite, style = bodyM, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(MediaLibraryRepository.formatBytes(item.sizeBytes), color = CiyatoMuted, style = labelL)
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) CiyatoWhite else Color.Transparent)
                .border(1.5.dp, if (selected) CiyatoWhite else CiyatoMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Default.Check, null, tint = CiyatoBg, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Data model ───────────────────────────────────────────────────────────────

/**
 * How much judgement a category needs before anything is deleted.
 *
 * These six were presented as peers, which invited one mental action - delete -
 * across wildly different evidence (F-118). Ciyato's own cache is regenerable
 * and costs nothing to clear. A zero-byte file cannot contain anything. But
 * "Downloads" is an ordinary folder that may hold the only copy of a document,
 * and a 60 MB file is large, which is not a reason to think it is unwanted.
 *
 * Ordering the screen by risk also puts the safest wins first, which is where
 * someone trying to free space should start.
 */
internal enum class CleanupTier(val title: String, val blurb: String) {
    SAFE(
        "Safe to clear",
        "Regenerable or provably empty. Nothing here can hold your only copy of anything.",
    ),
    REVIEW(
        "Worth reviewing",
        "Real files that are probably finished with. Ciyato is confident about the age or the size, not about whether you still want them.",
    ),
    SUGGESTION(
        "Look before deleting",
        "Only a signal, not a verdict. Ciyato knows these are big or in a folder that fills up - it has no idea whether they matter to you.",
    ),
}

internal enum class CleanupCategory(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val tier: CleanupTier,
) {
    // Ciyato's own scratch data, regenerated on demand.
    CACHE("App Cache", "Ciyato's own temporary data, rebuilt as needed", Icons.Default.Memory, CiyatoAmber, CleanupTier.SAFE),
    // Every other app's cache, measured through Usage access. Ciyato cannot clear
    // another app's cache - only that app's own settings can - so this reports and
    // routes rather than deletes.
    APP_CACHES("Apps' cache", "Temporary data other apps keep", Icons.Default.Apps, CiyatoAmber, CleanupTier.SAFE),
    // Already deleted by the person; Android is holding it for the trash window.
    TRASH("Trash", "Already deleted, still holding space", Icons.Default.DeleteForever, CiyatoAmber, CleanupTier.SAFE),

    // Age is decent evidence, and screenshots are usually disposable - but they
    // are still the person's own pictures.
    OLD_SCREENSHOTS("Old Screenshots", "Taken more than 30 days ago", Icons.Default.Screenshot, CiyatoPurple, CleanupTier.REVIEW),

    // Zero bytes holds no data, but that does not make a file disposable. This sat
    // in "Safe to clear" beside a promise that nothing there could matter, and
    // selected every zero-byte file - including .nomedia markers, which are empty BY
    // DESIGN and exist to keep a folder out of galleries. Deleting one makes hidden
    // media appear in every photo app on the phone. Dot-files are now excluded
    // entirely, and the rest are something to review, not a guaranteed win.
    EMPTY_FILES("Empty Files", "Free no space, only clutter", Icons.Default.DeleteSweep, CiyatoAmber, CleanupTier.REVIEW),

    // Size is not evidence of being unwanted, and Downloads holds real documents.
    LARGE_FILES("Large Files", "Over 50 MB each", Icons.Default.Storage, CiyatoBlue, CleanupTier.SUGGESTION),
    DOWNLOADS("Downloads", "Everything in Downloads - may include the only copy", Icons.Default.Download, CiyatoBlue, CleanupTier.SUGGESTION),
}

private data class CleanupItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val uri: Uri? = null,
    val file: java.io.File? = null,
    /** Set for an app's cache, which is opened in that app's settings, not deleted. */
    val packageName: String? = null,
)

private data class CategoryResult(
    val category: CleanupCategory,
    val totalBytes: Long,
    val totalCount: Int,
    val items: List<CleanupItem>,
    /** Why the category is empty or partial, when "none found" would mislead. */
    val note: String? = null,
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
    /**
     * How much of the library the measurements could see.
     *
     * Carried with the numbers rather than re-derived at render time, so the
     * chart cannot be drawn without the caveat that applies to it (F-115).
     */
    val access: MediaAccess = MediaAccess.NONE,
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

// Declared at API 29 but a compile-time String constant ("external"), so it is
// inlined at build time and works unchanged on minSdk 26. See the same note in
// MediaLibraryRepository.
@android.annotation.SuppressLint("InlinedApi")
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

/**
 * Photos sitting in the system trash.
 *
 * Deleting these goes through the same consent flow as every other media
 * category — and because the rows are already trashed, that delete is the
 * permanent one, which is exactly what emptying a trash means.
 */
private suspend fun scanTrash(context: Context): CategoryResult {
    val items = PhotoDeviceLibrary.loadTrashedImages(context).map { image ->
        CleanupItem(
            id = image.uri.toString(),
            name = image.name,
            sizeBytes = image.sizeBytes,
            uri = image.uri,
        )
    }
    // Totals queried separately from the review list, which every other category
    // already did. Summing the capped list meant a gallery with 3,000 trashed
    // photos reported the newest 1,000 as the whole of its trash (F-117) - an
    // under-report, in the screen whose only job is saying how much you could
    // reclaim. The "Showing largest N of M" header already existed and could
    // never fire here, because totalCount was set from items.size.
    val (totalCount, totalBytes) = PhotoDeviceLibrary.trashedTotals(context)
    return CategoryResult(
        // Samsung Gallery and several other gallery apps keep their own recycle bin,
        // which other apps cannot read. An empty system Trash on such a phone is the
        // truth about Android's Trash and says nothing about the gallery's, so the
        // card explains instead of implying there is nothing anywhere.
        note = if (maxOf(totalCount, items.size) == 0) {
            "Android's Trash is empty. Your gallery app's own recycle bin isn't visible to other apps."
        } else null,
        category = CleanupCategory.TRASH,
        // A totals query that returns nothing while the list has content means
        // the query failed rather than the trash being empty; fall back to the
        // list so the category is never reported as 0 bytes while showing items.
        totalBytes = if (totalCount > 0) totalBytes else items.sumOf { it.sizeBytes },
        totalCount = maxOf(totalCount, items.size),
        items = items,
    )
}

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
    // When it was TAKEN, not when the file was last modified. Copying, restoring
    // from a backup or moving between folders resets the modified date, so a
    // two-year-old screenshot restored last week counted as new and never appeared
    // here. DATE_TAKEN is in milliseconds; the modified date (seconds) remains the
    // fallback for the rows that have no taken date at all.
    val dated = if (Build.VERSION.SDK_INT >= 29) {
        "COALESCE(${MediaStore.MediaColumns.DATE_TAKEN}, ${MediaStore.Files.FileColumns.DATE_MODIFIED} * 1000)"
    } else {
        "${MediaStore.Files.FileColumns.DATE_MODIFIED} * 1000"
    }
    val selection = "$media = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND $pathColumn LIKE ? AND " +
        "$dated < ?"
    val args = arrayOf("%Screenshot%", (cutoffSeconds * 1000L).toString())
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
    // Dot-files are markers (.nomedia hides a folder from galleries) and Android/ is
    // other apps' own storage - neither is the person's clutter.
    val selection = "${MediaStore.Files.FileColumns.SIZE} = 0 AND " +
        "${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%' AND " +
        "$pathColumn NOT LIKE '%Android/%'"
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

/**
 * Every installed app's cache, measured with StorageStatsManager.
 *
 * The old App Cache card measured only Ciyato's own, so on a phone with a few
 * gigabytes of app caches it said "1 item, 0 B" - a real number about the wrong thing.
 * This needs Usage access, which Ciyato already uses for Insights; without it Android
 * throws SecurityException for other packages, and the card says so instead of
 * reporting an empty phone.
 */
private fun scanAppCaches(context: Context): CategoryResult {
    val stats = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
        ?: return CategoryResult(CleanupCategory.APP_CACHES, 0L, 0, emptyList(), note = "This phone does not report app storage.")
    val pm = context.packageManager
    val user = android.os.Process.myUserHandle()
    var denied = false
    val items = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        .filter { it.packageName != context.packageName }
        .mapNotNull { app ->
            val cache = try {
                stats.queryStatsForPackage(app.storageUuid, app.packageName, user).cacheBytes
            } catch (_: SecurityException) {
                denied = true
                0L
            } catch (_: Exception) {
                0L
            }
            if (cache <= 0L) {
                null
            } else {
                CleanupItem(
                    id = "appcache:${app.packageName}",
                    name = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName),
                    sizeBytes = cache,
                    packageName = app.packageName,
                )
            }
        }
        .sortedByDescending { it.sizeBytes }
    return CategoryResult(
        category = CleanupCategory.APP_CACHES,
        totalBytes = items.sumOf { it.sizeBytes },
        totalCount = items.size,
        items = items,
        note = if (denied && items.isEmpty()) "Turn on Usage access to measure other apps' caches." else null,
    )
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
private fun readDeviceStorageOverview(context: Context, access: MediaAccess): DeviceStorageOverview {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    val totalBytes = stat.blockCountLong * stat.blockSizeLong
    val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

    val slices = if (access.canSeeAnything) {
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

        // Whatever is left of used space that was not measured above.
        //
        // Under FULL access that really is system files, app installs and data
        // MediaStore does not expose. Under a PARTIAL grant it is mostly the
        // person's own photos and videos — the ones they did not hand-pick —
        // and calling that "app data" is a confident lie told by a chart
        // (F-115). Same arithmetic, honest label, plus a note saying why.
        val residualBytes = (usedBytes - (imageBytes + videoBytes + audioBytes + docBytes)).coerceAtLeast(0L)
        val residualLabel = if (access.totalsAreComplete) {
            "Other / app data"
        } else {
            "Not visible to Ciyato"
        }

        listOf(
            StorageBreakdownSlice("Images", imageBytes, CiyatoBlue),
            StorageBreakdownSlice("Videos", videoBytes, CiyatoPurple),
            StorageBreakdownSlice("Audio", audioBytes, CiyatoGreen),
            StorageBreakdownSlice("Documents & Downloads", docBytes, CiyatoAmber),
            StorageBreakdownSlice(residualLabel, residualBytes, CiyatoMuted),
        )
    } else {
        emptyList()
    }

    return DeviceStorageOverview(
        totalBytes = totalBytes,
        usedBytes = usedBytes,
        freeBytes = freeBytes,
        slices = slices,
        access = access,
    )
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
