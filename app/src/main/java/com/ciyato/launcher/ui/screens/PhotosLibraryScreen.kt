package com.ciyato.launcher.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ciyato.launcher.data.PhotoDeviceLibrary
import com.ciyato.launcher.data.PhotoDeviceLibrary.DeviceImage
import com.ciyato.launcher.ui.components.CiyatoTabRow
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) { COLLECTIONS("Collections"), GRID("Grid"), TIMELINE("Timeline") }

/**
 * Ciyato Photos — device-wide gallery in smart collections.
 * Falls back to the curated photo-picker flow when media permission is denied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosLibraryScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    fun mediaGranted(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(mediaGranted()) }
    var requestedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        hasPermission = mediaGranted()
        requestedOnce = true
    }
    // Returning from system Settings (the only path after a permanent denial)
    // re-checks so the gallery unlocks without a restart.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) hasPermission = mediaGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasPermission) {
        // Curated flow remains the no-permission experience, with a one-tap
        // upgrade to the full library.
        Box {
            PhotosScreen(viewModel = viewModel, onBack = onBack)
            Text(
                "Show full gallery",
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
                        if (requestedOnce && !hasPermission) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure {
                                android.widget.Toast.makeText(context, "Could not open app settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            permissionLauncher.launch(
                                if (Build.VERSION.SDK_INT >= 33) {
                                    android.Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                },
                            )
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        return
    }

    var images by remember { mutableStateOf<List<DeviceImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(LibraryTab.COLLECTIONS) }
    var openCollection by remember { mutableStateOf<String?>(null) }

    // System back inside an open collection returns to the collection list,
    // not straight out of Photos.
    androidx.activity.compose.BackHandler(enabled = openCollection != null) {
        openCollection = null
    }

    // Free on-device AI pass (ML Kit) — builds extra collections like Food/Pets/Nature.
    var aiCollections by remember { mutableStateOf<Map<String, List<DeviceImage>>>(emptyMap()) }
    var aiProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scanScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        images = PhotoDeviceLibrary.loadImages(context)
        isLoading = false
    }

    val collections = remember(images) { PhotoDeviceLibrary.collections(images) }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ciyato Photos", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text(
                            openCollection?.let { key -> collections.firstOrNull { it.key == key }?.title }
                                ?: "${images.size} photos on this device",
                            color = CiyatoGold,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (openCollection != null) openCollection = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        },
    ) { padding ->
        val openKey = openCollection
        if (openKey != null) {
            val collectionImages = remember(images, openKey, aiCollections) {
                if (openKey.startsWith("ai:")) {
                    aiCollections[openKey.removePrefix("ai:")].orEmpty()
                } else {
                    PhotoDeviceLibrary.imagesForCollection(images, openKey)
                }
            }
            PhotoGrid(
                images = collectionImages,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            )
            return@Scaffold
        }

        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            CiyatoTabRow(
                tabs = LibraryTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onTabSelected = { tab = LibraryTab.entries[it] },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                            hasResults = aiCollections.isNotEmpty(),
                            onScan = {
                                aiProgress = 0 to 0
                                scanScope.launch {
                                    val result = com.ciyato.launcher.data.PhotoAiLabeler.categorize(
                                        context = context,
                                        images = images,
                                        onProgress = { done, total -> aiProgress = done to total },
                                    )
                                    aiCollections = result.collections
                                    aiProgress = null
                                }
                            },
                        )
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
                                PhotoThumb(image)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiScanBanner(
    progress: Pair<Int, Int>?,
    hasResults: Boolean,
    onScan: () -> Unit,
) {
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
                    else -> "Group photos by what's in them — free, on-device, private."
                },
                color = CiyatoMuted,
                fontSize = 12.sp,
            )
        }
        if (progress == null) {
            Text(
                if (hasResults) "Rescan" else "Scan",
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
private fun PhotoGrid(images: List<DeviceImage>, contentPadding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(images, key = { it.uri.toString() }) { image ->
            PhotoThumb(image)
        }
    }
}

@Composable
private fun PhotoThumb(image: DeviceImage) {
    val context = LocalContext.current
    AsyncImage(
        model = image.uri,
        contentDescription = image.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(CiyatoBgEl)
            .clickable {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(image.uri, "image/*")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, "Could not open photo", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
    )
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
