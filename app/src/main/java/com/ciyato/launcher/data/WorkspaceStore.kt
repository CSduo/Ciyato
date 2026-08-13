package com.ciyato.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned workspace storage. Visual order and creation identity intentionally
 * stay separate so inserting or reordering a workspace never renumbers it.
 *
 * v2: each workspace stores positioned [cells] (an app at a linear row-major grid
 * index) instead of a bare ordered list, enabling free placement with gaps. The
 * [appPackages] read accessor keeps every existing caller working. v1 layouts are
 * upgraded losslessly on parse (sequential cell = list index).
 *
 * A cell may span more than 1x1 via [AppCell.spanX]/[AppCell.spanY] (default 1,
 * so every pre-span layout and caller is unaffected). Occupancy is therefore a
 * rectangle, not a point — see [coveredCells].
 */
data class AppCell(val packageName: String, val cell: Int, val spanX: Int = 1, val spanY: Int = 1)

/** Linear row-major indices this cell's spanX×spanY rectangle covers on a grid
 *  that is [columns] wide. Span 1x1 always resolves to exactly `{cell}`. */
fun AppCell.coveredCells(columns: Int): Set<Int> {
    val cols = columns.coerceAtLeast(1)
    val col = cell.coerceAtLeast(0) % cols
    val row = cell.coerceAtLeast(0) / cols
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    return buildSet {
        for (dy in 0 until sy) for (dx in 0 until sx) add((row + dy) * cols + (col + dx))
    }
}

data class WorkspaceRecord(
    val id: String,
    val creationOrder: Int,
    val name: String? = null,
    val cells: List<AppCell> = emptyList(),
    val categoryKeys: List<String> = emptyList(),
    val starterDismissed: Boolean = false,
) {
    /** Package names in cell (reading) order — read-compat for every legacy caller. */
    val appPackages: List<String> get() = cells.sortedBy { it.cell }.map { it.packageName }
}

/** Rebuild a record's cells from an ordered package list (sequential, no gaps). */
fun WorkspaceRecord.withPackages(packages: List<String>): WorkspaceRecord =
    copy(cells = packages.distinct().mapIndexed { i, pkg -> AppCell(pkg, i) })

data class WorkspaceLayout(
    val workspaces: List<WorkspaceRecord>,
    val visualOrder: List<String>,
    val defaultWorkspaceId: String,
    val authorColumns: Int = DEFAULT_COLUMNS,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 5
    }

    fun workspaceAt(index: Int): WorkspaceRecord? = visualOrder
        .getOrNull(index)
        ?.let { id -> workspaces.firstOrNull { it.id == id } }

    fun indexOf(id: String): Int = visualOrder.indexOf(id)
}

object WorkspaceStore {
    private const val MAX_WORKSPACES = 10

    /** Sane upper bound on a tile's span in either axis — bigger than any real
     *  grid dimension, just enough to stop corrupt data or a fat-fingered resize
     *  from producing an absurd or unfittable rectangle. */
    private const val MAX_SPAN = 8

