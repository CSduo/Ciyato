package com.ciyato.launcher.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.data.PhotoDeviceLibrary
import com.ciyato.launcher.data.PhotoDeviceLibrary.DeviceImage
import com.ciyato.launcher.data.PhotoDeviceLibrary.DeviceVideo
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * Photo Collections — one real, reachable screen that replaces the four dead-code
 * photo screens (SmartAlbums, Memories, ScreenshotCollection, PhotoAutoTagger).
 *
 * Every collection below is computed live from MediaStore — screenshots detected
 * by folder/filename, the last 30 days, every video on the device, photos over
 * 10 MB, and real month-by-month "memories" grouped by capture date. Nothing is
 * sample data: an empty result simply omits the card. Opening a photo or video
 * hands off to whatever app the phone already has via ACTION_VIEW / ACTION_EDIT /
 * ACTION_SEND, always through a chooser, and never crashes when nothing can handle it.
 */

/** Android 14 lets the person share only a hand-picked subset of their gallery. */
private const val VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

private const val LARGE_PHOTO_THRESHOLD = 10L * 1024 * 1024 // 10 MB

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
        VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** True only for the Android 14 "select photos" partial grant — full access is handled separately. */
private fun isPartialMediaAccess(context: Context): Boolean {
    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    val full = if (Build.VERSION.SDK_INT >= 33) {
        granted(android.Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        granted(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return !full && Build.VERSION.SDK_INT >= 34 && granted(VISUAL_USER_SELECTED)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCollectionsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mediaRepo = remember { MediaLibraryRepository(context) }

    var hasPermission by remember { mutableStateOf(mediaRepo.hasMediaPermission()) }
    var partialAccess by remember { mutableStateOf(isPartialMediaAccess(context)) }
    var requestedOnce by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = mediaRepo.hasMediaPermission()
        partialAccess = isPartialMediaAccess(context)
        requestedOnce = true
        // A changed "Select photos" set keeps the same access level, so force a rescan.
        reloadToken++
    }

    // Granting from system Settings, or changing the "Select photos" set, is only
    // visible again after a resume — re-check so the screen updates without a restart.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            hasPermission = mediaRepo.hasMediaPermission()
            partialAccess = isPartialMediaAccess(context)
            if (firstResume) firstResume = false else reloadToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isScanning by remember { mutableStateOf(true) }
    var images by remember { mutableStateOf<List<DeviceImage>>(emptyList()) }
    var videos by remember { mutableStateOf<List<DeviceVideo>>(emptyList()) }

    LaunchedEffect(hasPermission, reloadToken) {
        if (!hasPermission) {
            isScanning = false
            return@LaunchedEffect
        }
        isScanning = true
        images = PhotoDeviceLibrary.loadImages(context)
        videos = PhotoDeviceLibrary.loadVideos(context)
        isScanning = false
    }

    val collections = remember(images, videos) { buildPhotoCollections(images, videos) }
    var openCollection by remember { mutableStateOf<PhotoCollection?>(null) }
    var actionSheetFor by remember { mutableStateOf<CollectionMedia?>(null) }

    BackHandler(enabled = openCollection != null) { openCollection = null }

    val current = openCollection
    if (current != null) {
        PhotoCollectionDetail(
            collection = current,
            onBack = { openCollection = null },
            onItemClick = { runMediaAction(context, it, Intent.ACTION_VIEW) },
            onItemLongPress = { actionSheetFor = it },
        )
        actionSheetFor?.let { item ->
            MediaActionSheet(item = item, onDismiss = { actionSheetFor = null })
        }
        return
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Photo Collections",
                subtitle = if (hasPermission) "${images.size} photos · ${videos.size} videos on this device" else "Real collections, built on this device",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasPermission -> PhotoAccessCard(
                    requestedOnce = requestedOnce,
                    onGrant = {
                        if (requestedOnce) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure { Toast.makeText(context, "Could not open app settings", Toast.LENGTH_SHORT).show() }
                        } else {
                            permissionLauncher.launch(mediaPermissions())
                        }
                    },
                )
                isScanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = CiyatoGold)
                        Text("Scanning your photos and videos…", color = CiyatoMuted, style = bodyM)
                    }
                }
                collections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CiyatoEmptyState(
                        icon = Icons.Default.ImageNotSupported,
                        title = "No photos found",
                        subtitle = "Nothing on this device matches a collection yet.",
                        modifier = Modifier.padding(32.dp),
                    )
                }
                else -> {
                    if (partialAccess) {
                        PhotoPartialAccessBanner(onManage = { permissionLauncher.launch(mediaPermissions()) })
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(collections, key = { it.key }) { collection ->
                            PhotoCollectionCard(collection = collection, onClick = { openCollection = collection })
                        }
                    }
                }
            }
        }
    }
}

