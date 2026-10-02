package com.ciyato.launcher.ui.screens

import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ciyato.launcher.data.FileAccess
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.ui.components.FilePreview
import com.ciyato.launcher.ui.components.FolderGlyph
import com.ciyato.launcher.ui.components.QueryFailureState
import com.ciyato.launcher.ui.components.openSystemScreen
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBorder
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoRed
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoShapes
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Files: the phone's internal storage, as a file manager.
 *
 * This tab used to be a second organizer - a category grid, a folder picker, a
 * cleanup-and-duplicates panel - which duplicated the Overview tab beside it and never
 * showed what a Files tab is for: the files. It is now a browser rooted at internal
 * storage, with folders first, real previews for files, and long-press selection to
 * share or delete. Cleanup lives in Overview, where storage is.
 *
 * It works on java.io paths, which needs All-files access - a deliberate product
 * decision for this app - and says so plainly when the grant is missing rather than
 * showing an empty list that reads like an empty phone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
// viewModel and onBack are kept so both activities' call sites stay as they are; this
// tab is a root of the bottom bar, so it has no back of its own to offer.
fun FilesScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: LauncherViewModel,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    /** The storage card opens cleanup: the card states how full the phone is, and this
     *  is where that number gets acted on. Navigating home is the breadcrumb's job. */
    onOpenCleanup: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { FileAccess.primarySharedStorageRoot() }

    var allFilesGranted by remember { mutableStateOf(FileAccess.hasAllFiles(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Granted in system settings, not by a runtime dialog, so the answer changes
        // while Ciyato is in the background.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allFilesGranted = FileAccess.hasAllFiles(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var path by rememberSaveable { mutableStateOf(root.path) }
    val current = File(path)
    val atRoot = current.path == root.path
    var entries by remember { mutableStateOf<List<Entry>?>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable(path) { mutableStateOf(setOf<String>()) }
    val selecting = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    val storage = remember(reloadTick) { storageOf(root) }

    LaunchedEffect(path, allFilesGranted, reloadTick) {
        if (!allFilesGranted) { loading = false; return@LaunchedEffect }
        loading = true
        entries = listEntries(current)
        loading = false
    }

    BackHandler(enabled = selecting || !atRoot) {
        if (selecting) selected = emptySet() else current.parentFile?.let { path = it.path }
    }

    Column(Modifier.fillMaxSize().background(CiyatoBg)) {
        if (selecting) {
            val chosen = entries.orEmpty().filter { it.file.path in selected }
            FilesSelectionBar(
                count = selected.size,
                canShare = chosen.size == 1 && !chosen.first().isDirectory,
                onClose = { selected = emptySet() },
                onSelectAll = { selected = entries.orEmpty().map { it.file.path }.toSet() },
                onShare = {
                    val f = chosen.first().file
                    FileAccess.openExternally(context, Uri.fromFile(f), FileAccess.mimeTypeOf(f.name), Intent.ACTION_SEND)
                        ?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                },
                onDelete = { confirmDelete = true },
            )
        } else {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                Text("Files", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.6).sp)
                Spacer(Modifier.height(4.dp))
                Text("Everything stored on this phone.", color = CiyatoMuted, fontSize = 14.sp)
            }
        }

        if (!allFilesGranted) {
            NeedsAllFiles()
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "storage") {
                Box(Modifier.padding(bottom = 12.dp)) {
                InternalStorageCard(
                    usedBytes = storage.first,
                    totalBytes = storage.second,
                    onClick = onOpenCleanup,
                )
                }
            }
            if (!atRoot) {
                item(key = "crumbs") {
                    Breadcrumbs(root = root, current = current, onNavigate = { path = it.path })
                }
            }

            val list = entries
            when {
                loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CiyatoWhite, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }
                }
                list == null -> item(key = "error") {
                    QueryFailureState(
                        title = "Couldn't open this folder",
                        detail = "Android didn't let Ciyato read it. This doesn't mean it's empty.",
                    )
                }
                list.isEmpty() -> item(key = "empty") {
                    Text(
                        "This folder is empty.",
                        color = CiyatoMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                    )
                }
                else -> items(list, key = { it.file.path }) { entry ->
                    if (entry !== list.first()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 68.dp)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.05f)),
                        )
                    }
                    EntryRow(
                        entry = entry,
                        selecting = selecting,
                        selected = entry.file.path in selected,
                        onClick = {
                            val id = entry.file.path
                            when {
                                selecting -> selected = if (id in selected) selected - id else selected + id
                                entry.isDirectory -> path = id
                                else -> FileAccess.openExternally(context, Uri.fromFile(entry.file), entry.mimeType)
                                    ?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                            }
                        },
                        onLongClick = {
                            val id = entry.file.path
                            selected = if (id in selected) selected - id else selected + id
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val targets = entries.orEmpty().filter { it.file.path in selected }
        val hasFolders = targets.any { it.isDirectory }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CiyatoBgEl,
            title = {
                Text(
                    "Delete ${targets.size} ${if (targets.size == 1) "item" else "items"} permanently?",
                    color = CiyatoWhite,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                // There is no Trash for arbitrary files - MediaStore's Trash only holds
                // media - so this is said outright rather than implied.
                Text(
                    if (hasFolders) {
                        "This can't be undone. Folders are deleted with everything inside them."
                    } else {
                        "This can't be undone."
                    },
                    color = CiyatoSec,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val failed = deleteEntries(context, targets.map { it.file })
                        if (failed > 0) {
                            android.widget.Toast.makeText(
                                context,
                                "$failed couldn't be fully deleted.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                        selected = emptySet()
                        reloadTick += 1
                    }
                }) { Text("Delete", color = CiyatoRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = CiyatoSec) }
            },
        )
    }
}

