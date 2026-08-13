package com.ciyato.launcher.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.data.MediaLibraryRepository.CategoryKey
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoShapes
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import java.text.DateFormat
import java.util.Date

/**
 * Flat file list for one library category (Screenshots, Documents, Downloads…).
 * Tapping a row opens the file with the system's default app.
 */
@Composable
fun FileCategoryScreen(
    categoryKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { MediaLibraryRepository(context) }
    val key = remember(categoryKey) {
        runCatching { CategoryKey.valueOf(categoryKey) }.getOrNull()
    }
    var files by remember { mutableStateOf<List<MediaLibraryRepository.LibraryFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasAllFilesAccess by remember { mutableStateOf(repo.hasAllFilesAccess()) }

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

    LaunchedEffect(key, hasAllFilesAccess) {
        if (key != null) files = repo.filesForCategory(key)
        isLoading = false
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

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = title,
                subtitle = "${files.size} files",
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = CiyatoMuted)
            }
            files.isEmpty() -> {
                val needsAllFiles = key in setOf(CategoryKey.DOCUMENTS, CategoryKey.DOWNLOADS, CategoryKey.APKS) &&
                    android.os.Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (needsAllFiles) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$title needs All-files access",
                                color = CiyatoWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Android hides non-media files from apps by default. Grant " +
                                    "\"All files access\" to browse documents, downloads, and APKs. " +
                                    "Ciyato never uploads anything.",
                                color = CiyatoMuted,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Open settings",
                                color = CiyatoBg,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clip(CiyatoShapes.full)
                                    .background(CiyatoGold)
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}"),
                                                ),
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
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    FileRow(file = file, onOpen = {
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(file.uri, file.mimeType)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        // Tell the user when nothing on the phone can open the
                        // file type, instead of the tap silently doing nothing.
                        if (viewIntent.resolveActivity(context.packageManager) != null) {
                            runCatching { context.startActivity(viewIntent) }
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "No app can open this file type",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: MediaLibraryRepository.LibraryFile, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.medium)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isImage = file.mimeType.startsWith("image/")
        val isVideo = file.mimeType.startsWith("video/")
        if (isImage || isVideo) {
            AsyncImage(
                model = file.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CiyatoShapes.small)
                    .background(CiyatoBg),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CiyatoShapes.small)
                    .background(CiyatoBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        isVideo -> Icons.Default.Movie
                        isImage -> Icons.Default.Image
                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = CiyatoSec,
                    modifier = Modifier.size(20.dp),
                )
            }
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
    }
}
