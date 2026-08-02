package com.ciyato.launcher.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBgEl2
import com.ciyato.launcher.ui.theme.CiyatoBlue
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoGreen
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoPurple
import com.ciyato.launcher.ui.theme.CiyatoRed
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.data.CleanupAnalysisResult
import com.ciyato.launcher.data.CleanupFileRef
import com.ciyato.launcher.data.DuplicateCleanupGroup
import com.ciyato.launcher.data.FileCleanupResultStore
import com.ciyato.launcher.data.FileCleanupWorker
import com.ciyato.launcher.data.FileSearchIndexEntry
import com.ciyato.launcher.data.plannedDuplicateDeletions
import com.ciyato.launcher.viewmodel.LauncherViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private const val FILE_SCAN_LIMIT = 2_000
private const val LARGE_FILE_THRESHOLD_BYTES = 100L * 1024L * 1024L

private data class AccessibleFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

private data class FileScopeScan(
    val rootName: String,
    val files: List<AccessibleFile>,
    val inspectedCount: Int,
    val reachedLimit: Boolean,
) {
    val totalBytes: Long get() = files.sumOf(AccessibleFile::sizeBytes)
}

private data class FilesCategory(
    val label: String,
    val count: Int,
    val icon: ImageVector,
    val color: Color,
)

/**
 * Files Home is intentionally limited to the SAF folder selected by the user.
 * The browser remains a separate, familiar layer for direct navigation.
 */
