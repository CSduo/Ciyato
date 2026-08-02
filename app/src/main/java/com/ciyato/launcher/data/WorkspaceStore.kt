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
 */
data class AppCell(val packageName: String, val cell: Int)

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
            add(AppCell(pkg, obj.optInt("cell", index).coerceAtLeast(0)))
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
                                put(JSONObject().apply { put("pkg", c.packageName); put("cell", c.cell) })
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
                remaining[destinationIndex] = destination
                    .withPackages((destination.appPackages + removed.appPackages).distinct())
                    .copy(categoryKeys = (destination.categoryKeys + removed.categoryKeys).distinct())
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
                cells = normalizeCells(workspace.cells),
                categoryKeys = workspace.categoryKeys.distinct(),
            ) else current
        })
    }

    // ── Positioned-cell operations (free placement, gaps, swaps) ──────────────

    /** Smallest non-occupied cell index, row-major — fills gaps before extending. */
    fun firstFreeCell(cells: List<AppCell>): Int {
        val occupied = cells.mapTo(HashSet()) { it.cell }
        var i = 0
        while (i in occupied) i++
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
                cells = cells + AppCell(pkg, firstFreeCell(cells))
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

    /** Places [packageName] at [targetCell] within one workspace, swapping any occupant. */
    fun placeApp(layout: WorkspaceLayout, workspaceId: String, packageName: String, targetCell: Int): WorkspaceLayout? {
        val workspace = layout.workspaces.firstOrNull { it.id == workspaceId } ?: return null
        val cell = targetCell.coerceAtLeast(0)
        val sourceCell = workspace.cells.firstOrNull { it.packageName == packageName }?.cell
        val occupant = workspace.cells.firstOrNull { it.cell == cell && it.packageName != packageName }
        var cells = workspace.cells.filterNot { it.packageName == packageName }
        if (occupant != null) {
            val occupantTarget = sourceCell ?: firstFreeCell(cells.filterNot { it.packageName == occupant.packageName })
            cells = cells.map { if (it.packageName == occupant.packageName) it.copy(cell = occupantTarget) else it }
        }
        cells = cells + AppCell(packageName, cell)
        return withWorkspace(layout, workspace.copy(cells = cells))
    }

    /** Moves [packageName] from one workspace to a cell in another (or the same). */
    fun moveApp(layout: WorkspaceLayout, fromWorkspaceId: String, toWorkspaceId: String, packageName: String, targetCell: Int): WorkspaceLayout? {
        if (fromWorkspaceId == toWorkspaceId) return placeApp(layout, toWorkspaceId, packageName, targetCell)
        val from = layout.workspaces.firstOrNull { it.id == fromWorkspaceId } ?: return null
        if (packageName !in from.appPackages) return null
        val without = withWorkspace(layout, from.copy(cells = from.cells.filterNot { it.packageName == packageName }))
            ?: return null
        return placeApp(without, toWorkspaceId, packageName, targetCell)
    }

    /**
     * Legacy sequential move (drop into an ordered slot). Kept for the accessible
     * reorder path; repacks row-major so no gap or duplicate can appear.
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
        return withWorkspace(layout, workspace.withPackages(apps))
    }

    /** Repack a record's cells row-major (drops gaps) — used on grid resize. */
    fun reflow(record: WorkspaceRecord): WorkspaceRecord = record.withPackages(record.appPackages)

    // ── Validation & helpers ──────────────────────────────────────────────────

    private fun normalizeCells(cells: List<AppCell>): List<AppCell> {
        val seenPkg = HashSet<String>()
        val seenCell = HashSet<Int>()
        val result = ArrayList<AppCell>()
        for (c in cells.sortedBy { it.cell }) {
            if (!seenPkg.add(c.packageName)) continue
            var cell = c.cell.coerceAtLeast(0)
            while (cell in seenCell) cell++
            seenCell.add(cell)
            result.add(AppCell(c.packageName, cell))
        }
        return result
    }

    private fun isValid(layout: WorkspaceLayout): Boolean {
        val ids = layout.workspaces.map(WorkspaceRecord::id)
        val sequences = layout.workspaces.map(WorkspaceRecord::creationOrder)
        val cellsValid = layout.workspaces.all { ws ->
            val cells = ws.cells
            cells.map { it.packageName }.distinct().size == cells.size &&
                cells.map { it.cell }.distinct().size == cells.size &&
                cells.all { it.cell >= 0 }
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
