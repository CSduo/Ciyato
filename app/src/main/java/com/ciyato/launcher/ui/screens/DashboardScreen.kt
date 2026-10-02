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
import androidx.compose.runtime.derivedStateOf
import com.ciyato.launcher.data.MediaAccess
import com.ciyato.launcher.ui.theme.currentWidth
import com.ciyato.launcher.ui.theme.CiyatoWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlin.math.roundToInt
import java.text.NumberFormat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import com.ciyato.launcher.ui.components.openWithApp
import androidx.compose.ui.layout.ContentScale
import coil.request.ImageRequest
import coil.compose.AsyncImage
import androidx.compose.ui.layout.layout

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

    // The access LEVEL, not a boolean. hasMediaPermission() answers true for
    // Android 14's "Select photos" partial grant, where MediaStore reports only
    // the hand-picked subset — so every category count below would describe that
    // subset while being presented as the device's library (F-082). Same defect
    // as F-115 in Storage Cleanup, same owner reused.
    var access by remember { mutableStateOf(MediaAccess.of(context)) }
    val hasPermission by remember { derivedStateOf { access.canSeeAnything } }
    var summaries by remember { mutableStateOf<Map<CategoryKey, MediaLibraryRepository.CategorySummary>>(emptyMap()) }
    var recents by remember { mutableStateOf<List<MediaLibraryRepository.LibraryFile>>(emptyList()) }
    val storage = remember { mediaRepo.storageSummary() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { access = MediaAccess.of(context) }

    // Re-check on every resume so granting from system Settings (the only path
    // left after a permanent denial) updates the dashboard without a restart.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                access = MediaAccess.of(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(access) {
        if (access.canSeeAnything) {
            summaries = mediaRepo.categorySummaries()
            recents = mediaRepo.recentImages(limit = 12)
        }
    }

    // The status bar inset. The activity draws edge to edge and nothing above this
    // screen applies the inset, so a flat 24dp top padding put the title INSIDE the
    // status bar - "Ciyato Organizer" sat almost touching the clock.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CiyatoBg)
            // A faint indigo light behind the top of the screen. Drawn on the list's
            // own bounds, so it stays put while content scrolls over it - depth
            // without a single extra pixel of colour on any surface.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(StorageGlow.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.18f, statusBarTop.toPx() + 60.dp.toPx()),
                        radius = size.width * 1.05f,
                    ),
                )
            }
            // A scrim under the status bar, drawn OVER the list. Without it, scrolled
            // content ran straight beneath the clock - a tile's "428" sat on top of
            // "20:45". This fades it out instead, the way a native screen does.
            .drawWithContent {
                drawContent()
                // Solid through the status bar, fading only BELOW it. A plain two-stop
                // gradient across the whole height started fading at the top of the
                // screen, so by the clock's own row it was already three-quarters
                // transparent and a tile's size bar showed straight through.
                val bar = statusBarTop.toPx()
                val h = bar + 18.dp.toPx()
                drawRect(
                    Brush.verticalGradient(
                        0f to CiyatoBg.copy(alpha = 0.97f),
                        (bar / h) to CiyatoBg.copy(alpha = 0.90f),
                        1f to CiyatoBg.copy(alpha = 0f),
                        startY = 0f,
                        endY = h,
                    ),
                    size = Size(size.width, h),
                )
            },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = statusBarTop + 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                // "Ciyato Files" while a separate Files tab sits in the same
                // bar, on a tab that was itself called Home. The screen is the
                // organizer's overview — storage, categories, quick actions —
                // so it says that (F-071, F-073).
                Text(
                    "Ciyato Organizer",
                    color = CiyatoWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.6).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("Storage, categories and cleanup.", color = CiyatoMuted, fontSize = 14.sp)
            }
        }

        item {
            StorageOverviewCard(storage = storage, access = access, onReview = onOpenFiles)
        }

        // Directly under storage, because it is the action that number prompts. It
        // used to be one of three equal "quick actions" at the bottom of the screen -
        // beside Search and Photos, both already in the bottom bar - so the one thing
        // a storage screen exists to help with was the last thing on it.
        item {
            CleanupCard(onClick = onOpenCleanup)
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
                Text("Categories", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.2).sp, modifier = Modifier.padding(top = 6.dp))
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
                        Text(
                            "Recent images",
                            color = CiyatoWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.2).sp,
                        )
                        Text(
                            "View all",
                            color = CiyatoSec,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(CiyatoShapes.full)
                                .clickable(onClickLabel = "View all images", role = Role.Button, onClick = onOpenPhotos)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                item {
                    // Edge to edge. Inside the list's 20dp margins the strip was sliced
                    // at the margin, so the last visible thumbnail ended in a hard
                    // vertical cut mid-picture. Widening the row by both margins and
                    // padding its content back in keeps the first image aligned with
                    // everything above it, and lets the rest run off the screen edge -
                    // which is what says "there is more this way".
                    LazyRow(
                        modifier = Modifier.layout { measurable, constraints ->
                            val bleed = 20.dp.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + bleed * 2,
                                    maxWidth = constraints.maxWidth + bleed * 2,
                                ),
                            )
                            layout(placeable.width - bleed * 2, placeable.height) {
                                placeable.place(-bleed, 0)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recents, key = { it.uri.toString() }) { file ->
                            RecentImageTile(file)
                        }
                    }
                }
            }
        }

    }
}

// ── pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun StorageOverviewCard(
    storage: MediaLibraryRepository.StorageSummary,
    /**
     * How much of the library the category counts below could see.
     *
     * Passed in rather than re-derived, so the caption under the ring cannot
     * drift from the data it is describing (F-084).
     */
    access: MediaAccess,
    onReview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF171B22), Color(0xFF0F1115))))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(StorageGlow.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.14f, size.height * 0.5f),
                        radius = size.height * 1.4f,
                    ),
                )
            }
            .border(1.dp, TileEdge, CardShape)
            .clickable(onClickLabel = "Review storage", role = Role.Button, onClick = onReview)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StorageRing(fraction = storage.usedFraction, modifier = Modifier.size(96.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "STORAGE",
                color = CiyatoMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    MediaLibraryRepository.formatBytes(storage.usedBytes),
                    color = CiyatoWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(6.dp))
                Text("used", color = CiyatoSec, fontSize = 14.sp, modifier = Modifier.alignByBaseline())
            }
            Text(
                "of ${MediaLibraryRepository.formatBytes(storage.totalBytes)}",
                color = CiyatoMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            // These two numbers are not commensurable and sat side by side as if
            // they were. The ring is whole-partition usage from StatFs, which
            // needs no permission and includes the OS and every app. The category
            // counts below come from MediaStore and only ever describe what
            // Ciyato is allowed to see, so they can never sum to it — and under a
            // partial grant they are a subset of a subset (F-084).
            Text(
                if (access.totalsAreComplete) {
                    "Whole device, including the system and other apps"
                } else {
                    "Whole device. The categories below cover only what Ciyato can see"
                },
                color = CiyatoMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun StorageRing(fraction: Float, modifier: Modifier = Modifier) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = 10.dp.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            // A linear rather than sweep gradient: a sweep starts at three o'clock,
            // so the round start cap of an arc beginning at twelve picks up the
            // gradient's END colour and shows a seam. Linear has no seam to show.
            if (clamped > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(RingStart, RingEnd),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // roundToInt, not toInt: 20.9% used is "21%", and truncation said 20.
            Text(
                "${(clamped * 100).roundToInt()}%",
                color = CiyatoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                letterSpacing = (-0.5).sp,
            )
            Text("used", color = CiyatoMuted, fontSize = 11.sp)
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
            "Allow media access to show categories, recent files, and cleanup suggestions. Your files are read on this phone and never uploaded.",
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

/**
 * One category on the Organizer, and how it is drawn.
 *
 * [glow] is the light stop and [deep] the dark stop of the glyph's gradient, and
 * every category has its own pair. Three of these used to share CiyatoGreen, so
 * Documents, APKs and WhatsApp were the same flat green square and could only be
 * told apart by reading the label - which defeats the point of an icon.
 *
 * Colour lives only in the glyph and a faint glow from its corner. The surfaces stay
 * graphite, which keeps the near-black/silver direction the audit asked to preserve:
 * the chips carry each category's identity, the way a well-made settings screen does,
 * instead of the whole screen turning into a rainbow.
 */
private data class CategoryTileSpec(
    val key: CategoryKey,
    val label: String,
    val icon: ImageVector,
    val glow: Color,
    val deep: Color,
)

private val CATEGORY_TILES = listOf(
    CategoryTileSpec(CategoryKey.SCREENSHOTS, "Screenshots", Icons.Rounded.Screenshot, Color(0xFF60A5FA), Color(0xFF1E3A8A)),
    CategoryTileSpec(CategoryKey.DOCUMENTS, "Documents", Icons.Rounded.Description, Color(0xFFFBBF24), Color(0xFF92400E)),
    CategoryTileSpec(CategoryKey.DOWNLOADS, "Downloads", Icons.Rounded.Download, Color(0xFF818CF8), Color(0xFF312E81)),
    CategoryTileSpec(CategoryKey.PHOTOS, "Photos", Icons.Rounded.Image, Color(0xFFC084FC), Color(0xFF581C87)),
    CategoryTileSpec(CategoryKey.VIDEOS, "Videos", Icons.Rounded.Movie, Color(0xFFFB7185), Color(0xFF881337)),
    // Graphite rather than another colour: APKs are system-adjacent files, and one
    // neutral member stops the set from reading as a rainbow.
    CategoryTileSpec(CategoryKey.APKS, "APKs", Icons.Rounded.Android, Color(0xFF94A3B8), Color(0xFF1E293B)),
    CategoryTileSpec(CategoryKey.WHATSAPP, "WhatsApp", Icons.Rounded.Whatsapp, Color(0xFF4ADE80), Color(0xFF14532D)),
)

/**
 * How many category tiles fit across, by window width.
 *
 * Two on a compact phone rather than three: a tile carries an icon, a title and
 * a count, and three of those at 320dp compressed the labels to the point of
 * truncation.
 */
@Composable
private fun categoryColumns(): Int = when (currentWidth()) {
    CiyatoWidth.COMPACT -> 2
    CiyatoWidth.MEDIUM -> 3
    CiyatoWidth.EXPANDED -> 4
}

@Composable
private fun CategoryGrid(
    summaries: Map<CategoryKey, MediaLibraryRepository.CategorySummary>,
    onOpenCategory: (String) -> Unit,
) {
    // Rows of `columns`, not a FlowRow.
    //
    // FlowRow decides for itself where to break a line, and that made both earlier
    // versions fragile. `weight(1f, fill = false).fillMaxWidth(1f / columns)` shrank
    // every tile twice and left half the section empty. Measuring the width instead
    // was exact on paper, and then a sub-pixel rounding overflow made the second tile
    // of every row wrap, so the phone showed a single column. Both read as correct in
    // code and were wrong on the device. A Row cannot wrap.
    //
    // A single leftover tile is drawn wide rather than as a half-width orphan beside a
    // blank cell, which is what F-085 actually objected to: a lone card next to empty
    // space reads as missing content, a deliberate full-width tile reads as hierarchy.
    val columns = categoryColumns()
    val spacing = 12.dp
    // No size bar. An earlier pass drew one under every tile - each category's size
    // against the largest - and with no label even the app's owner could not tell
    // what the lines meant. A graphic that needs explaining is decoration; the size is
    // already written on the tile in plain words.
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        CATEGORY_TILES.chunked(columns).forEach { row ->
            val wide = row.size == 1 && columns > 1
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                row.forEach { tile ->
                    val summary = summaries[tile.key]
                    CategoryTile(
                        spec = tile,
                        summary = summary,
                        wide = wide,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onOpenCategory(tile.key.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    spec: CategoryTileSpec,
    /**
     * Null while the counts are still loading.
     *
     * Loading, unavailable and empty are three different sentences and the tile says
     * each one. Rendering 0 for a failed query is how a denied permission ends up
     * looking like an empty phone - a confident wall of zeroes that reads as fact
     * (F-083).
     */
    summary: MediaLibraryRepository.CategorySummary?,
    wide: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val known = summary?.takeUnless { it.unavailable }
    val countText = known?.let { NumberFormat.getIntegerInstance().format(it.count) } ?: "\u2014"
    // The size was always in CategorySummary and never shown. On a storage organizer
    // it is the number people came for: 428 screenshots is trivia, 227 MB of them is
    // a decision.
    val detail = when {
        summary == null -> "Counting\u2026"
        known == null -> "Unavailable"
        known.count == 0 -> "Empty"
        else -> MediaLibraryRepository.formatBytes(known.totalBytes)
    }

    // No colour on the surface itself. An earlier pass lit every tile with a glow in
    // its category colour, and seven glows on one screen is decoration doing the work
    // hierarchy should do. Colour now lives only in the glyph, where it carries meaning.
    val surface = modifier
        .clip(TileShape)
        .background(Brush.verticalGradient(listOf(TileTop, TileBottom)))
        .border(1.dp, TileEdge, TileShape)
        .clickable(onClickLabel = "Open ${spec.label}", role = Role.Button, onClick = onClick)
        .padding(16.dp)

    if (wide) {
        Row(surface, verticalAlignment = Alignment.CenterVertically) {
            CategoryGlyph(spec)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spec.label,
                        color = CiyatoWhite,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        countText,
                        color = CiyatoWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.4).sp,
                    )
                }
                Text(detail, color = CiyatoMuted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.30f),
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        Column(surface) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                CategoryGlyph(spec)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.30f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.weight(1f))
            Text(
                countText,
                color = CiyatoWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                letterSpacing = (-0.6).sp,
            )
            // maxLines = 1 with the default Clip overflow is what cut "Screenshots" to
            // "Screensh" with no ellipsis. Ellipsis stays correct now the tiles are the
            // right width, because a large system font scale can still overrun.
            Text(
                spec.label,
                color = CiyatoSec,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, color = CiyatoMuted, fontSize = 12.sp, maxLines = 1)
        }
    }
}


/**
 * The category's icon as a lit object rather than a printed swatch.
 *
 * Gradient fill, a highlight across the top half, and a shadow in the category's own
 * colour so the chip sits ON the tile. Shadow colour is honoured from API 28; on 26
 * and 27 it falls back to a neutral shadow, which still reads correctly.
 */
@Composable
private fun CategoryGlyph(spec: CategoryTileSpec, side: Dp = 44.dp) {
    Glyph(spec.icon, spec.glow, spec.deep, side)
}

@Composable
private fun Glyph(icon: ImageVector, glow: Color, deep: Color, side: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(side)
            .shadow(elevation = 8.dp, shape = GlyphShape, ambientColor = deep, spotColor = deep)
            .clip(GlyphShape)
            .background(Brush.linearGradient(listOf(glow, deep)))
            .drawWithContent {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        endY = size.height * 0.55f,
                    ),
                )
                drawContent()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(side * 0.5f))
    }
}