/** Runs an open/edit/share hand-off and says so out loud when nothing can take it. */
private fun runMediaAction(context: Context, item: CollectionMedia, action: String) {
    if (PhotoDeviceLibrary.launchPhotoAction(context, item.uri, action, item.mimeType)) return
    val message = when (action) {
        Intent.ACTION_EDIT -> if (item is CollectionMedia.Video) "No video editor installed on this phone" else "No photo editor installed on this phone"
        Intent.ACTION_SEND -> "No app can share this"
        else -> "No app can open this"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

// ── Collection building (real MediaStore data only) ─────────────────────────────

private data class PhotoCollection(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val items: List<CollectionMedia>,
)

private sealed class CollectionMedia {
    abstract val uri: Uri
    abstract val name: String
    abstract val sizeBytes: Long
    abstract val mimeType: String

    data class Photo(val image: DeviceImage) : CollectionMedia() {
        override val uri: Uri get() = image.uri
        override val name: String get() = image.name
        override val sizeBytes: Long get() = image.sizeBytes
        override val mimeType: String get() = "image/*"
    }

    data class Video(val video: DeviceVideo) : CollectionMedia() {
        override val uri: Uri get() = video.uri
        override val name: String get() = video.name
        override val sizeBytes: Long get() = video.sizeBytes
        override val mimeType: String get() = "video/*"
    }
}

/**
 * Builds the suggested collections straight from already-loaded MediaStore results —
 * Screenshots and Recent reuse [PhotoDeviceLibrary.imagesForCollection] so the bucket
 * heuristics stay in one place; Large Photos and the month buckets ("Memories") are
 * simple client-side filters/[PhotoDeviceLibrary.timeline] over real capture dates.
 * A collection that would be empty is left out entirely.
 */
private fun buildPhotoCollections(images: List<DeviceImage>, videos: List<DeviceVideo>): List<PhotoCollection> = buildList {
    val screenshots = PhotoDeviceLibrary.imagesForCollection(images, "bucket:Screenshots").map { CollectionMedia.Photo(it) }
    if (screenshots.isNotEmpty()) {
        add(PhotoCollection("screenshots", "Screenshots", Icons.Default.Screenshot, CiyatoPurple, screenshots))
    }

    val recent = PhotoDeviceLibrary.imagesForCollection(images, "recent").map { CollectionMedia.Photo(it) }
    if (recent.isNotEmpty()) {
        add(PhotoCollection("recent", "Recent", Icons.Default.History, CiyatoGold, recent))
    }

    if (videos.isNotEmpty()) {
        add(PhotoCollection("videos", "Videos", Icons.Default.Movie, CiyatoBlue, videos.map { CollectionMedia.Video(it) }))
    }

    val large = images.filter { it.sizeBytes > LARGE_PHOTO_THRESHOLD }
        .sortedByDescending { it.sizeBytes }
        .map { CollectionMedia.Photo(it) }
    if (large.isNotEmpty()) {
        add(PhotoCollection("large", "Large Photos", Icons.Default.Storage, CiyatoBlue, large))
    }

    PhotoDeviceLibrary.timeline(images).forEach { (month, monthImages) ->
        add(PhotoCollection("month:$month", month, Icons.Default.CalendarToday, CiyatoAmber, monthImages.map { CollectionMedia.Photo(it) }))
    }
}

// ── Top-level collection grid ────────────────────────────────────────────────────

@Composable
private fun PhotoAccessCard(requestedOnce: Boolean, onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(CiyatoShapes.large)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.PhotoLibrary, null, tint = CiyatoGold, modifier = Modifier.size(26.dp))
        Text("Photo & video access needed", color = CiyatoWhite, style = headingM)
        Text(
            "Ciyato builds these collections from photos and videos already on your phone — nothing is uploaded. Grant access to see real counts and open them.",
            color = CiyatoMuted,
            style = bodyM,
        )
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
            Text(if (requestedOnce) "Open App Settings" else "Grant Access", color = CiyatoBg, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PhotoPartialAccessBanner(onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "You shared only some photos. Counts may be incomplete.",
            color = CiyatoSec,
            style = bodyM,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Select more",
            color = CiyatoBg,
            fontWeight = FontWeight.SemiBold,
            style = labelL,
            modifier = Modifier
                .clip(CiyatoShapes.full)
                .background(CiyatoGold)
                .clickable(onClick = onManage)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PhotoCollectionCard(collection: PhotoCollection, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.92f)
            .clip(CiyatoShapes.large)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large)
            .clickable(onClick = onClick),
    ) {
        MediaThumbImage(item = collection.items.firstOrNull(), fallbackIcon = collection.icon, fallbackTint = collection.accent)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        startY = 200f,
                    ),
                ),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(collection.title, color = CiyatoWhite, style = headingS, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${collection.items.size} item${if (collection.items.size == 1) "" else "s"}",
                color = CiyatoSec,
                style = bodyS,
            )
        }
    }
}

