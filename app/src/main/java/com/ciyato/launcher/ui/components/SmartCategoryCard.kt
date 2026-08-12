package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.CustomCategoryPresentation
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl3
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import kotlin.math.roundToInt

private val SMALL_HEIGHT = 84.dp
private val MEDIUM_HEIGHT = 112.dp
private val LARGE_HEIGHT = 148.dp
private val MIN_HEIGHT = 68.dp
private val MAX_HEIGHT = 240.dp
private val SNAP_TOLERANCE = 10.dp

/** "small" / "medium" / "large" resolve to their preset; anything else (a
 *  free-dragged size) is the raw dp value stored as a plain number string. */
private fun resolveTileHeight(tileSize: String): Dp = when (tileSize) {
    "small" -> SMALL_HEIGHT
    "large" -> LARGE_HEIGHT
    "medium" -> MEDIUM_HEIGHT
    else -> tileSize.toFloatOrNull()?.dp ?: MEDIUM_HEIGHT
}

/** Snaps close to a named preset; otherwise keeps the free-form value. */
private fun encodeTileHeight(height: Dp): String {
    val presets = listOf("small" to SMALL_HEIGHT, "medium" to MEDIUM_HEIGHT, "large" to LARGE_HEIGHT)
    presets.firstOrNull { (_, presetHeight) -> (height - presetHeight).value.let { it > -SNAP_TOLERANCE.value && it < SNAP_TOLERANCE.value } }
        ?.let { (name, _) -> return name }
    return height.value.roundToInt().toString()
}

/**
 * Category folder tile in the iOS App Library style: a rounded square with a
 * 2×2 mini-grid. Mini-icons launch their app directly; tapping anywhere else
 * opens the category. The label sits below the tile.
 *
 * In edit mode a resize handle in the bottom-right corner can be dragged to
 * grow or shrink the card — the card's actual size updates live as the
 * finger moves (no toast, no discrete jump), and releasing snaps to the
 * nearest fixed preset (small/medium/large) or keeps the free-form size if
 * it's not close to one.
 */
