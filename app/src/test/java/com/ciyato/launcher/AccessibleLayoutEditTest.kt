package com.ciyato.launcher

import com.ciyato.launcher.data.WorkspaceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The accessible editing path must produce the same layout as a drag.
 *
 * F-048 is about a launcher whose central feature — arranging your own home
 * screen — was reachable only by precise touch. Adding accessibility actions
 * fixes nothing if they route somewhere that behaves differently, so these pin
 * the property that matters: an action lands a tile on the same cell a finger
 * would have dropped it on, and changes nothing else.
 *
 * The trap this caught during implementation: the obvious-looking
 * `moveAppWithinWorkspace` takes an ordinal position in a reflowed reading
 * order, not a grid cell. Wiring "move left" to it would have silently repacked
 * the whole workspace and collapsed every deliberate gap.
 */
class AccessibleLayoutEditTest {

    /** workspace-1 = ciyato@0, com.a@1, com.b@2 on the default 4-wide grid. */
    private fun base() = WorkspaceStore.migrateLegacy(
        3, "com.a,com.b", "", "{}", "{}", "com.ciyato.launcher",
    )

    private fun cellsOf(layout: com.ciyato.launcher.data.WorkspaceLayout) =
        layout.workspaceAt(0)!!.cells.associate { it.packageName to it.cell }

    @Test
    fun `moving to an exact cell is what a drop does`() {
        val moved = requireNotNull(
            WorkspaceStore.moveApp(base(), "workspace-1", "workspace-1", "com.b", 9),
        )
        assertEquals(9, cellsOf(moved)["com.b"])
    }

    /**
     * The whole reason a cell-precise path was added. A gap the user made on
     * purpose must survive nudging some other tile.
     */
    @Test
    fun `a cell-precise move does not repack the rest of the workspace`() {
        // Put com.b far away, leaving cells 2..8 empty on purpose.
        val spaced = requireNotNull(WorkspaceStore.placeApp(base(), "workspace-1", "com.b", 9))
        // Now nudge com.a one cell right, as "Move right" does.
        val nudged = requireNotNull(
            WorkspaceStore.moveApp(spaced, "workspace-1", "workspace-1", "com.a", 2),
        )
        val cells = cellsOf(nudged)
        assertEquals(2, cells["com.a"])
        assertEquals(9, cells["com.b"])            // untouched, gap preserved
        assertEquals(0, cells["com.ciyato.launcher"])
    }

    /** Contrast: the ordinal path deliberately repacks — right for lists, wrong here. */
    @Test
    fun `the ordinal path compacts, which is why it is not used for directional moves`() {
        val spaced = requireNotNull(WorkspaceStore.placeApp(base(), "workspace-1", "com.b", 9))
        val reordered = requireNotNull(
            WorkspaceStore.moveAppWithinWorkspace(spaced, "workspace-1", "com.b", 0),
        )
        // Every tile is repacked into reading order; the deliberate gap is gone.
        assertEquals(setOf(0, 1, 2), cellsOf(reordered).values.toSet())
    }

    @Test
    fun `a move onto an occupied cell is rejected rather than clobbering`() {
        val spaced = requireNotNull(WorkspaceStore.placeApp(base(), "workspace-1", "com.b", 9))
        val resized = requireNotNull(WorkspaceStore.resizeApp(spaced, "workspace-1", "com.b", 2, 2))
        // com.b now covers {9,10,13,14}. Landing it on cell 0 would cover
        // {0,1,4,5} and clobber both ciyato@0 and com.a@1 at once.
        assertNull(WorkspaceStore.moveApp(resized, "workspace-1", "workspace-1", "com.b", 0))
    }

    @Test
    fun `a move that would run off the right edge is rejected`() {
        val wide = requireNotNull(WorkspaceStore.resizeApp(base(), "workspace-1", "com.b", 2, 1))
        // Column 3 is the last on a 4-wide grid; a 2-wide tile cannot start there.
        assertNull(WorkspaceStore.moveApp(wide, "workspace-1", "workspace-1", "com.b", 3))
        // One column left is fine.
        assertNotNull(WorkspaceStore.moveApp(wide, "workspace-1", "workspace-1", "com.b", 6))
    }

    @Test
    fun `moving a tile between pages keeps its size`() {
        val wide = requireNotNull(WorkspaceStore.resizeApp(base(), "workspace-1", "com.b", 2, 2))
        val moved = requireNotNull(
            WorkspaceStore.moveApp(wide, "workspace-1", "workspace-2", "com.b", 0),
        )
        val landed = moved.workspaces.first { it.id == "workspace-2" }.cells.first { it.packageName == "com.b" }
        assertEquals(2, landed.spanX)
        assertEquals(2, landed.spanY)
        // And it is gone from the page it left.
        assertNull(moved.workspaces.first { it.id == "workspace-1" }.cells.firstOrNull { it.packageName == "com.b" })
    }

    @Test
    fun `a move survives a serialize and reparse`() {
        val moved = requireNotNull(
            WorkspaceStore.moveApp(base(), "workspace-1", "workspace-1", "com.b", 7),
        )
        val reparsed = requireNotNull(WorkspaceStore.parse(WorkspaceStore.serialize(moved)))
        assertEquals(7, cellsOf(reparsed)["com.b"])
    }
}
