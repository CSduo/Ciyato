package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * SwipeableHomeDrawer — Suggestion #13
 * Horizontal swipe gesture to transition between Home screen and App Drawer.
 *
 * Swipe LEFT → reveal App Drawer
 * Swipe RIGHT → return to Home (from Drawer)
 */

enum class DrawerState { HOME, DRAWER }

@Composable
fun SwipeableHomeDrawer(
    homeContent: @Composable () -> Unit,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }

    var drawerState by remember { mutableStateOf(DrawerState.HOME) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset = remember { Animatable(0f) }

    val targetOffset = when (drawerState) {
        DrawerState.HOME -> 0f
        DrawerState.DRAWER -> -screenWidthPx
    }

    LaunchedEffect(drawerState) {
        animatedOffset.animateTo(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = screenWidthPx * 0.3f
                        drawerState = when {
                            dragOffset < -threshold -> DrawerState.DRAWER
                            dragOffset > threshold -> DrawerState.HOME
                            else -> drawerState
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { _, amount ->
                        dragOffset += amount
                        val rawTarget = targetOffset + dragOffset
                        val clamped = rawTarget.coerceIn(-screenWidthPx * 1.05f, screenWidthPx * 0.05f)
                        animatedOffset.updateBounds(lowerBound = -screenWidthPx * 1.05f, upperBound = 0f)
                        kotlinx.coroutines.runBlocking {
                            animatedOffset.snapTo(clamped)
                        }
                    },
                )
            },
    ) {
        // Home layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = animatedOffset.value
                    alpha = 1f - (abs(animatedOffset.value) / screenWidthPx).coerceIn(0f, 0.3f)
                },
        ) {
            homeContent()
        }

        // Drawer layer (offset by full screen width to the right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = animatedOffset.value + screenWidthPx
                },
        ) {
            drawerContent()
        }
    }
}
