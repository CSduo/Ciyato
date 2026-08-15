package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.CanvasPos
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.CiyatoBgEl2
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import kotlin.math.roundToInt

/**
 * Fixed-grid workspace surface. Apps sit at explicit cells (row-major linear index)
 * and may span multiple columns/rows (spanX/spanY, upstream in AppCell). A plain
 * Row/Column-with-weight() grid can only ever express 1x1 tiles, so placement runs
 * through a custom Layout: every cell's pixel rectangle is derived once from the
 * incoming width, and each tile is measured to exactly spanX*spanY of those cells
 * and placed at its origin corner.
 * Dynamically scales icon sizes, font sizes, line heights, and aspect ratios based on column count
 * so app labels remain 100% legible and visible across 4x5, 5x5, 6x5, and high-density grids.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceGrid(
    columns: Int,
    minRows: Int,
    cellApps: Map<Int, InstalledApp>,
    isEditMode: Boolean,
    onAppTap: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
    onCellBounds: (cell: Int, bounds: Rect) -> Unit = { _, _ -> },
    hiddenPackage: String? = null,
    highlightCell: Int? = null,
    expandedPackages: Set<String> = emptySet(),
    tileGesture: (Modifier, cell: Int, app: InstalledApp) -> Modifier = { m, _, _ -> m },
    // cell index -> (spanX, spanY) for every occupied cell in [cellApps]; a cell
    // missing here is treated as 1x1, so a caller that never resizes anything
    // can leave this at its default and behaviour is unchanged.
    cellSpans: Map<Int, Pair<Int, Int>> = emptyMap(),
    onResize: (packageName: String, spanX: Int, spanY: Int) -> Unit = { _, _, _ -> },
    // packageName -> free placement, additive to [cellApps]'s grid cell (see
    // AppCell.pos / CanvasPos). A package present here renders absolutely at
    // (pos.x * canvasWidth, pos.y * canvasHeight) instead of grid flow,
    // ordered by pos.z, and never reserves/hole-punches its grid fallback
    // cell or affects the row count — the grid stays the DEFAULT layout
    // generator only, never a runtime constraint on a moved object.
    canvasPositions: Map<String, CanvasPos> = emptyMap(),
    // Package that just landed from a drag, for a brief settle animation.
    // Null (default) means no tile animates — existing callers are unaffected.
    settlingPackage: String? = null,
) {
    val cols = columns.coerceIn(3, 8)

    // Column-adaptive parameters for legibility (unchanged from before spans).
    val (iconSize, fontSize, lineHeight, cellAspectRatio) = when {
        cols >= 6 -> Quad(38.dp, 9.5.sp, 11.sp, 0.70f)
        cols == 5 -> Quad(42.dp, 10.sp, 12.sp, 0.74f)
        else -> Quad(46.dp, 11.sp, 13.sp, 0.78f)
    }
    val spacing = if (cols >= 6) 4.dp else 6.dp

    // Live resize preview, package -> proposed (spanX, spanY). Populated only
    // while a resize-handle drag is in progress and cleared the instant the
    // finger lifts, so the grid falls back to reading size straight from
    // [cellSpans] (the persisted layout) rather than an optimistic guess.
    // resizeAppTile is fire-and-forget and silently no-ops on overlap/off-grid,
    // so a size must never be shown here unless it's confirmed persisted.
    val resizePreview = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // Pixel size of a single 1x1 cell, learned from whatever child actually gets
    // measured to that size. Composition happens before measurement, so this
    // can't be computed up front — it's populated on the first layout pass and
    // is stable well before a person can physically grab a resize handle.
    var cellPxSize by remember { mutableStateOf(IntSize.Zero) }

    // A free-positioned app is exempt from grid flow entirely (see
    // AppCell.pos): it must never reserve its fallback cell, hole-punch the
    // grid, or count toward the row calculation below. Split once so every
    // grid computation that follows only ever sees [gridApps].
    val gridApps = if (canvasPositions.isEmpty()) cellApps else cellApps.filterValues { it.packageName !in canvasPositions }
    val freeApps = if (canvasPositions.isEmpty()) emptyMap() else cellApps.filterValues { it.packageName in canvasPositions }

    // Every rectangle currently covered by a placed (non-hidden) tile, keyed
    // covered-cell -> origin-cell. Drives both the row count (a spanning tile on
    // the last row extends the grid) and which cells skip the empty placeholder.
    val coveredBy = buildMap {
        gridApps.forEach { (origin, app) ->
            if (app.packageName == hiddenPackage) return@forEach
            val (spanX, spanY) = resizePreview[app.packageName] ?: cellSpans[origin] ?: (1 to 1)
            val originCol = origin % cols
            val originRow = origin / cols
            for (dy in 0 until spanY) for (dx in 0 until spanX) {
                put((originRow + dy) * cols + (originCol + dx), origin)
            }
        }
    }
    val maxCovered = coveredBy.keys.maxOrNull() ?: (gridApps.keys.maxOrNull() ?: -1)
    val neededRows = if (maxCovered < 0) 0 else (maxCovered / cols) + 1
    val effectiveRows = maxOf(minRows.coerceAtLeast(1), neededRows)
    val totalCells = effectiveRows * cols

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            for (cellIndex in 0 until totalCells) {
                val originOfCovered = coveredBy[cellIndex]
                val isOrigin = originOfCovered == cellIndex
                val isCoveredByOther = originOfCovered != null && !isOrigin
                val app = gridApps[cellIndex]

                if (isOrigin && app != null && app.packageName != hiddenPackage) {
                    val (spanX, spanY) = resizePreview[app.packageName] ?: cellSpans[cellIndex] ?: (1 to 1)
                    val isTargeted = highlightCell != null && coveredBy[highlightCell] == cellIndex
                    key(app.packageName) {
                        ResizableWorkspaceTile(
                            modifier = Modifier.layoutId(GridSlot(cellIndex, spanX, spanY)),
                            cell = cellIndex,
                            app = app,
                            spanX = spanX,
                            spanY = spanY,
                            columns = cols,
                            isEditMode = isEditMode,
                            isTargeted = isTargeted,
                            isSettling = app.packageName == settlingPackage,
                            iconSize = iconSize,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            isExpanded = app.packageName in expandedPackages,
                            cellPxSize = cellPxSize,
                            onTap = onAppTap,
                            tileGesture = tileGesture,
                            onCellSizeMeasured = { cellPxSize = it },
                            onResizePreview = { sx, sy -> resizePreview[app.packageName] = sx to sy },
                            onResizeCommit = { sx, sy ->
                                resizePreview.remove(app.packageName)
                                if (sx != spanX || sy != spanY) onResize(app.packageName, sx, sy)
                            },
                            onResizeCancel = { resizePreview.remove(app.packageName) },
                        )
                    }
                } else if (!isCoveredByOther) {
                    // Either genuinely empty, or the origin of the tile currently
                    // being dragged elsewhere (hidden here) — either way, a plain
                    // 1x1 zone: bounds-only outside edit mode, a placeholder inside it.
                    key("cell_$cellIndex") {
                        WorkspaceCellZone(
                            modifier = Modifier.layoutId(GridSlot(cellIndex, 1, 1)),
                            cell = cellIndex,
                            showPlaceholder = isEditMode && app == null,
                            isTargeted = highlightCell == cellIndex,
                            onCellBounds = onCellBounds,
                            onSizeMeasured = { cellPxSize = it },
                        )
                    }
                } else {
                    // Covered by a neighbour's span: no placeholder is drawn (it's
                    // already under that tile), but the cell still needs its own
                    // 1x1 bounds reported — the drag system resolves a target cell
                    // by index, independent of what currently occupies it.
                    key("zone_$cellIndex") {
                        WorkspaceCellZone(
                            modifier = Modifier.layoutId(GridSlot(cellIndex, 1, 1)),
                            cell = cellIndex,
                            showPlaceholder = false,
                            isTargeted = false,
                            onCellBounds = onCellBounds,
                            onSizeMeasured = { },
                        )
                    }
                }
            }

            // Free-positioned tiles: absolute canvas placement, never part of
            // grid flow. Declared (and therefore placed — see the measure
            // block below) AFTER every grid child, sorted ascending by z, so
            // they draw above the grid and higher-z free tiles draw above
            // lower-z ones — in Compose, later-placed children draw on top.
            freeApps.entries
                .sortedBy { (_, app) -> canvasPositions[app.packageName]?.z ?: 0 }
                .forEach { (cellIndex, app) ->
                    if (app.packageName == hiddenPackage) return@forEach
                    val pos = canvasPositions[app.packageName] ?: return@forEach
                    val (spanX, spanY) = resizePreview[app.packageName] ?: cellSpans[cellIndex] ?: (1 to 1)
                    key(app.packageName) {
                        ResizableWorkspaceTile(
                            modifier = Modifier.layoutId(FreeSlot(pos.x, pos.y, spanX, spanY)),
                            cell = cellIndex,
                            app = app,
                            spanX = spanX,
                            spanY = spanY,
                            columns = cols,
                            isEditMode = isEditMode,
                            isTargeted = false,
                            isSettling = app.packageName == settlingPackage,
                            iconSize = iconSize,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            isExpanded = app.packageName in expandedPackages,
                            cellPxSize = cellPxSize,
                            onTap = onAppTap,
                            tileGesture = tileGesture,
                            onCellSizeMeasured = { cellPxSize = it },
                            onResizePreview = { sx, sy -> resizePreview[app.packageName] = sx to sy },
                            onResizeCommit = { sx, sy ->
                                resizePreview.remove(app.packageName)
                                if (sx != spanX || sy != spanY) onResize(app.packageName, sx, sy)
                            },
                            onResizeCancel = { resizePreview.remove(app.packageName) },
                        )
                    }
                }
        },
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val cellW = ((constraints.maxWidth - spacingPx * (cols - 1)) / cols).coerceAtLeast(0)
        val cellH = (cellW / cellAspectRatio).roundToInt().coerceAtLeast(0)
        val totalHeight = effectiveRows * cellH + spacingPx * (effectiveRows - 1).coerceAtLeast(0)

        val placed = measurables.map { measurable ->
            val slot = measurable.layoutId as WorkspaceSlot
            val w = slot.spanX * cellW + spacingPx * (slot.spanX - 1)
            val h = slot.spanY * cellH + spacingPx * (slot.spanY - 1)
            slot to measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
        }

        layout(constraints.maxWidth, totalHeight) {
            // Grid tiles place first (any order among themselves), free tiles
            // place after in ascending-z content order — see the note above.
            placed.forEach { (slot, placeable) ->
                when (slot) {
                    is GridSlot -> {
                        val col = slot.cell % cols
                        val row = slot.cell / cols
                        placeable.placeRelative(col * (cellW + spacingPx), row * (cellH + spacingPx))
                    }
                    is FreeSlot -> {
                        placeable.placeRelative(
                            (slot.x * constraints.maxWidth).roundToInt(),
                            (slot.y * totalHeight).roundToInt(),
                        )
                    }
                }
            }
        }
    }
}

/** Placement key threaded through the custom [Layout]. Every child (grid or
 *  free) exposes its span so measurement is one shared code path; only
 *  placement (col/row vs. canvas fraction) differs — see the `layout {}`
 *  block above. */
