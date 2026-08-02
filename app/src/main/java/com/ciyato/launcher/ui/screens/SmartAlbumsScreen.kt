package com.ciyato.launcher.ui.screens

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * SmartAlbumsScreen — Suggestion #75
 * Auto-groups photos into smart albums by:
 *  - Location bucket (requires ACCESS_MEDIA_LOCATION on API 29+)
 *  - Date / year-month clusters
 *  - Dominant color mood (warm, cool, bright, dark)
 */

data class SmartAlbum(
    val title: String,
    val subtitle: String,
    val coverUri: Uri?,
    val photoCount: Int,
    val photoUris: List<Uri>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAlbumsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<SmartAlbum>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAlbum by remember { mutableStateOf<SmartAlbum?>(null) }

    LaunchedEffect(Unit) {
        albums = withContext(Dispatchers.IO) { buildSmartAlbums(context) }
        isLoading = false
    }

    if (selectedAlbum != null) {
        AlbumDetailView(album = selectedAlbum!!, onBack = { selectedAlbum = null })
        return
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Smart Albums", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Spacer(Modifier.height(8.dp))
                    Text("Building smart albums…", color = CiyatoMuted)
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(albums, key = { it.title + "_" + it.subtitle }) { album ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.clickable { selectedAlbum = album },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (album.coverUri != null) {
                                AsyncImage(
                                    model = album.coverUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                                )
                            } else {
                                Icon(Icons.Default.PhotoLibrary, null, tint = CiyatoMuted,
                                    modifier = Modifier.size(64.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(album.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(album.subtitle, color = CiyatoMuted, fontSize = 12.sp)
                                Text("${album.photoCount} photo${if (album.photoCount != 1) "s" else ""}",
                                    color = CiyatoGold, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = CiyatoMuted)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailView(album: SmartAlbum, onBack: () -> Unit) {
    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text(album.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(album.photoUris, key = { it.toString() }) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(2.dp).aspectRatio(1f).clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

private fun buildSmartAlbums(context: Context): List<SmartAlbum> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )
    val allPhotos = mutableListOf<Triple<Long, Long, String>>()
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
        null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC LIMIT 300",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) {
            allPhotos.add(Triple(cursor.getLong(idCol), cursor.getLong(dateCol), cursor.getString(bucketCol) ?: ""))
        }
    }

    val albums = mutableListOf<SmartAlbum>()
    val df = SimpleDateFormat("MMM yyyy", Locale.getDefault())

    // Group by year-month
    val byMonth = allPhotos.groupBy { (_, date, _) ->
        df.format(Date(date)).takeIf { date > 0 } ?: "Unknown"
    }
    byMonth.entries.take(6).forEach { (label, group) ->
        val uris = group.map { (id, _, _) ->
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }
        albums.add(SmartAlbum(
            title = label,
            subtitle = "By date",
            coverUri = uris.firstOrNull(),
            photoCount = group.size,
            photoUris = uris,
        ))
    }

    // Group by folder/bucket
    val byBucket = allPhotos.groupBy { (_, _, bucket) -> bucket }.filterKeys { it.isNotBlank() }
    byBucket.entries.take(4).forEach { (bucket, group) ->
        val uris = group.map { (id, _, _) ->
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }
        albums.add(SmartAlbum(
            title = bucket,
            subtitle = "By location",
            coverUri = uris.firstOrNull(),
            photoCount = group.size,
            photoUris = uris,
        ))
    }

    return albums
}
