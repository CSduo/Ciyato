package com.ciyato.launcher.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ciyato.launcher.data.FileAccess
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CiyatoFile(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val mimeType: String?,
    val lastModified: Long,
    val isDirectory: Boolean = false,
    val canDelete: Boolean = false,
    val document: DocumentFile? = null,
)

data class FileCollectionInfo(
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val mimeFilter: String?,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCollectionDetailScreen(
    collectionTitle: String,
    collectionIcon: ImageVector,
    collectionColor: Color,
    initialFolderUri: Uri? = null,
    onFolderSelected: (Uri) -> Unit = {},
    onForgetFolder: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFolderUri by remember(initialFolderUri) { mutableStateOf(initialFolderUri) }
    var files by remember { mutableStateOf<List<CiyatoFile>>(emptyList()) }
    // Starts true for the All-files path too, so the first frame is a spinner
    // rather than an empty state that flashes "no folder" before the scan.
    var isLoading by remember(initialFolderUri) {
        mutableStateOf(initialFolderUri != null || FileAccess.hasAllFiles(context))
    }
    var folderStack by remember(initialFolderUri) { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var pendingDeletion by remember { mutableStateOf<CiyatoFile?>(null) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var isClearingCache by remember { mutableStateOf(false) }
    var accessMessage by remember { mutableStateOf<String?>(null) }
    val currentFolderName = folderStack.lastOrNull()?.name ?: collectionTitle

    fun navigateUp() {
        val previousStack = folderStack.dropLast(1)
        if (previousStack.isEmpty()) {
            onBack()
            return
        }
        folderStack = previousStack
        isLoading = true
        scope.launch {
            files = loadFilesFromDocument(previousStack.last(), collectionTitle)
            isLoading = false
        }
    }

    // System back has to walk up the folder stack. Without this it pops the whole Files
    // destination, dropping the user on Home from three folders deep.
    BackHandler { navigateUp() }

    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { directorySize(context.cacheDir) }
    }

    LaunchedEffect(initialFolderUri) {
        if (initialFolderUri != null) {
            val root = DocumentFile.fromTreeUri(context, initialFolderUri)
            if (root != null && root.canRead()) {
                folderStack = listOf(root)
                files = loadFilesFromDocument(root, collectionTitle)
            } else {
                folderStack = emptyList()
                files = emptyList()
                accessMessage = "Folder access is no longer available. Choose the folder again to continue."
            }
            isLoading = false
        } else if (FileAccess.hasAllFiles(context)) {
            // No SAF folder, but Ciyato can read the disk directly. Offering
            // the folder picker here would be a dead end — that picker is
            // exactly what refuses to hand over internal storage. Seed from
            // the real root instead.
            //
            // DocumentFile.fromFile wraps a java.io.File behind the same
            // interface the rest of this screen already speaks, so browsing,
            // sorting and navigation work unchanged. Its getUri() returns a
            // file:// URI, which is why opening goes through
            // FileAccess.shareableUri below.
            isLoading = true
            val root = DocumentFile.fromFile(FileAccess.internalRoot())
            if (root.canRead()) {
                folderStack = listOf(root)
                files = loadFilesFromDocument(root, collectionTitle)
            } else {
                folderStack = emptyList()
                files = emptyList()
                accessMessage = "Ciyato could not read internal storage."
            }
            isLoading = false
        } else {
            folderStack = emptyList()
            files = emptyList()
            isLoading = false
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onFolderSelected(uri)
            selectedFolderUri = uri
            isLoading = true
            scope.launch {
                val root = DocumentFile.fromTreeUri(context, uri)
                if (root != null && root.canRead()) {
                    folderStack = listOf(root)
                    files = loadFilesFromDocument(root, collectionTitle)
                    accessMessage = null
                } else {
                    folderStack = emptyList()
                    files = emptyList()
                    accessMessage = "Ciyato could not read that folder. Choose another folder or check its Android permissions."
                }
                isLoading = false
            }
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(collectionColor.copy(alpha = 0.18f)),
                        ) {
                            Icon(collectionIcon, contentDescription = null, tint = collectionColor, modifier = Modifier.size(16.dp))
                        }
                        Text(collectionTitle, color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CiyatoSec)
                    }
                },
                actions = {
                    if (selectedFolderUri != null) {
                        IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Change folder", tint = CiyatoSec)
                        }
                        IconButton(onClick = {
                            selectedFolderUri = null
                            folderStack = emptyList()
                            files = emptyList()
                            onForgetFolder()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Forget folder", tint = CiyatoSec)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to CiyatoBgEl2,
                            0.15f to CiyatoBg,
                            1f to CiyatoBg,
                        )
                    )
                )
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    // Gate on whether a readable root was actually established,
                    // not on whether a SAF folder was picked.
                    //
                    // In All-files mode there is no selectedFolderUri by design —
                    // the root comes from the filesystem — so this branch used to
                    // hide a browser that had already loaded its data behind a
                    // "choose a folder" card, and the picker it offered is the one
                    // Android forbids from granting internal storage (F-090). The
                    // person was sent in a circle past working content.
                    selectedFolderUri == null && folderStack.isEmpty() && !isLoading -> {
                        item { GrantAccessCard(collectionTitle, collectionColor, onEnable = { folderPickerLauncher.launch(null) }) }
                        item { SafExplainerCard() }
                    }

                    isLoading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CircularProgressIndicator(color = CiyatoGold)
                                    Text("Reading folder contents…", color = CiyatoSec)
                                }
                            }
                        }
                    }

                    files.isEmpty() -> {
                        accessMessage?.let { message -> item { FileAccessNotice(message) } }
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No folders or files found in the selected folder.",
                                    color = CiyatoMuted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    else -> {
                        accessMessage?.let { message -> item { FileAccessNotice(message) } }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(currentFolderName, color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${files.count { it.isDirectory }} folders - ${files.count { !it.isDirectory }} files",
                                        color = CiyatoMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                Text("Folders first", color = CiyatoMuted, fontSize = 12.sp)
                            }
                        }

                        item {
                            FileMaintenanceCard(
                                cacheBytes = cacheBytes,
                                isClearing = isClearingCache,
                                onClearCache = {
                                    scope.launch {
                                        isClearingCache = true
                                        withContext(Dispatchers.IO) {
                                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                        }
                                        cacheBytes = 0L
                                        isClearingCache = false
                                    }
                                },
                            )
                        }

                        items(files) { file ->
                            FileRow(
                                file = file,
                                accentColor = collectionColor,
                                onTap = {
                                    if (file.isDirectory && file.document != null) {
                                        val nextFolder = file.document
                                        folderStack = folderStack + nextFolder
                                        isLoading = true
                                        scope.launch {
                                            files = loadFilesFromDocument(nextFolder, collectionTitle)
                                            isLoading = false
                                        }
                                    } else {
                                        // Browsing by real path yields file://
                                        // URIs; openExternally converts them and
                                        // refuses rather than leaking a raw path.
                                        FileAccess.openExternally(
                                            context, file.uri, file.mimeType,
                                        )?.let { reason ->
                                            android.widget.Toast.makeText(
                                                context, reason, android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                onDelete = if (file.canDelete) ({ pendingDeletion = file }) else null,
                            )
                        }
                    }
                }
            }
        }

        pendingDeletion?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingDeletion = null },
                containerColor = CiyatoBgEl,
                title = { Text("Delete ${if (file.isDirectory) "folder" else "file"}?", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "${file.name} will be permanently deleted from the folder you selected. This cannot be undone.",
                        color = CiyatoSec,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeletion = null }) { Text("Cancel", color = CiyatoSec) }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val activeFolder = folderStack.lastOrNull()
                        pendingDeletion = null
                        if (file.document != null && activeFolder != null) {
                            isLoading = true
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) {
                                    runCatching { file.document.delete() }.getOrDefault(false)
                                }
                                if (!deleted) {
                                    accessMessage = "Ciyato could not delete ${file.name}. It may be read-only or no longer available."
                                }
                                files = loadFilesFromDocument(activeFolder, collectionTitle)
                                isLoading = false
                            }
                        }
                    }) { Text("Delete", color = CiyatoRed) }
                },
            )
        }
    }
}

