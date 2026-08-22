package com.ciyato.launcher.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ciyato.launcher.data.FileAccess
import com.ciyato.launcher.data.FileTagStore
import com.ciyato.launcher.data.LauncherSettingsRepository
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.res.pluralStringResource
import com.ciyato.launcher.R

/**
 * RecentFilesScreen — real, on-device recent-file browser with per-file tagging.
 *
 * Replaces the two dead-code screens (RecentFilesScreen, FileTaggingScreen) with
 * one screen: every row comes from a live MediaStore.Files query sorted by
 * DATE_MODIFIED (via [MediaLibraryRepository.recentFiles], reused as-is — it
 * already runs off the main thread and closes its cursor). Tags are typed by
 * the person, attached to the file's content URI, and persisted through
 * [LauncherSettingsRepository] (DataStore) via [FileTagStore] so they survive
 * an app restart — nothing here is in-memory-only. Opening/sharing a file
 * always goes through the system chooser and never crashes when nothing on
 * the device can handle it.
 */
private const val RECENT_FILES_LIMIT = 200

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFilesScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mediaRepo = remember { MediaLibraryRepository(context) }
    val settingsRepo = remember { LauncherSettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(mediaRepo.hasMediaPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermission = mediaRepo.hasMediaPermission() }

    // Granting from system Settings after a permanent denial is the only path
    // left; re-check on resume so the screen updates without a restart.
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

    var isLoading by remember { mutableStateOf(true) }
    var files by remember { mutableStateOf<List<MediaLibraryRepository.LibraryFile>>(emptyList()) }

    LaunchedEffect(hasPermission) {
        isLoading = true
        files = if (hasPermission) mediaRepo.recentFiles(limit = RECENT_FILES_LIMIT) else emptyList()
        isLoading = false
    }

    val tagsJson by settingsRepo.fileTags.collectAsState(initial = "{}")
    val tagsByUri = remember(tagsJson) { FileTagStore.parse(tagsJson) }
    val allTags = remember(tagsJson) { FileTagStore.allTags(tagsJson) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var tagDialogFor by remember { mutableStateOf<MediaLibraryRepository.LibraryFile?>(null) }

    val entries = remember(files, tagsByUri, selectedTag) {
        files
            .map { file -> file to (tagsByUri[file.uri.toString()] ?: emptyList()) }
            .filter { (_, tags) -> selectedTag == null || tags.any { it.equals(selectedTag, ignoreCase = true) } }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Recent Files",
                subtitle = "Recently modified · tap to open, tag to organize",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!hasPermission) {
                RecentFilesPermissionCard(
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

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = CiyatoGold)
                        Text("Scanning recent files…", color = CiyatoMuted, style = bodyM)
                    }
                }
                hasPermission && files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CiyatoEmptyState(
                        icon = Icons.Default.FolderOff,
                        title = "No recent files",
                        subtitle = "Nothing on this device has been modified recently.",
                        modifier = Modifier.padding(32.dp),
                    )
                }
                hasPermission -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            if (files.size >= RECENT_FILES_LIMIT)
                                "Showing the $RECENT_FILES_LIMIT most recent files — this device may have more"
                            else
                                pluralStringResource(R.plurals.count_recent_files, files.size, files.size),
                            color = CiyatoMuted,
                            style = bodyS,
                        )
                    }
                    if (allTags.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    CiyatoToggleChip(
                                        text = "All",
                                        selected = selectedTag == null,
                                        onClick = { selectedTag = null },
                                    )
                                }
                                items(allTags) { tag ->
                                    CiyatoToggleChip(
                                        text = tag,
                                        selected = selectedTag == tag,
                                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                    )
                                }
                            }
                        }
                    }
                    items(entries, key = { (file, _) -> file.uri.toString() }) { (file, tags) ->
                        RecentFileCard(
                            file = file,
                            tags = tags,
                            onOpen = {
                                if (!launchRecentFileAction(context, file, Intent.ACTION_VIEW)) {
                                    Toast.makeText(context, "No app on this device can open this file type", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = {
                                if (!launchRecentFileAction(context, file, Intent.ACTION_SEND)) {
                                    Toast.makeText(context, "No app can share this file", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAddTag = { tagDialogFor = file },
                            onRemoveTag = { tag ->
                                scope.launch { settingsRepo.setFileTags(FileTagStore.removeTag(tagsJson, file.uri.toString(), tag)) }
                            },
                        )
                    }
                }
            }
        }
    }

    val dialogFile = tagDialogFor
    if (dialogFile != null) {
        AddTagDialog(
            fileName = dialogFile.name,
            onDismiss = { tagDialogFor = null },
            onConfirm = { tag ->
                scope.launch { settingsRepo.setFileTags(FileTagStore.addTag(tagsJson, dialogFile.uri.toString(), tag)) }
                tagDialogFor = null
            },
        )
    }
}

@Composable
private fun RecentFilesPermissionCard(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(CiyatoShapes.large).background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.FolderOpen, null, tint = CiyatoGold, modifier = Modifier.size(26.dp))
        Text("Media access needed", color = CiyatoWhite, style = headingM)
        Text(
            "Ciyato needs photo, video, and audio access to show your recently modified files across the device.",
            color = CiyatoMuted,
            style = bodyM,
        )
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
            Text("Grant Access", color = CiyatoBg, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecentFileCard(
    file: MediaLibraryRepository.LibraryFile,
    tags: List<String>,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isVisual = file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")
            if (isVisual) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CiyatoShapes.small).background(CiyatoBg),
                )
            } else {
                val (icon, tint) = recentFileIcon(file.mimeType)
                Box(
                    modifier = Modifier.size(44.dp).clip(CiyatoShapes.small).background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(file.name, color = CiyatoWhite, style = headingS, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MediaLibraryRepository.formatBytes(file.sizeBytes)} · ${dateFormat.format(java.util.Date(file.modifiedAtMs))}",
                    color = CiyatoMuted,
                    style = bodyS,
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Share, "Share", tint = CiyatoSec, modifier = Modifier.size(18.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                items(tags) { tag ->
                    AssistChip(
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag, style = labelM) },
                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = CiyatoGold.copy(alpha = 0.14f),
                            labelColor = CiyatoGold,
                            trailingIconContentColor = CiyatoGold,
                        ),
                        border = null,
                    )
                }
                item {
                    AssistChip(
                        onClick = onAddTag,
                        label = { Text("+ Tag", style = labelM) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.Transparent,
                            labelColor = CiyatoSec,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTagDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        title = { Text("Tag file", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(fileName, color = CiyatoMuted, style = bodyS, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                CiyatoInputField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "e.g. work, receipt, important",
                    onImeAction = { if (input.isNotBlank()) onConfirm(input) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (input.isNotBlank()) onConfirm(input) }) {
                Text("Add", color = CiyatoGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CiyatoMuted) }
        },
    )
}

private fun recentFileIcon(mimeType: String): Pair<ImageVector, Color> = when {
    mimeType.startsWith("image/") -> Icons.Default.Image to CiyatoBlue
    mimeType.startsWith("video/") -> Icons.Default.VideoFile to CiyatoPurple
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile to CiyatoGreen
    mimeType == "application/pdf" -> Icons.Default.PictureAsPdf to CiyatoRed
    mimeType.contains("spreadsheet") || mimeType.contains("excel") -> Icons.Default.TableChart to CiyatoGreen
    mimeType.contains("document") || mimeType.contains("word") -> Icons.Default.Description to CiyatoBlue
    mimeType.contains("zip") || mimeType.contains("archive") -> Icons.Default.Archive to CiyatoAmber
    mimeType == "application/vnd.android.package-archive" -> Icons.Default.Android to CiyatoGreen
    else -> Icons.Default.InsertDriveFile to CiyatoSec
}

/**
 * Hands [file] to the phone's own apps via [action] (ACTION_VIEW to open,
 * ACTION_SEND to share), always through a chooser. Returns false when
 * nothing on the device can handle it so the caller can say so instead of
 * failing silently.
 */
private fun launchRecentFileAction(context: Context, file: MediaLibraryRepository.LibraryFile, action: String): Boolean {
    // Delegates to the single external-handoff owner. That helper converts the
    // path through FileProvider and refuses outright when it can't, rather than
    // falling back to a raw file:// URI; it also honours the default app for
    // view/edit while keeping the chooser for share. Keeping a second copy of
    // that logic here is how the two drifted apart in the first place.
    return FileAccess.openExternally(context, file.uri, file.mimeType, action) == null
}
