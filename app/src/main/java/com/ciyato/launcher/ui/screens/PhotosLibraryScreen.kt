package com.ciyato.launcher.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.data.PhotoAiLabeler
import com.ciyato.launcher.data.PhotoDeviceLibrary
import com.ciyato.launcher.data.PhotoDeviceLibrary.DeviceImage
import com.ciyato.launcher.ui.components.CiyatoTabRow
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoRed
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) {
    COLLECTIONS("Collections"), GRID("Grid"), TIMELINE("Timeline"), TRASH("Trash")
}

/** A destructive media operation, and what to say once the system confirms it. */
private enum class MediaOp {
    TRASH, RESTORE, PURGE;

    fun doneMessage(count: Int): String = when (this) {
        TRASH -> if (count == 1) "Moved to trash" else "Moved $count photos to trash"
        RESTORE -> if (count == 1) "Restored" else "Restored $count photos"
        PURGE -> if (count == 1) "Deleted permanently" else "Deleted $count photos permanently"
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

/** What MediaStore will actually hand us right now. */
private enum class MediaAccess { NONE, PARTIAL, FULL }

/** Android 14 lets the person share only a hand-picked subset of their gallery. */
private const val VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 ->
        arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 ->
        arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
    else ->
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun mediaAccess(context: Context): MediaAccess {
    fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    val full = if (Build.VERSION.SDK_INT >= 33) {
        granted(android.Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        granted(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return when {
        full -> MediaAccess.FULL
        Build.VERSION.SDK_INT >= 34 && granted(VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
        else -> MediaAccess.NONE
    }
}

/**
 * Ciyato Photos — device-wide gallery in smart collections.
 * Falls back to the curated photo-picker flow when media permission is denied.
 */
@Composable
fun PhotosLibraryScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onOpenDuplicates: () -> Unit = {},
) {
    val context = LocalContext.current

    var access by remember { mutableStateOf(mediaAccess(context)) }
    var requestedOnce by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        access = mediaAccess(context)
        requestedOnce = true
        // A changed "Select photos" set keeps the same access level, so force a rescan.
        reloadToken++
    }
    // Returning from system Settings (the only path after a permanent denial)
    // re-checks so the gallery unlocks without a restart.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            access = mediaAccess(context)
            // Skip the resume that arrives with the initial composition.
            if (firstResume) firstResume = false else reloadToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (access == MediaAccess.NONE) {
        // Curated flow remains the no-permission experience, with a one-tap
        // upgrade to the full library.
        Box {
            PhotosScreen(viewModel = viewModel, onBack = onBack)
            Text(
                if (requestedOnce) "Enable photo access in Settings" else "Show full gallery",
                color = CiyatoBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(CiyatoGold)
                    .clickable {
                        // After a permanent denial the system dialog no longer
                        // appears, so route the second attempt to app settings.
                        if (requestedOnce) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure {
                                Toast.makeText(context, "Could not open app settings", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            permissionLauncher.launch(mediaPermissions())
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        return
    }

    var images by remember { mutableStateOf<List<DeviceImage>>(emptyList()) }
    var trashed by remember { mutableStateOf<List<DeviceImage>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(LibraryTab.COLLECTIONS) }
    var openCollection by remember { mutableStateOf<String?>(null) }
    var actionSheetFor by remember { mutableStateOf<DeviceImage?>(null) }

    // Selected photos, by URI. Kept across configuration changes because
    // losing a 200-photo selection to a screen rotation is its own bug.
    var selected by rememberSaveable(
        saver = listSaver(
            save = { state -> state.value.toList() },
            restore = { saved -> mutableStateOf(saved.toSet()) },
        ),
    ) { mutableStateOf(emptySet<String>()) }
    val selecting = selected.isNotEmpty()
    var confirmPurgeAll by remember { mutableStateOf(false) }

    // Back peels one layer at a time — clear the selection, then close the
    // collection, and only then leave Photos. Registration order matters:
    // the last enabled handler registered wins, so the narrowest goes last.
    BackHandler(enabled = !selecting && openCollection != null) { openCollection = null }
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Free on-device AI pass (ML Kit) — builds extra collections like Food/Pets/Nature.
    var aiResult by remember { mutableStateOf<PhotoAiLabeler.AiScanResult?>(null) }
    var aiProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scanScope = rememberCoroutineScope()
    val aiCollections = aiResult?.collections.orEmpty()

    // Deleting media Ciyato didn't write needs the system's own confirmation,
    // so the outcome only arrives back here as an activity result. Holding the
    // message until then means we report what actually happened rather than
    // what we asked for — the dialog can be declined.
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val message = pendingMessage
        pendingMessage = null
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            selected = emptySet()
            reloadToken++
            message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }

    fun requestMediaOp(targets: List<DeviceImage>, op: MediaOp) {
        if (targets.isEmpty()) return
        val uris = targets.map { it.uri }
        val sender = when (op) {
            MediaOp.TRASH -> PhotoDeviceLibrary.trashRequest(context, uris)
            MediaOp.RESTORE -> PhotoDeviceLibrary.restoreRequest(context, uris)
            MediaOp.PURGE -> PhotoDeviceLibrary.deleteRequest(context, uris)
        }
        if (sender != null) {
            pendingMessage = op.doneMessage(uris.size)
            consent.launch(IntentSenderRequest.Builder(sender).build())
            return
        }
        // Android 10 and older: no system trash and no batch consent dialog,
        // so a delete here is final. Restore can't be reached on those
        // versions — nothing can be trashed in the first place.
        if (op == MediaOp.RESTORE) return
        scanScope.launch {
            val removed = PhotoDeviceLibrary.deleteDirectly(context, uris)
            selected = emptySet()
            reloadToken++
            val text = if (removed == uris.size) {
                "Deleted ${uris.size}"
            } else {
                "Deleted $removed of ${uris.size} — the rest are owned by other apps"
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    // Emits an early slice, then the full library — see imageStream. `loaded`
    // is never reset on refresh so a delete doesn't blank the grid behind a
    // "Scanning…" message; the list simply updates under you.
    LaunchedEffect(access, reloadToken) {
        PhotoDeviceLibrary.imageStream(context).collect { batch ->
            images = batch
            loaded = true
        }
    }

    LaunchedEffect(access, reloadToken) {
        trashed = PhotoDeviceLibrary.loadTrashedImages(context)
    }

    val collections = remember(images) { PhotoDeviceLibrary.collections(images) }
    val visibleTabs = remember {
        LibraryTab.entries.filter { it != LibraryTab.TRASH || PhotoDeviceLibrary.trashSupported }
    }

    val openKey = openCollection
    val collectionImages = remember(images, openKey, aiCollections) {
        when {
            openKey == null -> emptyList()
            openKey.startsWith("ai:") -> aiCollections[openKey.removePrefix("ai:")].orEmpty()
            else -> PhotoDeviceLibrary.imagesForCollection(images, openKey)
        }
    }

    // Exactly what's on screen right now, so "Select all" means what it says
    // and a delete never reaches past what the person can see.
    val shownImages: List<DeviceImage> = when {
        openKey != null -> collectionImages
        tab == LibraryTab.TRASH -> trashed
        else -> images
    }
    val selectedImages = remember(selected, shownImages) {
        shownImages.filter { it.uri.toString() in selected }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            val viewingTrash = tab == LibraryTab.TRASH && openKey == null
            if (selecting) {
                PhotoSelectionBar(
                    count = selected.size,
                    allSelected = shownImages.isNotEmpty() && selectedImages.size == shownImages.size,
                    inTrash = viewingTrash,
                    onClose = { selected = emptySet() },
                    onToggleAll = {
                        selected = if (selectedImages.size == shownImages.size) {
                            emptySet()
                        } else {
                            shownImages.mapTo(mutableSetOf()) { it.uri.toString() }
                        }
                    },
                    onShare = {
                        if (!PhotoDeviceLibrary.launchShareMultiple(context, selectedImages.map { it.uri })) {
                            Toast.makeText(context, "No app can share these", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRestore = { requestMediaOp(selectedImages, MediaOp.RESTORE) },
                    onDelete = {
                        requestMediaOp(selectedImages, if (viewingTrash) MediaOp.PURGE else MediaOp.TRASH)
                    },
                    onMore = selectedImages.singleOrNull()?.let { single ->
                        { actionSheetFor = single }
                    },
                )
            } else {
                CiyatoTopBar(
                    title = "Ciyato Photos",
                    subtitle = openCollection?.let { key -> collectionTitle(key, collections) }
                        ?: when {
                            viewingTrash -> "${trashed.size} waiting to be cleared"
                            access == MediaAccess.PARTIAL -> "${images.size} photos you shared with Ciyato"
                            else -> "${images.size} photos on this device"
                        },
                    onBack = {
                        if (openCollection != null) openCollection = null else onBack()
                    },
                )
            }
        },
    ) { padding ->
        if (openKey != null) {
            PhotoGrid(
                images = collectionImages,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                selected = selected,
                selecting = selecting,
                onToggle = { selected = selected.toggle(it) },
            )
            return@Scaffold
        }

        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            CiyatoTabRow(
                tabs = visibleTabs.map { entry ->
                    if (entry == LibraryTab.TRASH && trashed.isNotEmpty()) {
                        "${entry.label} ${trashed.size}"
                    } else {
                        entry.label
                    }
                },
                selectedIndex = visibleTabs.indexOf(tab).coerceAtLeast(0),
                onTabSelected = {
                    // Selections don't carry across tabs: the photos they refer
                    // to aren't on screen any more, and a stale selection is
                    // how you delete something you can't see.
                    selected = emptySet()
                    tab = visibleTabs[it]
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (access == MediaAccess.PARTIAL) {
                PartialAccessBanner(onManage = { permissionLauncher.launch(mediaPermissions()) })
            }
            when {
                // Ahead of the empty check below — an empty library says
                // nothing about whether the trash has anything in it.
                tab == LibraryTab.TRASH -> {
                    if (trashed.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DeleteOutline, null, tint = CiyatoMuted, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Trash is empty", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Deleted photos rest here for 30 days before Android clears them.",
                                    color = CiyatoMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 48.dp),
                                )
                            }
                        }
                    } else {
                        Column {
                            TrashNotice(
                                count = trashed.size,
                                onEmpty = { confirmPurgeAll = true },
                            )
                            PhotoGrid(
                                images = trashed,
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp,
                                    bottom = padding.calculateBottomPadding() + 24.dp,
                                ),
                                selected = selected,
                                selecting = selecting,
                                onToggle = { selected = selected.toggle(it) },
                            )
                        }
                    }
                }
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Scanning your gallery…", color = CiyatoMuted, fontSize = 14.sp)
                }
                images.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = CiyatoMuted, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No photos found", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                    }
                }
                tab == LibraryTab.COLLECTIONS -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(2) }) {
                        AiScanBanner(
                            progress = aiProgress,
                            result = aiResult,
                            onScan = {
                                aiProgress = 0 to 0
                                scanScope.launch {
                                    aiResult = PhotoAiLabeler.categorize(
                                        context = context,
                                        images = images,
                                        onProgress = { done, total -> aiProgress = done to total },
                                    )
                                    aiProgress = null
                                }
                            },
                        )
                    }
                    item(span = { GridItemSpan(2) }) {
                        DuplicateScanEntry(onClick = onOpenDuplicates)
                    }
                    items(
                        aiCollections.entries.sortedByDescending { it.value.size },
                        key = { "ai:${it.key}" },
                    ) { (title, aiImages) ->
                        CollectionCard(
                            title = "✨ $title",
                            coverUri = aiImages.firstOrNull()?.uri?.toString(),
                            count = aiImages.size,
                            onClick = { openCollection = "ai:$title" },
                        )
                    }
                    items(collections, key = { it.key }) { collection ->
                        CollectionCard(
                            title = collection.title,
                            coverUri = collection.coverUri?.toString(),
                            count = collection.count,
                            onClick = { openCollection = collection.key },
                        )
                    }
                }
                tab == LibraryTab.GRID -> PhotoGrid(
                    images = images,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                    selected = selected,
                    selecting = selecting,
                    onToggle = { selected = selected.toggle(it) },
                )
                else -> {
                    val groups = remember(images) { PhotoDeviceLibrary.timeline(images) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        groups.forEach { (month, monthImages) ->
                            item(span = { GridItemSpan(4) }) {
                                Text(
                                    month,
                                    color = CiyatoGold,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                                )
                            }
                            items(monthImages, key = { it.uri.toString() }) { image ->
                                val id = image.uri.toString()
                                PhotoThumb(
                                    image = image,
                                    isSelected = id in selected,
                                    selecting = selecting,
                                    onToggle = { selected = selected.toggle(id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    actionSheetFor?.let { image ->
        PhotoActionSheet(image = image, onDismiss = { actionSheetFor = null })
    }

    if (confirmPurgeAll) {
        AlertDialog(
            onDismissRequest = { confirmPurgeAll = false },
            containerColor = CiyatoBgEl,
            title = { Text("Empty trash?", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "${trashed.size} ${if (trashed.size == 1) "photo" else "photos"} will be deleted " +
                        "for good. This can't be undone.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmPurgeAll = false
                    requestMediaOp(trashed, MediaOp.PURGE)
                }) {
                    Text("Delete all", color = CiyatoRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurgeAll = false }) {
                    Text("Keep", color = CiyatoSec)
                }
            },
        )
    }
}

private fun collectionTitle(key: String, collections: List<PhotoDeviceLibrary.DeviceCollection>): String =
    if (key.startsWith("ai:")) key.removePrefix("ai:")
    else collections.firstOrNull { it.key == key }?.title ?: key.removePrefix("bucket:")

/** Runs an open/edit/share hand-off and says so out loud when nothing can take it. */
private fun runPhotoAction(context: Context, image: DeviceImage, action: String) {
    if (PhotoDeviceLibrary.launchPhotoAction(context, image.uri, action)) return
    val message = when (action) {
        Intent.ACTION_EDIT -> "No photo editor installed on this phone"
        Intent.ACTION_SEND -> "No app can share this photo"
        else -> "No app can open this photo"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun PartialAccessBanner(onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "You shared only some photos. Ciyato can't see the rest.",
            color = CiyatoSec,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Select more",
            color = CiyatoBg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(CiyatoGold)
                .clickable(onClick = onManage)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AiScanBanner(
    progress: Pair<Int, Int>?,
    result: PhotoAiLabeler.AiScanResult?,
    onScan: () -> Unit,
) {
    val hasResults = result != null && result.collections.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (hasResults) "AI collections ready" else "AI photo organizer",
                color = CiyatoWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                when {
                    progress != null && progress.second > 0 ->
                        "Scanning ${progress.first}/${progress.second} photos on-device…"
                    progress != null -> "Starting scan…"
                    hasResults -> "Runs fully on this phone. Free, private, offline."
                    result != null ->
                        "Scanned ${result.scannedCount} photos — nothing grouped confidently yet."
                    else -> "Group photos by what's in them — free, on-device, private."
                },
                color = CiyatoMuted,
                fontSize = 12.sp,
            )
        }
        if (progress == null) {
            Text(
                if (result != null) "Rescan" else "Scan",
                color = CiyatoBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CiyatoGold)
                    .clickable(onClick = onScan)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    images: List<DeviceImage>,
    contentPadding: PaddingValues,
    selected: Set<String>,
    selecting: Boolean,
    onToggle: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(images, key = { it.uri.toString() }) { image ->
            val id = image.uri.toString()
            PhotoThumb(
                image = image,
                isSelected = id in selected,
                selecting = selecting,
                onToggle = { onToggle(id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoThumb(
    image: DeviceImage,
    isSelected: Boolean,
    selecting: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    // Inset rather than tinted: shrinking the photo shows the selection
    // against the background instead of washing the photo out, so you can
    // still tell what you picked.
    val inset by animateFloatAsState(if (isSelected) 0.86f else 1f, label = "photoSelect")
    Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.TopEnd) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = inset
                    scaleY = inset
                }
                .clip(RoundedCornerShape(8.dp))
                .background(CiyatoBgEl)
                .combinedClickable(
                    // Once a selection is running, a plain tap extends it.
                    // Launching a viewer mid-selection would lose the set.
                    onClick = {
                        if (selecting) onToggle() else runPhotoAction(context, image, Intent.ACTION_VIEW)
                    },
                    onLongClick = onToggle,
                ),
        )
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = CiyatoGold,
                modifier = Modifier.padding(4.dp).size(20.dp),
            )
        }
    }
}

/**
 * Replaces the title bar while photos are selected.
 *
 * Deliberately the same height and background as [CiyatoTopBar] so entering a
 * selection swaps the contents rather than shifting the grid underneath.
 */
@Composable
private fun PhotoSelectionBar(
    count: Int,
    allSelected: Boolean,
    inTrash: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onMore: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CiyatoBgEl)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "Clear selection", tint = CiyatoWhite)
        }
        Text(
            "$count selected",
            color = CiyatoWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleAll) {
            Icon(
                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                if (allSelected) "Select none" else "Select all",
                tint = CiyatoSec,
            )
        }
        if (inTrash) {
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.RestoreFromTrash, "Restore", tint = CiyatoSec)
            }
        } else {
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, "Share", tint = CiyatoSec)
            }
            // Open and Edit only make sense for one photo at a time.
            if (onMore != null) {
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.MoreVert, "More actions", tint = CiyatoSec)
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                if (inTrash) Icons.Default.DeleteForever else Icons.Default.Delete,
                if (inTrash) "Delete permanently" else "Move to trash",
                tint = if (inTrash) CiyatoRed else CiyatoSec,
            )
        }
    }
}

/** Way in to the perceptual-hash duplicate finder. */
@Composable
private fun DuplicateScanEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.ContentCopy, null, tint = CiyatoGold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Find duplicates", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "Compares photos by how they look, not by file name",
                color = CiyatoMuted,
                fontSize = 12.sp,
            )
        }
    }
}

/** Explains what the trash is for, and offers the one irreversible action. */
@Composable
private fun TrashNotice(count: Int, onEmpty: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count ${if (count == 1) "photo clears" else "photos clear"} automatically after 30 days.",
            color = CiyatoSec,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Empty now",
            color = CiyatoRed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onEmpty)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoActionSheet(image: DeviceImage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        contentColor = CiyatoWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CiyatoBg),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        image.name.ifBlank { "Photo" },
                        color = CiyatoWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${image.bucket} · ${MediaLibraryRepository.formatBytes(image.sizeBytes)}",
                        color = CiyatoMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PhotoActionRow(Icons.AutoMirrored.Filled.OpenInNew, "Open", "View it in your gallery app") {
                runPhotoAction(context, image, Intent.ACTION_VIEW)
                onDismiss()
            }
            PhotoActionRow(Icons.Default.Apps, "Open with…", "Pick a different app just this once") {
                if (!PhotoDeviceLibrary.launchPhotoAction(
                        context,
                        image.uri,
                        Intent.ACTION_VIEW,
                        forceChooser = true,
                    )
                ) {
                    Toast.makeText(context, "No app can open this photo", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }
            PhotoActionRow(Icons.Default.Edit, "Edit", "Crop, filter or mark up in your own editor") {
                runPhotoAction(context, image, Intent.ACTION_EDIT)
                onDismiss()
            }
            PhotoActionRow(Icons.Default.Share, "Share", "Send it from any installed app") {
                runPhotoAction(context, image, Intent.ACTION_SEND)
                onDismiss()
            }
        }
    }
}

@Composable
private fun PhotoActionRow(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = CiyatoWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = CiyatoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CollectionCard(
    title: String,
    coverUri: String?,
    count: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(18.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        startY = 240f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                title,
                color = CiyatoWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$count items", color = CiyatoSec, fontSize = 12.sp)
        }
    }
}
