package com.ciyato.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * DirectionalNestedScrollConnection
 *
 * Prevents horizontal gesture listeners (such as HorizontalPager or horizontal swipeable drawers)
 * from hijacking vertical gestures (such as top-to-bottom downside slides or diagonal northwest/northeast
 * touch angles).
 *
 * When a scroll or drag starts:
 * - If vertical movement is detected (|dy| >= |dx| * 0.75), vertical intent is established.
 * - Horizontal pre-scroll is suppressed during vertical swipes so horizontal pagers don't flip pages.
 */
class DirectionalNestedScrollConnection : NestedScrollConnection {
    private var isVerticalDirection: Boolean? = null

    /**
     * Must be called on every fresh touch-down (see [directionResetPointerInput]).
     * [onPostFling] alone isn't enough — a deliberate drag that ends without
     * enough velocity to fling never fires it, leaving the previous gesture's
     * direction classification stale. The very next swipe then inherits that
     * stale lock and can have its own motion silently eaten, which is exactly
     * what makes page swipes feel like they "don't register" sometimes.
     */
    fun reset() {
        isVerticalDirection = null
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val absX = abs(available.x)
        val absY = abs(available.y)

        // Evaluate scroll direction intent on initial movement
        if (isVerticalDirection == null && (absX > 0.5f || absY > 0.5f)) {
            isVerticalDirection = absY >= absX * 0.75f
        }

        // If the gesture is vertical, consume horizontal pre-scroll to lock the HorizontalPager
        return if (isVerticalDirection == true && absX > 0f) {
            Offset(x = available.x, y = 0f)
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val absX = abs(available.x)
        val absY = abs(available.y)

        if (isVerticalDirection == null && (absX > 0.5f || absY > 0.5f)) {
            isVerticalDirection = absY >= absX * 0.75f
        }

        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Reset direction classification on gesture end
        isVerticalDirection = null
        return Velocity.Zero
    }
}

@Composable
fun rememberDirectionalNestedScrollConnection(): DirectionalNestedScrollConnection {
    return remember { DirectionalNestedScrollConnection() }
}

/**
 * Resets [connection]'s direction lock on every new touch-down, using
 * [PointerEventPass.Initial] so it fires before the pager/column's own
 * gesture detectors see the event — non-consuming, so it never blocks them.
 * Pair with `.nestedScroll(connection)` on the same modifier chain.
 */
fun Modifier.directionResetPointerInput(connection: DirectionalNestedScrollConnection): Modifier =
    this.pointerInput(connection) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            connection.reset()
        }
    }

/**
 * DrawerSwipeNestedScrollConnection
 *
 * Lets an upward drag or fling that a scrollable descendant (e.g. Home's
 * LazyColumn) can't itself consume — because it's already at the end of its
 * content, or has nothing scrollable at all — bubble up and open the app
 * drawer, from anywhere on the page.
 *
 * This replaces a raw `pointerInput { detectVerticalDragGestures(...) }` at
 * the screen root, which sounds like it should work but doesn't: Compose
 * delivers pointer events to the innermost consumer first on the default
 * (Main) pass, so a scrollable child always gets first claim on a vertical
 * drag, and a root-level detector only ever sees what's left over — in
 * practice, only drags that start somewhere the scrollable content doesn't
 * cover at all. Going through the nested-scroll system instead means the
 * drag doesn't need to start anywhere special: the moment the list can't
 * absorb any more of an upward drag, the remainder bubbles here.
 */
class DrawerSwipeNestedScrollConnection(private val thresholdPx: Float) : NestedScrollConnection {
    var enabled: Boolean = true
    var onOpen: () -> Unit = {}

    private var accumulated = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!enabled || available.y >= 0f) {
            accumulated = 0f
            return Offset.Zero
        }
        accumulated += available.y
        if (accumulated <= -thresholdPx) {
            accumulated = 0f
            onOpen()
            // Claim the remainder so the (already-exhausted) scrollable
            // doesn't also try to react to it, e.g. with an overscroll glow.
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A quick upward flick the list couldn't use at all (already at its
        // end, or nothing to scroll) often never accumulates enough
        // onPostScroll distance on its own to cross the threshold above —
        // treat a decisive flick the same as a held drag past it.
        if (enabled && accumulated <= 0f && available.y < -FLING_OPEN_VELOCITY_PX_PER_S) onOpen()
        accumulated = 0f
        return Velocity.Zero
    }
}

private const val FLING_OPEN_VELOCITY_PX_PER_S = 600f

/** Remembers a [DrawerSwipeNestedScrollConnection] for [thresholdPx], keeping
 *  [enabled]/[onOpen] current across recomposition without tearing the
 *  connection down mid-gesture (which would lose its accumulated drag). */
@Composable
fun rememberDrawerSwipeNestedScrollConnection(
    thresholdPx: Float,
    enabled: Boolean,
    onOpen: () -> Unit,
): DrawerSwipeNestedScrollConnection {
    val connection = remember(thresholdPx) { DrawerSwipeNestedScrollConnection(thresholdPx) }
    connection.enabled = enabled
    connection.onOpen = onOpen
    return connection
}

/**
 * Directional Touch Lock Modifier
 * Pointer input modifier that monitors initial touch down and movement to lock pointer direction.
 */
fun Modifier.directionalTouchLock(
    onVerticalSwipe: ((Offset) -> Unit)? = null,
    onHorizontalSwipe: ((Offset) -> Unit)? = null
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        var totalDx = 0f
        var totalDy = 0f
        var directionLocked = false
        var isVertical = false

        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            
            if (change.pressed) {
                val dragAmount = change.position - change.previousPosition
                totalDx += dragAmount.x
                totalDy += dragAmount.y

                if (!directionLocked && (abs(totalDx) > 8f || abs(totalDy) > 8f)) {
                    directionLocked = true
                    isVertical = abs(totalDy) >= abs(totalDx) * 0.75f
                }

                if (directionLocked) {
                    if (isVertical) {
                        onVerticalSwipe?.invoke(dragAmount)
                    } else {
                        onHorizontalSwipe?.invoke(dragAmount)
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
