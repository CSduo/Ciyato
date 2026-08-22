package com.ciyato.launcher.data

/**
 * The mapping between pager pages and workspaces.
 *
 * Home does not sit in [WorkspaceLayout.visualOrder]. It is pinned at pager page
 * 1, with the first movable workspace to its left at page 0 and the rest to its
 * right from page 2 onward. So page index and visual index differ by one on one
 * side of Home and not the other, which is exactly the kind of arithmetic that
 * is right until someone edits it.
 *
 * This lived as two private functions in LauncherViewModel plus a third,
 * `workspacePagerPage`, in HomeScreen.kt — and that third one is the *inverse*
 * of the other two, in a different file, with nothing anywhere asserting the two
 * directions agree. A launcher whose page mapping disagrees with itself sends
 * edits to the wrong workspace. Gathered here so the round trip can be tested
 * (F-041: focused tests without constructing the entire launcher).
 */
object WorkspacePaging {

    /** Pager page that shows Home. */
    const val HOME_PAGE = 1

    /**
     * Which workspace a pager page shows, or null when the page has none
     * (an index past the end, or a negative page).
     */
    fun idForPage(layout: WorkspaceLayout, pageIndex: Int): String? = when {
        pageIndex == HOME_PAGE -> WorkspaceLayout.HOME_WORKSPACE_ID
        pageIndex == 0 -> layout.visualOrder.getOrNull(0)
        pageIndex >= 2 -> layout.visualOrder.getOrNull(pageIndex - 1)
        else -> null
    }

    /**
     * The [WorkspaceLayout.visualOrder] index a pager page corresponds to.
     *
     * Null for Home, which has no visual position at all — callers use this when
     * inserting a workspace, and "insert next to Home" has no single answer.
     */
    fun visualIndexForPage(pageIndex: Int): Int? = when {
        pageIndex == 0 -> 0
        pageIndex >= 2 -> pageIndex - 1
        else -> null
    }

    /** The inverse of [visualIndexForPage]: which pager page shows a workspace. */
    fun pageForVisualIndex(visualIndex: Int): Int =
        if (visualIndex == 0) 0 else visualIndex + 1

    /**
     * "6x5" to columns and rows, clamped to what the grid can actually draw.
     *
     * Malformed input falls back to the shipping default rather than throwing:
     * this reads a persisted preference, and a corrupt value should cost a
     * layout, not a launch.
     */
    fun parseGridSize(raw: String): Pair<Int, Int> {
        val parts = raw.split("x")
        val cols = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(MIN_AXIS, MAX_AXIS) ?: DEFAULT_COLS
        val rows = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(MIN_AXIS, MAX_AXIS) ?: DEFAULT_ROWS
        return cols to rows
    }

    const val MIN_AXIS = 3
    const val MAX_AXIS = 8
    const val DEFAULT_COLS = 6
    const val DEFAULT_ROWS = 5
}
