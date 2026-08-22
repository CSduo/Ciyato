package com.ciyato.launcher

import com.ciyato.launcher.data.WorkspaceLayout
import com.ciyato.launcher.data.WorkspacePaging
import com.ciyato.launcher.data.WorkspaceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pager page to workspace, and back.
 *
 * Home is pinned at page 1 and is absent from visualOrder, so page and visual
 * index differ by one on the right of Home and not on the left. That arithmetic
 * was spread across three private functions in two files — two in the ViewModel
 * and their inverse in HomeScreen.kt — with nothing asserting the directions
 * agreed. If they ever disagreed, an edit would land on the wrong workspace,
 * which is the sort of bug that looks like data loss to the person using it.
 *
 * These tests exist mainly to pin the round trip. Everything else here is the
 * boundary conditions that arithmetic like this gets wrong.
 */
class WorkspacePagingTest {

    /** 3 movable workspaces plus the synthesized Home record. */
    private fun layout(): WorkspaceLayout =
        WorkspaceStore.migrateLegacy(4, "", "", "{}", "{}", "com.ciyato.launcher")

    // -- the round trip ------------------------------------------------------

    @Test
    fun `visual index survives a trip through page and back`() {
        val l = layout()
        for (visual in l.visualOrder.indices) {
            val page = WorkspacePaging.pageForVisualIndex(visual)
            assertEquals(
                "visual $visual -> page $page -> visual",
                visual,
                WorkspacePaging.visualIndexForPage(page),
            )
        }
    }

    @Test
    fun `page survives a trip through visual index and back`() {
        val l = layout()
        val pages = listOf(0) + (2..l.visualOrder.size).toList()
        for (page in pages) {
            val visual = requireNotNull(WorkspacePaging.visualIndexForPage(page))
            assertEquals(page, WorkspacePaging.pageForVisualIndex(visual))
        }
    }

    @Test
    fun `both directions resolve to the same workspace id`() {
        val l = layout()
        for (visual in l.visualOrder.indices) {
            val page = WorkspacePaging.pageForVisualIndex(visual)
            assertEquals(l.visualOrder[visual], WorkspacePaging.idForPage(l, page))
        }
    }

    // -- Home's special position ---------------------------------------------

    @Test
    fun `page one is Home`() {
        assertEquals(WorkspaceLayout.HOME_WORKSPACE_ID, WorkspacePaging.idForPage(layout(), 1))
        assertEquals(1, WorkspacePaging.HOME_PAGE)
    }

    @Test
    fun `Home has no visual index because it has no visual position`() {
        assertNull(WorkspacePaging.visualIndexForPage(1))
    }

    @Test
    fun `the first movable workspace sits left of Home`() {
        val l = layout()
        assertEquals(l.visualOrder[0], WorkspacePaging.idForPage(l, 0))
        assertEquals(0, WorkspacePaging.visualIndexForPage(0))
        assertEquals(0, WorkspacePaging.pageForVisualIndex(0))
    }

    @Test
    fun `pages after Home skip its slot`() {
        val l = layout()
        // page 2 is the SECOND movable workspace, not the third: Home occupies
        // page 1 without occupying a visual slot.
        assertEquals(l.visualOrder[1], WorkspacePaging.idForPage(l, 2))
        assertEquals(l.visualOrder[2], WorkspacePaging.idForPage(l, 3))
    }

    // -- boundaries ----------------------------------------------------------

    @Test
    fun `a page past the end has no workspace rather than the last one`() {
        val l = layout()
        assertNull(WorkspacePaging.idForPage(l, l.visualOrder.size + 5))
    }

    @Test
    fun `a negative page has no workspace and no visual index`() {
        assertNull(WorkspacePaging.idForPage(layout(), -1))
        assertNull(WorkspacePaging.visualIndexForPage(-1))
    }

    @Test
    fun `an empty layout still answers Home and nothing else`() {
        val empty = WorkspaceStore.migrateLegacy(1, "", "", "{}", "{}", "com.ciyato.launcher")
        assertEquals(WorkspaceLayout.HOME_WORKSPACE_ID, WorkspacePaging.idForPage(empty, 1))
        assertNull(WorkspacePaging.idForPage(empty, 5))
    }

    // -- grid size parsing ---------------------------------------------------

    @Test
    fun `grid size parses columns and rows`() {
        assertEquals(6 to 5, WorkspacePaging.parseGridSize("6x5"))
        assertEquals(4 to 4, WorkspacePaging.parseGridSize("4x4"))
        assertEquals(5 to 6, WorkspacePaging.parseGridSize(" 5 x 6 "))
    }

    @Test
    fun `out of range axes are clamped, not rejected`() {
        assertEquals(WorkspacePaging.MAX_AXIS to WorkspacePaging.MIN_AXIS,
            WorkspacePaging.parseGridSize("99x1"))
    }

    @Test
    fun `a corrupt preference costs a layout, not a launch`() {
        val default = WorkspacePaging.DEFAULT_COLS to WorkspacePaging.DEFAULT_ROWS
        assertEquals(default, WorkspacePaging.parseGridSize(""))
        assertEquals(default, WorkspacePaging.parseGridSize("nonsense"))
        assertEquals(default, WorkspacePaging.parseGridSize("x"))
        // A half-valid value keeps the half that parsed.
        assertEquals(4 to WorkspacePaging.DEFAULT_ROWS, WorkspacePaging.parseGridSize("4xNaN"))
    }
}
