package com.ciyato.launcher.ui.screens

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
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
import coil.compose.AsyncImage
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * MemoriesScreen — Suggestion #73
 * Groups past photos by year and month, showing a "On this day" / memories view.
 */

data class PhotoMemory(
    val id: Long,
    val uri: Uri,
    val dateMs: Long,
)

data class MemoryGroup(
    val label: String,
    val sublabel: String,
    val photos: List<PhotoMemory>,
    val isOnThisDay: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<MemoryGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        groups = loadMemories(context)
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Memories", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
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
                CircularProgressIndicator(color = CiyatoGold)
            }
            return@Scaffold
        }

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No memories yet", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Photos will appear here organized by date", color = CiyatoMuted)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(groups, key = { it.label }) { group ->
                MemoryGroupRow(group)
            }
        }
    }
}

@Composable
private fun MemoryGroupRow(group: MemoryGroup) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (group.isOnThisDay) {
                Text("✨", fontSize = 16.sp)
            }
            Column {
                Text(group.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(group.sublabel, color = CiyatoMuted, fontSize = 12.sp)
            }
        }

        LazyHorizontalGrid(
            rows = GridCells.Fixed(if (group.photos.size > 4) 2 else 1),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(if (group.photos.size > 4) 196.dp else 96.dp),
        ) {
            items(group.photos.take(12), key = { it.id }) { photo ->
                AsyncImage(
                    model = photo.uri,
                    contentDescription = "Memory photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(90.dp).clip(RoundedCornerShape(10.dp))
                        .background(CiyatoBgEl)
                        .clickable {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(photo.uri, "image/*")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Could not open photo", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                )
            }
        }
    }
}

private fun loadMemories(context: Context): List<MemoryGroup> {
    val photos = mutableListOf<PhotoMemory>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val proj = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
    )
    try {
        context.contentResolver.query(collection, proj, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val dateMs = cursor.getLong(dateCol)
                photos.add(PhotoMemory(id, ContentUris.withAppendedId(collection, id), dateMs))
            }
        }
    } catch (_: Exception) {}

    val cal = Calendar.getInstance()
    val today = cal.clone() as Calendar
    val groups = mutableListOf<MemoryGroup>()

    // On this day in previous years
    val sameDayPhotos = photos.filter {
        val c = Calendar.getInstance().also { c -> c.timeInMillis = it.dateMs }
        c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                c.get(Calendar.YEAR) < today.get(Calendar.YEAR)
    }
    if (sameDayPhotos.isNotEmpty()) {
        val years = sameDayPhotos.mapTo(mutableSetOf()) {
            Calendar.getInstance().also { c -> c.timeInMillis = it.dateMs }.get(Calendar.YEAR)
        }
        groups.add(MemoryGroup(
            label = "On This Day",
            sublabel = "${years.min()}–${years.max()} · ${sameDayPhotos.size} photos",
            photos = sameDayPhotos.take(8),
            isOnThisDay = true,
        ))
    }

    // By year-month
    val byMonth = photos.groupBy {
        val c = Calendar.getInstance().also { c -> c.timeInMillis = it.dateMs }
        "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}"
    }
    val df = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    byMonth.entries.take(12).forEach { (_, monthPhotos) ->
        val first = monthPhotos.first()
        val label = df.format(Date(first.dateMs))
        groups.add(MemoryGroup(
            label = label,
            sublabel = "${monthPhotos.size} photos",
            photos = monthPhotos.take(8),
        ))
    }
    return groups
}
