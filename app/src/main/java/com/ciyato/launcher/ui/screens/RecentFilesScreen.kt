package com.ciyato.launcher.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecentFilesScreen — Suggestion #71
 * Quick-access list of the 50 most recently modified files across all media types.
 */

data class RecentFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateMs: Long,
    val sizeBytes: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFilesScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<RecentFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        files = loadRecentFiles(context)
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Recent Files",
                subtitle = "Recently modified device media",
                onBack = onBack
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CiyatoGold)
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text("${files.size} recent files", color = CiyatoMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp))
            }

            items(files, key = { "${it.mimeType}:${it.id}" }) { file ->
                RecentFileRow(
                    file = file,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(file.uri, file.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                )
            }
        }
    }
}

@Composable
private fun RecentFileRow(file: RecentFile, onClick: () -> Unit) {
    val df = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val (icon, tint) = mimeToIcon(file.mimeType)

    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(df.format(Date(file.dateMs)), color = CiyatoMuted, fontSize = 11.sp)
        }
        Text(formatBytes(file.sizeBytes), color = CiyatoSec, fontSize = 11.sp)
    }
    HorizontalDivider(color = CiyatoBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
}

private fun mimeToIcon(mime: String): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when {
    mime.startsWith("image") -> Icons.Default.Image to CiyatoGold.copy(alpha = 0.8f)
    mime.startsWith("video") -> Icons.Default.Videocam to androidx.compose.ui.graphics.Color(0xFF7DB7FF)
    mime.startsWith("audio") -> Icons.Default.AudioFile to androidx.compose.ui.graphics.Color(0xFF39C66A)
    mime.contains("pdf") -> Icons.Default.PictureAsPdf to androidx.compose.ui.graphics.Color(0xFFEF4444)
    else -> Icons.Default.InsertDriveFile to CiyatoSec
}

private fun loadRecentFiles(context: Context): List<RecentFile> {
    val results = mutableListOf<RecentFile>()
    val collections = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "image/*",
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video/*",
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "audio/*",
    )
    val proj = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.MIME_TYPE,
    )

    collections.forEach { (uri, fallbackMime) ->
        try {
            context.contentResolver.query(uri, proj, null, null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                    val dateS = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: fallbackMime else fallbackMime
                    results.add(RecentFile(id, ContentUris.withAppendedId(uri, id), name ?: "", mime, dateS * 1000, size))
                    count++
                }
            }
        } catch (_: Exception) {}
    }
    return results.sortedByDescending { it.dateMs }.take(50)
}
