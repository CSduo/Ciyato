package com.ciyato.launcher.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.MediaLibraryRepository
import com.ciyato.launcher.data.MediaLibraryRepository.CategoryKey
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBgEl2
import com.ciyato.launcher.ui.theme.CiyatoBlue
import com.ciyato.launcher.ui.theme.CiyatoBorder
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoGreen
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoPurple
import com.ciyato.launcher.ui.theme.CiyatoRed
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoShapes
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * Organizer home — a real file/storage dashboard.
 * Storage ring + category tiles + recent files + quick actions.
 */
@Composable
fun DashboardScreen(
    viewModel: LauncherViewModel,
    onOpenFiles: () -> Unit = {},
    /**
     * Storage Cleanup.
     *
     * The tile here was labelled "AI Cleanup" with a sparkle icon and simply
     * called onOpenFiles — the same destination as the storage card and the
     * "View all" link, so three controls did one thing and none of them cleaned
     * anything (F-081). There was no AI involved at any point. It now says
     * "Clean up" and opens the screen that actually performs a cleanup.
     */
    onOpenCleanup: () -> Unit = {},
    onOpenPhotos: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val mediaRepo = remember { MediaLibraryRepository(context) }

    var hasPermission by remember { mutableStateOf(mediaRepo.hasMediaPermission()) }
    var summaries by remember { mutableStateOf<Map<CategoryKey, MediaLibraryRepository.CategorySummary>>(emptyMap()) }
    var recents by remember { mutableStateOf<List<MediaLibraryRepository.LibraryFile>>(emptyList()) }
    val storage = remember { mediaRepo.storageSummary() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermission = mediaRepo.hasMediaPermission() }

    // Re-check on every resume so granting from system Settings (the only path
    // left after a permanent denial) updates the dashboard without a restart.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = mediaRepo.hasMediaPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            summaries = mediaRepo.categorySummaries()
            recents = mediaRepo.recentFiles(limit = 10)
        }
    }

    LazyColumn(
        modifier = Modifier.background(CiyatoBg),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                // "Ciyato Files" while a separate Files tab sits in the same
                // bar, on a tab that was itself called Home. The screen is the
                // organizer's overview — storage, categories, quick actions —
                // so it says that (F-071, F-073).
                Text("Ciyato Organizer", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Spacer(Modifier.height(2.dp))
                Text("Storage, categories and cleanup.", color = CiyatoGold, fontSize = 13.sp)
            }
        }

        item {
            StorageOverviewCard(storage = storage, onReview = onOpenFiles)
        }

        if (!hasPermission) {
            item {
                PermissionCard(
                    onGrant = {
                        val permissions = if (Build.VERSION.SDK_INT >= 33) {
                            arrayOf(
                                android.Manifest.permission.READ_MEDIA_IMAGES,
                                android.Manifest.permission.READ_MEDIA_VIDEO,
                            )
                        } else {
                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissionLauncher.launch(permissions)
                    },
                )
            }
        } else {
            item {
                Text("Categories", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            item {
                CategoryGrid(summaries = summaries, onOpenCategory = onOpenCategory)
            }
            if (recents.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent files", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(
                            "View all",
                            color = CiyatoGold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(onClick = onOpenFiles),
                        )
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recents, key = { it.uri.toString() }) { file ->
                            RecentFileChip(file)
                        }
                    }
                }
            }
        }

        item {
            Text("Quick actions", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Default.DeleteSweep, "Clean up", CiyatoGold, Modifier.weight(1f), onOpenCleanup)
                QuickAction(Icons.Default.Search, "Search", CiyatoBlue, Modifier.weight(1f), onOpenSearch)
                QuickAction(Icons.Default.Image, "Photos", CiyatoPurple, Modifier.weight(1f), onOpenPhotos)
            }
        }
    }
}

// ── pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun StorageOverviewCard(
    storage: MediaLibraryRepository.StorageSummary,
    onReview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoBorder, CiyatoShapes.medium)
            .clickable(onClick = onReview)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StorageRing(fraction = storage.usedFraction, modifier = Modifier.size(84.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("Storage overview", color = CiyatoSec, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${MediaLibraryRepository.formatBytes(storage.usedBytes)} used",
                color = CiyatoWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Text(
                "of ${MediaLibraryRepository.formatBytes(storage.totalBytes)} total",
                color = CiyatoMuted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun StorageRing(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = CiyatoGold,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(fraction * 100).toInt()}%",
                color = CiyatoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text("Used", color = CiyatoMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoBorder, CiyatoShapes.medium)
            .padding(18.dp),
    ) {
        Text("See your files here", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Allow media access to show categories, recent files, and cleanup suggestions. Everything stays on this device.",
            color = CiyatoMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Allow access",
                color = CiyatoBg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(CiyatoShapes.full)
                    .background(CiyatoGold)
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Spacer(Modifier.width(14.dp))
            // Fallback for a permanent denial, where the system dialog no longer
            // appears — open the app's settings page so access can be granted.
            Text(
                "Open settings",
                color = CiyatoSec,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

private data class CategoryTileSpec(
    val key: CategoryKey,
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

private val CATEGORY_TILES = listOf(
    CategoryTileSpec(CategoryKey.SCREENSHOTS, "Screenshots", Icons.Default.Screenshot, CiyatoBlue),
    CategoryTileSpec(CategoryKey.DOCUMENTS, "Documents", Icons.Default.Article, CiyatoGreen),
    CategoryTileSpec(CategoryKey.DOWNLOADS, "Downloads", Icons.Default.Download, CiyatoGold),
    CategoryTileSpec(CategoryKey.PHOTOS, "Photos", Icons.Default.Image, CiyatoPurple),
    CategoryTileSpec(CategoryKey.VIDEOS, "Videos", Icons.Default.Movie, CiyatoRed),
    CategoryTileSpec(CategoryKey.APKS, "APKs", Icons.Default.Android, CiyatoGreen),
    CategoryTileSpec(CategoryKey.WHATSAPP, "WhatsApp", Icons.Default.Whatsapp, CiyatoGreen),
)

@Composable
private fun CategoryGrid(
    summaries: Map<CategoryKey, MediaLibraryRepository.CategorySummary>,
    onOpenCategory: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CATEGORY_TILES.chunked(3).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { tile ->
                    val summary = summaries[tile.key]
                    CategoryTile(
                        spec = tile,
                        count = summary?.count ?: 0,
                        // No summary yet (still loading) is also "unknown", not zero.
                        unavailable = summary == null || summary.unavailable,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCategory(tile.key.name) },
                    )
                }
                repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    spec: CategoryTileSpec,
    count: Int,
    /**
     * True when the count could not be read at all.
     *
     * Rendering 0 for a failed query is how a denied permission ends up looking
     * like an empty phone — a confident wall of zeroes that reads as fact
     * (F-083). "—" says the number is unknown, which is the truth.
     */
    unavailable: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoBorder, CiyatoShapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CiyatoShapes.small)
                .background(spec.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(spec.icon, contentDescription = spec.label, tint = spec.color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(spec.label, color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
        Text(if (unavailable) "—" else "$count", color = CiyatoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun RecentFileChip(file: MediaLibraryRepository.LibraryFile) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoBorder, CiyatoShapes.medium)
            .clickable {
                runCatching {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(file.uri, file.mimeType.ifBlank { "*/*" })
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    android.widget.Toast.makeText(context, "No app found to open ${file.name}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(12.dp),
    ) {
        Icon(
            Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = CiyatoSec,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            file.name,
            color = CiyatoWhite,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            MediaLibraryRepository.formatBytes(file.sizeBytes),
            color = CiyatoMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(CiyatoShapes.medium)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoBorder, CiyatoShapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CiyatoShapes.full)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = CiyatoSec, fontSize = 12.sp)
    }
}
