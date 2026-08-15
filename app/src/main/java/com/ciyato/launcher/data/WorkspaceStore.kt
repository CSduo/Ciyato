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
 *
 * The grid is the DEFAULT layout generator only, never a runtime constraint:
 * [AppCell.pos] is an additive free-canvas overlay (default null, so every
 * pre-canvas layout and caller is unaffected): once an object is deliberately
 * dragged off the grid it gets a [CanvasPos] and is rendered there instead,
 * while [AppCell.cell] is kept untouched as the fallback grid slot it returns
 * to via [WorkspaceStore.resetAppToGrid]. Grid rules (distinct cells, no
 * rectangle overlap, on-grid) apply only to cells with `pos == null` — a
 * free-positioned object may overlap anything by design.
 */
data class AppCell(
    val packageName: String,
    val cell: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val pos: CanvasPos? = null,
)

/**
 * A free placement on the workspace canvas, additive to [AppCell.cell].
 *
 * [x]/[y] are NORMALIZED FRACTIONS of the usable canvas (0f..1f), anchoring
 * the object's own top-left corner, measured from the canvas's top-left —
 * never raw pixels. Raw pixels would break on rotation, on a different screen
 * size, and if dock/status-bar insets change; fractions survive all three and
 * are converted to pixels only at render time. Always clamped into 0f..1f on
 * write (see [WorkspaceStore.moveAppToCanvas]) so an object can never be
 * persisted somewhere it can no longer be grabbed again.
 *
 * [z] is stacking order — higher draws on top. See [WorkspaceStore.nextZ] for
 * "whatever you move comes to the front" (desktop-window) behaviour. Overlap
 * between free-positioned objects is explicitly allowed; there is no
 * collision rule for [z] or for (x, y).
 */
data class CanvasPos(val x: Float, val y: Float, val z: Int = 0)

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
    // Free canvas placement for non-app Home objects (greeting, search, weather,
    // category cards…), keyed by a stable object id — e.g. "greeting",
    // "datetime", "search", "weather", "today", "categories-heading",
    // "category:Work". Additive sibling to [AppCell.pos]/[CanvasPos]: an id
    // absent here has no free placement and keeps its ordinary flow position
    // (see WorkspaceStore.moveObjectToCanvas / resetObjectToFlow). Defaults
    // empty so every layout saved before this feature — including the user's
    // real one — parses back with an untouched, byte-identical round trip.
    val objectPositions: Map<String, CanvasPos> = emptyMap(),
    // Object ids explicitly removed from this workspace's canvas (see
    // WorkspaceStore.hideObject/showObject) — additive, defaults empty so
    // every layout saved before this feature keeps every object visible,
    // byte-identical to before. This is the hide/show mechanism for canvas
    // objects that have no dedicated global setting of their own (e.g.
    // "datetime"); the pre-existing sections that already had one (greeting,
    // search, weather, today, the categories heading, individual category
    // cards) keep using that instead, so their history, Settings entry, and
    // Undo snackbar wiring are untouched — see HomeScreen's per-object
    // visibility resolution.
    val hiddenObjects: Set<String> = emptySet(),
) {
    /** Package names in cell (reading) order — read-compat for every legacy caller. */
    val appPackages: List<String> get() = cells.sortedBy { it.cell }.map { it.packageName }
}

/** Rebuild a record's cells from an ordered package list (sequential, no gaps).
 *  Already resets every span to 1x1 by construction; for the same reason it
 *  also clears any free [CanvasPos] — a bare package list carries no
 *  positional metadata at all, so there is nothing to preserve. */
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

        /** Home's fixed record id. Home lives in [workspaces] like any other
         *  record (so it can hold real cells) but is deliberately never in
         *  [visualOrder] — it's the fixed centre page, not a swipeable workspace. */
        const val HOME_WORKSPACE_ID = "home"
    }

    fun workspaceAt(index: Int): WorkspaceRecord? = visualOrder
        .getOrNull(index)
        ?.let { id -> workspaces.firstOrNull { it.id == id } }

    fun indexOf(id: String): Int = visualOrder.indexOf(id)

    /** Direct id lookup — the only way to reach [HOME_WORKSPACE_ID], since it has
     *  no [visualOrder] position for [workspaceAt] to resolve. */
    fun workspaceById(id: String): WorkspaceRecord? = workspaces.firstOrNull { it.id == id }
}

object WorkspaceStore {
    private const val MAX_WORKSPACES = 10