private sealed interface WorkspaceSlot {
    val spanX: Int
    val spanY: Int
}

/** A grid-flowed child: which cell it originates at. */
private data class GridSlot(val cell: Int, override val spanX: Int, override val spanY: Int) : WorkspaceSlot

/** A free-positioned child (see [CanvasPos]): [x]/[y] are normalized
 *  fractions of the canvas this [Layout] itself renders at — resolved to
 *  pixels only here, against this pass's own measured size, so it always
 *  matches whatever a caller derives from this same node's
 *  [androidx.compose.ui.layout.onGloballyPositioned] bounds. */
private data class FreeSlot(val x: Float, val y: Float, override val spanX: Int, override val spanY: Int) : WorkspaceSlot

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** A single 1x1 grid cell that isn't (visibly) covered by a spanning tile:
 *  reports its own root-coordinate rect for the drag system regardless of
 *  content, and optionally draws the empty-cell placeholder or the drag-target
 *  highlight border. */
@Composable
private fun WorkspaceCellZone(
    cell: Int,
    showPlaceholder: Boolean,
    isTargeted: Boolean,
    onCellBounds: (cell: Int, bounds: Rect) -> Unit,
    onSizeMeasured: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .onGloballyPositioned {
                onCellBounds(cell, it.boundsInRoot())
                onSizeMeasured(it.size)
            }
            .then(
                if (isTargeted) Modifier.border(2.dp, CiyatoGold, RoundedCornerShape(16.dp)) else Modifier,
            ),
    ) {
        if (showPlaceholder) EmptyCellPlaceholder()
    }
}