@Composable
fun FilesScreen(viewModel: LauncherViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val storedRoot by viewModel.filesRootUri.collectAsState()
    val rootUri = remember(storedRoot) { storedRoot.takeIf(String::isNotBlank)?.let(Uri::parse) }
    var scan by remember { mutableStateOf<FileScopeScan?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    var cleanupResult by remember(rootUri) {
        mutableStateOf(rootUri?.let { uri -> FileCleanupResultStore.loadResult(context, uri.toString()) })
    }
    var cleanupWorkId by remember(rootUri) { mutableStateOf<UUID?>(null) }
    var cleanupProgress by remember(rootUri) { mutableStateOf(0 to 0) }
    var cleanupError by remember(rootUri) { mutableStateOf<String?>(null) }
    var cleanupNotice by remember(rootUri) { mutableStateOf<String?>(null) }
    var showDuplicateReview by remember(rootUri, cleanupResult?.completedAt) { mutableStateOf(false) }

    LaunchedEffect(cleanupWorkId) {
        val workId = cleanupWorkId ?: return@LaunchedEffect
        while (true) {
            val info = withContext(Dispatchers.IO) {
                WorkManager.getInstance(context).getWorkInfoById(workId).get()
            }
            cleanupProgress = info.progress.getInt(FileCleanupWorker.PROGRESS_HASHED, 0) to
                info.progress.getInt(FileCleanupWorker.PROGRESS_TOTAL, 0)
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    cleanupResult = rootUri?.let { uri -> FileCleanupResultStore.loadResult(context, uri.toString()) }
                    cleanupWorkId = null
                    break
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    cleanupError = info.outputData.getString(FileCleanupWorker.RESULT_ERROR)
                        ?: "Duplicate analysis could not finish. No files were changed."
                    cleanupWorkId = null
                    break
                }
                else -> delay(350)
            }
        }
    }

    LaunchedEffect(rootUri, refreshNonce) {
        scan = null
        scanError = null
        if (rootUri == null) {
            isScanning = false
        } else {
            isScanning = true
            val result = runCatching { scanAuthorisedFolder(context, rootUri) }
            scan = result.getOrNull()
            result.getOrNull()?.let { scopedFiles ->
                viewModel.updateFileSearchIndex(
                    rootUri = rootUri.toString(),
                    entries = scopedFiles.files.map { file ->
                        FileSearchIndexEntry(
                            uri = file.uri.toString(),
                            name = file.name,
                            mimeType = file.mimeType,
                            modifiedAt = file.modifiedAt,
                            sizeBytes = file.sizeBytes,
                        )
                    },
                    reachedLimit = scopedFiles.reachedLimit,
                )
            }
            scanError = result.exceptionOrNull()?.let { "Ciyato could not read this folder. Choose it again in Files Browser." }
            isScanning = false
        }
    }

    if (showBrowser) {
        FileCollectionDetailScreen(
            collectionTitle = "Files Browser",
            collectionIcon = Icons.Default.FolderOpen,
            collectionColor = CiyatoGold,
            initialFolderUri = rootUri,
            onFolderSelected = { uri -> viewModel.setFilesRootUri(uri.toString()) },
            onForgetFolder = viewModel::clearFilesRootUri,
            onBack = { showBrowser = false },
        )
        return
    }

    FilesHomeContent(
        rootUri = rootUri,
        scan = scan,
        isScanning = isScanning,
        scanError = scanError,
        cleanupResult = cleanupResult,
        cleanupProgress = cleanupProgress,
        cleanupError = cleanupError,
        cleanupNotice = cleanupNotice,
        isCleanupScanning = cleanupWorkId != null,
        onBack = onBack,
        onOpenBrowser = { showBrowser = true },
        onRefresh = {
            if (rootUri != null) refreshNonce += 1
        },
        onScanDuplicates = {
            rootUri?.let { uri ->
                cleanupError = null
                cleanupNotice = null
                cleanupProgress = 0 to 0
                cleanupWorkId = FileCleanupWorker.enqueue(context, uri).id
            }
        },
        onReviewDuplicates = { showDuplicateReview = true },
    )

    val resultForReview = cleanupResult
    if (showDuplicateReview && rootUri != null && resultForReview != null) {
        DuplicateCleanupReviewDialog(
            result = resultForReview,
            onDismiss = { showDuplicateReview = false },
            onDeletionFinished = { deletion ->
                showDuplicateReview = false
                FileCleanupResultStore.clearResult(context, rootUri.toString())
                cleanupResult = null
                refreshNonce += 1
                cleanupNotice = when {
                    deletion.deleted.isEmpty() -> "No files were removed. Check folder access and try again."
                    deletion.failed.isEmpty() -> "Removed ${deletion.deleted.size} selected duplicate ${if (deletion.deleted.size == 1) "copy" else "copies"}. Run another scan to verify the folder."
                    else -> "Removed ${deletion.deleted.size} selected ${if (deletion.deleted.size == 1) "copy" else "copies"}; ${deletion.failed.size} could not be removed because Android no longer allowed it."
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesHomeContent(
    rootUri: Uri?,
    scan: FileScopeScan?,
    isScanning: Boolean,
    scanError: String?,
    cleanupResult: CleanupAnalysisResult?,
    cleanupProgress: Pair<Int, Int>,
    cleanupError: String?,
    cleanupNotice: String?,
    isCleanupScanning: Boolean,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onRefresh: () -> Unit,
    onScanDuplicates: () -> Unit,
    onReviewDuplicates: () -> Unit,
) {
    val categories = remember(scan) { scan?.let(::buildCategories).orEmpty() }
    val recentFiles = remember(scan) { scan?.files?.sortedByDescending(AccessibleFile::modifiedAt)?.take(6).orEmpty() }
    val largeFiles = remember(scan) {
        scan?.files?.filter { it.sizeBytes >= LARGE_FILE_THRESHOLD_BYTES }
            ?.sortedByDescending(AccessibleFile::sizeBytes)
            ?.take(3)
            .orEmpty()
    }

    androidx.compose.material3.Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Files", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CiyatoSec)
                    }
                },
                actions = {
                    if (rootUri != null) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh selected folder", tint = CiyatoSec)
                        }
                    }
                    IconButton(onClick = onOpenBrowser) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open Files Browser", tint = CiyatoSec)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                rootUri == null -> {
                    item { FilesAccessState(onOpenBrowser = onOpenBrowser) }
                }

                isScanning -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = CiyatoGold)
                                Text("Scanning only your selected folder", color = CiyatoSec, fontSize = 13.sp)
                            }
                        }
                    }
                }

                scanError != null || scan == null -> {
                    item { FilesErrorState(message = scanError ?: "No accessible folder data is available.", onOpenBrowser = onOpenBrowser) }
                }

                else -> {
                    item { FilesScopeCard(scan = scan, onOpenBrowser = onOpenBrowser) }

                    if (categories.isNotEmpty()) {
                        item { Text("Categories", color = CiyatoWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                        items(categories, key = { it.label }) { category ->
                            FilesCategoryRow(category = category, onOpenBrowser = onOpenBrowser)
                        }
                    }

                    item { Text("Recent files", color = CiyatoWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    if (recentFiles.isEmpty()) {
                        item { TruthfulEmptyState("No accessible files were found in this selected folder.") }
                    } else {
                        items(recentFiles, key = { it.uri.toString() }) { file -> FilesRecentRow(file) }
                    }

                    item { Text("Cleanup review", color = CiyatoWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    item {
                        CleanupReviewCard(
                            largeFiles = largeFiles,
                            reachedLimit = scan.reachedLimit,
                            cleanupResult = cleanupResult,
                            cleanupProgress = cleanupProgress,
                            cleanupError = cleanupError,
                            cleanupNotice = cleanupNotice,
                            isCleanupScanning = isCleanupScanning,
                            onScanDuplicates = onScanDuplicates,
                            onOpenBrowser = onOpenBrowser,
                            onReviewDuplicates = onReviewDuplicates,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesAccessState(onOpenBrowser: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBrowser),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(28.dp))
            Text("Choose a folder to analyse", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Ciyato uses Android's Storage Access Framework. It sees only the folder you select and never bypasses protected storage.",
                color = CiyatoSec,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text("Open Files Browser", color = CiyatoGold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FilesErrorState(message: String, onOpenBrowser: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBrowser),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Folder access needs attention", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = CiyatoSec, fontSize = 13.sp, lineHeight = 19.sp)
            Text("Choose folder again", color = CiyatoGold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FilesScopeCard(scan: FileScopeScan, onOpenBrowser: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBrowser),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(scan.rootName, color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                    Text("Selected folder only", color = CiyatoMuted, fontSize = 12.sp)
                }
                Text(formatScopeBytes(scan.totalBytes), color = CiyatoGold, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${scan.files.size} accessible files scanned${if (scan.reachedLimit) " (first $FILE_SCAN_LIMIT entries)" else ""}. Values reflect this selected scope, not device-wide storage.",
                color = CiyatoSec,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun FilesCategoryRow(category: FilesCategory, onOpenBrowser: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CiyatoBgEl)
            .clickable(onClick = onOpenBrowser)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(category.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(20.dp))
        }
        Text(category.label, color = CiyatoWhite, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${category.count}", color = CiyatoSec, fontSize = 13.sp)
    }
}

@Composable
private fun FilesRecentRow(file: AccessibleFile) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CiyatoBgEl)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(fileIcon(file), contentDescription = null, tint = fileColor(file), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, color = CiyatoWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(dateFormat.format(Date(file.modifiedAt)), color = CiyatoMuted, fontSize = 11.sp)
        }
        Text(formatScopeBytes(file.sizeBytes), color = CiyatoSec, fontSize = 11.sp)
    }
}

@Composable
private fun CleanupReviewCard(
    largeFiles: List<AccessibleFile>,
    reachedLimit: Boolean,
    cleanupResult: CleanupAnalysisResult?,
    cleanupProgress: Pair<Int, Int>,
    cleanupError: String?,
    cleanupNotice: String?,
    isCleanupScanning: Boolean,
    onScanDuplicates: () -> Unit,
    onOpenBrowser: () -> Unit,
    onReviewDuplicates: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBrowser),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Review before deleting", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
            when {
                isCleanupScanning -> {
                    val (hashed, total) = cleanupProgress
                    CircularProgressIndicator(color = CiyatoGold, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        if (total > 0) "Verifying duplicate candidates: $hashed of $total. You can leave this screen; no file will be deleted."
                        else "Preparing duplicate candidates in the selected folder. No file will be deleted.",
                        color = CiyatoSec,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
                cleanupNotice != null -> Text(cleanupNotice, color = CiyatoGreen, fontSize = 13.sp, lineHeight = 19.sp)
                cleanupError != null -> Text(cleanupError, color = CiyatoRed, fontSize = 13.sp, lineHeight = 19.sp)
                cleanupResult != null && cleanupResult.groups.isNotEmpty() -> {
                    Text(
                        "${cleanupResult.groups.size} verified duplicate group${if (cleanupResult.groups.size == 1) "" else "s"} found. Up to ${formatScopeBytes(cleanupResult.reclaimableBytes)} can be reclaimed after you inspect individual files.",
                        color = CiyatoSec,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    cleanupResult.groups.take(3).forEach { group ->
                        Text(
                            "${group.files.size} matching files · ${formatScopeBytes(group.bytesPerFile)} each",
                            color = CiyatoMuted,
                            fontSize = 12.sp,
                        )
                    }
                    if (cleanupResult.wasBounded) {
                        Text("Analysis was capped for battery and storage safety.", color = CiyatoMuted, fontSize = 12.sp)
                    }
                }
                cleanupResult != null -> Text(
                    "No verified duplicates were found among ${cleanupResult.hashedFiles} same-size candidates. ${if (cleanupResult.wasBounded) "The analysis was capped for safety." else "No files were changed."}",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                largeFiles.isNotEmpty() -> {
                    Text(
                        "${largeFiles.size} large accessible file${if (largeFiles.size == 1) "" else "s"} over ${formatScopeBytes(LARGE_FILE_THRESHOLD_BYTES)}. Open Files Browser to inspect and delete only items Android permits.",
                        color = CiyatoSec,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    largeFiles.forEach { file ->
                        Text("${file.name} · ${formatScopeBytes(file.sizeBytes)}", color = CiyatoMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                reachedLimit -> Text(
                    "The selected folder is large, so the summary is intentionally bounded. Open Files Browser to review content directly.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                else -> Text(
                    "No large-file candidates were found in this selected folder. Ciyato has not run duplicate detection or deleted anything.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            Button(
                onClick = onScanDuplicates,
                enabled = !isCleanupScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CiyatoGold,
                    contentColor = CiyatoBg,
                    disabledContainerColor = CiyatoBgEl2,
                    disabledContentColor = CiyatoMuted,
                ),
            ) {
                Text(if (cleanupResult == null) "Scan duplicate candidates" else "Scan again")
            }
            if (cleanupResult?.groups?.isNotEmpty() == true) {
                Text(
                    "Choose exactly which copy to keep before Ciyato asks Android to remove any selected duplicate.",
                    color = CiyatoMuted,
                    fontSize = 12.sp,
                )
                TextButton(onClick = onReviewDuplicates) {
                    Text("Review verified duplicates", color = CiyatoGold)
                }
            }
        }
    }
}

private data class DuplicateDeletionResult(
    val deleted: List<CleanupFileRef>,
    val failed: List<CleanupFileRef>,
)

@Composable
private fun DuplicateCleanupReviewDialog(
    result: CleanupAnalysisResult,
    onDismiss: () -> Unit,
    onDeletionFinished: (DuplicateDeletionResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keptUris by remember(result.completedAt) {
        mutableStateOf(result.groups.mapIndexedNotNull { index, group ->
            group.files.firstOrNull()?.uri?.let { index to it }
        }.toMap())
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf<String?>(null) }
    val deletionTargets = remember(result.groups, keptUris) {
        plannedDuplicateDeletions(result.groups, keptUris)
    }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = CiyatoBgEl,
        title = { Text("Review verified duplicates", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Each group has matching SHA-256 content. Select one copy to keep in every group. Ciyato will ask Android to delete only the remaining copies you confirm.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                result.groups.forEachIndexed { groupIndex, group ->
                    DuplicateGroupReviewCard(
                        groupIndex = groupIndex,
                        group = group,
                        keptUri = keptUris[groupIndex],
                        onKeep = { uri -> keptUris = keptUris + (groupIndex to uri) },
                    )
                }
                deletionError?.let { Text(it, color = CiyatoRed, fontSize = 12.sp, lineHeight = 18.sp) }
                Text(
                    "${deletionTargets.size} selected duplicate ${if (deletionTargets.size == 1) "copy" else "copies"} can be removed after confirmation.",
                    color = CiyatoMuted,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { showDeleteConfirmation = true },
                enabled = deletionTargets.isNotEmpty() && !isDeleting,
            ) {
                Text(if (isDeleting) "Removing..." else "Delete selected copies", color = CiyatoRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel", color = CiyatoSec) }
        },
    )

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmation = false },
            containerColor = CiyatoBgEl,
            title = { Text("Delete selected copies?", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Android will be asked to permanently delete ${deletionTargets.size} selected duplicate ${if (deletionTargets.size == 1) "copy" else "copies"}. This cannot be undone.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            val outcome = deleteReviewedDuplicates(context, deletionTargets)
                            isDeleting = false
                            showDeleteConfirmation = false
                            if (outcome.deleted.isNotEmpty()) {
                                onDeletionFinished(outcome)
                            } else {
                                deletionError = "Android could not remove the selected files. Their folder access may have changed."
                            }
                        }
                    },
                    enabled = !isDeleting,
                ) { Text("Delete permanently", color = CiyatoRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }, enabled = !isDeleting) {
                    Text("Keep files", color = CiyatoSec)
                }
            },
        )
    }
}

@Composable
private fun DuplicateGroupReviewCard(
    groupIndex: Int,
    group: DuplicateCleanupGroup,
    keptUri: String?,
    onKeep: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl2),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Duplicate group ${groupIndex + 1}: ${group.files.size} copies, ${formatScopeBytes(group.bytesPerFile)} each",
                color = CiyatoWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            group.files.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onKeep(file.uri) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = keptUri == file.uri, onClick = { onKeep(file.uri) })
                    Column(Modifier.weight(1f)) {
                        Text(file.name, color = CiyatoSec, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (keptUri == file.uri) "Keep this copy" else "Selected for deletion", color = CiyatoMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private suspend fun deleteReviewedDuplicates(
    context: Context,
    files: List<CleanupFileRef>,
): DuplicateDeletionResult = withContext(Dispatchers.IO) {
    val deleted = mutableListOf<CleanupFileRef>()
    val failed = mutableListOf<CleanupFileRef>()
    files.distinctBy(CleanupFileRef::uri).forEach { file ->
        val didDelete = runCatching {
            DocumentFile.fromSingleUri(context, Uri.parse(file.uri))
                ?.takeIf(DocumentFile::canWrite)
                ?.delete() == true
        }.getOrDefault(false)
        if (didDelete) deleted += file else failed += file
    }
    DuplicateDeletionResult(deleted = deleted, failed = failed)
}

@Composable
private fun TruthfulEmptyState(message: String) {
    Text(message, color = CiyatoMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
}

private fun buildCategories(scan: FileScopeScan): List<FilesCategory> {
    fun count(predicate: (AccessibleFile) -> Boolean) = scan.files.count(predicate)
    return listOfNotNull(
        FilesCategory("Screenshots", count { "screenshot" in it.name.lowercase() }, Icons.Default.Screenshot, CiyatoPurple).takeIf { it.count > 0 },
        FilesCategory("Documents", count { isDocument(it) }, Icons.Default.Article, CiyatoBlue).takeIf { it.count > 0 },
        FilesCategory("Photos", count { it.mimeType.startsWith("image/") && "screenshot" !in it.name.lowercase() }, Icons.Default.Image, CiyatoGreen).takeIf { it.count > 0 },
        FilesCategory("Videos", count { it.mimeType.startsWith("video/") }, Icons.Default.Movie, CiyatoRed).takeIf { it.count > 0 },
        FilesCategory("APKs", count { it.mimeType == "application/vnd.android.package-archive" || it.name.endsWith(".apk", true) }, Icons.Default.InsertDriveFile, CiyatoGold).takeIf { it.count > 0 },
        FilesCategory("Other files", count { !isDocument(it) && !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("video/") && !it.name.endsWith(".apk", true) }, Icons.Default.InsertDriveFile, CiyatoSec).takeIf { it.count > 0 },
    )
}

private fun fileIcon(file: AccessibleFile): ImageVector = when {
    file.mimeType.startsWith("image/") -> Icons.Default.Image
    file.mimeType.startsWith("video/") -> Icons.Default.Movie
    file.name.endsWith(".apk", true) -> Icons.Default.InsertDriveFile
    isDocument(file) -> Icons.Default.Article
    else -> Icons.Default.InsertDriveFile
}

private fun fileColor(file: AccessibleFile): Color = when {
    file.mimeType.startsWith("image/") -> CiyatoGreen
    file.mimeType.startsWith("video/") -> CiyatoRed
    file.name.endsWith(".apk", true) -> CiyatoGold
    isDocument(file) -> CiyatoBlue
    else -> CiyatoSec
}

private fun isDocument(file: AccessibleFile): Boolean =
    file.mimeType.startsWith("application/") || file.mimeType.startsWith("text/") ||
        file.name.endsWith(".pdf", true) || file.name.endsWith(".doc", true) ||
        file.name.endsWith(".docx", true) || file.name.endsWith(".txt", true)

private suspend fun scanAuthorisedFolder(context: Context, treeUri: Uri): FileScopeScan = withContext(Dispatchers.IO) {
    val root = DocumentFile.fromTreeUri(context, treeUri)?.takeIf(DocumentFile::canRead)
        ?: throw IllegalStateException("Selected folder is no longer readable")
    val folders = ArrayDeque<DocumentFile>().apply { add(root) }
    val files = mutableListOf<AccessibleFile>()
    var inspected = 0

    while (folders.isNotEmpty() && inspected < FILE_SCAN_LIMIT) {
        val folder = folders.removeFirst()
        val children = runCatching { folder.listFiles().asList() }.getOrDefault(emptyList())
        children.forEach { document ->
            if (inspected >= FILE_SCAN_LIMIT) return@forEach
            inspected += 1
            when {
                document.isDirectory && document.canRead() -> folders.add(document)
                document.isFile && document.canRead() -> files += AccessibleFile(
                    uri = document.uri,
                    name = document.name.orEmpty().ifBlank { "Unnamed file" },
                    mimeType = document.type.orEmpty(),
                    sizeBytes = document.length().coerceAtLeast(0L),
                    modifiedAt = document.lastModified().coerceAtLeast(0L),
                )
            }
        }
    }

    FileScopeScan(
        rootName = root.name.orEmpty().ifBlank { "Selected folder" },
        files = files,
        inspectedCount = inspected,
        reachedLimit = folders.isNotEmpty() || inspected >= FILE_SCAN_LIMIT,
    )
}

private fun formatScopeBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024f)
    bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    else -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
}