// ── Collection detail: real grid, open/edit/share ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoCollectionDetail(
    collection: PhotoCollection,
    onBack: () -> Unit,
    onItemClick: (CollectionMedia) -> Unit,
    onItemLongPress: (CollectionMedia) -> Unit,
) {
    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = collection.title,
                subtitle = "${collection.items.size} item${if (collection.items.size == 1) "" else "s"}",
                onBack = onBack,
            )
        },
    ) { padding ->
        if (collection.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing left in this collection.", color = CiyatoMuted, style = bodyM)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(collection.items, key = { it.uri.toString() }) { item ->
                    MediaThumb(item = item, onClick = { onItemClick(item) }, onLongPress = { onItemLongPress(item) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumb(item: CollectionMedia, onClick: () -> Unit, onLongPress: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        MediaThumbImage(item = item, fallbackIcon = Icons.Default.Image, fallbackTint = CiyatoMuted)
        if (item is CollectionMedia.Video) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/**
 * Renders a real thumbnail for [item]. Photos go straight through Coil. Videos have
 * no format Coil can decode by default, so a real decoded frame is fetched via
 * [PhotoDeviceLibrary.loadVideoThumbnail] and shown as a plain Bitmap once ready.
 */
@Composable
private fun MediaThumbImage(item: CollectionMedia?, fallbackIcon: ImageVector, fallbackTint: Color) {
    when (item) {
        is CollectionMedia.Photo -> AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(CiyatoBgEl2),
        )
        is CollectionMedia.Video -> {
            val context = LocalContext.current
            var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(item.uri) {
                bitmap = PhotoDeviceLibrary.loadVideoThumbnail(context, item.uri, item.video.id)
            }
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(CiyatoBgEl2), contentAlignment = Alignment.Center) {
                    Icon(fallbackIcon, null, tint = fallbackTint, modifier = Modifier.size(28.dp))
                }
            }
        }
        null -> Box(Modifier.fillMaxSize().background(CiyatoBgEl2), contentAlignment = Alignment.Center) {
            Icon(fallbackIcon, null, tint = fallbackTint, modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaActionSheet(item: CollectionMedia, onDismiss: () -> Unit) {
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
                Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(CiyatoBg)) {
                    MediaThumbImage(item = item, fallbackIcon = Icons.Default.Image, fallbackTint = CiyatoMuted)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name.ifBlank { if (item is CollectionMedia.Video) "Video" else "Photo" },
                        color = CiyatoWhite,
                        style = headingS,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(MediaLibraryRepository.formatBytes(item.sizeBytes), color = CiyatoMuted, style = bodyS)
                }
            }
            Spacer(Modifier.height(16.dp))
            MediaActionRow(
                Icons.Default.OpenInNew,
                "Open",
                if (item is CollectionMedia.Video) "Play it in your video app" else "View it in your gallery app",
            ) {
                runMediaAction(context, item, Intent.ACTION_VIEW)
                onDismiss()
            }
            MediaActionRow(
                Icons.Default.Edit,
                "Edit",
                if (item is CollectionMedia.Video) "Trim or edit in your own video editor" else "Crop, filter or mark up in your own editor",
            ) {
                runMediaAction(context, item, Intent.ACTION_EDIT)
                onDismiss()
            }
            MediaActionRow(Icons.Default.Share, "Share", "Send it from any installed app") {
                runMediaAction(context, item, Intent.ACTION_SEND)
                onDismiss()
            }
        }
    }
}

@Composable
private fun MediaActionRow(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
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
            Text(label, color = CiyatoWhite, style = bodyL, fontWeight = FontWeight.Medium)
            Text(hint, color = CiyatoMuted, style = bodyS)
        }
    }
}