    /** Sane upper bound on a tile's span in either axis — bigger than any real
     *  grid dimension, just enough to stop corrupt data or a fat-fingered resize
     *  from producing an absurd or unfittable rectangle. */
    private const val MAX_SPAN = 8

    /** Home's reserved creationOrder — far outside the small counting range every
     *  movable workspace uses, so it can never collide with one. */
    private const val HOME_CREATION_ORDER = Int.MAX_VALUE

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
        WorkspaceLayout(ensureHomeWorkspace(workspaces), visualOrder, defaultWorkspaceId, authorColumns = authorColumns)
            .takeIf(::isValid)
    }.getOrNull()

    /** Every saved layout predates Home having its own backing record (or is a
     *  brand-new one that never wrote one). Synthesize an empty Home the first
     *  time it's missing so it round-trips from then on — every other workspace
     *  and app in [workspaces] is returned untouched. */
    private fun ensureHomeWorkspace(workspaces: List<WorkspaceRecord>): List<WorkspaceRecord> {
        if (workspaces.any { it.id == WorkspaceLayout.HOME_WORKSPACE_ID }) return workspaces
        return workspaces + WorkspaceRecord(
            id = WorkspaceLayout.HOME_WORKSPACE_ID,
            // Reserved, not max()+1: Home must never occupy a creationOrder that
            // insert()/duplicate() could otherwise hand out next — see nextCreationOrder.
            creationOrder = HOME_CREATION_ORDER,
            name = "Home",
        )
    }

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
            // Missing "objectPositions" (every pre-canvas-objects saved layout —
            // including the user's real one) parses back to emptyMap(), i.e.
            // every object stays flow-positioned.
            objectPositions = parseObjectPositions(item.optJSONObject("objectPositions")),
            // Missing "hiddenObjects" (every pre-canvas-objects saved layout —
            // including the user's real one) parses back to emptySet(), i.e.
            // every object stays visible.
            hiddenObjects = stringList(item.optJSONArray("hiddenObjects")).toSet(),
        )
    }

    private fun parseObjectPositions(obj: JSONObject?): Map<String, CanvasPos> {
        if (obj == null) return emptyMap()
        val result = LinkedHashMap<String, CanvasPos>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val id = keys.next() as String
            val p = obj.optJSONObject(id) ?: continue
            result[id] = CanvasPos(
                x = p.optDouble("x", 0.0).toFloat().coerceIn(0f, 1f),
                y = p.optDouble("y", 0.0).toFloat().coerceIn(0f, 1f),
                z = p.optInt("z", 0),
            )
        }
        return result
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
                    // Missing "pos" (every pre-canvas saved layout — including the
                    // user's real one) parses back to null, i.e. still grid-positioned.
                    pos = obj.optJSONObject("pos")?.let { p ->
                        CanvasPos(
                            x = p.optDouble("x", 0.0).toFloat().coerceIn(0f, 1f),
                            y = p.optDouble("y", 0.0).toFloat().coerceIn(0f, 1f),
                            z = p.optInt("z", 0),
                        )
                    },
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
                                        // Only written when free-positioned, so a layout with no
                                        // canvas placements (every layout before this feature,
                                        // including the user's real saved one) serializes
                                        // byte-identical to before this feature.
                                        c.pos?.let { p ->
                                            put(
                                                "pos",
                                                JSONObject().apply {
                                                    put("x", p.x.toDouble())
                                                    put("y", p.y.toDouble())
                                                    if (p.z != 0) put("z", p.z)
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        })
                        // Legacy mirror so any older reader still degrades gracefully.
                        put("appPackages", JSONArray(workspace.appPackages))
                        put("categoryKeys", JSONArray(workspace.categoryKeys.distinct()))
                        put("starterDismissed", workspace.starterDismissed)
                        // Only written when at least one object is free-positioned, so a
                        // layout with none (every layout before this feature, including
                        // the user's real saved one) serializes byte-identical to before.
                        if (workspace.objectPositions.isNotEmpty()) {
                            put(
                                "objectPositions",
                                JSONObject().apply {
                                    workspace.objectPositions.forEach { (id, p) ->
                                        put(
                                            id,
                                            JSONObject().apply {
                                                put("x", p.x.toDouble())
                                                put("y", p.y.toDouble())
                                                if (p.z != 0) put("z", p.z)
                                            },
                                        )
                                    }
                                },
                            )
                        }
                        // Only written when at least one object is hidden, so a
                        // layout with none (every layout before this feature,
                        // including the user's real saved one) serializes
                        // byte-identical to before.
                        if (workspace.hiddenObjects.isNotEmpty()) {
                            put("hiddenObjects", JSONArray(workspace.hiddenObjects.toList()))
                        }
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
            workspaces = ensureHomeWorkspace(workspaces),
            visualOrder = workspaces.map(WorkspaceRecord::id),
            defaultWorkspaceId = workspaces.first().id,
        )
    }

    fun insert(layout: WorkspaceLayout, visualIndex: Int): WorkspaceLayout? {
        if (!isValid(layout) || layout.visualOrder.size >= MAX_WORKSPACES) return null
        val creationOrder = nextCreationOrder(layout)
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
        // visualOrder.size, not workspaces.size — Home always adds one to the
        // latter, and Home never counts as "the last remaining workspace".
        if (!isValid(layout) || layout.visualOrder.size <= 1 || workspaceId !in layout.visualOrder) return null
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
                        cells = if (incoming.pos != null) {
                            // Free-positioned tiles carry their (x, y) straight across —
                            // it's a canvas fraction, not a grid cell, so it means the
                            // same thing on any workspace's same-sized canvas. No
                            // grid-fit search needed: overlap is allowed for these.
                            cells + incoming
                        } else {
                            val spanX = incoming.spanX.coerceIn(1, columns.coerceAtLeast(1))
                            val spanY = incoming.spanY.coerceIn(1, MAX_SPAN)
                            val onGrid = cells.filter { it.pos == null }
                            cells + AppCell(incoming.packageName, firstFreeCell(onGrid, columns, spanX, spanY), spanX, spanY)
                        }
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
        // workspaceId !in visualOrder also rules out Home — it has no visual
        // position for the new copy to be inserted next to.
        if (!isValid(layout) || layout.visualOrder.size >= MAX_WORKSPACES || workspaceId !in layout.visualOrder) return null
        val source = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val creationOrder = nextCreationOrder(layout)
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
        // Existence in `workspaces`, not visualOrder membership — Home is a real
        // record but (by design) never appears in visualOrder.
        if (!isValid(layout) || layout.workspaces.none { it.id == workspace.id }) return null
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
                // Free-positioned tiles are exempt from grid occupancy, so their
                // stale `cell` fallback must not block or shift where a new app lands.
                cells = cells + AppCell(pkg, firstFreeCell(cells.filter { it.pos == null }, layout.authorColumns))
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
     *
     * `placeApp` targets a grid cell, so it is a deliberate grid placement: any
     * free [CanvasPos] the app previously had is cleared (the freshly built
     * [moving] cell never carries one over) — same intent as [resetAppToGrid],
     * just landing at a chosen cell in one step instead of the old one. Existing
     * free-positioned tiles are likewise exempt from this function's collision
     * checks; only grid tiles can be swapped or bumped.
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
        val grid = without.filter { it.pos == null } // free-positioned tiles never collide
        val covered = moving.coveredCells(columns)
        val overlapping = grid.filter { it.coveredCells(columns).any(covered::contains) }
        if (overlapping.size > 1) return null // mover's rectangle would clobber more than one tile
        var cells = without
        overlapping.singleOrNull()?.let { occupant ->
            val rest = without.filterNot { it.packageName == occupant.packageName }
            val restGrid = rest.filter { it.pos == null }
            // Swap the sole occupant back to the mover's old spot if it still fits
            // there, else drop it on its own next free cell (matching its span).
            val backAtSource = current?.let { occupant.copy(cell = it.cell) }?.takeIf { fits(it, restGrid, columns) }
            val occupantTarget = backAtSource?.cell ?: firstFreeCell(restGrid, columns, occupant.spanX, occupant.spanY)
            cells = rest + occupant.copy(cell = occupantTarget)
        }
        return withWorkspace(layout, workspace.copy(cells = cells + moving))
    }

    /** Moves [packageName] from one workspace to a cell in another (or the same),
     *  carrying its existing span along. Delegates to [placeApp], so — same as
     *  that function — any free [CanvasPos] the app had is cleared; it lands
     *  grid-positioned at the given cell. */
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
     * caller keeps whatever size last fit. Uses `.copy()` on the current cell, so
     * a free [CanvasPos] (if any) is left exactly as-is; a free-positioned tile
     * is exempt from the grid fit check below (overlap is allowed for it either
     * way), so resizing it always succeeds.
     */
    fun resizeApp(layout: WorkspaceLayout, workspaceId: String, packageName: String, spanX: Int, spanY: Int): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val current = workspace.cells.firstOrNull { it.packageName == packageName } ?: return null
        val resized = current.copy(spanX = spanX.coerceIn(1, MAX_SPAN), spanY = spanY.coerceIn(1, MAX_SPAN))
        val others = workspace.cells.filterNot { it.packageName == packageName }
        if (current.pos == null && !fits(resized, others.filter { it.pos == null }, layout.authorColumns)) return null
        return withWorkspace(layout, workspace.copy(cells = others + resized))
    }

    /**
     * Legacy sequential move (drop into an ordered slot). Kept for the accessible
     * reorder path; repacks row-major so no gap or duplicate can appear. Unlike
     * [WorkspaceRecord.withPackages] (1x1-by-construction), this re-places every
     * tile at its first-fitting cell in the new reading order, so the moved tile
     * and every tile shifted around it keep their existing span.
     *
     * Free-positioned tiles aren't grid-bound, so they take no part in this
     * reading-order reflow: they're left exactly as-is (cell fallback and
     * [CanvasPos] both untouched) and [packageName] itself must be a grid tile —
     * there's no "reading order index" for a canvas object, so this returns null
     * (no-op) if it isn't one.
     */
    fun moveAppWithinWorkspace(
        layout: WorkspaceLayout,
        workspaceId: String,
        packageName: String,
        destinationIndex: Int,
    ): WorkspaceLayout? {
        // Existence in `workspaces`, not visualOrder membership — this accessible
        // reorder path works on Home's grid too.
        if (!isValid(layout) || layout.workspaces.none { it.id == workspaceId }) return null
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val free = workspace.cells.filter { it.pos != null }
        val apps = workspace.cells.filter { it.pos == null }.sortedBy { it.cell }.map { it.packageName }.toMutableList()
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
        return withWorkspace(layout, workspace.copy(cells = cells + free))
    }

    /**
     * Repack a record's cells row-major on a grid [columns] wide — used when the
     * grid's column count changes. Every tile keeps its span where its rectangle
     * still fits; where it no longer does (columns shrank under it), spanX is
     * clamped down to what fits rather than the app being dropped or reset to
     * 1x1. Every app is always preserved.
     *
     * A free-positioned tile isn't grid-bound, so a grid-size change is not a
     * reason to move it: it's excluded from the repack entirely and carried
     * through untouched (cell fallback and [CanvasPos] both preserved).
     */
    fun reflow(record: WorkspaceRecord, columns: Int = WorkspaceLayout.DEFAULT_COLUMNS): WorkspaceRecord {
        val cols = columns.coerceAtLeast(1)
        val free = record.cells.filter { it.pos != null }
        var cells = emptyList<AppCell>()
        record.cells.filter { it.pos == null }.sortedBy { it.cell }.forEach { c ->
            if (cells.none { it.packageName == c.packageName }) {
                val spanX = c.spanX.coerceIn(1, cols)
                val spanY = c.spanY.coerceIn(1, MAX_SPAN)
                cells = cells + AppCell(c.packageName, firstFreeCell(cells, cols, spanX, spanY), spanX, spanY)
            }
        }
        return record.copy(cells = cells + free)
    }

    // ── Canvas placement (free-position overlay) ──────────────────────────────
    // See [AppCell.pos] / [CanvasPos] for the model. These are additive: they
    // never touch [AppCell.cell], so an object can always fall back to grid flow.

    /**
     * Sets (or updates) [packageName]'s free canvas placement within
     * [workspaceId], overriding [AppCell.cell] for rendering while leaving
     * `cell` itself untouched as the grid fallback (see [resetAppToGrid]).
     * [x]/[y] are clamped into 0f..1f — an object must never be persisted
     * somewhere it can no longer be grabbed again. Free-positioned tiles are
     * exempt from every grid rule (see [isValid]), so this never fails for
     * overlap — only if [packageName] isn't on this workspace at all. Pass
     * [nextZ] as [z] to bring the moved tile to the front, desktop-window style.
     */
    fun moveAppToCanvas(layout: WorkspaceLayout, workspaceId: String, packageName: String, x: Float, y: Float, z: Int): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        if (workspace.cells.none { it.packageName == packageName }) return null
        val pos = CanvasPos(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), z)
        val cells = workspace.cells.map { if (it.packageName == packageName) it.copy(pos = pos) else it }
        return withWorkspace(layout, workspace.copy(cells = cells))
    }

    /** Clears [packageName]'s free canvas placement so it returns to ordinary
     *  grid flow at its preserved [AppCell.cell]. Null (no-op) if it's already
     *  grid-positioned, matching this file's usual not-applicable convention. */
    fun resetAppToGrid(layout: WorkspaceLayout, workspaceId: String, packageName: String): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val current = workspace.cells.firstOrNull { it.packageName == packageName } ?: return null
        if (current.pos == null) return null
        val cells = workspace.cells.map { if (it.packageName == packageName) it.copy(pos = null) else it }
        return withWorkspace(layout, workspace.copy(cells = cells))
    }

    /** [packageName]'s free canvas position within [workspaceId], or null if
     *  it's still grid-positioned — the UI derives pixels from [AppCell.cell]
     *  itself in that case. This store stays pure/JVM-only and never computes
     *  pixels; callers convert the normalized fraction at render time. */
    fun canvasPosition(layout: WorkspaceLayout, workspaceId: String, packageName: String): CanvasPos? =
        layout.workspaces.firstOrNull { it.id == workspaceId }
            ?.cells?.firstOrNull { it.packageName == packageName }?.pos

    /** Next z-index above every free-positioned app tile OR object already in
     *  [workspaceId] — pass this into [moveAppToCanvas] or [moveObjectToCanvas]
     *  so "whatever you move comes to the front" (desktop-window stacking)
     *  holds across BOTH: apps and objects share this one z space, so an
     *  object dragged after an app always outranks it, and vice versa.
     *  Grid-only tiles and flow-only objects have no z and don't count; a
     *  workspace with nothing free-positioned at all starts at 0. */
    fun nextZ(layout: WorkspaceLayout, workspaceId: String): Int {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return 0
        val appZ = workspace.cells.mapNotNull { it.pos?.z }
        val objectZ = workspace.objectPositions.values.map { it.z }
        return ((appZ + objectZ).maxOrNull() ?: -1) + 1
    }

    // ── Canvas placement — non-app objects ─────────────────────────────────────
    // Mirrors the app canvas overlay above (see AppCell.pos / CanvasPos), but for
    // whole Home sections (greeting, search, weather…) and category cards, keyed
    // by a stable object id instead of a package name. Additive: [objectPositions]
    // is a sibling map on WorkspaceRecord that never touches [cells], so an
    // object always has a flow fallback (its ordinary stacked position) when
    // absent here — same "grid/flow generates the default layout only, never a
    // runtime constraint" rule the app canvas already follows.

    /**
     * Sets (or updates) [objectId]'s free canvas placement within [workspaceId].
     * [x]/[y] are clamped into 0f..1f — an object must never be persisted
     * somewhere it can no longer be grabbed again. Pass [nextZ] as [z] to bring
     * the moved object to the front, desktop-window style, in the SAME z space
     * apps use. Never fails for overlap (free placement has no collision rule
     * by design) — only if [workspaceId] doesn't exist.
     */
    fun moveObjectToCanvas(layout: WorkspaceLayout, workspaceId: String, objectId: String, x: Float, y: Float, z: Int): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val pos = CanvasPos(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), z)
        return withWorkspace(layout, workspace.copy(objectPositions = workspace.objectPositions + (objectId to pos)))
    }

    /** Clears [objectId]'s free canvas placement so it returns to ordinary
     *  document flow. Null (no-op) if it was never free-positioned, matching
     *  this file's usual not-applicable convention. */
    fun resetObjectToFlow(layout: WorkspaceLayout, workspaceId: String, objectId: String): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        if (objectId !in workspace.objectPositions) return null
        return withWorkspace(layout, workspace.copy(objectPositions = workspace.objectPositions - objectId))
    }

    /** [objectId]'s free canvas position within [workspaceId], or null if it's
     *  still flow-positioned — the UI keeps its ordinary stacked position in
     *  that case. This store stays pure/JVM-only; callers convert the
     *  normalized fraction to pixels at render time. */
    fun objectCanvasPosition(layout: WorkspaceLayout, workspaceId: String, objectId: String): CanvasPos? =
        layout.workspaces.firstOrNull { it.id == workspaceId }?.objectPositions?.get(objectId)

    /** Hides [objectId] on [workspaceId] — see [WorkspaceRecord.hiddenObjects].
     *  Null (no-op) if it's already hidden. */
    fun hideObject(layout: WorkspaceLayout, workspaceId: String, objectId: String): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        if (objectId in workspace.hiddenObjects) return null
        return withWorkspace(layout, workspace.copy(hiddenObjects = workspace.hiddenObjects + objectId))
    }

    /** Reveals a previously hidden [objectId] on [workspaceId]. Its free
     *  canvas position (if it had one) is untouched — it reappears exactly
     *  where it was left. Null (no-op) if it wasn't hidden. */
    fun showObject(layout: WorkspaceLayout, workspaceId: String, objectId: String): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        if (objectId !in workspace.hiddenObjects) return null
        return withWorkspace(layout, workspace.copy(hiddenObjects = workspace.hiddenObjects - objectId))
    }

    /** Next creationOrder for a brand-new movable workspace — deliberately
     *  ignores Home's reserved [HOME_CREATION_ORDER] sentinel so it can never
     *  get pulled into the normal 1, 2, 3… sequence real workspaces use. */
    private fun nextCreationOrder(layout: WorkspaceLayout): Int =
        (layout.workspaces.filterNot { it.id == WorkspaceLayout.HOME_WORKSPACE_ID }
            .maxOfOrNull(WorkspaceRecord::creationOrder) ?: 0) + 1

    // ── Validation & helpers ──────────────────────────────────────────────────

    /** De-dupes packages and nudges any cell whose rectangle overlaps an
     *  already-placed one (or a duplicate index) forward to the next fit.
     *  Free-positioned tiles are exempt: they're carried through with their
     *  `pos` and fallback `cell` exactly as given, and (being exempt from
     *  overlap themselves) don't block where a grid tile can land either. */
    private fun normalizeCells(cells: List<AppCell>, columns: Int): List<AppCell> {
        val seenPkg = HashSet<String>()
        val result = ArrayList<AppCell>()
        for (c in cells.sortedBy { it.cell }) {
            if (!seenPkg.add(c.packageName)) continue
            if (c.pos != null) {
                result.add(c.copy(cell = c.cell.coerceAtLeast(0)))
                continue
            }
            // Clamped to columns (not just MAX_SPAN) so the search below is
            // guaranteed to terminate — see firstFreeCell for the same reasoning.
            val spanX = c.spanX.coerceIn(1, columns.coerceAtLeast(1))
            val spanY = c.spanY.coerceIn(1, MAX_SPAN)
            var cell = c.cell.coerceAtLeast(0)
            val onGrid = result.filter { it.pos == null }
            while (!fits(AppCell(c.packageName, cell, spanX, spanY), onGrid, columns)) cell++
            result.add(AppCell(c.packageName, cell, spanX, spanY))
        }
        return result
    }

    private fun isValid(layout: WorkspaceLayout): Boolean {
        val ids = layout.workspaces.map(WorkspaceRecord::id)
        // Home lives in `workspaces` but is deliberately absent from `visualOrder`
        // — it's the fixed centre page, not a swipeable workspace — so every
        // visual-order/default-workspace check compares against ids with it
        // excluded, instead of the raw id set.
        val movableIds = ids.filterNot { it == WorkspaceLayout.HOME_WORKSPACE_ID }
        val sequences = layout.workspaces.map(WorkspaceRecord::creationOrder)
        val columns = layout.authorColumns
        val cellsValid = layout.workspaces.all { ws ->
            val cells = ws.cells
            // Grid rules (on-grid, no rectangle overlap) apply only to cells
            // without a free `pos` — a free-positioned tile is exempt from both
            // and may overlap anything, by design (see AppCell.pos).
            val gridCells = cells.filter { it.pos == null }
            cells.map { it.packageName }.distinct().size == cells.size &&
                cells.all { it.cell >= 0 && it.spanX >= 1 && it.spanY >= 1 } &&
                cells.all { it.pos == null || (it.pos.x in 0f..1f && it.pos.y in 0f..1f) } &&
                gridCells.all { onGrid(it, columns) } &&
                !hasOverlap(gridCells, columns)
        }
        // Objects carry no grid/overlap rule at all (they're never anything but
        // free-positioned or flow) — only the same in-bounds fraction check.
        val objectPositionsValid = layout.workspaces.all { ws ->
            ws.objectPositions.values.all { it.x in 0f..1f && it.y in 0f..1f }
        }
        return layout.version == WorkspaceLayout.CURRENT_VERSION &&
            movableIds.size in 1..MAX_WORKSPACES &&
            ids.distinct().size == ids.size &&
            sequences.distinct().size == sequences.size &&
            layout.visualOrder.size == movableIds.size &&
            layout.visualOrder.toSet() == movableIds.toSet() &&
            layout.defaultWorkspaceId in movableIds &&
            cellsValid &&
            objectPositionsValid
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
