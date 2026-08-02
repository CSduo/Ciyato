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
