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
 * Implemented by nested-scroll connections in this file that accumulate
 * state across a single gesture. [onPostFling] firing is NOT a reliable
 * end-of-gesture signal on its own — a deliberate drag that ends without
 * enough velocity to register as a fling never triggers it, leaving
 * accumulated state stale for whatever gesture comes next. Every such
 * connection must instead be reset explicitly on the NEXT touch-down, via
 * [directionResetPointerInput].
 */
interface GestureResettable {
    fun reset()
}

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
class DirectionalNestedScrollConnection : NestedScrollConnection, GestureResettable {
    private var isVerticalDirection: Boolean? = null

    /**
     * Must be called on every fresh touch-down (see [directionResetPointerInput]).
     * [onPostFling] alone isn't enough — a deliberate drag that ends without
     * enough velocity to fling never fires it, leaving the previous gesture's
     * direction classification stale. The very next swipe then inherits that
     * stale lock and can have its own motion silently eaten, which is exactly
     * what makes page swipes feel like they "don't register" sometimes.
     */
    override fun reset() {
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
 * Resets every listed [connections]' accumulated gesture state on every new
 * touch-down, using [PointerEventPass.Initial] so it fires before the
 * pager/column's own gesture detectors see the event — non-consuming, so it
 * never blocks them. Pair with a matching `.nestedScroll(connection)` for
 * each one on the same modifier chain. Accepts any number of connections so
 * a single touch-down pass can clear all of them at once (e.g. both the
 * directional lock and the drawer-swipe accumulator that sit on the same
 * Pager) — see [GestureResettable] for why this matters.
 */
fun Modifier.directionResetPointerInput(vararg connections: GestureResettable): Modifier =
    this.pointerInput(*connections) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            connections.forEach { it.reset() }
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
 *
 * Deliberate-only trigger rule — every condition below must hold, biased
 * hard toward "missed it" over "opened by accident":
 *  - DRAG path ([onPostScroll]): sustained upward travel the list couldn't
 *    consume reaches [thresholdPx] AND that travel is dominantly vertical
 *    (tracked against accumulated horizontal travel over the same span, via
 *    [VERTICAL_DOMINANCE_FACTOR]) — so a diagonal drag, or the vertical
 *    wobble in an ordinary horizontal page-swipe, can never satisfy it on
 *    its own. Only genuine finger-drag deltas count ([NestedScrollSource.Drag]) —
 *    the frame-by-frame replay of a list's OWN momentum settling into its
 *    end after the finger has already lifted arrives as
 *    [NestedScrollSource.Fling] and is ignored here entirely, so it can
 *    never masquerade as a fresh pull.
 *  - FLING path ([onPostFling]): only a fling the list did NOT itself mostly
 *    absorb — i.e. [consumed] is a small fraction of the gesture's total
 *    velocity, meaning the list was already exhausted before this fling
 *    began, not decelerating into the wall on its own steam — past a
 *    decisively fast, [VERTICAL_DOMINANCE_FACTOR]-dominant velocity
 *    ([FLING_OPEN_VELOCITY_PX_PER_S]). A fling that the list mostly consumed
 *    before running out of room is exactly "residual momentum from a fling
 *    the list itself started" and must never open the drawer.
 *  - [enabled] (edit mode / the app-drawer feature being off) and
 *    [isSuppressed] (wired by the caller to "a competing gesture — a home
 *    object drag — is active") both gate everything above.
 *  - [reset] clears all accumulated state; call it on every fresh
 *    touch-down (see [GestureResettable] / [directionResetPointerInput]) so
 *    a gesture that trails off without firing [onPostFling] can never leak
 *    its partial pull into the next, unrelated swipe.
 */
class DrawerSwipeNestedScrollConnection(private val thresholdPx: Float) : NestedScrollConnection, GestureResettable {
    var enabled: Boolean = true
    var onOpen: () -> Unit = {}

    /** Wired by the caller to true while a competing gesture — a home
     *  object drag, most notably — is active, so a stray upward scroll
     *  during that drag can never also open the drawer. */
    var isSuppressed: () -> Boolean = { false }

    private var verticalTravel = 0f
    private var horizontalTravel = 0f
    private var openedThisGesture = false

    override fun reset() {
        verticalTravel = 0f
        horizontalTravel = 0f
        openedThisGesture = false
    }

    private fun blocked(): Boolean = !enabled || openedThisGesture || isSuppressed()

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Only a live finger drag counts toward sustained pull distance —
        // the tail of the list's own fling settling into its end
        // (source == Fling, dispatched frame-by-frame after the finger has
        // already lifted) must never be mistaken for a fresh deliberate pull.
        if (source != NestedScrollSource.Drag || blocked()) return Offset.Zero

        horizontalTravel += abs(available.x)
        if (available.y >= 0f) {
            verticalTravel = 0f
            horizontalTravel = 0f
            return Offset.Zero
        }
        verticalTravel += -available.y

        val dominantlyVertical = verticalTravel >= horizontalTravel * VERTICAL_DOMINANCE_FACTOR
        if (dominantlyVertical && verticalTravel >= thresholdPx) {
            openedThisGesture = true
            onOpen()
            // Claim the remainder so the (already-exhausted) scrollable
            // doesn't also try to react to it, e.g. with an overscroll glow.
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!blocked()) {
            val totalY = abs(consumed.y) + abs(available.y)
            val listConsumedFraction = if (totalY > 0f) abs(consumed.y) / totalY else 0f
            // The list absorbed most of this fling's velocity itself before
            // running out of room — that's its OWN momentum finishing, not a
            // decisive flick aimed past it.
            val notResidualListMomentum = listConsumedFraction <= FLING_MAX_LIST_CONSUMED_FRACTION
            val dominantlyVertical = abs(available.y) >= abs(available.x) * VERTICAL_DOMINANCE_FACTOR
            val decisiveFlick = available.y < -FLING_OPEN_VELOCITY_PX_PER_S
            if (notResidualListMomentum && dominantlyVertical && decisiveFlick) onOpen()
        }
        reset()
        return Velocity.Zero
    }
}

/** How much more vertical than horizontal travel a gesture needs to count
 *  as "dominantly vertical" for the drawer swipe — roughly a 34-degree cone
 *  either side of straight up. Deliberately stricter than
 *  [DirectionalNestedScrollConnection]'s 0.75 factor (a ~53-degree cone):
 *  that one only needs to stop a horizontal pager from stealing a vertical
 *  swipe, this one gates an action that shouldn't fire by accident at all. */
private const val VERTICAL_DOMINANCE_FACTOR = 1.5f

/** Raised from the original 600 px/s, which — being a raw pixel velocity
 *  with no density scaling — could be as gentle as ~200dp/s on a dense
 *  screen: an easy, easily-accidental flick. 1500 px/s asks for a real,
 *  decisive upward throw. */
private const val FLING_OPEN_VELOCITY_PX_PER_S = 1500f

/** Above this fraction of a fling's total (consumed + available) vertical
 *  velocity having been absorbed by the list itself, the leftover is
 *  treated as that fling's own residual momentum hitting the end — not a
 *  fresh decisive flick — and never opens the drawer. */
private const val FLING_MAX_LIST_CONSUMED_FRACTION = 0.2f

/** Remembers a [DrawerSwipeNestedScrollConnection] for [thresholdPx], keeping
 *  [enabled]/[onOpen]/[isSuppressed] current across recomposition without
 *  tearing the connection down mid-gesture (which would lose its
 *  accumulated drag). [isSuppressed] defaults to "never suppressed" for
 *  callers that have no competing gesture to report. */
@Composable
fun rememberDrawerSwipeNestedScrollConnection(
    thresholdPx: Float,
    enabled: Boolean,
    onOpen: () -> Unit,
    isSuppressed: () -> Boolean = { false },
): DrawerSwipeNestedScrollConnection {
    val connection = remember(thresholdPx) { DrawerSwipeNestedScrollConnection(thresholdPx) }
    connection.enabled = enabled
    connection.onOpen = onOpen
    connection.isSuppressed = isSuppressed
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