// ── data ─────────────────────────────────────────────────────────────────────

private data class Entry(
    val file: File,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    /** Visible children, for a folder; 0 for a file. */
    val childCount: Int,
    val mimeType: String,
)

/**
 * A folder's contents, folders first, or null when Android would not let Ciyato read it.
 *
 * Null, not an empty list: "you may not look in here" and "there is nothing in here"
 * are different answers, and this codebase has rendered the first as the second too
 * many times. Dot-folders are hidden - `.thumbnails` and friends are app caches, not
 * the person's files.
 */
private suspend fun listEntries(dir: File): List<Entry>? = withContext(Dispatchers.IO) {
    val children = runCatching { dir.listFiles() }.getOrNull() ?: return@withContext null
    children.asSequence()
        .filterNot { it.name.startsWith(".") }
        .map { f ->
            val isDir = f.isDirectory
            Entry(
                file = f,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else f.length(),
                modifiedAtMs = f.lastModified(),
                childCount = if (isDir) runCatching { f.list()?.count { !it.startsWith(".") } }.getOrNull() ?: 0 else 0,
                mimeType = if (isDir) "" else FileAccess.mimeTypeOf(f.name),
            )
        }
        .sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.file.name.lowercase() })
        .toList()
}

/**
 * Deletes [targets], returning how many could not be fully removed.
 *
 * MediaStore is told afterwards, because Photos, Overview and every gallery on the
 * phone read from it; without the rescan they would keep showing deleted images until
 * the next system scan.
 */
private suspend fun deleteEntries(context: android.content.Context, targets: List<File>): Int =
    withContext(Dispatchers.IO) {
        var failed = 0
        val removed = mutableListOf<String>()
        targets.forEach { f ->
            val ok = runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }.getOrDefault(false)
            if (ok) removed += f.path else failed += 1
        }
        if (removed.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, removed.toTypedArray(), null, null)
        }
        failed
    }

/** Used and total bytes of the volume [root] sits on. */
private fun storageOf(root: File): Pair<Long, Long> = runCatching {
    val stat = StatFs(root.path)
    val total = stat.totalBytes
    total - stat.availableBytes to total
}.getOrDefault(0L to 0L)

// ── pieces ───────────────────────────────────────────────────────────────────

private val CardShape = RoundedCornerShape(22.dp)

/**
 * The volume itself, at the top: what it is, how full it is, and the way back to it.
 */
