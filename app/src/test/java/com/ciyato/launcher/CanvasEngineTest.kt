package com.ciyato.launcher

import com.ciyato.launcher.ui.canvas.CanvasEngine
import com.ciyato.launcher.ui.canvas.CanvasItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELTA = 1e-4f

class CanvasEngineTest {

    // ── No-op / stability ──────────────────────────────────────────────────

    @Test
    fun `items that do not overlap are left untouched`() {
        val a = CanvasItem("a", 0f, 0f, 0.2f, 0.2f)
        val b = CanvasItem("b", 0.5f, 0.5f, 0.2f, 0.2f)

        val result = CanvasEngine.settle(listOf(a, b), draggedId = "a")

        assertEquals(listOf(a, b), result)
    }

    @Test
    fun `edge touching rectangles are not treated as overlap and do not move`() {
        val a = CanvasItem("a", 0f, 0f, 0.3f, 0.3f)
        val b = CanvasItem("b", 0.3f, 0f, 0.3f, 0.3f) // a's right edge == b's left edge exactly

        assertFalse(CanvasEngine.overlaps(a, b))

        val result = CanvasEngine.settle(listOf(a, b), draggedId = "a")

        assertEquals(listOf(a, b), result)
    }

    // ── Basic displacement ──────────────────────────────────────────────────

    @Test
    fun `overlapping item is displaced while the dragged anchor never moves`() {
        val anchor = CanvasItem("anchor", 0.3f, 0.3f, 0.2f, 0.2f)
        val other = CanvasItem("other", 0.35f, 0.35f, 0.2f, 0.2f)

        val result = CanvasEngine.settle(listOf(anchor, other), draggedId = "anchor")
        val resultAnchor = result.first { it.id == "anchor" }
        val resultOther = result.first { it.id == "other" }

        assertEquals(anchor, resultAnchor) // anchor is bit-for-bit unchanged
        assertTrue(resultOther != other) // the non-dragged item moved
        assertFalse(CanvasEngine.overlaps(resultAnchor, resultOther))
    }

    @Test
    fun `least penetration axis is horizontal when the x overlap is smaller`() {
        // overlapX = 0.05, overlapY = 0.25 -> must push horizontally, y untouched.
        val anchor = CanvasItem("anchor", 0f, 0f, 0.3f, 0.3f)
        val other = CanvasItem("other", 0.25f, 0.05f, 0.3f, 0.3f)

        val result = CanvasEngine.settle(listOf(anchor, other), draggedId = "anchor")
        val resultOther = result.first { it.id == "other" }

        assertEquals(other.y, resultOther.y, DELTA)
        assertTrue(kotlin.math.abs(resultOther.x - other.x) > DELTA)
        assertFalse(CanvasEngine.overlaps(result.first { it.id == "anchor" }, resultOther))
    }

    @Test
    fun `least penetration axis is vertical when the y overlap is smaller`() {
        // overlapX = 0.25, overlapY = 0.05 -> must push vertically, x untouched.
        val anchor = CanvasItem("anchor", 0f, 0f, 0.3f, 0.3f)
        val other = CanvasItem("other", 0.05f, 0.25f, 0.3f, 0.3f)

        val result = CanvasEngine.settle(listOf(anchor, other), draggedId = "anchor")
        val resultOther = result.first { it.id == "other" }

        assertEquals(other.x, resultOther.x, DELTA)
        assertTrue(kotlin.math.abs(resultOther.y - other.y) > DELTA)
        assertFalse(CanvasEngine.overlaps(result.first { it.id == "anchor" }, resultOther))
    }

    @Test
    fun `push direction favors the side the item was already closer to`() {
        // "other" sits to the right of the anchor's center, so it must be
        // pushed further right, never flipped across to the left side.
        val anchor = CanvasItem("anchor", 0f, 0f, 0.3f, 0.3f)
        val other = CanvasItem("other", 0.25f, 0.05f, 0.3f, 0.3f)

        val result = CanvasEngine.settle(listOf(anchor, other), draggedId = "anchor")
        val resultOther = result.first { it.id == "other" }

        assertTrue(resultOther.x > other.x)
    }

    // ── Cascade ─────────────────────────────────────────────────────────────

    @Test
    fun `cascade - dragging A displaces B which in turn displaces C`() {
        val a = CanvasItem("a", 0f, 0f, 0.3f, 0.3f) // dragged anchor
        val b = CanvasItem("b", 0.1f, 0f, 0.3f, 0.3f) // overlaps A only, to start
        val c = CanvasItem("c", 0.45f, 0f, 0.3f, 0.3f) // clear of both, until B lands on it

        // Sanity on the starting fixture: only A/B overlap initially.
        assertTrue(CanvasEngine.overlaps(a, b))
        assertFalse(CanvasEngine.overlaps(a, c))
        assertFalse(CanvasEngine.overlaps(b, c))

        val result = CanvasEngine.settle(listOf(a, b, c), draggedId = "a")
        val ra = result.first { it.id == "a" }
        val rb = result.first { it.id == "b" }
        val rc = result.first { it.id == "c" }

        assertEquals(a, ra) // anchor untouched
        assertEquals(0.3f, rb.x, DELTA) // pushed clear of A
        assertEquals(0.6f, rc.x, DELTA) // then pushed clear of B's new spot
        assertFalse(CanvasEngine.overlaps(ra, rb))
        assertFalse(CanvasEngine.overlaps(rb, rc))
        assertFalse(CanvasEngine.overlaps(ra, rc))
    }

