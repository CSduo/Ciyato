package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite

/**
 * Fixed-grid workspace surface. Apps sit at explicit cells (row-major linear index).
 * Dynamically scales icon sizes, font sizes, line heights, and aspect ratios based on column count
 * so app labels remain 100% legible and visible across 4x5, 5x5, 6x5, and high-density grids.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceGrid(
    columns: Int,
    minRows: Int,
    cellApps: Map<Int, InstalledApp>,
    isEditMode: Boolean,
    onAppTap: (InstalledApp) -> Unit,
    onAppLongPress: (cell: Int, app: InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
    onCellBounds: (cell: Int, bounds: Rect) -> Unit = { _, _ -> },
    hiddenPackage: String? = null,
    highlightCell: Int? = null,
    expandedPackages: Set<String> = emptySet(),
    tileGesture: (Modifier, cell: Int, app: InstalledApp) -> Modifier = { m, _, _ -> m },
) {
    val cols = columns.coerceIn(3, 8)
    val maxCell = cellApps.keys.maxOrNull() ?: -1
    val neededRows = if (maxCell < 0) 0 else (maxCell / cols) + 1
    val effectiveRows = maxOf(minRows.coerceAtLeast(1), neededRows)

    // Calculate column-adaptive parameters for optimal legibility
    val (iconSize, fontSize, lineHeight, cellAspectRatio) = when {
        cols >= 6 -> Quad(38.dp, 9.5.sp, 11.sp, 0.70f)
        cols == 5 -> Quad(42.dp, 10.sp, 12.sp, 0.74f)
        else -> Quad(46.dp, 11.sp, 13.sp, 0.78f)
    }
    val spacing = if (cols >= 6) 4.dp else 6.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(effectiveRows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                repeat(cols) { col ->
                    val cell = row * cols + col
                    val app = cellApps[cell]
                    val isCellTargeted = highlightCell == cell

                    // Calculate sliding scale displacement when an app is hovered over a cell
                    val displaceScale by animateFloatAsState(
                        targetValue = if (isCellTargeted && app != null) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "grid_scale"
                    )

                    key(app?.packageName ?: "cell_$cell") {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(cellAspectRatio)
                                .onGloballyPositioned { onCellBounds(cell, it.boundsInRoot()) }
                                .graphicsLayer {
                                    scaleX = displaceScale
                                    scaleY = displaceScale
                                }
                                .then(
                                    if (isCellTargeted) {
                                        Modifier.border(2.dp, CiyatoGold, RoundedCornerShape(16.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                app != null && app.packageName != hiddenPackage ->
                                    WorkspaceAppTile(
                                        cell = cell,
                                        app = app,
                                        isEditMode = isEditMode,
                                        iconSize = iconSize,
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        onTap = onAppTap,
                                        onLongPress = onAppLongPress,
                                        tileGesture = tileGesture,
                                        isExpanded = app.packageName in expandedPackages,
                                    )
                                isEditMode && app == null -> EmptyCellPlaceholder()
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceAppTile(
    cell: Int,
    app: InstalledApp,
    isEditMode: Boolean,
    iconSize: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onTap: (InstalledApp) -> Unit,
    onLongPress: (cell: Int, app: InstalledApp) -> Unit,
    tileGesture: (Modifier, cell: Int, app: InstalledApp) -> Modifier,
    isExpanded: Boolean = false,
) {
    val gestured = tileGesture(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), cell, app)
    if (isExpanded) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = gestured
                .clip(RoundedCornerShape(16.dp))
                .background(com.ciyato.launcher.ui.theme.CiyatoBgEl)
                .border(1.dp, CiyatoGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { onTap(app) },
                    onLongClick = { onLongPress(cell, app) },
                )
                .padding(10.dp),
        ) {
            RealAppIcon(
                drawable = app.icon,
                size = iconSize * 1.3f,
                cornerRadius = (iconSize.value * 0.35f).dp,
                scale = app.iconScale,
                rotation = app.iconRotation,
                accentHex = app.iconAccent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                color = CiyatoWhite,
                fontSize = fontSize * 1.1f,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = gestured.combinedClickable(
                onClick = { onTap(app) },
                onLongClick = { onLongPress(cell, app) },
            ),
        ) {
            RealAppIcon(
                drawable = app.icon,
                size = iconSize,
                cornerRadius = (iconSize.value * 0.28f).dp,
                scale = app.iconScale,
                rotation = app.iconRotation,
                accentHex = app.iconAccent,
            )
            Text(
                text = app.label,
                color = CiyatoWhite,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyCellPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(CiyatoWhite.copy(alpha = 0.03f))
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(13.dp)),
    ) { Spacer(Modifier.height(0.dp)) }
}