/** A placed app tile, sized to spanX*spanY cells by the parent [Layout]. In
 *  edit mode it also carries a free-drag resize handle (bottom-right) and a
 *  compact fixed-preset menu (top-left) — both are separate child elements
 *  from the tile body itself, never additional gestures on it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResizableWorkspaceTile(
    cell: Int,
    app: InstalledApp,
    spanX: Int,
    spanY: Int,
    columns: Int,
    isEditMode: Boolean,
    isTargeted: Boolean,
    iconSize: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    isExpanded: Boolean,
    cellPxSize: IntSize,
    onTap: (InstalledApp) -> Unit,
    tileGesture: (Modifier, cell: Int, app: InstalledApp) -> Modifier,
    onCellSizeMeasured: (IntSize) -> Unit,
    onResizePreview: (spanX: Int, spanY: Int) -> Unit,
    onResizeCommit: (spanX: Int, spanY: Int) -> Unit,
    onResizeCancel: () -> Unit,
    modifier: Modifier = Modifier,
    // True for exactly one recomposition right after this tile lands from a
    // drag (see WorkspaceGrid's settlingPackage). Drives a small landing
    // bounce; the caller is responsible for clearing it soon after (and for
    // never setting it at all when reduceMotion is on).
    isSettling: Boolean = false,
) {
    val displaceScale by animateFloatAsState(
        targetValue = if (isTargeted) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "grid_scale",
    )
    // Small "landing" bounce right after a drop — a dip-then-spring-back to
    // 1f, distinct from [displaceScale] above (which reacts to a DIFFERENT
    // tile being dragged over this one). Multiplied together below so both
    // can be mid-flight at once without fighting each other.
    val settleScale = remember(app.packageName) { Animatable(1f) }
    LaunchedEffect(isSettling) {
        if (isSettling) {
            settleScale.snapTo(0.92f)
            settleScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f))
        }
    }
    var showPresetMenu by remember(app.packageName) { mutableStateOf(false) }
    var dragBaseSpan by remember(app.packageName) { mutableStateOf(spanX to spanY) }
    var dragAccum by remember(app.packageName) { mutableStateOf(Offset.Zero) }

    fun spanFromDrag(): Pair<Int, Int> {
        val cellW = cellPxSize.width.coerceAtLeast(1)
        val cellH = cellPxSize.height.coerceAtLeast(1)
        val maxSpanX = (columns - (cell % columns)).coerceAtLeast(1)
        val proposedX = (dragBaseSpan.first + (dragAccum.x / cellW).roundToInt()).coerceIn(1, maxSpanX)
        val proposedY = (dragBaseSpan.second + (dragAccum.y / cellH).roundToInt()).coerceIn(1, MAX_RESIZE_SPAN)
        return proposedX to proposedY
    }

    // A 1x1 tile (the overwhelming common case) must resolve to exactly 1f so
    // its content renders byte-for-byte as before spans existed; only compute
    // scaled copies when the span actually grows the tile.
    val tileScale = tileVisualScale(spanX, spanY)
    val tileIconSize = if (tileScale == 1f) iconSize else iconSize * tileScale
    val tileFontSize = if (tileScale == 1f) fontSize else fontSize * tileScale
    val tileLineHeight = if (tileScale == 1f) lineHeight else lineHeight * tileScale

    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = displaceScale * settleScale.value
                scaleX = scale
                scaleY = scale
            }
            .then(if (isTargeted) Modifier.border(2.dp, CiyatoGold, RoundedCornerShape(16.dp)) else Modifier)
            .onGloballyPositioned {
                onCellSizeMeasured(IntSize(it.size.width / spanX, it.size.height / spanY))
            },
        contentAlignment = Alignment.Center,
    ) {
        WorkspaceAppTile(
            cell = cell,
            app = app,
            isEditMode = isEditMode,
            iconSize = tileIconSize,
            fontSize = tileFontSize,
            lineHeight = tileLineHeight,
            onTap = onTap,
            tileGesture = tileGesture,
            isExpanded = isExpanded,
        )

        if (isEditMode) {
            // Fixed-proportion presets: a compact size badge that opens a small
            // menu of the four common shapes — a one-tap alternative to dragging.
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = "$spanX×$spanY",
                    color = CiyatoSec,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CiyatoBgEl2.copy(alpha = 0.88f))
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(6.dp))
                        .clickable { showPresetMenu = true }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .semantics { contentDescription = "Resize ${app.label}, currently $spanX by $spanY cells" },
                )
                DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                    RESIZE_PRESETS.forEach { (presetX, presetY) ->
                        DropdownMenuItem(
                            text = { Text("$presetX×$presetY", color = CiyatoWhite) },
                            onClick = {
                                showPresetMenu = false
                                if (presetX != spanX || presetY != spanY) onResizeCommit(presetX, presetY)
                            },
                        )
                    }
                }
            }

            // Free-drag handle. Its own pointerInput scope entirely separate from
            // the tile body above, so it never races the tile's long-press drag.
            ResizeHandle(
                description = "Resize ${app.label}",
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                onDragStart = {
                    dragBaseSpan = spanX to spanY
                    dragAccum = Offset.Zero
                },
                onDrag = { delta ->
                    dragAccum += delta
                    val (sx, sy) = spanFromDrag()
                    onResizePreview(sx, sy)
                },
                onDragEnd = {
                    val (sx, sy) = spanFromDrag()
                    onResizeCommit(sx, sy)
                },
                onDragCancel = onResizeCancel,
            )
        }
    }
}

/** Sane UI-side ceiling on a live spanY drag preview. The model (WorkspaceStore)
 *  is the real authority and clamps/rejects independently of this. */
