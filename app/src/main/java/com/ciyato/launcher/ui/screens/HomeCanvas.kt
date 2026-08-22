package com.ciyato.launcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.CanvasPos
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.launcher.*
import com.ciyato.launcher.ui.theme.*
import java.util.*
import kotlin.math.roundToInt

/**
 * Canvas geometry and the free-placement surface.
 *
 * Split out of HomeScreen.kt, which was a 213 KB file whose single HomeScreen
 * composable accounted for 155 KB of it (F-040). These four are the canvas
 * layer: where a dragged object lands, which page an edge drop belongs to, and
 * the surface that renders freely-positioned items.
 */

// The inverse of WorkspacePaging.visualIndexForPage. It used to be an
// independent copy of the arithmetic in a different file from the two functions
// it inverts, with nothing asserting they agreed (F-041).
internal fun workspacePagerPage(visualIndex: Int): Int =
    com.ciyato.launcher.data.WorkspacePaging.pageForVisualIndex(visualIndex)

private const val WORKSPACE_EDGE_DROP_THRESHOLD_PX = 120f

internal fun workspaceEdgeDropDestination(
    sourcePage: Int,
    horizontalOffset: Float,
    workspaceCount: Int,
): Int? = when {
    horizontalOffset > WORKSPACE_EDGE_DROP_THRESHOLD_PX && sourcePage == 0 && workspaceCount > 2 -> 2
    horizontalOffset > WORKSPACE_EDGE_DROP_THRESHOLD_PX && sourcePage >= 2 && sourcePage + 1 < workspaceCount -> sourcePage + 1
    horizontalOffset < -WORKSPACE_EDGE_DROP_THRESHOLD_PX && sourcePage == 2 -> 0
    horizontalOffset < -WORKSPACE_EDGE_DROP_THRESHOLD_PX && sourcePage > 2 -> sourcePage - 1
    else -> null
}

internal fun nearestWorkspaceGridTarget(
    sourceKey: String,
    sourceBounds: Rect?,
    dragOffset: Offset,
    validKeys: Set<String>,
    boundsByKey: Map<String, Rect>,
): String? {
    val draggedCenter = (sourceBounds ?: return null).center + dragOffset
    return boundsByKey
        .asSequence()
        .filter { (key, _) -> key != sourceKey && key in validKeys }
        .minByOrNull { (_, bounds) ->
            val dx = bounds.center.x - draggedCenter.x
            val dy = bounds.center.y - draggedCenter.y
            dx * dx + dy * dy
        }
        ?.key
}

// ── THE single canvas layout (Home + every workspace page) ─────────────────
// Replaces the old "flow LazyColumn with an absolute overlay drawn on top of
// it" hybrid — the root cause of the shipped defect (weather across
// Categories, greeting over the date/clock, no displacement). Every visible
// object, default-arranged or freely dragged, is measured and placed by ONE
// [Layout] pass in [HomeCanvasSurface]; there is no second container that
// can independently reflow around a gap the first one already accounted for.

/** One child of [HomeCanvasSurface]'s DEFAULT (never-moved) arrangement — see
 *  [HomeCanvasSurface.flowRows]. [rowIndex] groups objects that share one
 *  visual row (category cards sit side by side; every other object is alone
 *  in its row), [colIndex]/[colsInRow] split that row's width evenly. */
private data class CanvasFlowSlot(val id: String, val rowIndex: Int, val colIndex: Int, val colsInRow: Int)

/** A freely-positioned child — [xFrac]/[yFrac] use the exact same
 *  normalized-fraction convention as [CanvasPos], resolved to pixels only
 *  here against this pass's own measured canvas size. */
private data class CanvasFreeSlot(val id: String, val xFrac: Float, val yFrac: Float)

private sealed interface CanvasSlot
private data class FlowSlotId(val slot: CanvasFlowSlot) : CanvasSlot
private data class FreeSlotId(val slot: CanvasFreeSlot) : CanvasSlot

/**
 * THE single canvas layout for one Home/workspace page. [flowRows] is the
 * DEFAULT arrangement generator only (THE PRINCIPLE: a grid computes
 * defaults, never a runtime constraint) — each inner list is one visual row
 * (size 1 = a full-width section or the app grid, size N = N category cards
 * sharing that row), stacked top-to-bottom using each row's REAL measured
 * height, never a guess. A caller must never put an id that's also a key of
 * [freePositions] into [flowRows] — it renders exactly once, from whichever
 * list it's actually in, so removing/moving something never leaves a
 * reserved gap.
 *
 * [freePositions] renders after every flow row (Compose siblings paint in
 * declaration order, so free objects draw on top of the default stack — and
 * a higher-z free object draws on top of a lower-z one), each placed at its
 * persisted fraction of [canvasWidthPx] / [canvasHeightPx] — the SAME fixed
 * rect the object's own drag gesture measures a drop against (see
 * HomeCanvasItem/CanvasObject), so writing and reading a position always
 * agree.
 *
 * Reports its own natural content height (the flow stack's bottom, or the
 * lowest free object, whichever is greater) plus [bottomPaddingPx]. Callers
 * wrap this in `Modifier.verticalScroll` so a page that genuinely needs more
 * than one screen scrolls — as ONE rigid surface whose children never
 * reflow, just translate together with the scroll offset.
 */
