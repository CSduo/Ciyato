package com.ciyato.launcher.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * ScreenshotCollectionScreen — Suggestion #63
 * Shows a grid of all screenshots from MediaStore, auto-filtered by RELATIVE_PATH.
 */

data class Screenshot(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateMs: Long,
    val sizeBytes: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotCollectionScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var screenshots by remember { mutableStateOf<List<Screenshot>>(emptyList()) }
    var selectedCount by remember { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            screenshots = loadScreenshots(context)
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = {
                    Text("Screenshots", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("📸", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Media Access Required", color = CiyatoWhite, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Ciyato needs media access to show your screenshots.",
                    color = CiyatoMuted, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permLauncher.launch(permission) },
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                ) { Text("Grant Access", color = Color.Black) }
            }
            return@Scaffold
        }

        if (screenshots.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📸", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No screenshots found", color = CiyatoWhite, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Screenshots will appear here automatically", color = CiyatoMuted)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${screenshots.size} screenshots", color = CiyatoMuted, fontSize = 13.sp)
                val totalMb = screenshots.sumOf { it.sizeBytes } / 1_048_576
                Text("${totalMb} MB", color = CiyatoMuted, fontSize = 13.sp)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(screenshots, key = { it.id }) { ss ->
                    Box(
                        modifier = Modifier.aspectRatio(0.56f).clip(RoundedCornerShape(6.dp))
                            .background(CiyatoBgEl).clickable {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(ss.uri, "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                            },
                    ) {
                        AsyncImage(
                            model = ss.uri,
                            contentDescription = "Screenshot ${ss.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private fun loadScreenshots(context: Context): List<Screenshot> {
    val screenshots = mutableListOf<Screenshot>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.RELATIVE_PATH,
    )

    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    } else null

    val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf("%Screenshot%")
    } else null

    context.contentResolver.query(
        collection, projection, selection, selectionArgs,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: continue
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                !name.contains("screenshot", ignoreCase = true)) continue

            val uri = ContentUris.withAppendedId(collection, id)
            screenshots.add(Screenshot(
                id = id,
                uri = uri,
                name = name,
                dateMs = cursor.getLong(dateCol) * 1000,
                sizeBytes = cursor.getLong(sizeCol),
            ))
        }
    }
    return screenshots
}
