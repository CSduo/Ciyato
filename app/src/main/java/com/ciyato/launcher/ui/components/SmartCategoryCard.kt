package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.CustomCategoryPresentation
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoBgEl3
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite

/**
 * Category folder tile in the iOS App Library style: a rounded square with a
 * 2×2 mini-grid. Mini-icons launch their app directly; tapping anywhere else
 * opens the category. The label sits below the tile.
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
    onToggleSize: (() -> Unit)? = null,
    onAppTap: ((InstalledApp) -> Unit)? = null,
) {
    val boxHeight: Dp = when (tileSize) {
        "small" -> 84.dp
        "large" -> 148.dp
        else -> 112.dp
    }
    val iconMiniSize: Dp = when (tileSize) {
        "small" -> 26.dp
        "large" -> 40.dp
        else -> 32.dp
    }
    val isCompactGroup = category == AppCategory.CUSTOM &&
        customPresentation == CustomCategoryPresentation.GROUP
    val clusterIconSize: Dp = when (tileSize) {
        "small" -> 11.dp
        "large" -> 17.dp
        else -> 13.dp
    }

    val cardBackground = androidx.compose.ui.graphics.Color(0x2EFFFFFF)
    val cardBorder = androidx.compose.ui.graphics.Color(0x24FFFFFF)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(boxHeight)
                .clip(RoundedCornerShape(22.dp))
                .background(cardBackground)
                .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                .clickable(onClick = onTap)
                .padding(if (tileSize == "small") 8.dp else 12.dp),
        ) {
            if (category == AppCategory.CUSTOM) {
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
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CiyatoMuted.copy(alpha = 0.2f))
                        .align(Alignment.Center),
                )
            } else {
                // Exact iOS App Library folder behaviour:
                //   • up to 4 apps  → every app shown full-size, one per quadrant
                //     (like "Sugerencias").
                //   • 5+ apps       → 3 full-size icons (TL, TR, BL) + a 2×2
                //     mini-cluster in the bottom-right holding the rest
                //     (like "Recién añadidas"). Cluster tap opens the group;
                //     large icons launch their app directly.
                val gap = if (tileSize == "small") 5.dp else 7.dp
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
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "Long press and drag to move $displayName",
                            tint = CiyatoWhite,
                            modifier = Modifier.size(20.dp),
                        )
                        onToggleSize?.let { changeSize ->
                            IconButton(onClick = changeSize, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.ZoomOutMap,
                                    contentDescription = "Change $displayName size",
                                    tint = CiyatoGold,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
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
