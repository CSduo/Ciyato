package com.ciyato.launcher.ui.screens

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.ciyato.launcher.ui.components.BulkDeleteBar
import com.ciyato.launcher.ui.components.BulkDeleteState
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * BulkDeleteFilesScreen — Suggestion #66
 * Multi-select photo/file grid with bulk delete and undo support.
 */

data class MediaItem(val id: Long, val uri: Uri, val name: String, val sizeBytes: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkDeleteFilesScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var deletedItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showUndoSnack by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { items = loadAllImages(context) }

    val bulkState = remember(items) { BulkDeleteState(items.map { it.id.toString() }) }

    fun doDelete() {
        val toDelete = items.filter { bulkState.isSelected(it.id.toString()) }
        deletedItems = toDelete
        items = items.filter { !bulkState.isSelected(it.id.toString()) }
        bulkState.clearAll()
        showUndoSnack = true
    }

    LaunchedEffect(showUndoSnack) {
        if (!showUndoSnack) return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = "${deletedItems.size} items deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        showUndoSnack = false
        if (result == SnackbarResult.ActionPerformed) {
            items = (deletedItems + items).sortedBy { it.name }
            deletedItems = emptyList()
        } else {
            deletedItems.forEach { item ->
                try {
                    context.contentResolver.delete(item.uri, null, null)
                } catch (_: Exception) {}
            }
            deletedItems = emptyList()
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Select & Delete", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize().padding(bottom = if (bulkState.selectedCount > 0) 80.dp else 0.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    val isSelected = bulkState.isSelected(item.id.toString())
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CiyatoBgEl)
                            .then(
                                if (isSelected) Modifier.border(2.dp, CiyatoGold, RoundedCornerShape(6.dp))
                                else Modifier
                            )
                            .clickable { bulkState.toggle(item.id.toString()) },
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(CiyatoGold.copy(alpha = 0.2f))
                            )
                            Box(
                                modifier = Modifier.padding(6.dp).size(22.dp).clip(CircleShape)
                                    .background(CiyatoGold).align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.Black,
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            BulkDeleteBar(
                selectedCount = bulkState.selectedCount,
                totalCount = items.size,
                onSelectAll = { bulkState.selectAll() },
                onClearSelection = { bulkState.clearAll() },
                onDelete = { doDelete() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun loadAllImages(context: Context): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
    )
    context.contentResolver.query(collection, projection, null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            items.add(MediaItem(id, ContentUris.withAppendedId(collection, id),
                cursor.getString(nameCol) ?: "image_$id", cursor.getLong(sizeCol)))
        }
    }
    return items
}
