package com.ciyato.launcher

import com.ciyato.launcher.data.AppCell
import com.ciyato.launcher.data.WorkspaceStore
import com.ciyato.launcher.data.coveredCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceStoreTest {

    @Test
    fun `legacy pager data migrates without losing either workspace`() {
        val layout = WorkspaceStore.migrateLegacy(
            count = 3,
            page0Apps = "com.example.left",
            page2Apps = "com.example.right",
            workspaceApps = "{}",
            workspaceCategories = "{\"0\":\"WORK\",\"2\":\"SOCIAL\"}",
            ciyatoPackage = "com.ciyato.launcher",
        )

        assertEquals(2, layout.workspaces.size)
        assertEquals("workspace-1", layout.workspaceAt(0)?.id)
        assertEquals(listOf("com.ciyato.launcher", "com.example.left"), layout.workspaceAt(0)?.appPackages)
        assertEquals(listOf("com.example.right"), layout.workspaceAt(1)?.appPackages)
        assertEquals(listOf("SOCIAL"), layout.workspaceAt(1)?.categoryKeys)
    }

    @Test
    fun `inserting before a workspace preserves creation identity`() {
        val original = WorkspaceStore.migrateLegacy(3, "", "", "{}", "{}", "com.ciyato.launcher")
        val inserted = requireNotNull(WorkspaceStore.insert(original, 0))

        assertEquals(listOf("workspace-3", "workspace-1", "workspace-2"), inserted.visualOrder)
        assertEquals(1, inserted.workspaces.first { it.id == "workspace-1" }.creationOrder)
        assertEquals(3, inserted.workspaces.first { it.id == "workspace-3" }.creationOrder)
    }

    @Test
    fun `removing a workspace can transfer its shortcuts without renumbering`() {
        val initial = WorkspaceStore.migrateLegacy(3, "com.example.left", "com.example.right", "{}", "{}", "com.ciyato.launcher")
        val result = requireNotNull(WorkspaceStore.remove(initial, "workspace-1", "workspace-2"))

        assertEquals(listOf("workspace-2"), result.visualOrder)
        assertEquals(
            listOf("com.example.right", "com.ciyato.launcher", "com.example.left"),
            result.workspaceAt(0)?.appPackages,
        )
        assertNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(result)))
    }

    @Test
    fun `duplicate inserts a new permanent identity directly after its source`() {
        val initial = WorkspaceStore.migrateLegacy(3, "com.example.left", "com.example.right", "{}", "{}", "com.ciyato.launcher")
        val result = requireNotNull(WorkspaceStore.duplicate(initial, "workspace-1"))

        assertEquals(listOf("workspace-1", "workspace-3", "workspace-2"), result.visualOrder)
        assertEquals(listOf("com.ciyato.launcher", "com.example.left"), result.workspaceAt(1)?.appPackages)
        assertEquals(3, result.workspaceAt(1)?.creationOrder)
    }

    @Test
    fun `default workspace is persisted independently from visual order`() {
        val initial = WorkspaceStore.migrateLegacy(3, "", "", "{}", "{}", "com.ciyato.launcher")
        val result = requireNotNull(WorkspaceStore.setDefault(initial, "workspace-2"))

        assertEquals("workspace-2", result.defaultWorkspaceId)
        assertNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(result)))
    }

    @Test
    fun `moving a shortcut reorders only the selected workspace grid`() {
        val initial = WorkspaceStore.migrateLegacy(
            count = 3,
            page0Apps = "com.example.one,com.example.two,com.example.three",
            page2Apps = "com.example.other",
            workspaceApps = "{}",
            workspaceCategories = "{}",
            ciyatoPackage = "com.ciyato.launcher",
        )

        val result = requireNotNull(
            WorkspaceStore.moveAppWithinWorkspace(
                layout = initial,
                workspaceId = "workspace-1",
                packageName = "com.example.one",
                destinationIndex = 2,
            ),
        )

        assertEquals(
            listOf("com.ciyato.launcher", "com.example.two", "com.example.one", "com.example.three"),
            result.workspaceAt(0)?.appPackages,
        )
        assertEquals(listOf("com.example.other"), result.workspaceAt(1)?.appPackages)
    }

    @Test
    fun `moving a shortcut toward the end preserves every unique shortcut`() {
        val initial = WorkspaceStore.migrateLegacy(
            count = 3,
            page0Apps = "com.example.one,com.example.two,com.example.three",
            page2Apps = "com.example.other",
            workspaceApps = "{}",
            workspaceCategories = "{}",
            ciyatoPackage = "com.ciyato.launcher",
        )

        val result = requireNotNull(
            WorkspaceStore.moveAppWithinWorkspace(
                layout = initial,
                workspaceId = "workspace-1",
                packageName = "com.ciyato.launcher",
                destinationIndex = 2,
            ),
        )

        assertEquals(
            listOf("com.example.one", "com.example.two", "com.ciyato.launcher", "com.example.three"),
            result.workspaceAt(0)?.appPackages,
        )
        assertEquals(4, result.workspaceAt(0)?.appPackages?.distinct()?.size)
    }

    @Test
    fun `version 1 layout upgrades to positioned cells without losing order`() {
        val v1 = """{"version":1,"defaultWorkspaceId":"workspace-1","visualOrder":["workspace-1"],""" +
            """"workspaces":[{"id":"workspace-1","creationOrder":1,"name":"W1",""" +
            """"appPackages":["com.a","com.b","com.c"],"categoryKeys":[],"starterDismissed":false}]}"""
        val layout = requireNotNull(WorkspaceStore.parse(v1))
        assertEquals(listOf("com.a", "com.b", "com.c"), layout.workspaceAt(0)?.appPackages)
        // Re-serialized as v2 and re-parsed, the order is identical (lossless upgrade).
        val reparsed = requireNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(layout)))
        assertEquals(listOf("com.a", "com.b", "com.c"), reparsed.workspaceAt(0)?.appPackages)
    }

    @Test
    fun `placing an app at a specific cell leaves a gap`() {
        val initial = WorkspaceStore.migrateLegacy(3, "com.a,com.b", "", "{}", "{}", "com.ciyato.launcher")
        // workspace-1 = [ciyato@0, com.a@1, com.b@2]; move com.b to cell 5.
        val result = requireNotNull(WorkspaceStore.placeApp(initial, "workspace-1", "com.b", 5))
        val cells = result.workspaceAt(0)!!.cells.associate { it.packageName to it.cell }
        assertEquals(5, cells["com.b"])
        assertEquals(0, cells["com.ciyato.launcher"])
        assertEquals(1, cells["com.a"])
    }

    @Test
    fun `reordering a workspace never changes its default identity`() {
        val initial = WorkspaceStore.migrateLegacy(3, "", "", "{}", "{}", "com.ciyato.launcher")
        val defaulted = requireNotNull(WorkspaceStore.setDefault(initial, "workspace-2"))
        val result = requireNotNull(WorkspaceStore.reorder(defaulted, sourceIndex = 1, destinationIndex = 0))

        assertEquals(listOf("workspace-2", "workspace-1"), result.visualOrder)
        assertEquals("workspace-2", result.defaultWorkspaceId)
        assertNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(result)))
    }

    // ── Span (multi-cell tile) coverage ─────────────────────────────────────

    @Test
    fun `a spanning cell covers exactly its rectangle of grid indices`() {
        val tile = AppCell("com.wide", cell = 1, spanX = 2, spanY = 2)
        // On a 4-wide grid, cell 1 is row 0 col 1; a 2x2 tile from there covers
        // (0,1),(0,2),(1,1),(1,2) => linear indices 1,2,5,6.
        assertEquals(setOf(1, 2, 5, 6), tile.coveredCells(columns = 4))
    }

    @Test
    fun `placing a spanning tile that would clobber more than one occupant is rejected`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.a,com.b,com.mover", "", "{}", "{}", "")
        // workspace-1 starts as a@0, b@1, mover@2 on the default 4-wide grid.
        val moved = requireNotNull(WorkspaceStore.placeApp(base, "workspace-1", "com.mover", 8))
        val resized = requireNotNull(WorkspaceStore.resizeApp(moved, "workspace-1", "com.mover", 2, 2))
        // mover is now a 2x2 tile at cell 8, covering {8,9,12,13} — clear of a@0 and b@1.

        // Dragging mover onto cell 0 would cover {0,1,4,5}, clobbering both a@0 and b@1 at once.
        val result = WorkspaceStore.placeApp(resized, "workspace-1", "com.mover", 0)
        assertNull(result)
    }

    @Test
    fun `placeApp rejects a placement that would run the tile off the grid's right edge`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.wide", "", "{}", "{}", "")
        val resized = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.wide", 2, 1))
        // com.wide is 2 wide at cell 0; column 3 is the last column on the default
        // 4-wide grid, so starting a 2-wide tile there would spill past the edge.
        val result = WorkspaceStore.placeApp(resized, "workspace-1", "com.wide", 3)
        assertNull(result)
    }

    @Test
    fun `firstFreeCell skips a slot a wider tile would overlap or run off the edge`() {
        val blocked = listOf(AppCell("com.x", cell = 1))
        // On a 3-wide grid, a 2-wide search must skip index 0 (would cover 0,1 —
        // overlaps com.x at 1), skip index 1 (is com.x itself), skip index 2
        // (2+2 > 3, runs off the right edge), and land on index 3 (row 1 — clear).
        assertEquals(3, WorkspaceStore.firstFreeCell(blocked, columns = 3, spanX = 2, spanY = 1))
    }

    @Test
    fun `addAppsAtFreeCells skips past a larger tile's full footprint, not just its origin`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.big", "", "{}", "{}", "")
        val resized = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.big", 2, 2))
        // com.big is a 2x2 tile at cell 0, covering {0,1,4,5} on the default 4-wide grid.
        val result = requireNotNull(WorkspaceStore.addAppsAtFreeCells(resized, "workspace-1", listOf("com.new")))
        val cells = result.workspaceAt(0)!!.cells.associateBy { it.packageName }
        assertEquals(2, cells["com.new"]?.cell)
    }

    @Test
    fun `resizeApp grows a tile into empty space`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.solo", "", "{}", "{}", "")
        val result = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.solo", 2, 2))
        val cell = result.workspaceAt(0)!!.cells.single { it.packageName == "com.solo" }
        assertEquals(0, cell.cell)
        assertEquals(2, cell.spanX)
        assertEquals(2, cell.spanY)
    }

    @Test
    fun `resizeApp rejects a resize that would overlap another tile`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.a,com.b", "", "{}", "{}", "")
        // a@0, b@1 — growing a to 2 wide would reach into b's cell.
        val result = WorkspaceStore.resizeApp(base, "workspace-1", "com.a", 2, 1)
        assertNull(result)
    }

    @Test
    fun `resizeApp rejects a resize that would run off the grid's right edge`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.a,com.b,com.c,com.last", "", "{}", "{}", "")
        // com.last sits at cell 3 — the last column on the default 4-wide grid.
        val result = WorkspaceStore.resizeApp(base, "workspace-1", "com.last", 2, 1)
        assertNull(result)
    }

    @Test
    fun `serialize then parse round-trips a tile's span`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.wide", "", "{}", "{}", "")
        val resized = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.wide", 3, 2))
        val reparsed = requireNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(resized)))
        val cell = reparsed.workspaceAt(0)!!.cells.single { it.packageName == "com.wide" }
        assertEquals(3, cell.spanX)
        assertEquals(2, cell.spanY)
    }

    @Test
    fun `a v2 layout saved without span fields still parses with every span defaulting to 1`() {
        val v2 = """{"version":2,"defaultWorkspaceId":"workspace-1","visualOrder":["workspace-1"],"authorColumns":4,""" +
            """"workspaces":[{"id":"workspace-1","creationOrder":1,"name":"W1",""" +
            """"cells":[{"pkg":"com.a","cell":0},{"pkg":"com.b","cell":1}],""" +
            """"categoryKeys":[],"starterDismissed":false}]}"""
        val layout = requireNotNull(WorkspaceStore.parse(v2))
        val cells = layout.workspaceAt(0)!!.cells.associateBy { it.packageName }
        assertEquals(1, cells["com.a"]?.spanX)
        assertEquals(1, cells["com.a"]?.spanY)
        assertEquals(1, cells["com.b"]?.spanX)
        assertEquals(1, cells["com.b"]?.spanY)
    }

    // ── Span-preserving flattening paths (reflow, move, remove+merge) ────────

    @Test
    fun `reflow preserves a spanning tile when the new grid still fits it`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.big", "", "{}", "{}", "")
        val resized = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.big", 2, 2))
        val withSolo = requireNotNull(WorkspaceStore.addAppsAtFreeCells(resized, "workspace-1", listOf("com.solo")))
        // com.big is a 2x2 tile at cell 0 (covers {0,1,4,5}); com.solo sits at cell 2.
        // Widening the grid to 6 columns still comfortably fits the 2x2 rectangle.
        val reflowed = WorkspaceStore.reflow(withSolo.workspaceAt(0)!!, columns = 6)
        val big = reflowed.cells.single { it.packageName == "com.big" }
        assertEquals(2, big.spanX)
        assertEquals(2, big.spanY)
        assertEquals(setOf("com.big", "com.solo"), reflowed.cells.map { it.packageName }.toSet())
    }

    @Test
    fun `reflow clamps a span that no longer fits a narrower grid instead of dropping the app`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.wide", "", "{}", "{}", "")
        val resized = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.wide", 3, 1))
        val withOther = requireNotNull(WorkspaceStore.addAppsAtFreeCells(resized, "workspace-1", listOf("com.other")))
        // com.wide is 3 columns wide on the default 4-wide grid. Shrinking to a
        // 2-column grid can no longer fit that width — spanX must clamp down to
        // what fits, and both apps must still be present afterward.
        val reflowed = WorkspaceStore.reflow(withOther.workspaceAt(0)!!, columns = 2)
        val wide = reflowed.cells.single { it.packageName == "com.wide" }
        assertEquals(2, wide.spanX)
        assertEquals(setOf("com.wide", "com.other"), reflowed.cells.map { it.packageName }.toSet())
    }

    @Test
    fun `moveAppWithinWorkspace preserves the moved tile's span and any tile it displaces`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.wide1", "", "{}", "{}", "")
        val wide1 = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.wide1", 2, 1))
        val withApps = requireNotNull(WorkspaceStore.addAppsAtFreeCells(wide1, "workspace-1", listOf("com.b", "com.wide2")))
        // com.wide1 is 2x1 at cell 0 (covers {0,1}); com.b lands at cell 2; com.wide2
        // lands at cell 3 as a plain 1x1. Move it to cell 4 (row 1) so it has room
        // to grow to 2x1 without running off the grid's right edge.
        val relocated = requireNotNull(WorkspaceStore.placeApp(withApps, "workspace-1", "com.wide2", 4))
        val setup = requireNotNull(WorkspaceStore.resizeApp(relocated, "workspace-1", "com.wide2", 2, 1))
        // Reading order is now [com.wide1, com.b, com.wide2]; moving com.wide2 to
        // the front displaces both com.wide1 and com.b.
        val result = requireNotNull(
            WorkspaceStore.moveAppWithinWorkspace(setup, "workspace-1", "com.wide2", destinationIndex = 0),
        )
        val cells = result.workspaceAt(0)!!.cells.associateBy { it.packageName }
        assertEquals(2, cells["com.wide2"]?.spanX) // the moved tile kept its span
        assertEquals(1, cells["com.wide2"]?.spanY)
        assertEquals(2, cells["com.wide1"]?.spanX) // a tile it displaced kept its span too
        assertEquals(setOf("com.wide1", "com.b", "com.wide2"), cells.keys)
    }

    @Test
    fun `remove with moveContentsTo carries a spanning tile into the destination without overlap`() {
        val base = WorkspaceStore.migrateLegacy(3, "com.wide", "com.solo", "{}", "{}", "")
        val resized = requireNotNull(WorkspaceStore.resizeApp(base, "workspace-1", "com.wide", 2, 2))
        // workspace-1 has a 2x2 com.wide at cell 0; workspace-2 has a plain com.solo at cell 0.
        val result = requireNotNull(WorkspaceStore.remove(resized, "workspace-1", moveContentsTo = "workspace-2"))
        val destination = result.workspaceAt(0)!!
        val wide = destination.cells.single { it.packageName == "com.wide" }
        val solo = destination.cells.single { it.packageName == "com.solo" }
        assertEquals(2, wide.spanX)
        assertEquals(2, wide.spanY)
        assertEquals(0, solo.cell) // destination's own tile keeps its original cell
        // The merged workspace must still be internally valid — no overlap, on-grid.
        assertNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(result)))
    }
}