/**
 * The screen's one action, under the number that prompts it.
 *
 * The subtitle names exactly what the cleanup scan reviews - large files, screenshots
 * older than 30 days, and Downloads - and nothing it does not. A cleanup card promising
 * "junk" or "duplicates" here would be selling a scan this screen never runs.
 */
@Composable
private fun CleanupCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(Brush.verticalGradient(listOf(TileTop, TileBottom)))
            .border(1.dp, TileEdge, TileShape)
            .clickable(onClickLabel = "Open storage cleanup", role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Icons.Rounded.CleaningServices, Color(0xFF5EEAD4), Color(0xFF134E4A))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Free up space", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "Large files, old screenshots and downloads to review",
                color = CiyatoMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        // A pill rather than a chevron: this is the one action on the screen, and it
        // should look like one.
        Text(
            "Clean up",
            color = CiyatoBg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(CiyatoShapes.full)
                .background(CiyatoWhite)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * A recent image, as the image.
 *
 * Name and size were the whole of the old chip: a grey document icon, then
 * "Screenshot_20..." truncated to the point of telling nobody anything. The picture is
 * the information. The filename is still read out by TalkBack through the content
 * description, so nothing is lost for someone who cannot see the thumbnail.
 */
@Composable
private fun RecentImageTile(file: MediaLibraryRepository.LibraryFile) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(file.uri)
            // Decoded at thumbnail size. A full-resolution photo per tile is tens of
            // megabytes for a 112dp square.
            .size(320)
            .crossfade(true)
            .build(),
        contentDescription = file.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(112.dp)
            .clip(ThumbShape)
            .background(CiyatoBgEl2)
            .border(1.dp, CiyatoBorder, ThumbShape)
            .clickable(onClickLabel = "Open image", role = Role.Button) {
                openWithApp(
                    context,
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(file.uri, file.mimeType.ifBlank { "image/*" })
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "No app on this phone can open this image.",
                )
            },
    )
}

private val TileShape = RoundedCornerShape(22.dp)
private val GlyphShape = RoundedCornerShape(13.dp)
private val ThumbShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(24.dp)
private val TileTop = Color(0xFF15181D)
private val TileBottom = Color(0xFF0E1013)

/** A glass edge: brighter where light would catch the top, fading toward the bottom. */
private val TileEdge = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.03f)),
)
private val RingStart = Color(0xFF7DD3FC)
private val RingEnd = Color(0xFFA78BFA)
private val StorageGlow = Color(0xFF6366F1)




