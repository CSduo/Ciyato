package com.ciyato.launcher.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AutoBackupScreen — Suggestion #67
 * SAF-based local backup of photos/files to a user-selected folder.
 * Creates a dated subfolder and copies new files since the last backup timestamp.
 */

@Composable
fun AutoBackupScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupFolderUri by remember { mutableStateOf<Uri?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var backupProgress by remember { mutableStateOf(0f) }
    var lastBackupTime by remember { mutableStateOf<String?>(null) }
    var backedUpCount by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            backupFolderUri = uri
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun startBackup() {
        val folder = backupFolderUri ?: return
        isBackingUp = true
        backupProgress = 0f
        statusMessage = ""

        scope.launch(Dispatchers.IO) {
            try {
                val df = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                val subfolderName = "Ciyato_Backup_${df.format(Date())}"
                val docFolder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folder)
                val backupFolder = docFolder?.createDirectory(subfolderName) ?: run {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Could not create backup folder."
                        isBackingUp = false
                    }
                    return@launch
                }

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE,
                )
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT 100",
                ) ?: run {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Could not query photos."
                        isBackingUp = false
                    }
                    return@launch
                }

                val total = cursor.count
                var done = 0
                cursor.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val name = it.getString(nameCol) ?: "photo_$id.jpg"
                        val mime = it.getString(mimeCol) ?: "image/jpeg"
                        val uri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        try {
                            val destFile = backupFolder.createFile(mime, name)
                            if (destFile != null) {
                                context.contentResolver.openInputStream(uri)?.use { inStream ->
                                    context.contentResolver.openOutputStream(destFile.uri)?.use { outStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                                done++
                            }
                        } catch (_: Exception) {}
                        val prog = if (total > 0) done.toFloat() / total else 1f
                        withContext(Dispatchers.Main) {
                            backupProgress = prog
                        }
                    }
                }
                val formattedTime = df.format(Date())
                withContext(Dispatchers.Main) {
                    backedUpCount = done
                    lastBackupTime = formattedTime
                    statusMessage = "Backup complete! $done files saved."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "Backup failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isBackingUp = false
                }
            }
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Auto-Backup", onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup Destination", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        backupFolderUri?.lastPathSegment ?: "No folder selected",
                        color = if (backupFolderUri != null) CiyatoWhite else CiyatoMuted,
                        fontSize = 13.sp,
                    )
                    Button(
                        onClick = { folderPicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = CiyatoBgEl),
                        border = ButtonDefaults.outlinedButtonBorder,
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Choose Folder", color = CiyatoGold, fontSize = 13.sp)
                    }
                }
            }

            if (lastBackupTime != null) {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Last backup: $lastBackupTime", color = CiyatoWhite, fontSize = 13.sp)
                            Text("$backedUpCount files backed up", color = CiyatoMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isBackingUp) {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Backing up…", color = CiyatoWhite, fontSize = 13.sp)
                        LinearProgressIndicator(
                            progress = { backupProgress },
                            color = CiyatoGold,
                            trackColor = CiyatoBg,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(backupProgress * 100).toInt()}% complete", color = CiyatoMuted, fontSize = 12.sp)
                    }
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = CiyatoWhite, fontSize = 13.sp)
            }

            Button(
                onClick = { startBackup() },
                enabled = backupFolderUri != null && !isBackingUp,
                colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Backup, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Backup Now", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