@Composable
private fun InternalStorageCard(usedBytes: Long, totalBytes: Long, onClick: () -> Unit) {
    val fraction = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF171B22), Color(0xFF0F1115))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.03f))), CardShape)
            .clickable(onClickLabel = "Open storage cleanup", role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .shadow(8.dp, RoundedCornerShape(13.dp), ambientColor = Color(0xFF1E3A8A), spotColor = Color(0xFF1E3A8A))
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7DD3FC), Color(0xFF1E40AF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Internal storage", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                if (totalBytes > 0) {
                    "${MediaLibraryRepository.formatBytes(usedBytes)} of ${MediaLibraryRepository.formatBytes(totalBytes)} used"
                } else {
                    "Size unavailable"
                },
                color = CiyatoMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(CiyatoShapes.full).background(Color.White.copy(alpha = 0.08f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                            .fillMaxHeight()
                            .clip(CiyatoShapes.full)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF7DD3FC), Color(0xFFA78BFA)))),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${(fraction * 100).toInt()}%",
                color = CiyatoWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text("Clean up", color = CiyatoMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Breadcrumbs(root: File, current: File, onNavigate: (File) -> Unit) {
    val chain = remember(current.path) {
        generateSequence(current) { it.parentFile }
            .takeWhile { it.path.startsWith(root.path) }
            .toList()
            .reversed()
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chain.forEachIndexed { index, dir ->
            val last = index == chain.lastIndex
            Text(
                if (dir.path == root.path) "Internal storage" else dir.name,
                color = if (last) CiyatoWhite else CiyatoMuted,
                fontSize = 13.sp,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(CiyatoShapes.full)
                    .clickable(enabled = !last, role = Role.Button) { onNavigate(dir) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            if (!last) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CiyatoMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: Entry,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // A plain row, not a card. A border round every folder made the listing read as a
    // stack of buttons; a file manager is a list, and the eye should be scanning names.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.medium)
            .background(if (selected) Color(0xFF1C2027) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.isDirectory) {
            FolderGlyph(Modifier.size(width = 46.dp, height = 40.dp))
        } else {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))) {
                FilePreview(
                    name = entry.file.name,
                    mimeType = entry.mimeType,
                    uri = Uri.fromFile(entry.file),
                    file = entry.file,
                    compact = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.file.name,
                color = CiyatoWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (entry.isDirectory) {
                    when (entry.childCount) {
                        0 -> "Empty"
                        1 -> "1 item"
                        else -> "${entry.childCount} items"
                    }
                } else {
                    "${MediaLibraryRepository.formatBytes(entry.sizeBytes)} · " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.modifiedAtMs))
                },
                color = CiyatoMuted,
                fontSize = 12.sp,
            )
        }
        when {
            selecting -> Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) CiyatoWhite else Color.Transparent)
                    .border(1.5.dp, if (selected) CiyatoWhite else CiyatoMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = CiyatoBg, modifier = Modifier.size(15.dp))
            }
            entry.isDirectory -> Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.30f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FilesSelectionBar(
    count: Int,
    canShare: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF13161B))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection", tint = CiyatoWhite)
        }
        Text(
            "$count selected",
            color = CiyatoWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
        )
        IconButton(onClick = onSelectAll) {
            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all", tint = CiyatoSec)
        }
        // One file at a time: sharing is a handoff to another app, and handing over a
        // folder or a mixed batch is not something most share targets accept.
        if (canShare) {
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = CiyatoSec)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete permanently", tint = CiyatoRed)
        }
    }
}

@Composable
private fun NeedsAllFiles() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FolderGlyph(Modifier.size(width = 84.dp, height = 72.dp))
        Spacer(Modifier.height(18.dp))
        Text("Allow access to all files", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Android keeps files outside your photo library hidden until you allow it. " +
                "Ciyato reads them on this phone and never uploads anything.",
            color = CiyatoMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Allow access",
            color = CiyatoBg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(CiyatoShapes.full)
                .background(CiyatoWhite)
                .clickable(role = Role.Button) {
                    val intent = FileAccess.allFilesSettingsIntent(context)
                    if (intent == null) {
                        android.widget.Toast.makeText(context, "This phone has no all-files access screen.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        openSystemScreen(context, intent, "look under Settings > Apps > Special app access > All files access")
                    }
                }
                .padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }
}

