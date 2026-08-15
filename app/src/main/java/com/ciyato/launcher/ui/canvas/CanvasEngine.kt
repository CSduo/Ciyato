package com.ciyato.launcher.ui.canvas

import kotlin.math.max
import kotlin.math.min

/**
 * A canvas object's normalized rectangle. (x, y) is the TOP-LEFT corner; both
 * the position and the size are 0f..1f fractions of the usable canvas
 * (mirrors [com.ciyato.launcher.data.CanvasPos]) so a layout survives
 * rotation, a different screen size, or a changed dock/status-bar inset —
 * pixels are derived from this only at render time, never stored here.
 * [z] is stacking order only; it plays no part in collision or displacement.
 */
data class CanvasItem(
    val id: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val z: Int = 0,
)

/**
 * Resolves overlap after a canvas drag. The product requirement is verbatim:
 * "if there is already something, then it should move itself away, so there
 * can be space for the app that I'm trying to move" — so on this canvas free
 * overlap between placed objects is a bug state to repair, not a feature to
 * allow (contrast [com.ciyato.launcher.data.CanvasPos], which deliberately
 * allows overlap for app tiles). [settle] is the entry point every caller
 * needs; [overlaps] and [clampToBounds] are exposed too because each is
 * independently useful — and independently testable.
 */
object CanvasEngine {

    /** Below this, two edges are considered touching rather than overlapping.
     *  Without slack, exact edge-alignment (a.right == b.left) computes a
     *  zero-width "overlap" that float rounding can flip sign on frame to
     *  frame, so two edge-touching items would jitter forever as each settle()
     *  call "resolves" an overlap that immediately reappears. */
    private const val EPSILON = 1e-4f

    /** Hard cap on displacement passes. A dense canvas can have no arrangement
     *  that eliminates every overlap (more items than free space); without a
     *  cap that cascade would spin forever and freeze the launcher on drag
     *  release. Ordinary drags settle in 2-3 passes — this is headroom for a
     *  long chain reaction, not a number real usage is expected to reach.
     *  `internal` (not private) so tests can pin the bound directly, per the
     *  "assert an upper bound on iterations" requirement. */
    internal const val MAX_ITERATIONS = 32

    /**
     * Settles [items] after [draggedId] was released at its current position.
     * The dragged item is the ANCHOR and never moves — everything else yields,
     * pushed along whichever axis currently has the least overlap (the "short
     * way out" reads as a natural nudge rather than a teleport), cascading
     * when a push creates a new overlap elsewhere. Always terminates within
     * [MAX_ITERATIONS] passes and always returns exactly one item per distinct
     * input id, so an impossible (over-full) canvas ends in some residual
     * overlap rather than a thrown item or a hung caller.
     *
     * Unknown ids need no special case: a [draggedId] absent from [items]
     * simply anchors nothing, so nothing has a reason to move and the
     * (sanitized, de-duplicated) input is returned as-is. Duplicate ids
     * resolve last-write-wins via a [LinkedHashMap] keyed by id, which is also
     * what keeps evaluation order tied to first-seen input order rather than
     * hash order — the reason identical input reliably produces identical
     * output.
     */
    fun settle(items: List<CanvasItem>, draggedId: String): List<CanvasItem> {
        if (items.isEmpty()) return emptyList()
        val current = LinkedHashMap<String, CanvasItem>()
        for (item in items) current[item.id] = item.sanitized()
        val order = current.keys.toList()
        if (draggedId !in current) return order.map { current.getValue(it) }

        var iteration = 0
        while (iteration < MAX_ITERATIONS) {
            var movedAny = false
            for (id in order) {
                if (id == draggedId) continue
                val item = current.getValue(id)
                val blocker = current.values.firstOrNull { it.id != id && overlaps(item, it) } ?: continue
                val pushed = pushOut(item, blocker)
                if (pushed != item) {
                    current[id] = pushed
                    movedAny = true
                }
            }
            iteration++
            if (!movedAny) break
        }
        return order.map { current.getValue(it) }
    }

    /** True if two items' rectangles intersect at all. Edge-touching (one
     *  item's right edge exactly on another's left edge) is NOT an overlap —
     *  see [EPSILON]. */
    fun overlaps(a: CanvasItem, b: CanvasItem): Boolean {
        val overlapX = min(a.x + a.w, b.x + b.w) - max(a.x, b.x)
        val overlapY = min(a.y + a.h, b.y + b.h) - max(a.y, b.y)
        return overlapX > EPSILON && overlapY > EPSILON
    }