    @Test
    fun `cascade result stays fully within canvas bounds`() {
        val a = CanvasItem("a", 0f, 0f, 0.3f, 0.3f)
        val b = CanvasItem("b", 0.1f, 0f, 0.3f, 0.3f)
        val c = CanvasItem("c", 0.45f, 0f, 0.3f, 0.3f)

        val result = CanvasEngine.settle(listOf(a, b, c), draggedId = "a")

        result.forEach { item ->
            assertTrue("${item.id}.x=${item.x} out of bounds", item.x >= -DELTA && item.x + item.w <= 1f + DELTA)
            assertTrue("${item.id}.y=${item.y} out of bounds", item.y >= -DELTA && item.y + item.h <= 1f + DELTA)
        }
    }

    // ── Termination on a dense / impossible canvas ─────────────────────────

    @Test(timeout = 5_000)
    fun `dense canvas with no free space terminates and returns one item per id`() {
        // 16 oversized (0.4 x 0.4) items packed into a small cluster: there is
        // no arrangement that removes every overlap, so this exercises the
        // iteration cap rather than a normal convergence.
        val items = (0 until 16).map { i ->
            CanvasItem("item-$i", (i % 4) * 0.06f, (i / 4) * 0.06f, 0.4f, 0.4f)
        }

        val result = CanvasEngine.settle(items, draggedId = "item-0")

        assertEquals(16, result.size)
        assertEquals(items.map { it.id }.toSet(), result.map { it.id }.toSet())
        result.forEach { item ->
            assertTrue(item.x.isFinite() && item.y.isFinite())
        }
    }

    @Test
    fun `iteration cap is a small bounded constant`() {
        // Locks the safety margin in place: if a future change removes or
        // balloons this cap, this test fails loudly instead of a future dense
        // layout silently hanging the launcher on drag release.
        assertTrue(CanvasEngine.MAX_ITERATIONS in 1..64)
    }

    // ── Determinism ─────────────────────────────────────────────────────────

    @Test
    fun `settle is deterministic for identical input`() {
        val items = listOf(
            CanvasItem("a", 0f, 0f, 0.3f, 0.3f),
            CanvasItem("b", 0.1f, 0f, 0.3f, 0.3f),
            CanvasItem("c", 0.45f, 0f, 0.3f, 0.3f),
            CanvasItem("d", 0.2f, 0.2f, 0.3f, 0.3f),
        )

        val first = CanvasEngine.settle(items, draggedId = "a")
        val second = CanvasEngine.settle(items, draggedId = "a")

        assertEquals(first, second)
    }

    // ── Degenerate inputs ────────────────────────────────────────────────────

    @Test
    fun `empty list settles to an empty list`() {
        assertEquals(emptyList<CanvasItem>(), CanvasEngine.settle(emptyList(), draggedId = "anything"))
    }

    @Test
    fun `single item settles to itself unchanged`() {
        val only = CanvasItem("solo", 0.4f, 0.4f, 0.2f, 0.2f)

        assertEquals(listOf(only), CanvasEngine.settle(listOf(only), draggedId = "solo"))
    }

    @Test
    fun `unknown draggedId anchors nothing so overlaps are left as-is`() {
        val a = CanvasItem("a", 0f, 0f, 0.3f, 0.3f)
        val b = CanvasItem("b", 0.1f, 0f, 0.3f, 0.3f) // overlaps a

        val result = CanvasEngine.settle(listOf(a, b), draggedId = "does-not-exist")

        assertEquals(listOf(a, b), result)
    }

    @Test
    fun `duplicate ids resolve last-write-wins without crashing`() {
        val first = CanvasItem("dup", 0f, 0f, 0.2f, 0.2f)
        val second = CanvasItem("dup", 0.5f, 0.5f, 0.2f, 0.2f)

        val result = CanvasEngine.settle(listOf(first, second), draggedId = "dup")

        assertEquals(1, result.size)
        assertEquals(second, result.single())
    }

    @Test
    fun `zero size items never overlap and never move anything`() {
        val anchor = CanvasItem("anchor", 0.4f, 0.4f, 0.2f, 0.2f)
        val zeroWidth = CanvasItem("zero-w", 0.4f, 0.4f, 0f, 0.2f)
        val zeroHeight = CanvasItem("zero-h", 0.4f, 0.4f, 0.2f, 0f)

        assertFalse(CanvasEngine.overlaps(anchor, zeroWidth))
        assertFalse(CanvasEngine.overlaps(anchor, zeroHeight))

        val result = CanvasEngine.settle(listOf(anchor, zeroWidth, zeroHeight), draggedId = "anchor")

        assertEquals(listOf(anchor, zeroWidth, zeroHeight), result)
    }

