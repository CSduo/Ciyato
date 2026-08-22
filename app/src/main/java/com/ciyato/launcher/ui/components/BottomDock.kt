package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.*

/**
 * Premium bottom dock: up to 5 real app icons in a frosted-glass pill.
 * Icons are fully clickable, removable, and reorderable via drag-and-drop.
 * Surrounding icons slide away with smooth spring animations when an app is hovered over them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomDock(
    dockApps: List<InstalledApp>,
    onAppTap: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveApp: ((InstalledApp) -> Unit)? = null,
    isEditMode: Boolean = false,
    isDragActive: Boolean = false,
    hoverSlotIndex: Int? = null,
    hiddenPackage: String? = null,
    onDockBounds: (Rect) -> Unit = {},
    onSlotBounds: (index: Int, bounds: Rect) -> Unit = { _, _ -> },
    tileGesture: (Modifier, index: Int, app: InstalledApp) -> Modifier = { m, _, _ -> m },
) {
    val isDropTargetActive = isDragActive || isEditMode
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .onGloballyPositioned { onDockBounds(it.boundsInRoot()) }
            .clip(RoundedCornerShape(32.dp))
            .background(
                if (dockApps.isEmpty() && isDropTargetActive) CiyatoGold.copy(alpha = 0.12f)
                else CiyatoBgEl2.copy(alpha = 0.88f)
            )
            .border(
                width = 1.dp,
                color = if (dockApps.isEmpty() && isDropTargetActive) CiyatoGold.copy(alpha = 0.6f) else CiyatoSubtleBorder,
                shape = RoundedCornerShape(32.dp),
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (dockApps.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    tint = CiyatoGold,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Drop app here to pin to Dock",
                    color = CiyatoGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (dockApps.size >= 4) {
                    Arrangement.SpaceBetween
                } else {
                    Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dockApps.take(5).forEachIndexed { index, app ->
                    val slotBase = Modifier.onGloballyPositioned { onSlotBounds(index, it.boundsInRoot()) }
                    val gestured = tileGesture(slotBase, index, app)

                    // Calculate sliding displacement when an icon is dragged over dock slots
                    val isDisplaced = isDragActive && hoverSlotIndex != null && index >= hoverSlotIndex && app.packageName != hiddenPackage
                    val displacementX by animateFloatAsState(
                        targetValue = if (isDisplaced) 32f else 0f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f),
                        label = "dock_displace_x"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = displacementX
                                if (app.packageName == hiddenPackage) alpha = 0f
                            }
                    ) {
                        Box(
                            // Long-press is owned solely by [tileGesture]'s drag
                            // detector. A combinedClickable(onLongClick) here as
                            // well meant two long-press detectors raced on every
                            // dock icon, so drags were routinely hijacked.
                            modifier = gestured.clickable { onAppTap(app) }
                        ) {
                            RealAppIcon(
                                drawable = app.icon,
                                size = 56.dp,
                                cornerRadius = 15.dp,
                                scale = app.iconScale,
                                rotation = app.iconRotation,
                                accentHex = app.iconAccent,
                            )

                            // Show remove 'X' badge in edit mode
                            if (isEditMode && onRemoveApp != null) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .clip(CircleShape)
                                        .background(CiyatoGold)
                                        .clickable { onRemoveApp(app) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from dock",
                                        tint = CiyatoBg,
                                        modifier = Modifier.size(12.dp)
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
