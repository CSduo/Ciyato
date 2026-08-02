package com.ciyato.launcher.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * PinchZoomGrid — Suggestion #23
 * Detects pinch gestures to cycle home screen grid density:
 *   pinch-in  → denser grid (more apps visible)
 *   pinch-out → sparser grid (larger icons)
 *
 * Usage: attach pinchZoomGridModifier to the top-level home container.
 */

enum class GridDensityLevel(val columns: Int, val label: String) {
    COZY(2, "Cozy 2×"),
    STANDARD(4, "Standard 4×"),
    COMPACT(5, "Compact 5×"),
    DENSE(6, "Dense 6×"),
}

fun Modifier.pinchZoomGrid(
    currentDensity: GridDensityLevel,
    onDensityChange: (GridDensityLevel) -> Unit,
): Modifier = this.pointerInput(currentDensity) {
    val levels = GridDensityLevel.entries
    var accumulatedScale = 1f

    detectTransformGestures { _, _, zoom, _ ->
        accumulatedScale *= zoom
        val idx = levels.indexOf(currentDensity)

        when {
            accumulatedScale < 0.8f -> {
                accumulatedScale = 1f
                val next = (idx + 1).coerceAtMost(levels.lastIndex)
                if (next != idx) onDensityChange(levels[next])
            }
            accumulatedScale > 1.25f -> {
                accumulatedScale = 1f
                val prev = (idx - 1).coerceAtLeast(0)
                if (prev != idx) onDensityChange(levels[prev])
            }
        }
    }
}
