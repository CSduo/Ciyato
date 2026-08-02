package com.ciyato.launcher.ui.screens

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import coil.compose.AsyncImage
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PhotoAutoTaggerScreen — Suggestion #42
 * Auto-tags photos with AI-generated labels using heuristic color/brightness
 * analysis (production: replace with ML Kit image labeling).
 */

data class TaggedPhoto(
    val id: Long,
    val uri: Uri,
    val tags: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoAutoTaggerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var taggedPhotos by remember { mutableStateOf<List<TaggedPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val photos = loadRecentPhotoUris(context, limit = 30)
            taggedPhotos = photos.mapIndexed { i, (id, uri) ->
                withContext(Dispatchers.Main) { progress = (i + 1f) / photos.size }
                val tags = autoTagPhoto(context, uri)
                TaggedPhoto(id, uri, tags)
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("AI Photo Tags", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = CiyatoGold, progress = { progress })
                    Text("Tagging photos… ${(progress * 100).toInt()}%", color = CiyatoMuted)
                }
            }
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("${taggedPhotos.size} photos auto-tagged", color = CiyatoMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(taggedPhotos) { photo ->
                    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                        shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("AI Tags", color = CiyatoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    photo.tags.take(4).forEach { tag ->
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(tag, fontSize = 11.sp) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = CiyatoGold.copy(alpha = 0.15f),
                                                labelColor = CiyatoGold,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadRecentPhotoUris(context: Context, limit: Int): List<Pair<Long, Uri>> {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val results = mutableListOf<Pair<Long, Uri>>()
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
        null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        var count = 0
        while (cursor.moveToNext() && count < limit) {
            val id = cursor.getLong(idCol)
            results.add(id to ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
            count++
        }
    }
    return results
}

private fun autoTagPhoto(context: Context, uri: Uri): List<String> {
    val tags = mutableListOf<String>()
    return try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return listOf("photo")

        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()

        var r = 0L; var g = 0L; var b = 0L
        pixels.forEach { px ->
            r += (px shr 16) and 0xFF
            g += (px shr 8) and 0xFF
            b += px and 0xFF
        }
        val n = pixels.size.toLong().coerceAtLeast(1)
        val avgR = r / n; val avgG = g / n; val avgB = b / n
        val brightness = (avgR * 0.299 + avgG * 0.587 + avgB * 0.114).toLong()

        // Dominant color tags
        when {
            avgB > avgR + 20 && avgB > avgG + 10 -> tags.add("sky")
            avgG > avgR + 15 && avgG > avgB + 10 -> tags.add("nature")
            avgR > avgG + 20 && avgR > avgB + 20 -> tags.add("warm tones")
        }
        // Brightness tags
        when {
            brightness > 200 -> tags.add("bright")
            brightness < 80  -> tags.add("dark")
            else             -> tags.add("balanced light")
        }
        // Aspect ratio tags
        when {
            w > h * 1.5  -> tags.add("landscape")
            h > w * 1.5  -> tags.add("portrait")
            else         -> tags.add("square")
        }
        if (tags.isEmpty()) tags.add("photo")
        tags
    } catch (_: Exception) { listOf("photo") }
}