@Composable
fun SmartCategoryCard(
    category: AppCategory,
    displayName: String,
    apps: List<InstalledApp>,
    onTap: () -> Unit,
    customIcon: String = "folder",
    customPresentation: CustomCategoryPresentation = CustomCategoryPresentation.CARD,
    modifier: Modifier = Modifier,
    tileSize: String = "medium",
    isEditMode: Boolean = false,
    onResize: ((newSize: String) -> Unit)? = null,
    onAppTap: ((InstalledApp) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val restingHeight = resolveTileHeight(tileSize)

    var isResizing by remember { mutableStateOf(false) }
    var liveHeightPx by remember { mutableFloatStateOf(0f) }
    val boxHeight: Dp = if (isResizing) with(density) { liveHeightPx.toDp() } else restingHeight

    // Continuous 0..1 scale factor (small..large) so icons/spacing/padding
    // grow smoothly with the card instead of jumping between 3 fixed steps.
    val sizeFactor = ((boxHeight - SMALL_HEIGHT) / (LARGE_HEIGHT - SMALL_HEIGHT)).coerceIn(0f, 1f)
    fun lerp(small: Dp, large: Dp): Dp = small + (large - small) * sizeFactor

    val iconMiniSize = lerp(26.dp, 40.dp)
    val clusterIconSize = lerp(11.dp, 17.dp)
    val gap = lerp(5.dp, 7.dp)
    val innerPadding = lerp(8.dp, 12.dp)
    val isCompactGroup = category == AppCategory.CUSTOM &&
        customPresentation == CustomCategoryPresentation.GROUP

    val cardBackground = androidx.compose.ui.graphics.Color(0x2EFFFFFF)
    val cardBorder = androidx.compose.ui.graphics.Color(0x24FFFFFF)
    val cardShape = RoundedCornerShape(22.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(boxHeight)
                .clip(cardShape)
                .background(cardBackground)
                .border(1.dp, cardBorder, cardShape)
                .editableOutline(isEditMode, cardShape)
                .clickable(onClick = onTap)
                .padding(innerPadding),
        ) {
            // An empty folder shows no icon at all — the type badge and the
            // placeholder dot are both content indicators, so neither belongs
            // on a folder with nothing in it (only edit mode needs a hint that
            // the empty tile is still tappable/movable).
            if (category == AppCategory.CUSTOM && (apps.isNotEmpty() || isEditMode)) {
                val icon = when (customIcon) {
                    "bookmark" -> Icons.Default.Bookmark
                    "star" -> Icons.Default.Star
                    else -> Icons.Default.Folder
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(CiyatoBgEl3)
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(7.dp)),
                ) {
                    Icon(icon, contentDescription = null, tint = CiyatoSec, modifier = Modifier.size(13.dp))
                }
            }

            if (apps.isEmpty()) {
                // Nothing to draw — an empty folder is just its background tile.
            } else {
                // Exact iOS App Library folder behaviour:
                //   • up to 4 apps  → every app shown full-size, one per quadrant
                //     (like "Sugerencias").
                //   • 5+ apps       → 3 full-size icons (TL, TR, BL) + a 2×2
                //     mini-cluster in the bottom-right holding the rest
                //     (like "Recién añadidas"). Cluster tap opens the group;
                //     large icons launch their app directly.
                val hasCluster = apps.size > 4
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    repeat(2) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            repeat(2) { column ->
                                val quadrant = row * 2 + column
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        quadrant == 3 && hasCluster ->
                                            MiniAppCluster(apps.drop(3).take(4), clusterIconSize, onOpen = onTap)
                                        quadrant < apps.size -> {
                                            val app = apps[quadrant]
                                            Box {
                                                RealAppIcon(
                                                    drawable = app.icon,
                                                    size = iconMiniSize,
                                                    cornerRadius = 10.dp,
                                                    scale = app.iconScale,
                                                    rotation = app.iconRotation,
                                                    accentHex = app.iconAccent,
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

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CiyatoBg.copy(alpha = 0.65f)),
                ) {
                    Icon(
                        Icons.Default.DragIndicator,
                        contentDescription = "Long press and drag to move $displayName",
                        tint = CiyatoWhite,
                        modifier = Modifier.align(Alignment.Center).size(20.dp),
                    )
                    if (onResize != null) {
                        val minHeightPx = with(density) { MIN_HEIGHT.toPx() }
                        val maxHeightPx = with(density) { MAX_HEIGHT.toPx() }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(CiyatoGold.copy(alpha = 0.9f))
                                .pointerInput(displayName) {
                                    detectDragGestures(
                                        onDragStart = {
                                            liveHeightPx = with(density) { restingHeight.toPx() }
                                            isResizing = true
                                        },
                                        onDragEnd = {
                                            isResizing = false
                                            onResize(encodeTileHeight(with(density) { liveHeightPx.toDp() }))
                                        },
                                        onDragCancel = { isResizing = false },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            liveHeightPx = (liveHeightPx + dragAmount.y).coerceIn(minHeightPx, maxHeightPx)
                                        },
                                    )
                                },
                        ) {
                            Icon(
                                Icons.Default.ZoomOutMap,
                                contentDescription = "Drag to resize $displayName",
                                tint = CiyatoBg,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        Text(
            text = displayName,
            color = CiyatoWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** Bottom-right 2×2 cluster of the overflow apps. Tapping opens the group. */
@Composable
private fun MiniAppCluster(
    apps: List<InstalledApp>,
    iconSize: Dp,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    if (index < apps.size) {
                        val app = apps[index]
                        RealAppIcon(
                            drawable = app.icon,
                            size = iconSize,
                            cornerRadius = 4.dp,
                            scale = app.iconScale,
                            rotation = app.iconRotation,
                            accentHex = app.iconAccent,
                        )
                    } else {
                        Spacer(Modifier.size(iconSize))
                    }
                }
            }
        }
    }
}