    @Test
    fun `item larger than the whole canvas does not crash and keeps its size`() {
        val anchor = CanvasItem("anchor", 0.4f, 0.4f, 0.3f, 0.3f)
        val huge = CanvasItem("huge", 0.3f, 0.3f, 1.5f, 0.5f) // overlaps the anchor

        val result = CanvasEngine.settle(listOf(anchor, huge), draggedId = "anchor")
        val resultHuge = result.first { it.id == "huge" }

        assertEquals(1.5f, resultHuge.w, DELTA) // never shrunk
        assertEquals(0.5f, resultHuge.h, DELTA)
        assertTrue(resultHuge.x.isFinite() && resultHuge.y.isFinite())
    }

    @Test
    fun `identical positions resolve deterministically without crashing`() {
        val anchor = CanvasItem("anchor", 0.3f, 0.3f, 0.2f, 0.2f)
        val twin = CanvasItem("twin", 0.3f, 0.3f, 0.2f, 0.2f) // exact same rectangle

        val once = CanvasEngine.settle(listOf(anchor, twin), draggedId = "anchor")
        val again = CanvasEngine.settle(listOf(anchor, twin), draggedId = "anchor")

        assertEquals(once, again) // deterministic even for a degenerate tie
        assertEquals(anchor, once.first { it.id == "anchor" })
    }

    @Test
    fun `an out-of-bounds item that overlaps nothing is left exactly as given`() {
        val anchor = CanvasItem("anchor", 0f, 0f, 0.2f, 0.2f)
        val strayed = CanvasItem("strayed", 1.4f, 1.4f, 0.2f, 0.2f) // far outside 0..1, no overlap

        val result = CanvasEngine.settle(listOf(anchor, strayed), draggedId = "anchor")

        assertEquals(strayed, result.first { it.id == "strayed" }) // untouched: stability beats tidying
    }

    @Test
    fun `dragged anchor keeps the exact position the user chose even out of bounds`() {
        val anchor = CanvasItem("anchor", 1.5f, 1.2f, 0.2f, 0.2f) // both axes past 1.0, but finite/non-negative
        val other = CanvasItem("other", 0.5f, 0.5f, 0.2f, 0.2f)

        val result = CanvasEngine.settle(listOf(anchor, other), draggedId = "anchor")

        assertEquals(anchor, result.first { it.id == "anchor" })
    }

    @Test
    fun `NaN and negative coordinates are sanitized instead of propagating`() {
        val anchor = CanvasItem("anchor", 0.6f, 0.6f, 0.2f, 0.2f)
        val nanItem = CanvasItem("nan", Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        val negativeItem = CanvasItem("negative", -3f, -2f, 0.2f, 0.2f)

        val result = CanvasEngine.settle(listOf(anchor, nanItem, negativeItem), draggedId = "anchor")

        val resultNan = result.first { it.id == "nan" }
        assertEquals(0f, resultNan.x, DELTA)
        assertEquals(0f, resultNan.y, DELTA)
        assertEquals(0f, resultNan.w, DELTA)
        assertEquals(0f, resultNan.h, DELTA)

        val resultNegative = result.first { it.id == "negative" }
        assertEquals(0f, resultNegative.x, DELTA)
        assertEquals(0f, resultNegative.y, DELTA)
    }

    // ── clampToBounds ────────────────────────────────────────────────────────

    @Test
    fun `clampToBounds leaves in-bounds items untouched`() {
        val items = listOf(
            CanvasItem("a", 0.2f, 0.2f, 0.3f, 0.3f),
            CanvasItem("b", 0f, 0f, 0.1f, 0.1f),
        )

        assertEquals(items, CanvasEngine.clampToBounds(items))
    }

    @Test
    fun `clampToBounds pulls an overflowing item back inside without resizing it`() {
        val overflowing = CanvasItem("a", 0.9f, 0.9f, 0.3f, 0.3f) // right/bottom edge past 1.0

        val result = CanvasEngine.clampToBounds(listOf(overflowing)).single()

        assertEquals(0.7f, result.x, DELTA)
        assertEquals(0.7f, result.y, DELTA)
        assertEquals(0.3f, result.w, DELTA)
        assertEquals(0.3f, result.h, DELTA)
    }

    @Test
    fun `clampToBounds pins an oversized item to zero rather than shrinking it`() {
        val oversized = CanvasItem("a", 0.5f, -0.5f, 1.4f, 2f)

        val result = CanvasEngine.clampToBounds(listOf(oversized)).single()

        assertEquals(0f, result.x, DELTA)
        assertEquals(0f, result.y, DELTA)
        assertEquals(1.4f, result.w, DELTA) // size preserved
        assertEquals(2f, result.h, DELTA)
    }

    @Test
    fun `clampToBounds sanitizes NaN and negative coordinates too`() {
        val bad = CanvasItem("a", Float.NaN, -5f, 0.2f, 0.2f)

        val result = CanvasEngine.clampToBounds(listOf(bad)).single()

        assertEquals(0f, result.x, DELTA)
        assertEquals(0f, result.y, DELTA)
    }
}