@Composable
private fun GrantAccessCard(title: String, color: Color, onEnable: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Enable $title Access", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Choose a folder you want Ciyato to browse. Ciyato only reads what you select and never deletes or modifies files automatically.",
            color = CiyatoSec,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Choose Folder", color = CiyatoBg, fontWeight = FontWeight.Bold)
        }
        Text(
            "Uses Android Storage Access Framework · No broad storage access",
            color = CiyatoMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SafExplainerCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(16.dp))
            Text("Privacy first", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Text(
            "Ciyato uses Android's built-in folder picker. You control exactly which folder is shared and can revoke that access from Android settings.",
            color = CiyatoMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun FileAccessNotice(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoGold.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
        Text(message, color = CiyatoSec, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun FileMaintenanceCard(
    cacheBytes: Long,
    isClearing: Boolean,
    onClearCache: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoDelete, contentDescription = null, tint = CiyatoSec, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Ciyato cache", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (cacheBytes > 0L) "${MediaLibraryRepository.formatBytes(cacheBytes)} stored by Ciyato" else "No Ciyato cache to clean",
                    color = CiyatoMuted,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onClearCache, enabled = cacheBytes > 0L && !isClearing) {
                Text(if (isClearing) "Cleaning" else "Clear", color = CiyatoGold)
            }
        }
        Text(
            "Android does not let Ciyato clear other apps' caches. Review and delete files you selected below instead.",
            color = CiyatoMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun FileRow(
    file: CiyatoFile,
    accentColor: Color,
    onTap: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.14f)),
        ) {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else fileIcon(file.mimeType),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.name, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(if (file.isDirectory) "Folder" else MediaLibraryRepository.formatBytes(file.sizeBytes), color = CiyatoMuted, fontSize = 11.sp)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${file.name}", tint = CiyatoMuted, modifier = Modifier.size(18.dp))
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CiyatoMuted, modifier = Modifier.size(18.dp))
    }
}

