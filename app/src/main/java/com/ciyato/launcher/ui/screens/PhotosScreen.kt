package com.ciyato.launcher.ui.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ciyato.launcher.data.AuthorizedMedia
import com.ciyato.launcher.data.PhotoCollection
import com.ciyato.launcher.data.PhotoLibraryStore
import com.ciyato.launcher.data.PhotoMediaRepository
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBgEl2
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.DateFormat
import java.util.Date

private enum class PhotosMode(val label: String) {
    GRID("Grid"),
    TIMELINE("Timeline"),
    COLLECTIONS("Collections"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val storedUrisJson by viewModel.photoMediaUris.collectAsState()
    val collectionsJson by viewModel.photoCollections.collectAsState()
    val storedUris = remember(storedUrisJson) { PhotoLibraryStore.parseUris(storedUrisJson) }
    val collections = remember(collectionsJson) { PhotoLibraryStore.parseCollections(collectionsJson) }
    val mediaRepository = remember(context) { PhotoMediaRepository(context.applicationContext) }
    var media by remember { mutableStateOf<List<AuthorizedMedia>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(PhotosMode.GRID) }
    var query by remember { mutableStateOf("") }
    var activeCollectionId by remember { mutableStateOf<String?>(null) }
    var detailsFor by remember { mutableStateOf<AuthorizedMedia?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showSourceDeleteDialog by remember { mutableStateOf(false) }
    var pendingSourceDeletion by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectedUris = remember { mutableStateListOf<String>() }

    LaunchedEffect(storedUris) {
        isLoading = true
        media = mediaRepository.resolve(storedUris)
        selectedUris.retainAll(storedUris.toSet())
        isLoading = false
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPhotoUris(uris.map(Uri::toString))
            statusMessage = "${uris.size} item${if (uris.size == 1) "" else "s"} added to Ciyato Photos."
            mode = PhotosMode.GRID
        }
    }

    val sourceDeletion = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.removePhotoUris(pendingSourceDeletion)
            selectedUris.removeAll(pendingSourceDeletion)
            statusMessage = "Android deleted the selected media."
        } else {
            statusMessage = "Android did not approve deletion. The media is unchanged."
        }
        pendingSourceDeletion = emptySet()
    }

    val openPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val selectedSet = selectedUris.toSet()
    val selectedCollection = collections.firstOrNull { it.id == activeCollectionId }
    val visibleMedia = media
        .asSequence()
        .filter { item -> selectedCollection == null || item.uri.toString() in selectedCollection.uris }
        .filter { item ->
            query.isBlank() || item.displayName.contains(query.trim(), ignoreCase = true) ||
                item.mimeType.orEmpty().contains(query.trim(), ignoreCase = true)
        }
        .sortedByDescending { it.lastModified }
        .toList()
    val unavailableUris = media.filterNot { it.isAvailable }.map { it.uri.toString() }.toSet()

    fun clearSelection() {
        selectedUris.clear()
        detailsFor = null
    }

    fun requestSourceDeletion(uris: Set<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty() || uris.any { Uri.parse(it).authority != "media" }) {
            statusMessage = "Android source deletion is available only for authorized MediaStore items on Android 11 or later."
            return
        }
        runCatching {
            val request = MediaStore.createDeleteRequest(context.contentResolver, uris.map(Uri::parse))
            pendingSourceDeletion = uris
            sourceDeletion.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure {
            statusMessage = "Android could not prepare a deletion request. The media is unchanged."
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ciyato Photos", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text("Only media you explicitly select", color = CiyatoGold, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CiyatoSec)
                    }
                },
                actions = {
                    IconButton(onClick = openPicker) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add media", tint = CiyatoGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search selected media") },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotosMode.entries.forEach { item ->
                    PhotoModeChip(
                        label = item.label,
                        selected = item == mode,
                        onClick = {
                            mode = item
                            if (item == PhotosMode.COLLECTIONS) clearSelection()
                        },
                    )
                }
            }
            if (selectedCollection != null && mode != PhotosMode.COLLECTIONS) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedCollection.name,
                        color = CiyatoWhite,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { activeCollectionId = null }) {
                        Text("All selected", color = CiyatoGold)
                    }
                }
            }
            if (selectedUris.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                PhotoSelectionToolbar(
                    count = selectedUris.size,
                    onCreateCollection = { showCollectionDialog = true },
                    onRemoveFromCiyato = { showRemoveDialog = true },
                    onDeleteFromDevice = { showSourceDeleteDialog = true },
                    onClear = ::clearSelection,
                )
            }
            statusMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = CiyatoSec, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> LoadingMediaState()
                storedUris.isEmpty() -> EmptyMediaState(onAddMedia = openPicker)
                mode == PhotosMode.COLLECTIONS -> CollectionsContent(
                    allMedia = media,
                    collections = collections,
                    onAllMedia = {
                        activeCollectionId = null
                        mode = PhotosMode.GRID
                    },
                    onOpenCollection = { id ->
                        activeCollectionId = id
                        mode = PhotosMode.GRID
                    },
                )
                mode == PhotosMode.TIMELINE -> PhotoTimeline(
                    media = visibleMedia,
                    selectedUris = selectedSet,
                    onMediaClick = { item ->
                        if (selectedUris.isNotEmpty()) toggleSelection(selectedUris, item.uri.toString()) else detailsFor = item
                    },
                    onMediaLongClick = { item -> toggleSelection(selectedUris, item.uri.toString()) },
                )
                else -> PhotoGrid(
                    media = visibleMedia,
                    selectedUris = selectedSet,
                    onMediaClick = { item ->
                        if (selectedUris.isNotEmpty()) toggleSelection(selectedUris, item.uri.toString()) else detailsFor = item
                    },
                    onMediaLongClick = { item -> toggleSelection(selectedUris, item.uri.toString()) },
                )
            }

            if (unavailableUris.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                RevokedMediaNotice(
                    count = unavailableUris.size,
                    onRemove = { viewModel.removePhotoUris(unavailableUris) },
                )
            }
        }
    }

    detailsFor?.let { item ->
        MediaDetailsDialog(
            item = item,
            onDismiss = { detailsFor = null },
            onRemove = {
                viewModel.removePhotoUris(setOf(item.uri.toString()))
                detailsFor = null
                statusMessage = "Media removed from Ciyato Photos. The original is unchanged."
            },
            onRequestSourceDeletion = {
                detailsFor = null
                requestSourceDeletion(setOf(item.uri.toString()))
            },
        )
    }

    if (showCollectionDialog) {
        CreateCollectionDialog(
            selectionCount = selectedUris.size,
            onDismiss = { showCollectionDialog = false },
            onCreate = { name ->
                viewModel.addPhotoCollection(name, selectedUris)
                statusMessage = "Collection created on this device."
                clearSelection()
                showCollectionDialog = false
            },
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove from Ciyato Photos") },
            text = { Text("This only removes Ciyato's saved reference. The original media stays on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePhotoUris(selectedUris)
                    statusMessage = "Media removed from Ciyato Photos. Originals are unchanged."
                    clearSelection()
                    showRemoveDialog = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") } },
        )
    }

    if (showSourceDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDeleteDialog = false },
            title = { Text("Request Android deletion") },
            text = { Text("Android will show its own confirmation before deleting anything. Ciyato cannot delete media without that approval.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = selectedUris.toSet()
                    showSourceDeleteDialog = false
                    requestSourceDeletion(target)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showSourceDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhotoModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) CiyatoBg else CiyatoSec,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CiyatoGold else CiyatoBgEl)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun LoadingMediaState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CiyatoGold)
    }
}

