package com.ciyato.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Full-screen, non-interactive layer that paints the lifted icon following the
 * finger. Drawn LAST in the HomeScreen root so it floats above the pager and the
 * dock. It has no pointer input — the originating drag gesture keeps pointer
 * capture, so touches pass straight through.
 */
@Composable
fun DragOverlay(
    controller: LauncherDragController,
    iconSize: Dp = 54.dp,
) {
    val icon = controller.activeIcon
    if (!controller.isActive || icon == null) return
    val iconPx = with(LocalDensity.current) { iconSize.toPx() }
    Box(Modifier.fillMaxSize()) {
        RealAppIcon(
            drawable = icon,
            size = iconSize,
            cornerRadius = 15.dp,
            modifier = Modifier.graphicsLayer {
                translationX = controller.fingerRoot.x - iconPx / 2f
                translationY = controller.fingerRoot.y - iconPx / 2f
                scaleX = 1.12f
                scaleY = 1.12f
                alpha = 0.94f
                shadowElevation = 18f
            },
        )
    }
}