private fun fileIcon(mimeType: String?): ImageVector {
    return when {
        mimeType == null -> Icons.Default.InsertDriveFile
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        mimeType.contains("spreadsheet") || mimeType.contains("excel") -> Icons.Default.TableChart
        mimeType.contains("document") || mimeType.contains("word") -> Icons.Default.Description
        mimeType.contains("zip") || mimeType.contains("archive") -> Icons.Default.Archive
        mimeType == "application/vnd.android.package-archive" -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
    }
}

private fun directorySize(directory: java.io.File): Long {
    return directory.listFiles()?.sumOf { file ->
        if (file.isDirectory) directorySize(file) else file.length()
    } ?: 0L
}

private suspend fun loadFilesFromDocument(
    folder: DocumentFile,
    collectionTitle: String,
): List<CiyatoFile> {
    return withContext(Dispatchers.IO) {
        val files = mutableListOf<CiyatoFile>()
        try {
            folder.listFiles().forEach { doc ->
                if ((doc.isFile || doc.isDirectory) && doc.name != null) {
                    files.add(
                        CiyatoFile(
                            name = doc.name!!,
                            uri = doc.uri,
                            sizeBytes = if (doc.isDirectory) 0L else doc.length(),
                            mimeType = doc.type,
                            lastModified = doc.lastModified(),
                            isDirectory = doc.isDirectory,
                            canDelete = doc.canWrite(),
                            document = doc,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Silently return empty on access error
        }
        files
            .filter { it.isDirectory || matchesCollection(it, collectionTitle) }
            .sortedWith(
                compareByDescending<CiyatoFile> { it.isDirectory }
                    .thenByDescending { it.lastModified }
                    .thenBy { it.name.lowercase() }
            )
    }
}

private fun matchesCollection(file: CiyatoFile, title: String): Boolean {
    val name = file.name.lowercase()
    val mime = file.mimeType.orEmpty().lowercase()
    val isDocument = mime.contains("document") || mime.contains("word") ||
        mime.contains("spreadsheet") || mime.contains("excel") ||
        mime.contains("presentation") || mime == "application/pdf" ||
        name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".xls") ||
        name.endsWith(".xlsx") || name.endsWith(".ppt") || name.endsWith(".pptx")

    return when (title) {
        "Screenshots" -> name.contains("screenshot") || name.contains("screen_shot")
        "Documents" -> isDocument || mime.startsWith("text/")
        "Photos" -> mime.startsWith("image/")
        "WhatsApp", "WhatsApp Media" -> name.contains("whatsapp") || name.startsWith("wa")
        "Videos" -> mime.startsWith("video/")
        "APKs" -> mime == "application/vnd.android.package-archive" || name.endsWith(".apk")
        "Work Files" -> isDocument
        "Receipts" -> listOf("receipt", "invoice", "bill", "payment").any(name::contains)
        "PDFs" -> mime == "application/pdf" || name.endsWith(".pdf")
        "Contracts" -> listOf("contract", "agreement", "nda").any(name::contains)
        "Screen Recordings" -> mime.startsWith("video/") &&
            listOf("screen", "recording", "record").any(name::contains)
        "Design Assets" -> mime.startsWith("image/") ||
            listOf(".psd", ".ai", ".fig", ".sketch", ".xd").any(name::endsWith)
        "Travel" -> listOf("travel", "flight", "hotel", "ticket", "trip", "itinerary").any(name::contains)
        "College" -> listOf("college", "class", "lecture", "assignment", "semester", "notes").any(name::contains)
        "Recently Added" -> file.lastModified > 0L &&
            file.lastModified >= System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        else -> true
    }
}
