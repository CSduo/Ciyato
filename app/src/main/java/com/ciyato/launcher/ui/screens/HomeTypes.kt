package com.ciyato.launcher.ui.screens

import com.ciyato.launcher.data.AppCategory

/**
 * Types and constants shared across Home's files.
 *
 * These lived as private top-level declarations inside the 213 KB HomeScreen.kt.
 * Splitting that file (F-040) made them cross-file, and the compiler said so
 * immediately — which is the useful part: it named exactly which declarations
 * were genuinely shared rather than local to one region. They are gathered here
 * as `internal` instead of being scattered through whichever file happened to
 * carry them out, so the sharing is deliberate and visible.
 */

/** How long a drag must hover over a page edge before the pager flips. */
internal const val WORKSPACE_EDGE_HOVER_DELAY_MS = 420L

internal data class LayoutEditSnapshot(
    val categoryOrder: String,
    val tileSizes: String,
    val workspaceLayout: String,
    val customCategories: String,
    val customCategoryIcons: String,
    val customCategoryPresentations: String,
    val appCategoryOverrides: String,
    val hiddenHomeCategories: String,
)

/** The long-press menu currently open for one canvas object (see
 *  [com.ciyato.launcher.ui.components.HomeCanvasItem]/CanvasObjectMenu) —
 *  [onRemove] is supplied by the call site that knows which hide/Undo path
 *  this particular object uses (a legacy global toggle for the five
 *  pre-existing sections, [WorkspaceStore] hiddenObjects for everything
 *  else), so the menu/dialog UI itself stays completely generic. */
internal data class ObjectMenuTarget(
    val objectId: String,
    val label: String,
    val canReset: Boolean,
    val onReset: () -> Unit,
    val onRemove: () -> Unit,
)

internal data class WorkspaceStarterTemplate(
    val title: String,
    val description: String,
    val categoryKeys: List<String>,
)

internal val WORKSPACE_STARTER_TEMPLATES = listOf(
    WorkspaceStarterTemplate(
        title = "Focus",
        description = "Add Work and Productivity categories without pinning any apps.",
        categoryKeys = listOf(AppCategory.WORK.name, AppCategory.PRODUCTIVITY.name),
    ),
    WorkspaceStarterTemplate(
        title = "Personal",
        description = "Add Daily and Social categories without pinning any apps.",
        categoryKeys = listOf(AppCategory.DAILY.name, AppCategory.SOCIAL.name),
    ),
)
