package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * THE shared long-press-drag gesture primitive for every movable object in
 * the launcher. App grid tiles, dock tiles, and Home canvas objects (see
 * [HomeCanvasItem] below) all route through this ONE detector rather than
 * each hand-rolling their own `detectDragGesturesAfterLongPress` block.
 * Contract:
 *   - A plain tap is untouched — it belongs entirely to whatever `clickable`
 *     the caller's own content sets up; this modifier never sees it at all
 *     ([detectDragGesturesAfterLongPress] waits out the long-press window
 *     first, exactly like the tile gesture it replaces).
 *   - Long-press, then release without crossing [moveThresholdPx] of travel:
 *     [onPress] fires with `movedFar = false` — the context-menu path.
 *   - Long-press, then travel past [moveThresholdPx]: [onDragStart] fires
 *     once, [onDrag] fires for every subsequent delta, and [onPress] fires
 *     with `movedFar = true` on release — the commit path.
 *   - A gesture stolen by an ancestor (e.g. the pager) calls [onDragCancel]
 *     instead of [onPress].
 * A small haptic click fires automatically the instant the long-press
 * registers, so callers never each hand-roll their own
 * `performHapticFeedback` call.
 *
 * [key] MUST be something stable across the gesture (an id, never mutable
 * offset/position state this same gesture writes) — keying on mutated state
 * tears the detector down and restarts it mid-drag, which is exactly the bug
 * class this file exists to avoid repeating. There is never a second
 * long-press-sensitive gesture stacked on the same node as this one — a
 * resize handle (see SmartCategoryCard/WorkspaceGrid) is always a separate
 * child with its own, non-long-press [detectDragGesturesAfterLongPress]-free
 * detector, never layered on top of this.
 */
fun Modifier.canvasDraggable(
    key: Any?,
    enabled: Boolean = true,
    moveThresholdPx: Float,
    haptic: HapticFeedback,
    hapticEnabled: Boolean = true,
    onDragStart: (touch: Offset) -> Unit = {},
    onDrag: (amount: Offset) -> Unit = {},
    onPress: (movedFar: Boolean) -> Unit = {},
    onDragCancel: () -> Unit = {},
): Modifier = if (!enabled) this else this.pointerInput(key) {
    var traveled = 0f
    detectDragGesturesAfterLongPress(
        onDragStart = { touch ->
            traveled = 0f
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDragStart(touch)
        },
        onDrag = { change, amount ->
            change.consume()
            traveled += amount.getDistance()
            onDrag(amount)
        },
        onDragEnd = { onPress(traveled > moveThresholdPx) },
        onDragCancel = onDragCancel,
    )
}

/**
 * Shared wrapper for every free-positionable, non-app Home object (greeting,
 * date/time, search, weather, today, the categories heading, a category
 * card…) — THE PRINCIPLE's "independent interactive objects" made real. This
 * is also the one place the drag FEEL lives: long-press lifts the object to
 * ~108% with a touch of extra elevation and alpha separation, it tracks the
 * finger with no lag via a live [graphicsLayer] translation (no snapping,
 * no threshold delay once the drag has actually begun), and releasing
 * settles it back with a small spring. All of that is skipped (snapped
 * instantly) when [reduceMotion] is on. Built on the shared [canvasDraggable]
 * gesture primitive above — apps use the exact same primitive (see
 * HomeScreen's grid/dock tile gestures), just with their own floating-icon
 * rendering instead of this in-place transform.
 *
 * [canvasBounds] is the page's own safe canvas rect in ROOT coordinates —
 * already inset from the status bar and the dock/gesture area (see
 * HomeScreen's per-page canvas Box) — never raw screen bounds, so a drop can
 * never land somewhere the object couldn't be grabbed again. [onMoved]
 * receives the result as a 0f..1f fraction of that rect (anchoring the
 * object's own top-left corner, same convention as [com.ciyato.launcher.data.CanvasPos]),
 * already clamped.
 *
 * A plain tap is entirely the caller's own concern (a `clickable` inside
 * [content], or elsewhere on [modifier]) — this wrapper only ever reacts to
 * a long-press. [onTapHold] fires for "long-press, released without moving"
 * (open this object's menu); a real drag calls [onMoved] instead and never
 * fires [onTapHold].
 */
@Composable
fun HomeCanvasItem(
    objectId: String,
    isEditMode: Boolean,
    reduceMotion: Boolean,
    hapticEnabled: Boolean,
    canvasBounds: Rect?,
    onMoved: (x: Float, y: Float) -> Unit,
    onTapHold: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val moveThresholdPx = with(density) { 12.dp.toPx() }

    // The object's own last-settled (non-dragging) position in root
    // coordinates — captured BEFORE the graphicsLayer translation below, so
    // it reflects only real layout, never the live drag offset. That's what
    // lets a drop be computed as "rest position + total drag delta" without
    // re-measuring mid-drag.
    var restRoot by remember(objectId) { mutableStateOf<Offset?>(null) }
    var dragOffset by remember(objectId) { mutableStateOf(Offset.Zero) }
    var isDragging by remember(objectId) { mutableStateOf(false) }

    val liftScale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = if (isDragging) 0.7f else 0.55f,
            stiffness = if (isDragging) 700f else 300f,
        ),
        label = "canvas_item_scale",
    )
    val liftAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.94f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(stiffness = 500f),
        label = "canvas_item_alpha",
    )

    Box(
        modifier = modifier
            .onGloballyPositioned { restRoot = it.positionInRoot() }
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = liftScale
                scaleY = liftScale
                alpha = liftAlpha
                shadowElevation = if (isDragging) 16f else 0f
            }
            .then(if (isEditMode) Modifier.editableOutline(true) else Modifier)
            .canvasDraggable(
                key = objectId,
                moveThresholdPx = moveThresholdPx,
                haptic = haptic,
                hapticEnabled = hapticEnabled,
                onDragStart = { isDragging = true },
                onDrag = { amount -> dragOffset += amount },
                onPress = { movedFar ->
                    isDragging = false
                    if (movedFar) {
                        val bounds = canvasBounds
                        val origin = restRoot
                        if (bounds != null && bounds.width > 0f && bounds.height > 0f && origin != null) {
                            val dropped = origin + dragOffset
                            val fx = (dropped.x - bounds.left) / bounds.width
                            val fy = (dropped.y - bounds.top) / bounds.height
                            onMoved(fx.coerceIn(0f, 1f), fy.coerceIn(0f, 1f))
                        }
                    } else {
                        onTapHold()
                    }
                    dragOffset = Offset.Zero
                },
                onDragCancel = {
                    isDragging = false
                    dragOffset = Offset.Zero
                },
            ),
    ) {
        content()
    }
}
