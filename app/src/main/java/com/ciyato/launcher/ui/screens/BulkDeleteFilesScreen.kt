package com.ciyato.launcher.ui.screens

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.ui.components.BulkDeleteBar
import com.ciyato.launcher.ui.components.BulkDeleteState
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * BulkDeleteFilesScreen — Suggestion #66
 * Multi-select photo/file grid with bulk delete and undo support.
 */

data class MediaItem(val id: Long, val uri: Uri, val name: String, val sizeBytes: Long)

@Composable
fun BulkDeleteFilesScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { loadAllImages(context) }
        isLoading = false
    }

    val bulkState = remember(items) { BulkDeleteState(items.map { it.id.toString() }) }
    val selectedBytes = items.filter { bulkState.isSelected(it.id.toString()) }.sumOf(MediaItem::sizeBytes)

    // Android 10+ requires explicit user consent (via a system dialog) before an app can
    // delete MediaStore entries it did not create itself. Without this, contentResolver.delete()
    // throws (or is silently swallowed) and nothing is actually removed from the device.
    var pendingConsentResume by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pendingConsentResume?.invoke(result.resultCode == Activity.RESULT_OK)
        pendingConsentResume = null
    }

    // The undo window and the delete itself run on the composition scope. They must NOT hang
    // off a LaunchedEffect keyed on a flag this block flips: writing the key cancels the very
    // coroutine that still has to perform the deletion, so the files would leave the grid and
    // stay on the device.
    fun doDelete() {
        val batch = items.filter { bulkState.isSelected(it.id.toString()) }
        if (batch.isEmpty()) return
        val batchIds = batch.map(MediaItem::id).toSet()
        items = items.filterNot { it.id in batchIds }
        bulkState.clearAll()
        scope.launch {
            val undone = snackbarHost.showSnackbar(
                message = "Deleting ${batch.size} · ${MediaLibraryRepository.formatBytes(batch.sumOf(MediaItem::sizeBytes))}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            ) == SnackbarResult.ActionPerformed
            if (undone) {
                items = (batch + items).sortedBy { it.name }
                return@launch
            }
            val failed = deleteMediaItems(context, batch) { intentSender ->
                suspendCancellableCoroutine { cont ->
                    pendingConsentResume = { granted -> cont.resume(granted) }
                    deleteConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            }
            if (failed.isNotEmpty()) {
                // Consent was denied or the delete otherwise failed: these files are still on
                // the device, so put them back instead of letting them silently disappear.
                items = (failed + items).sortedBy { it.name }
                snackbarHost.showSnackbar("${failed.size} of ${batch.size} could not be deleted")
            }
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Select & Delete", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(color = CiyatoGold, modifier = Modifier.align(Alignment.Center))
            } else if (items.isEmpty()) {
                // A blank grid would read as "you have no photos" even when the real reason is
                // a missing permission, so say which one it is.
                val hasAccess = remember { MediaLibraryRepository(context).hasMediaPermission() }
                Text(
                    if (hasAccess) "No photos found on this device."
                    else "Ciyato needs photo access to list what can be deleted. Grant it in Android settings.",
                    color = CiyatoMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
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
                    selectedBytes = selectedBytes,
                    onSelectAll = { bulkState.selectAll() },
                    onClearSelection = { bulkState.clearAll() },
                    onDelete = { doDelete() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Actually removes [items] from the device, requesting the user's consent through the system
 * dialog when Android requires it (any MediaStore entry the app did not create itself, on
 * API 29+). Returns the items that could NOT be deleted so callers can restore them in the UI
 * instead of leaving the user with a false "deleted" state.
 */
private suspend fun deleteMediaItems(
    context: Context,
    items: List<MediaItem>,
    requestConsent: suspend (IntentSender) -> Boolean,
): List<MediaItem> = withContext(Dispatchers.IO) {
    val failed = mutableListOf<MediaItem>()
    when {
        items.isEmpty() -> Unit
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
            val granted = withContext(Dispatchers.Main) { requestConsent(pendingIntent.intentSender) }
            if (!granted) failed += items
        }
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
            items.forEach { item ->
                try {
                    // A zero row count means the row survived; treating that as success would
                    // drop the photo from the grid while it is still on the device.
                    if (context.contentResolver.delete(item.uri, null, null) <= 0) failed += item
                } catch (security: RecoverableSecurityException) {
                    val granted = withContext(Dispatchers.Main) {
                        requestConsent(security.userAction.actionIntent.intentSender)
                    }
                    val deleted = granted &&
                        runCatching { context.contentResolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
                    if (!deleted) failed += item
                } catch (_: Exception) {
                    failed += item
                }
            }
        }
        else -> {
            // Android 8–9: without WRITE_EXTERNAL_STORAGE this throws, which is reported back
            // as a failure rather than pretending the photo is gone.
            items.forEach { item ->
                try {
                    if (context.contentResolver.delete(item.uri, null, null) <= 0) failed += item
                } catch (_: Exception) {
                    failed += item
                }
            }
        }
    }
    failed
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
    // A revoked media permission makes this query throw; that must surface as the empty
    // state on screen, not as a crash on entering it.
    runCatching {
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
    }
    return items
}
