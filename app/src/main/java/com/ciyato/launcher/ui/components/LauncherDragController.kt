package com.ciyato.launcher.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Shared state for one floating drag across the whole launcher — workspace grid,
 * Home, and the dock. Everything is in ROOT coordinates so a single overlay can
 * render the lifted icon and hit-test drop zones no matter which container the
 * finger is over.
 *
 * Held once at the HomeScreen root via remember. Zones register their bounds on
 * layout; the active drag updates [fingerRoot] and resolves a [DropZone].
 */
class LauncherDragController {
    /** Package currently lifted, or null when idle. */
    var activePackage by mutableStateOf<String?>(null)
    var activeIcon by mutableStateOf<Drawable?>(null)

    /** Origin: exactly one of these is set while dragging. */
    var originPage by mutableStateOf<Int?>(null)
    var originDockIndex by mutableStateOf<Int?>(null)

    /** Finger position in root coordinates. */
    var fingerRoot by mutableStateOf(Offset.Zero)
    var startRoot by mutableStateOf(Offset.Zero)

    /** Resolved drop target. */
    var targetCell by mutableStateOf<Int?>(null)
    var overDock by mutableStateOf(false)

    /** Cell bounds for the CURRENT page only (cell index → root Rect). */
    val cellZones = mutableStateMapOf<Int, Rect>()
    /** Whole-dock bounds and per-slot bounds, in root coordinates. */
    var dockBounds by mutableStateOf<Rect?>(null)
    val dockSlots = mutableStateMapOf<Int, Rect>()

    val isActive: Boolean get() = activePackage != null

    fun begin(pkg: String, icon: Drawable?, fromPage: Int?, fromDockIndex: Int?, start: Offset) {
        activePackage = pkg
        activeIcon = icon
        originPage = fromPage
        originDockIndex = fromDockIndex
        startRoot = start
        fingerRoot = start
        targetCell = null
        overDock = false
    }

    /** True once the finger has travelled far enough to count as a move (not a still hold). */
    fun movedFar(thresholdPx: Float): Boolean = (fingerRoot - startRoot).getDistance() > thresholdPx

    /** Resolve which zone the finger is over. Dock wins over cells. */
    fun resolveTarget() {
        val finger = fingerRoot
        val dock = dockBounds
        if (dock != null && dock.contains(finger)) {
            overDock = true
            targetCell = null
            return
        }
        overDock = false
        targetCell = cellZones.entries.firstOrNull { it.value.contains(finger) }?.key
    }

    /** Nearest dock slot index for the current finger x (0 when the dock is empty). */
    fun dockDropIndex(): Int {
        if (dockSlots.isEmpty()) return 0
        return dockSlots.entries.minByOrNull { kotlin.math.abs(it.value.center.x - fingerRoot.x) }?.key ?: 0
    }

    fun reset() {
        activePackage = null
        activeIcon = null
        originPage = null
        originDockIndex = null
        targetCell = null
        overDock = false
    }
}