    /** Nudges every item fully inside 0..1 on both axes, preserving size. An
     *  item wider or taller than the whole canvas can't be made to fit either
     *  way, so it's pinned to 0 on that axis — as close to "inside" as an
     *  oversized rectangle can get — rather than shrunk, which would silently
     *  rewrite the object's own size contract. */
    fun clampToBounds(items: List<CanvasItem>): List<CanvasItem> = items.map { it.sanitized().clampedToBounds() }

    // ── Displacement ────────────────────────────────────────────────────────

    /**
     * Pushes [item] off [blocker] along whichever axis currently has less
     * penetration, trying the direction that keeps [item] closest to where it
     * already was first (so a nudge doesn't overshoot to the far side of the
     * blocker when either direction would clear it). If that axis can't
     * resolve in bounds either way, falls back to the other axis; if neither
     * axis can, [item] is returned unchanged — a residual overlap beats
     * ejecting it somewhere it can no longer be grabbed from.
     */
    private fun pushOut(item: CanvasItem, blocker: CanvasItem): CanvasItem {
        val overlapX = min(item.x + item.w, blocker.x + blocker.w) - max(item.x, blocker.x)
        val overlapY = min(item.y + item.h, blocker.y + blocker.h) - max(item.y, blocker.y)
        val horizontalFirst = overlapX <= overlapY
        val primary = if (horizontalFirst) tryHorizontal(item, blocker, overlapX) else tryVertical(item, blocker, overlapY)
        if (primary != null) return primary
        val secondary = if (horizontalFirst) tryVertical(item, blocker, overlapY) else tryHorizontal(item, blocker, overlapX)
        return secondary ?: item
    }

    private fun tryHorizontal(item: CanvasItem, blocker: CanvasItem, overlapX: Float): CanvasItem? {
        val maxX = (1f - item.w).coerceAtLeast(0f)
        val preferLeft = item.x + item.w / 2f < blocker.x + blocker.w / 2f
        val primary = if (preferLeft) item.x - overlapX else item.x + overlapX
        val secondary = if (preferLeft) item.x + overlapX else item.x - overlapX
        return fittingAxisValue(primary, secondary, maxX)?.let { item.copy(x = it) }
    }

    private fun tryVertical(item: CanvasItem, blocker: CanvasItem, overlapY: Float): CanvasItem? {
        val maxY = (1f - item.h).coerceAtLeast(0f)
        val preferUp = item.y + item.h / 2f < blocker.y + blocker.h / 2f
        val primary = if (preferUp) item.y - overlapY else item.y + overlapY
        val secondary = if (preferUp) item.y + overlapY else item.y - overlapY
        return fittingAxisValue(primary, secondary, maxY)?.let { item.copy(y = it) }
    }

    /** Whichever candidate lands inside 0..[maxValue] (preferring [primary]),
     *  snapped to the boundary if float error puts it a hair outside; null if
     *  neither candidate fits at all. */
    private fun fittingAxisValue(primary: Float, secondary: Float, maxValue: Float): Float? = when {
        primary >= -EPSILON && primary <= maxValue + EPSILON -> primary.coerceIn(0f, maxValue)
        secondary >= -EPSILON && secondary <= maxValue + EPSILON -> secondary.coerceIn(0f, maxValue)
        else -> null
    }

    // ── Sanitization ────────────────────────────────────────────────────────

    /** Strips NaN/infinite values and negative size or position down to 0 so a
     *  corrupt or adversarial input can't propagate NaN through every
     *  downstream comparison — NaN compares false to everything, which would
     *  silently disable overlap detection instead of failing loudly. Does NOT
     *  clamp into 0..1: an already-placed item that isn't overlapping anything
     *  must not move (see [settle] doc), and the dragged item is the anchor
     *  precisely because it keeps the position the user chose, in or out of
     *  bounds. Only [clampToBounds] and the in-bounds checks inside a push
     *  enforce the 0..1 boundary. */
    private fun CanvasItem.sanitized(): CanvasItem = copy(
        x = x.sanitizedCoordinate(),
        y = y.sanitizedCoordinate(),
        w = w.sanitizedSize(),
        h = h.sanitizedSize(),
    )

    private fun CanvasItem.clampedToBounds(): CanvasItem {
        val maxX = (1f - w).coerceAtLeast(0f)
        val maxY = (1f - h).coerceAtLeast(0f)
        return copy(x = x.coerceIn(0f, maxX), y = y.coerceIn(0f, maxY))
    }

    private fun Float.sanitizedCoordinate(): Float = if (isFinite()) coerceAtLeast(0f) else 0f
    private fun Float.sanitizedSize(): Float = if (isFinite()) coerceAtLeast(0f) else 0f
}