@Composable
internal fun HomeCanvasSurface(
    flowRows: List<List<String>>,
    freePositions: Map<String, CanvasPos>,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    flowStartYPx: Float,
    freeStartYPx: Float,
    horizontalPaddingPx: Float,
    rowSpacingPx: Float,
    columnSpacingPx: Float,
    bottomPaddingPx: Float,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            flowRows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { colIndex, id ->
                    key("flow_$id") {
                        Box(Modifier.layoutId(FlowSlotId(CanvasFlowSlot(id, rowIndex, colIndex, row.size)))) {
                            content(id)
                        }
                    }
                }
            }
            freePositions.entries.sortedBy { it.value.z }.forEach { (id, pos) ->
                key("free_$id") {
                    Box(Modifier.layoutId(FreeSlotId(CanvasFreeSlot(id, pos.x, pos.y)))) {
                        content(id)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // The externally-tracked [canvasWidthPx] (from onGloballyPositioned,
        // ~one frame behind) is what free-position fractions are read/written
        // against, so free slots must use it for correctness. Flow content has
        // no such constraint — falling back to this pass's OWN constraints
        // when the tracked value isn't ready yet (composition's very first
        // frame) avoids a one-frame flash of zero-width content.
        val flowWidthPx = if (canvasWidthPx > 0f) canvasWidthPx else constraints.maxWidth.toFloat()
        val widthPx = canvasWidthPx.roundToInt().coerceAtLeast(0)
        val contentWidthPx = (flowWidthPx - horizontalPaddingPx * 2f).coerceAtLeast(0f)
        val hPaddingPx = horizontalPaddingPx.roundToInt()
        val colSpacingPx = columnSpacingPx.roundToInt()

        val measured = measurables.map { m ->
            when (val wrapped = m.layoutId as CanvasSlot) {
                is FlowSlotId -> {
                    val cols = wrapped.slot.colsInRow.coerceAtLeast(1)
                    val colWidth = ((contentWidthPx - columnSpacingPx * (cols - 1)) / cols).roundToInt().coerceAtLeast(0)
                    wrapped to m.measure(Constraints(maxWidth = colWidth))
                }
                is FreeSlotId -> wrapped to m.measure(Constraints(maxWidth = widthPx))
            }
        }

        val flowByRow = measured
            .mapNotNull { (wrapped, placeable) -> (wrapped as? FlowSlotId)?.let { it.slot to placeable } }
            .groupBy { (slot, _) -> slot.rowIndex }

        var runningY = flowStartYPx
        val rowTops = HashMap<Int, Float>()
        flowByRow.keys.sorted().forEach { rowIndex ->
            rowTops[rowIndex] = runningY
            val rowHeight = flowByRow.getValue(rowIndex).maxOf { (_, placeable) -> placeable.height }
            runningY += rowHeight + rowSpacingPx
        }
        val flowBottomPx = if (flowByRow.isEmpty()) flowStartYPx else runningY - rowSpacingPx

        val freeBottomPx = measured
            .mapNotNull { (wrapped, placeable) -> (wrapped as? FreeSlotId)?.let { it.slot to placeable } }
            .maxOfOrNull { (slot, placeable) -> freeStartYPx + slot.yFrac * canvasHeightPx + placeable.height }
            ?: 0f

        val totalHeightPx = (maxOf(flowBottomPx, freeBottomPx) + bottomPaddingPx).roundToInt().coerceAtLeast(0)

        layout(constraints.maxWidth, totalHeightPx) {
            measured.forEach { (wrapped, placeable) ->
                when (wrapped) {
                    is FlowSlotId -> {
                        val slot = wrapped.slot
                        val cols = slot.colsInRow.coerceAtLeast(1)
                        val colWidth = ((contentWidthPx - columnSpacingPx * (cols - 1)) / cols).roundToInt().coerceAtLeast(0)
                        val x = hPaddingPx + slot.colIndex * (colWidth + colSpacingPx)
                        val y = rowTops.getValue(slot.rowIndex).roundToInt()
                        placeable.placeRelative(x, y)
                    }
                    is FreeSlotId -> {
                        val slot = wrapped.slot
                        val x = (slot.xFrac * canvasWidthPx).roundToInt()
                        val y = (freeStartYPx + slot.yFrac * canvasHeightPx).roundToInt()
                        placeable.placeRelative(x, y)
                    }
                }
            }
        }
    }
}