private const val MAX_RESIZE_SPAN = 6

/** Visual scale for a tile's icon/label, derived from its span so a resized
 *  tile reads as deliberate rather than a small icon lost in a bigger box.
 *  Primarily driven by min(spanX, spanY): a 2x2 tile has more room in both
 *  directions and can grow noticeably, while a 2x1 tile only gained width —
 *  its height is unchanged, so scaling it as aggressively as a 2x2 would
 *  overflow the row. A small secondary term keyed on max(spanX, spanY) still
 *  gives that wide-but-short tile some modest growth instead of none. Never
 *  multiplies spanX*spanY directly (that would scale on area, overreacting
 *  to elongated shapes). A 1x1 tile always resolves to exactly 1f. */
private fun tileVisualScale(spanX: Int, spanY: Int): Float {
    val minSpan = minOf(spanX, spanY)
    val maxSpan = maxOf(spanX, spanY)
    val scale = 1f + 0.18f * (minSpan - 1) + 0.05f * (maxSpan - 1)
    return scale.coerceIn(1f, 1.4f)
}

private val RESIZE_PRESETS = listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)

/** Small drag-affordance anchored to a tile's bottom-right corner. Uses an
 *  immediate [detectDragGestures] (no long-press) since it's a dedicated,
 *  physically separate hit target from the tile body's long-press-to-move. */