    fun parse(raw: String): WorkspaceLayout? = runCatching {
        val root = JSONObject(raw)
        val version = root.optInt("version", 0)
        if (version !in 1..WorkspaceLayout.CURRENT_VERSION) return null
        val records = root.optJSONArray("workspaces") ?: return null
        val workspaces = buildList {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                parseRecord(item, version)?.let(::add)
            }
        }
        val visualOrder = stringList(root.optJSONArray("visualOrder"))
        val defaultWorkspaceId = root.optString("defaultWorkspaceId")
        val authorColumns = root.optInt("authorColumns", WorkspaceLayout.DEFAULT_COLUMNS).coerceIn(1, 12)
        WorkspaceLayout(workspaces, visualOrder, defaultWorkspaceId, authorColumns = authorColumns)
            .takeIf(::isValid)
    }.getOrNull()

    private fun parseRecord(item: JSONObject, version: Int): WorkspaceRecord? {
        val id = item.optString("id")
        val creationOrder = item.optInt("creationOrder", 0)
        if (id.isBlank() || creationOrder <= 0) return null
        val cells = if (version >= 2 && item.optJSONArray("cells") != null) {
            parseCells(item.optJSONArray("cells"))
        } else {
            // v1 (or a v2 record missing cells): synthesize row-major from the
            // legacy package list — reproduces the old chunked(4) layout exactly.
            stringList(item.optJSONArray("appPackages")).mapIndexed { i, pkg -> AppCell(pkg, i) }
        }
        return WorkspaceRecord(
            id = id,
            creationOrder = creationOrder,
            name = item.optString("name").takeIf { it.isNotBlank() },
            cells = cells,
            categoryKeys = stringList(item.optJSONArray("categoryKeys")),
            starterDismissed = item.optBoolean("starterDismissed", false),
        )
    }

    private fun parseCells(array: JSONArray?): List<AppCell> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val pkg = obj.optString("pkg").trim()
            if (pkg.isBlank()) continue
            add(
                AppCell(
                    packageName = pkg,
                    cell = obj.optInt("cell", index).coerceAtLeast(0),
                    // Missing spanX/spanY (every pre-span saved layout) defaults to 1x1.
                    spanX = obj.optInt("spanX", 1).coerceIn(1, MAX_SPAN),
                    spanY = obj.optInt("spanY", 1).coerceIn(1, MAX_SPAN),
                ),
            )
        }
    }

    fun serialize(layout: WorkspaceLayout): String = JSONObject().apply {
        put("version", WorkspaceLayout.CURRENT_VERSION)
        put("defaultWorkspaceId", layout.defaultWorkspaceId)
        put("authorColumns", layout.authorColumns)
        put("visualOrder", JSONArray(layout.visualOrder))
        put("workspaces", JSONArray().apply {
            layout.workspaces.forEach { workspace ->
                put(
                    JSONObject().apply {
                        put("id", workspace.id)
                        put("creationOrder", workspace.creationOrder)
                        workspace.name?.let { put("name", it) }
                        put("cells", JSONArray().apply {
                            workspace.cells.forEach { c ->
                                put(
                                    JSONObject().apply {
                                        put("pkg", c.packageName)
                                        put("cell", c.cell)
                                        // Only written when spanning, so a non-spanning layout
                                        // serializes byte-identical to before this feature.
                                        if (c.spanX != 1) put("spanX", c.spanX)
                                        if (c.spanY != 1) put("spanY", c.spanY)
                                    },
                                )
                            }
                        })
                        // Legacy mirror so any older reader still degrades gracefully.
                        put("appPackages", JSONArray(workspace.appPackages))
                        put("categoryKeys", JSONArray(workspace.categoryKeys.distinct()))
                        put("starterDismissed", workspace.starterDismissed)
                    },
                )
            }
        })
    }.toString()

    fun migrateLegacy(
        count: Int,
        page0Apps: String,
        page2Apps: String,
        workspaceApps: String,
        workspaceCategories: String,
        ciyatoPackage: String,
    ): WorkspaceLayout {
        // Legacy storage counted the central Home page as a workspace page.
        // V2 stores only movable workspaces, leaving Home outside this model.
        val safePagerCount = count.coerceIn(3, MAX_WORKSPACES + 1)
        val safeCount = safePagerCount - 1
        val legacyApps = runCatching { JSONObject(workspaceApps) }.getOrDefault(JSONObject())
        val legacyCategories = runCatching { JSONObject(workspaceCategories) }.getOrDefault(JSONObject())
        val workspaces = (0 until safeCount).map { index ->
            val legacyPageIndex = if (index == 0) 0 else index + 1
            val apps = when (legacyPageIndex) {
                0 -> csv(page0Apps)
                2 -> csv(page2Apps)
                else -> csv(legacyApps.optString(legacyPageIndex.toString()))
            }.toMutableList()
            if (index == 0 && ciyatoPackage.isNotBlank()) {
                // Ciyato is a first-class standalone app on Workspace 1. Keep
                // every legacy shortcut, but make the Ciyato entry deterministic.
                apps.remove(ciyatoPackage)
                apps.add(0, ciyatoPackage)
            }
            WorkspaceRecord(
                id = "workspace-${index + 1}",
                creationOrder = index + 1,
                name = "Workspace ${index + 1}",
                cells = apps.distinct().mapIndexed { i, pkg -> AppCell(pkg, i) },
                categoryKeys = csv(legacyCategories.optString(legacyPageIndex.toString())),
            )
        }
        return WorkspaceLayout(
            workspaces = workspaces,
            visualOrder = workspaces.map(WorkspaceRecord::id),
            defaultWorkspaceId = workspaces.first().id,
        )
    }

    fun insert(layout: WorkspaceLayout, visualIndex: Int): WorkspaceLayout? {
        if (!isValid(layout) || layout.workspaces.size >= MAX_WORKSPACES) return null
        val creationOrder = (layout.workspaces.maxOfOrNull(WorkspaceRecord::creationOrder) ?: 0) + 1
        val workspace = WorkspaceRecord(
            id = "workspace-$creationOrder",
            creationOrder = creationOrder,
            name = "Workspace $creationOrder",
        )
        val order = layout.visualOrder.toMutableList()
        order.add(visualIndex.coerceIn(0, order.size), workspace.id)
        return layout.copy(workspaces = layout.workspaces + workspace, visualOrder = order)
    }

    fun remove(layout: WorkspaceLayout, workspaceId: String, moveContentsTo: String? = null): WorkspaceLayout? {
        if (!isValid(layout) || layout.workspaces.size <= 1 || workspaceId !in layout.visualOrder) return null
        val removed = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val remaining = layout.workspaces.filterNot { it.id == workspaceId }.toMutableList()
        val destinationId = moveContentsTo?.takeIf { it != workspaceId && it in layout.visualOrder }
        if (destinationId != null) {
            val destinationIndex = remaining.indexOfFirst { it.id == destinationId }
            if (destinationIndex >= 0) {
                val destination = remaining[destinationIndex]
                // Span-preserving merge: destination's own tiles keep their exact
                // cells; each incoming tile (destination wins on package clash,
                // matching the old distinct() precedence) lands at the first free
                // cell its own rectangle fits, so nothing is dropped or flattened.
                val columns = layout.authorColumns
                val existing = destination.cells.mapTo(HashSet()) { it.packageName }
                var cells = destination.cells
                removed.cells.sortedBy { it.cell }.forEach { incoming ->
                    if (existing.add(incoming.packageName)) {
                        val spanX = incoming.spanX.coerceIn(1, columns.coerceAtLeast(1))
                        val spanY = incoming.spanY.coerceIn(1, MAX_SPAN)
                        cells = cells + AppCell(incoming.packageName, firstFreeCell(cells, columns, spanX, spanY), spanX, spanY)
                    }
                }
                remaining[destinationIndex] = destination.copy(
                    cells = cells,
                    categoryKeys = (destination.categoryKeys + removed.categoryKeys).distinct(),
                )
            }
        }
        val order = layout.visualOrder.filterNot { it == workspaceId }
        val defaultId = layout.defaultWorkspaceId.takeIf { it != workspaceId } ?: order.first()
        return layout.copy(workspaces = remaining, visualOrder = order, defaultWorkspaceId = defaultId)
    }

    fun reorder(layout: WorkspaceLayout, sourceIndex: Int, destinationIndex: Int): WorkspaceLayout? {
        if (!isValid(layout) || sourceIndex !in layout.visualOrder.indices) return null
        val order = layout.visualOrder.toMutableList()
        val id = order.removeAt(sourceIndex)
        order.add(destinationIndex.coerceIn(0, order.size), id)
        return layout.copy(visualOrder = order)
    }

    fun rename(layout: WorkspaceLayout, workspaceId: String, name: String): WorkspaceLayout? {
        val cleanName = name.trim().take(40)
        if (!isValid(layout) || workspaceId !in layout.visualOrder || cleanName.isBlank()) return null
        return layout.copy(workspaces = layout.workspaces.map { workspace ->
            if (workspace.id == workspaceId) workspace.copy(name = cleanName) else workspace
        })
    }

    fun duplicate(layout: WorkspaceLayout, workspaceId: String): WorkspaceLayout? {
        if (!isValid(layout) || layout.workspaces.size >= MAX_WORKSPACES) return null
        val source = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val creationOrder = (layout.workspaces.maxOfOrNull(WorkspaceRecord::creationOrder) ?: 0) + 1
        val copy = source.copy(
            id = "workspace-$creationOrder",
            creationOrder = creationOrder,
            name = "Copy of ${source.name ?: "Workspace ${source.creationOrder}"}".take(40),
            starterDismissed = source.starterDismissed ||
                source.appPackages.isNotEmpty() || source.categoryKeys.isNotEmpty(),
        )
        val order = layout.visualOrder.toMutableList()
        order.add(layout.indexOf(workspaceId) + 1, copy.id)
        return layout.copy(workspaces = layout.workspaces + copy, visualOrder = order)
    }

    fun setDefault(layout: WorkspaceLayout, workspaceId: String): WorkspaceLayout? {
        if (!isValid(layout) || workspaceId !in layout.visualOrder) return null
        return layout.copy(defaultWorkspaceId = workspaceId)
    }

    fun withWorkspace(layout: WorkspaceLayout, workspace: WorkspaceRecord): WorkspaceLayout? {
        if (!isValid(layout) || workspace.id !in layout.visualOrder) return null
        return layout.copy(workspaces = layout.workspaces.map { current ->
            if (current.id == workspace.id) workspace.copy(
                cells = normalizeCells(workspace.cells, layout.authorColumns),
                categoryKeys = workspace.categoryKeys.distinct(),
            ) else current
        })
    }

    // ── Positioned-cell operations (free placement, gaps, swaps) ──────────────

    /**
     * Smallest cell index, row-major, whose spanX×spanY rectangle fits without
     * overlapping [cells] or running off the right edge — fills gaps before
     * extending. [columns] defaults so the pre-span single-arg call site keeps
     * compiling; internal callers thread the workspace's real [columns] through.
     */
    fun firstFreeCell(
        cells: List<AppCell>,
        columns: Int = WorkspaceLayout.DEFAULT_COLUMNS,
        spanX: Int = 1,
        spanY: Int = 1,
    ): Int {
        val cols = columns.coerceAtLeast(1)
        // Clamped to cols (not just MAX_SPAN) so a candidate at column 0 can
        // always eventually fit — otherwise the search below would never end.
        val sx = spanX.coerceIn(1, cols)
        val sy = spanY.coerceIn(1, MAX_SPAN)
        var i = 0
        while (!fits(AppCell("", i, sx, sy), cells, cols)) i++
        return i
    }

    /** Append packages, each at the next free cell, preserving existing positions. */
    fun addAppsAtFreeCells(layout: WorkspaceLayout, workspaceId: String, packages: Collection<String>): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val existing = workspace.cells.mapTo(HashSet()) { it.packageName }
        var cells = workspace.cells
        var added = false
        packages.forEach { pkg ->
            if (existing.add(pkg)) {
                cells = cells + AppCell(pkg, firstFreeCell(cells, layout.authorColumns))
                added = true
            }
        }
        if (!added) return null
        return withWorkspace(layout, workspace.copy(cells = cells))
    }

    fun removeApp(layout: WorkspaceLayout, workspaceId: String, packageName: String): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        if (packageName !in workspace.appPackages) return null
        return withWorkspace(layout, workspace.copy(cells = workspace.cells.filterNot { it.packageName == packageName }))
    }

    /**
     * Places [packageName] at [targetCell] within one workspace, swapping any sole
     * occupant. Preserves the app's existing span if it's already on this workspace;
     * [spanX]/[spanY] only seed a brand-new arrival (e.g. from [moveApp]). Fails if
     * the rectangle runs off-grid or would overlap more than one existing tile.
     */
    fun placeApp(
        layout: WorkspaceLayout,
        workspaceId: String,
        packageName: String,
        targetCell: Int,
        spanX: Int = 1,
        spanY: Int = 1,
    ): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val columns = layout.authorColumns
        val current = workspace.cells.firstOrNull { it.packageName == packageName }
        val moving = AppCell(packageName, targetCell.coerceAtLeast(0), current?.spanX ?: spanX, current?.spanY ?: spanY)
        if (!onGrid(moving, columns)) return null
        val without = workspace.cells.filterNot { it.packageName == packageName }
        val covered = moving.coveredCells(columns)
        val overlapping = without.filter { it.coveredCells(columns).any(covered::contains) }
        if (overlapping.size > 1) return null // mover's rectangle would clobber more than one tile
        var cells = without
        overlapping.singleOrNull()?.let { occupant ->
            val rest = without.filterNot { it.packageName == occupant.packageName }
            // Swap the sole occupant back to the mover's old spot if it still fits
            // there, else drop it on its own next free cell (matching its span).
            val backAtSource = current?.let { occupant.copy(cell = it.cell) }?.takeIf { fits(it, rest, columns) }
            val occupantTarget = backAtSource?.cell ?: firstFreeCell(rest, columns, occupant.spanX, occupant.spanY)
            cells = rest + occupant.copy(cell = occupantTarget)
        }
        return withWorkspace(layout, workspace.copy(cells = cells + moving))
    }

    /** Moves [packageName] from one workspace to a cell in another (or the same),
     *  carrying its existing span along. */
    fun moveApp(layout: WorkspaceLayout, fromWorkspaceId: String, toWorkspaceId: String, packageName: String, targetCell: Int): WorkspaceLayout? {
        if (fromWorkspaceId == toWorkspaceId) return placeApp(layout, toWorkspaceId, packageName, targetCell)
        val from = layout.workspaces.firstOrNull { it.id == fromWorkspaceId } ?: return null
        val source = from.cells.firstOrNull { it.packageName == packageName } ?: return null
        val without = withWorkspace(layout, from.copy(cells = from.cells.filterNot { it.packageName == packageName }))
            ?: return null
        return placeApp(without, toWorkspaceId, packageName, targetCell, source.spanX, source.spanY)
    }

    /**
     * Changes [packageName]'s span within [workspaceId]. Returns null (no-op) if
     * the resized rectangle would run off-grid or overlap another tile — the
     * caller keeps whatever size last fit.
     */
    fun resizeApp(layout: WorkspaceLayout, workspaceId: String, packageName: String, spanX: Int, spanY: Int): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val current = workspace.cells.firstOrNull { it.packageName == packageName } ?: return null
        val resized = current.copy(spanX = spanX.coerceIn(1, MAX_SPAN), spanY = spanY.coerceIn(1, MAX_SPAN))
        val others = workspace.cells.filterNot { it.packageName == packageName }
        if (!fits(resized, others, layout.authorColumns)) return null
        return withWorkspace(layout, workspace.copy(cells = others + resized))
    }

    /**
     * Legacy sequential move (drop into an ordered slot). Kept for the accessible
     * reorder path; repacks row-major so no gap or duplicate can appear. Unlike
     * [WorkspaceRecord.withPackages] (1x1-by-construction), this re-places every
     * tile at its first-fitting cell in the new reading order, so the moved tile
     * and every tile shifted around it keep their existing span.
     */
    fun moveAppWithinWorkspace(
        layout: WorkspaceLayout,
        workspaceId: String,
        packageName: String,
        destinationIndex: Int,
    ): WorkspaceLayout? {
        if (!isValid(layout) || workspaceId !in layout.visualOrder) return null
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val apps = workspace.appPackages.toMutableList()
        val sourceIndex = apps.indexOf(packageName)
        if (sourceIndex < 0 || apps.size < 2) return null
        val targetIndex = destinationIndex.coerceIn(0, apps.lastIndex)
        if (sourceIndex == targetIndex) return layout
        apps.removeAt(sourceIndex)
        apps.add(targetIndex, packageName)
        val spanByPackage = workspace.cells.associateBy { it.packageName }
        val columns = layout.authorColumns
        var cells = emptyList<AppCell>()
        apps.forEach { pkg ->
            val span = spanByPackage[pkg]
            val spanX = (span?.spanX ?: 1).coerceIn(1, columns.coerceAtLeast(1))
            val spanY = (span?.spanY ?: 1).coerceIn(1, MAX_SPAN)
            cells = cells + AppCell(pkg, firstFreeCell(cells, columns, spanX, spanY), spanX, spanY)
        }
        return withWorkspace(layout, workspace.copy(cells = cells))
    }

    /**
     * Repack a record's cells row-major on a grid [columns] wide — used when the
     * grid's column count changes. Every tile keeps its span where its rectangle
     * still fits; where it no longer does (columns shrank under it), spanX is
     * clamped down to what fits rather than the app being dropped or reset to
     * 1x1. Every app is always preserved.
     */
    fun reflow(record: WorkspaceRecord, columns: Int = WorkspaceLayout.DEFAULT_COLUMNS): WorkspaceRecord {
        val cols = columns.coerceAtLeast(1)
        var cells = emptyList<AppCell>()
        record.cells.sortedBy { it.cell }.forEach { c ->
            if (cells.none { it.packageName == c.packageName }) {
                val spanX = c.spanX.coerceIn(1, cols)
                val spanY = c.spanY.coerceIn(1, MAX_SPAN)
                cells = cells + AppCell(c.packageName, firstFreeCell(cells, cols, spanX, spanY), spanX, spanY)
            }
        }
        return record.copy(cells = cells)
    }

    // ── Validation & helpers ──────────────────────────────────────────────────

    /** De-dupes packages and nudges any cell whose rectangle overlaps an
     *  already-placed one (or a duplicate index) forward to the next fit. */
    private fun normalizeCells(cells: List<AppCell>, columns: Int): List<AppCell> {
        val seenPkg = HashSet<String>()
        val result = ArrayList<AppCell>()
        for (c in cells.sortedBy { it.cell }) {
            if (!seenPkg.add(c.packageName)) continue
            // Clamped to columns (not just MAX_SPAN) so the search below is
            // guaranteed to terminate — see firstFreeCell for the same reasoning.
            val spanX = c.spanX.coerceIn(1, columns.coerceAtLeast(1))
            val spanY = c.spanY.coerceIn(1, MAX_SPAN)
            var cell = c.cell.coerceAtLeast(0)
            while (!fits(AppCell(c.packageName, cell, spanX, spanY), result, columns)) cell++
            result.add(AppCell(c.packageName, cell, spanX, spanY))
        }
        return result
    }

    private fun isValid(layout: WorkspaceLayout): Boolean {
        val ids = layout.workspaces.map(WorkspaceRecord::id)
        val sequences = layout.workspaces.map(WorkspaceRecord::creationOrder)
        val columns = layout.authorColumns
        val cellsValid = layout.workspaces.all { ws ->
            val cells = ws.cells
            cells.map { it.packageName }.distinct().size == cells.size &&
                cells.all { it.cell >= 0 && it.spanX >= 1 && it.spanY >= 1 && onGrid(it, columns) } &&
                !hasOverlap(cells, columns)
        }
        return layout.version == WorkspaceLayout.CURRENT_VERSION &&
            ids.size in 1..MAX_WORKSPACES &&
            ids.distinct().size == ids.size &&
            sequences.distinct().size == sequences.size &&
            layout.visualOrder.size == ids.size &&
            layout.visualOrder.toSet() == ids.toSet() &&
            layout.defaultWorkspaceId in ids &&
            cellsValid
    }

    /** True if [cell]'s rectangle stays within [columns] on its right edge. */
    private fun onGrid(cell: AppCell, columns: Int): Boolean {
        if (cell.cell < 0) return false
        val cols = columns.coerceAtLeast(1)
        return cell.cell % cols + cell.spanX.coerceAtLeast(1) <= cols
    }

    /** True if [candidate] is on-grid and overlaps none of [others]. */
    private fun fits(candidate: AppCell, others: List<AppCell>, columns: Int): Boolean {
        if (!onGrid(candidate, columns)) return false
        val covered = candidate.coveredCells(columns)
        return others.none { it.coveredCells(columns).any(covered::contains) }
    }

    /** True if any two cells in [cells] share a covered index on a [columns]-wide grid. */
    private fun hasOverlap(cells: List<AppCell>, columns: Int): Boolean {
        val seen = HashSet<Int>()
        return cells.any { c -> c.coveredCells(columns).any { !seen.add(it) } }
    }

    private fun stringList(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }.distinct()

    private fun csv(value: String): List<String> = value
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
}
