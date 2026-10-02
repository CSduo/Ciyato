package com.ciyato.launcher.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.ViewList
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.FileAccess
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.data.MediaLibraryRepository.CategoryKey
import com.ciyato.launcher.data.PhotoDeviceLibrary
import com.ciyato.launcher.ui.components.FileKind
import com.ciyato.launcher.ui.components.FilePreview
import com.ciyato.launcher.ui.components.fileKindOf
import com.ciyato.launcher.ui.components.openSystemScreen
import com.ciyato.launcher.ui.components.openWithApp
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBorder
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoRed
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoShapes
import com.ciyato.launcher.ui.theme.CiyatoWhite
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One library category (Screenshots, Documents, Downloads, ...) as a browsable,
 * selectable collection.
 *
 * This was a flat list of grey document icons with a filename and a date, the same
 * for every category: a PDF could not be told from a spreadsheet, every video was an
 * empty square, and nothing could be removed. Now:
 *
 * - Real previews (see [FilePreview]): images and video frames, a PDF's first page,
 *   an APK's own icon, a text file's opening lines.
 * - Grid or list. Media categories default to a gallery grid; the rest to cards with
 *   the preview on top, so a document's page is visible before it is opened.
 * - Long-press to select, then share, move to Trash, or delete permanently. Trash and
 *   permanent delete both go through Android's own confirmation, because these files
 *   belong to whichever app wrote them and the system - not Ciyato - asks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCategoryScreen(
    categoryKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MediaLibraryRepository(context) }
    val key = remember(categoryKey) {
        runCatching { CategoryKey.valueOf(categoryKey) }.getOrNull()
    }
    var files by remember { mutableStateOf<List<MediaLibraryRepository.LibraryFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasAllFilesAccess by remember { mutableStateOf(repo.hasAllFilesAccess()) }
    var reloadTick by remember { mutableIntStateOf(0) }

    val mediaOnly = key in setOf(CategoryKey.PHOTOS, CategoryKey.SCREENSHOTS, CategoryKey.VIDEOS)
    var gridMode by rememberSaveable(categoryKey) {
        mutableStateOf(key !in setOf(CategoryKey.APKS, CategoryKey.AUDIO))
    }
    var selected by rememberSaveable(categoryKey) { mutableStateOf(setOf<String>()) }
    // Selection used to begin only on a long-press, with nothing on screen saying so,
    // and the owner of the app reasonably concluded that nothing in a category could
    // be deleted. The header's Select button opens the same mode.
    var selectMode by rememberSaveable(categoryKey) { mutableStateOf(false) }
    val selecting = selectMode || selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }

    // Granting "All files access" happens in Android's settings, outside this activity. Without
    // re-checking on resume the user came back to the same "needs access" wall they just cleared.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = repo.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(key, hasAllFilesAccess, reloadTick) {
        if (key != null) files = repo.filesForCategory(key)
        isLoading = false
    }

    BackHandler(enabled = selecting) { selected = emptySet(); selectMode = false }

    // The system dialog's answer. On OK the files are gone (or in Trash); reload so the
    // grid shows the truth rather than tiles for files that no longer exist.
    val systemRequest: ActivityResultLauncher<IntentSenderRequest> = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selected = emptySet()
            selectMode = false
            reloadTick += 1
        }
    }

    val title = when (key) {
        CategoryKey.SCREENSHOTS -> "Screenshots"
        CategoryKey.DOCUMENTS -> "Documents"
        CategoryKey.DOWNLOADS -> "Downloads"
        CategoryKey.PHOTOS -> "Photos"
        CategoryKey.VIDEOS -> "Videos"
        CategoryKey.APKS -> "APKs"
        CategoryKey.WHATSAPP -> "WhatsApp"
        CategoryKey.AUDIO -> "Audio"
        null -> "Files"
    }
    val selectedFiles = files.filter { it.uri.toString() in selected }

    fun launchSystem(sender: android.content.IntentSender?, fallback: () -> Unit) {
        if (sender != null) {
            systemRequest.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            fallback()
        }
    }

    Column(Modifier.fillMaxSize().background(CiyatoBg)) {
        // ── header ──────────────────────────────────────────────────────────
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClose = { selected = emptySet(); selectMode = false },
                onSelectAll = { selected = files.map { it.uri.toString() }.toSet() },
                onShare = {
                    val uris = ArrayList(selectedFiles.map { it.uri })
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    openWithApp(
                        context,
                        Intent.createChooser(intent, "Share ${selected.size}"),
                        "No app on this phone can share these files.",
                    )
                },
                // Trash exists from Android 11. Below that there is no recoverable
                // delete, so the button is not offered rather than pretending.
                onTrash = if (Build.VERSION.SDK_INT >= 30) {
                    {
                        val targets = selectedFiles
                        if (hasAllFilesAccess) {
                            // With all-files access Ciyato may mark any media item
                            // trashed itself; Trash is recoverable, so no dialog.
                            scope.launch {
                                val failed = trashDirectly(context, targets)
                                val moved = targets.size - failed.size
                                if (failed.isNotEmpty()) {
                                    // Anything the direct route refused goes through
                                    // Android's own request rather than silently staying.
                                    launchSystem(PhotoDeviceLibrary.trashRequest(context, failed.map { it.uri })) {}
                                }
                                if (moved > 0) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Moved $moved to Trash. Restore from Trash within 30 days.",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                selected = emptySet()
                                selectMode = false
                                reloadTick += 1
                            }
                        } else {
                            launchSystem(PhotoDeviceLibrary.trashRequest(context, targets.map { it.uri })) {
                                android.widget.Toast.makeText(context, "These files can't be moved to Trash.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else null,
                onDelete = {
                    if (hasAllFilesAccess || Build.VERSION.SDK_INT < 30) {
                        // Ciyato can delete these itself, so it asks once, itself.
                        confirmDelete = true
                    } else {
                        launchSystem(PhotoDeviceLibrary.deleteRequest(context, selectedFiles.map { it.uri })) {
                            confirmDelete = true
                        }
                    }
                },
            )
        } else {
            CategoryHeader(
                title = title,
                subtitle = if (isLoading) "" else
                    "${files.size} ${if (files.size == 1) "file" else "files"} · " +
                        MediaLibraryRepository.formatBytes(files.sumOf { it.sizeBytes }),
                gridMode = gridMode,
                onToggleLayout = { gridMode = !gridMode },
                onSelect = if (files.isNotEmpty()) ({ selectMode = true }) else null,
                onBack = onBack,
            )
        }

        // ── body ────────────────────────────────────────────────────────────
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CiyatoGold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }

            files.isEmpty() -> EmptyCategory(
                title = title,
                needsAllFiles = key in setOf(CategoryKey.DOCUMENTS, CategoryKey.DOWNLOADS, CategoryKey.APKS) &&
                    Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess,
            )

            else -> {
                val open: (MediaLibraryRepository.LibraryFile) -> Unit = { file ->
                    FileAccess.openExternally(context, file.uri, file.mimeType)?.let { reason ->
                        android.widget.Toast.makeText(context, reason, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                val onItemClick: (MediaLibraryRepository.LibraryFile) -> Unit = { file ->
                    val id = file.uri.toString()
                    if (selecting) {
                        selected = if (id in selected) selected - id else selected + id
                    } else {
                        open(file)
                    }
                }
                val onItemLongClick: (MediaLibraryRepository.LibraryFile) -> Unit = { file ->
                    val id = file.uri.toString()
                    selected = if (id in selected) selected - id else selected + id
                }

                if (gridMode && mediaOnly) {
                    // A gallery: edge-to-edge squares, hairline gaps, no text.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    ) {
                        items(files, key = { it.uri.toString() }) { file ->
                            val isSelected = file.uri.toString() in selected
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .combinedClickable(
                                        onClick = { onItemClick(file) },
                                        onLongClick = { onItemLongClick(file) },
                                        onLongClickLabel = "Select",
                                    ),
                            ) {
                                FilePreview(
                                    name = file.name,
                                    mimeType = file.mimeType,
                                    uri = file.uri,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                SelectionMark(selecting = selecting, selected = isSelected)
                            }
                        }
                    }
                } else if (gridMode) {
                    // Cards: the preview on top, so a document's page is visible
                    // before it is opened; name and size beneath.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    ) {
                        items(files, key = { it.uri.toString() }) { file ->
                            FileCard(
                                file = file,
                                apkPath = apkPathFor(file),
                                selecting = selecting,
                                selected = file.uri.toString() in selected,
                                onClick = { onItemClick(file) },
                                onLongClick = { onItemLongClick(file) },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    ) {
                        items(files, key = { it.uri.toString() }) { file ->
                            FileListRow(
                                file = file,
                                apkPath = apkPathFor(file),
                                selecting = selecting,
                                selected = file.uri.toString() in selected,
                                onClick = { onItemClick(file) },
                                onLongClick = { onItemLongClick(file) },
                            )
                        }
                    }
                }
            }
        }
    }

    // One confirmation, Ciyato's own. With all-files access Ciyato can delete these
    // directly, so there is no reason to route each through a system dialog - and
    // below Android 11 there is no system delete request to route through anyway.
    if (confirmDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CiyatoBgEl,
            title = {
                Text(
                    "Delete $count ${if (count == 1) "file" else "files"} permanently?",
                    color = CiyatoWhite,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    if (Build.VERSION.SDK_INT >= 30) {
                        "This can't be undone. To keep a way back, move them to Trash instead."
                    } else {
                        "This can't be undone."
                    },
                    color = CiyatoSec,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val targets = selectedFiles
                    scope.launch {
                        val failed = deleteDirectly(context, targets)
                        if (failed > 0) {
                            android.widget.Toast.makeText(
                                context,
                                "$failed couldn't be deleted.",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        selected = emptySet()
                        selectMode = false
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

/**
 * Marks [files] trashed directly, returning the ones Android refused.
 *
 * Permitted because Ciyato holds all-files access; without it, IS_TRASHED can only be
 * set on media the app itself created.
 */
private suspend fun trashDirectly(
    context: android.content.Context,
    files: List<MediaLibraryRepository.LibraryFile>,
): List<MediaLibraryRepository.LibraryFile> = withContext(Dispatchers.IO) {
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.IS_TRASHED, 1)
    }
    files.filterNot { file ->
        runCatching { context.contentResolver.update(file.uri, values, null, null) > 0 }.getOrDefault(false)
    }
}

/**
 * Deletes [files], returning how many could not be removed.
 *
 * MediaStore first, so the index and the file go together. If the store refuses -
 * some providers will not delete rows they did not create, even for an app with
 * all-files access - the file on disk is removed directly and the store told
 * afterwards, so no gallery keeps showing a picture that is gone.
 */
private suspend fun deleteDirectly(
    context: android.content.Context,
    files: List<MediaLibraryRepository.LibraryFile>,
): Int = withContext(Dispatchers.IO) {
    var failed = 0
    val rescan = mutableListOf<String>()
    files.forEach { file ->
        val viaStore = runCatching { context.contentResolver.delete(file.uri, null, null) > 0 }.getOrDefault(false)
        if (viaStore) return@forEach
        val onDisk = File(Environment.getExternalStorageDirectory(), file.relativePath + file.name)
        val removed = runCatching { onDisk.exists() && onDisk.delete() }.getOrDefault(false)
        if (removed) rescan += onDisk.path else failed += 1
    }
    if (rescan.isNotEmpty()) {
        android.media.MediaScannerConnection.scanFile(context, rescan.toTypedArray(), null, null)
    }
    failed
}

/** APKs need an on-disk path for their icon; everything else previews from its URI. */
private fun apkPathFor(file: MediaLibraryRepository.LibraryFile): File? =
    if (fileKindOf(file.name, file.mimeType) == FileKind.APK) {
        File(Environment.getExternalStorageDirectory(), file.relativePath + file.name)
    } else null

// ── header ───────────────────────────────────────────────────────────────────

@Composable
private fun CategoryHeader(
    title: String,
    subtitle: String,
    gridMode: Boolean,
    onToggleLayout: () -> Unit,
    /** Opens selection; null when there is nothing to select. */
    onSelect: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = CiyatoWhite)
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            // 20sp: the old bar's title was oversized for a screen whose content is
            // the point, and long names (WhatsApp) crowded the actions.
            Text(
                title,
                color = CiyatoWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = CiyatoMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (onSelect != null) {
            IconButton(onClick = onSelect) {
                Icon(Icons.Rounded.Checklist, contentDescription = "Select files", tint = CiyatoSec)
            }
        }
        IconButton(onClick = onToggleLayout) {
            Icon(
                if (gridMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                contentDescription = if (gridMode) "Show as list" else "Show as grid",
                tint = CiyatoSec,
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onTrash: (() -> Unit)?,
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
        IconButton(onClick = onShare) {
            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = CiyatoSec)
        }
        if (onTrash != null) {
            IconButton(onClick = onTrash) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Move to Trash", tint = CiyatoSec)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete permanently", tint = CiyatoRed)
        }
    }
}

// ── items ────────────────────────────────────────────────────────────────────

/** The selection state, drawn over a tile: a ring when selectable, a filled check when chosen. */
@Composable
private fun SelectionMark(selecting: Boolean, selected: Boolean) {
    if (!selecting) return
    Box(
        Modifier
            .fillMaxSize()
            .background(if (selected) Color.Black.copy(alpha = 0.35f) else Color.Transparent)
            .padding(8.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) CiyatoWhite else Color.Black.copy(alpha = 0.35f))
                .border(1.5.dp, CiyatoWhite, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = CiyatoBg, modifier = Modifier.size(15.dp))
        }
    }
}

private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(
    file: MediaLibraryRepository.LibraryFile,
    apkPath: File?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(CardShape)
            .background(CiyatoBgEl)
            .border(1.dp, if (selected) CiyatoWhite.copy(alpha = 0.7f) else CiyatoBorder, CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select"),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.82f)) {
            FilePreview(
                name = file.name,
                mimeType = file.mimeType,
                uri = file.uri,
                file = apkPath,
                modifier = Modifier.fillMaxSize(),
            )
            SelectionMark(selecting = selecting, selected = selected)
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                file.name,
                color = CiyatoWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(MediaLibraryRepository.formatBytes(file.sizeBytes), color = CiyatoMuted, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListRow(
    file: MediaLibraryRepository.LibraryFile,
    apkPath: File?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.medium)
            .background(if (selected) Color(0xFF1C2027) else CiyatoBgEl)
            .border(1.dp, if (selected) CiyatoWhite.copy(alpha = 0.6f) else CiyatoBorder, CiyatoShapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
            FilePreview(
                name = file.name,
                mimeType = file.mimeType,
                uri = file.uri,
                file = apkPath,
                compact = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                color = CiyatoWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${MediaLibraryRepository.formatBytes(file.sizeBytes)} · " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(file.modifiedAtMs)),
                color = CiyatoMuted,
                fontSize = 12.sp,
            )
        }
        if (selecting) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) CiyatoWhite else Color.Transparent)
                    .border(1.5.dp, if (selected) CiyatoWhite else CiyatoMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = CiyatoBg, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun EmptyCategory(title: String, needsAllFiles: Boolean) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        if (needsAllFiles) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$title needs All-files access", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android hides non-media files from apps by default. Grant \"All files access\" to " +
                        "browse documents, downloads, and APKs. Ciyato never uploads anything.",
                    color = CiyatoMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Open settings",
                    color = CiyatoBg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(CiyatoShapes.full)
                        .background(CiyatoWhite)
                        .clickable(role = Role.Button) {
                            // FileAccess.allFilesSettingsIntent resolves the per-app screen
                            // first and falls back to the global list on OEM builds that
                            // lack it; building the Intent here bypassed both.
                            val intent = FileAccess.allFilesSettingsIntent(context)
                            if (intent == null) {
                                android.widget.Toast.makeText(
                                    context,
                                    "This phone has no all-files access screen.",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                openSystemScreen(
                                    context,
                                    intent,
                                    "look under Settings > Apps > Special app access > All files access",
                                )
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        } else {
            Text("Nothing here yet", color = CiyatoMuted)
        }
    }
}