@Composable
private fun ResizeHandle(
    description: String,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The gesture below is deliberately keyed on Unit — never on span/position
    // state the drag itself changes — so it's never torn down and restarted
    // mid-drag. That means its detectDragGestures block is only ever entered
    // ONCE and then handles every future gesture from that same coroutine, so
    // the callbacks it closes over must stay current via rememberUpdatedState
    // rather than being captured stale from the first composition.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, bottomEnd = 12.dp))
            .background(CiyatoBgEl2.copy(alpha = 0.9f))
            .border(1.dp, CiyatoGold.copy(alpha = 0.55f), RoundedCornerShape(topStart = 8.dp, bottomEnd = 12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, amount -> change.consume(); currentOnDrag(amount) },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                )
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val strokeW = 1.6.dp.toPx()
            drawLine(CiyatoGold, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = strokeW)
            drawLine(
                CiyatoGold,
                Offset(size.width * 0.5f, size.height),
                Offset(size.width, size.height * 0.5f),
                strokeWidth = strokeW,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceAppTile(
    cell: Int,
    app: InstalledApp,
    isEditMode: Boolean,
    iconSize: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onTap: (InstalledApp) -> Unit,
    tileGesture: (Modifier, cell: Int, app: InstalledApp) -> Modifier,
    isExpanded: Boolean = false,
) {
    // Long-press belongs to [tileGesture]'s drag detector alone. Stacking a
    // combinedClickable(onLongClick = ...) here too put two long-press detectors
    // on the same tile: both fired at ~500ms and raced, so the context menu
    // regularly hijacked a drag the moment it began — the app felt un-draggable.
    // Tap launches; long-press lifts into a drag; releasing without moving is
    // handled by the drag's own commit path, which opens the menu instead.
    val gestured = tileGesture(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), cell, app)
    if (isExpanded) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = gestured
                .clip(RoundedCornerShape(16.dp))
                .background(com.ciyato.launcher.ui.theme.CiyatoBgEl)
                .border(1.dp, CiyatoGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { onTap(app) }
                .padding(10.dp),
        ) {
            RealAppIcon(
                drawable = app.icon,
                size = iconSize * 1.3f,
                cornerRadius = (iconSize.value * 0.35f).dp,
                scale = app.iconScale,
                rotation = app.iconRotation,
                accentHex = app.iconAccent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                color = CiyatoWhite,
                fontSize = fontSize * 1.1f,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = gestured.clickable { onTap(app) },
        ) {
            RealAppIcon(
                drawable = app.icon,
                size = iconSize,
                cornerRadius = (iconSize.value * 0.28f).dp,
                scale = app.iconScale,
                rotation = app.iconRotation,
                accentHex = app.iconAccent,
            )
            Text(
                text = app.label,
                color = CiyatoWhite,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyCellPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(CiyatoWhite.copy(alpha = 0.03f))
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(13.dp)),
    ) { Spacer(Modifier.height(0.dp)) }
}