@Composable
private fun EmptyMediaState(onAddMedia: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(8.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(28.dp))
        Text("Choose media to organize", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(
            "Android Photo Picker lets you choose specific photos and videos. Ciyato does not request access to your complete gallery.",
            color = CiyatoSec,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Button(
            onClick = onAddMedia,
            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select media", color = CiyatoBg)
        }
        Text("Selection is optional. You can remove Ciyato's access record at any time.", color = CiyatoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun PhotoSelectionToolbar(
    count: Int,
    onCreateCollection: () -> Unit,
    onRemoveFromCiyato: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CiyatoBgEl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count selected",
            color = CiyatoWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        IconButton(onClick = onCreateCollection) {
            Icon(Icons.Default.CollectionsBookmark, contentDescription = "Create collection", tint = CiyatoGold)
        }
        IconButton(onClick = onRemoveFromCiyato) {
            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove from Ciyato Photos", tint = CiyatoSec)
        }
        IconButton(onClick = onDeleteFromDevice) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Request Android deletion", tint = CiyatoSec)
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Info, contentDescription = "Clear selection", tint = CiyatoSec)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    media: List<AuthorizedMedia>,
    selectedUris: Set<String>,
    onMediaClick: (AuthorizedMedia) -> Unit,
    onMediaLongClick: (AuthorizedMedia) -> Unit,
) {
    if (media.none { it.isAvailable }) {
        EmptySearchState()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(media, key = { it.uri.toString() }) { item ->
            PhotoTile(
                item = item,
                selected = item.uri.toString() in selectedUris,
                onClick = { onMediaClick(item) },
                onLongClick = { onMediaLongClick(item) },
            )
        }
    }
}

@Composable
private fun PhotoTimeline(
    media: List<AuthorizedMedia>,
    selectedUris: Set<String>,
    onMediaClick: (AuthorizedMedia) -> Unit,
    onMediaLongClick: (AuthorizedMedia) -> Unit,
) {
    val groups = remember(media) {
        media.filter { it.isAvailable }.groupBy { item ->
            if (item.lastModified <= 0L) "Selected media"
            else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.lastModified))
        }
    }
    if (groups.isEmpty()) {
        EmptySearchState()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { (date, entries) ->
            item(key = "header_$date") {
                Text(date, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            items(entries, key = { it.uri.toString() }) { item ->
                TimelinePhotoRow(
                    item = item,
                    selected = item.uri.toString() in selectedUris,
                    onClick = { onMediaClick(item) },
                    onLongClick = { onMediaLongClick(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(item: AuthorizedMedia, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(CiyatoBgEl)
            .border(if (selected) 2.dp else 1.dp, if (selected) CiyatoGold else CiyatoSubtleBorder, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (item.isAvailable) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.ImageNotSupported, contentDescription = null, tint = CiyatoMuted, modifier = Modifier.align(Alignment.Center))
        }
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize().background(CiyatoGold.copy(alpha = 0.22f)),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text("Selected", color = CiyatoBg, fontSize = 10.sp, modifier = Modifier.background(CiyatoGold).padding(horizontal = 5.dp, vertical = 3.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelinePhotoRow(item: AuthorizedMedia, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CiyatoBgEl)
            .border(if (selected) 2.dp else 1.dp, if (selected) CiyatoGold else CiyatoSubtleBorder, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.isAvailable) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Icon(Icons.Default.ImageNotSupported, contentDescription = null, tint = CiyatoMuted, modifier = Modifier.size(54.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.displayName, color = CiyatoWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mimeType ?: "Selected media", color = CiyatoMuted, fontSize = 12.sp)
        }
        if (selected) Text("Selected", color = CiyatoGold, fontSize = 12.sp)
    }
}

@Composable
private fun CollectionsContent(
    allMedia: List<AuthorizedMedia>,
    collections: List<PhotoCollection>,
    onAllMedia: () -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Collections", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        item {
            CollectionRow(
                title = "All selected media",
                count = allMedia.count { it.isAvailable },
                cover = allMedia.firstOrNull { it.isAvailable },
                onClick = onAllMedia,
            )
        }
        if (collections.isEmpty()) {
            item {
                Text(
                    "Select media, then use the collection action to create an on-device collection.",
                    color = CiyatoMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(collections, key = { it.id }) { collection ->
                CollectionRow(
                    title = collection.name,
                    count = collection.uris.size,
                    cover = allMedia.firstOrNull { it.uri.toString() in collection.uris && it.isAvailable },
                    onClick = { onOpenCollection(collection.id) },
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(title: String, count: Int, cover: AuthorizedMedia?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(CiyatoBgEl2), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FolderCopy, contentDescription = null, tint = CiyatoGold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = CiyatoWhite, fontWeight = FontWeight.Medium)
            Text("$count item${if (count == 1) "" else "s"}", color = CiyatoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptySearchState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No selected media matches this view.", color = CiyatoMuted, fontSize = 13.sp)
    }
}

@Composable
private fun RevokedMediaNotice(count: Int, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.ImageNotSupported, contentDescription = null, tint = CiyatoMuted)
        Text("$count selected item${if (count == 1) " is" else "s are"} no longer available.", color = CiyatoSec, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRemove) { Text("Remove", color = CiyatoGold) }
    }
}

@Composable
private fun MediaDetailsDialog(
    item: AuthorizedMedia,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onRequestSourceDeletion: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.isAvailable) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
                Text(item.mimeType ?: "Selected media", color = CiyatoMuted, fontSize = 12.sp)
                Text("Removing from Ciyato does not change the original file.", color = CiyatoSec, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onRemove) { Text("Remove from Ciyato") } },
        dismissButton = {
            Row {
                if (item.isAvailable) {
                    TextButton(onClick = onRequestSourceDeletion) { Text("Request Android deletion") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun CreateCollectionDialog(selectionCount: Int, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$selectionCount selected item${if (selectionCount == 1) "" else "s"} will stay on your device.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Collection name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun toggleSelection(selection: MutableList<String>, uri: String) {
    if (!selection.remove(uri)) selection.add(uri)
}
