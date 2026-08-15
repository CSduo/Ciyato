package com.ciyato.launcher.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.WindowManager
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.CanvasPos
import com.ciyato.launcher.data.CustomCategoryPresentation
import com.ciyato.launcher.data.FocusSessionManager
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.data.SearchRankingEngine
import com.ciyato.launcher.data.WorkspaceRecord
import com.ciyato.launcher.ui.canvas.CanvasEngine
import com.ciyato.launcher.ui.canvas.CanvasItem
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.ui.launcher.*
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

// Automatic Home content is limited to the approved six. The remaining
// classifications are available in the App Library or through manual placement.
private val APPROVED_HOME_CATEGORIES = listOf(
    AppCategory.WORK,
    AppCategory.SOCIAL,
    AppCategory.FINANCE,
    AppCategory.CREATIVITY,
    AppCategory.UTILITIES,
    AppCategory.DAILY,
)

private val WORKSPACE_CATEGORY_CHOICES = listOf(
    AppCategory.WORK,
    AppCategory.SOCIAL,
    AppCategory.FINANCE,
    AppCategory.CREATIVITY,
    AppCategory.UTILITIES,
    AppCategory.DAILY,
    AppCategory.ENTERTAINMENT,
    AppCategory.PRODUCTIVITY,
    AppCategory.COMMUNICATION,
    AppCategory.GAMES,
    AppCategory.TRAVEL,
    AppCategory.SHOPPING,
    AppCategory.AI,
    AppCategory.VIDEO_EDITING,
    AppCategory.CONTACTS,
)

private fun workspacePagerPage(visualIndex: Int): Int = if (visualIndex == 0) 0 else visualIndex + 1

private const val WORKSPACE_EDGE_DROP_THRESHOLD_PX = 120f
private const val WORKSPACE_EDGE_HOVER_DELAY_MS = 420L

private fun workspaceEdgeDropDestination(
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

private fun nearestWorkspaceGridTarget(
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

private data class WorkspaceStarterTemplate(
    val title: String,
    val description: String,
    val categoryKeys: List<String>,
)

/** The long-press menu currently open for one canvas object (see
 *  [com.ciyato.launcher.ui.components.HomeCanvasItem]/CanvasObjectMenu) —
 *  [onRemove] is supplied by the call site that knows which hide/Undo path
 *  this particular object uses (a legacy global toggle for the five
 *  pre-existing sections, [WorkspaceStore] hiddenObjects for everything
 *  else), so the menu/dialog UI itself stays completely generic. */
private data class ObjectMenuTarget(
    val objectId: String,
    val label: String,
    val canReset: Boolean,
    val onReset: () -> Unit,
    val onRemove: () -> Unit,
)

private data class LayoutEditSnapshot(
    val categoryOrder: String,
    val tileSizes: String,
    val workspaceLayout: String,
    val customCategories: String,
    val customCategoryIcons: String,
    val customCategoryPresentations: String,
    val appCategoryOverrides: String,
    val hiddenHomeCategories: String,
)

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
private fun HomeCanvasSurface(
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

private val WORKSPACE_STARTER_TEMPLATES = listOf(
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenSystemWallpaper: () -> Unit = {},
    onOpenOrganizerSettings: () -> Unit = {},
    onCategoryTap: (AppCategory) -> Unit = {},
    onWeatherTap: () -> Unit = {},
    onAgendaTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val apps              by viewModel.apps.collectAsState()
    val isLoading         by viewModel.isLoading.collectAsState()
    val denseLayout       by viewModel.denseLayout.collectAsState()
    val homeLayoutMode    by viewModel.homeLayoutMode.collectAsState()
    val showSmartCategories by viewModel.smartCategories.collectAsState()
    val goldAccentEnabled by viewModel.goldAccent.collectAsState()
    val homeTipDismissed by viewModel.homeTipDismissed.collectAsState()
    val dockPackages by viewModel.dockPackages.collectAsState()
    val dockInitialized by viewModel.dockInitialized.collectAsState()
    val gridSizePref by viewModel.gridSize.collectAsState()
    val (gridCols, gridRows) = remember(gridSizePref) { viewModel.gridColsRows() }
    val workspaceCount by viewModel.workspaceCount.collectAsState()
    val workspaceLayoutV2 by viewModel.workspaceLayoutV2.collectAsState()
    // Home's canvas-object state: which ids are free-positioned (else they
    // keep flowing in visibleHomeSections order) and which have no dedicated
    // global setting of their own so rely on WorkspaceStore.hiddenObjects
    // (currently just "datetime" — see ObjectMenuTarget / CanvasObject).
    val homeObjectPositions = remember(workspaceLayoutV2) { viewModel.objectPositionsForPage(1) }
    val homeHiddenObjects = remember(workspaceLayoutV2) { viewModel.hiddenObjectsForPage(1) }
    val toastEvent        by viewModel.toastEvent.collectAsState()
    val weatherState      by viewModel.weatherState.collectAsState()
    val tempUnitPref      by viewModel.tempUnit.collectAsState()
    val timeAwareLayout   by viewModel.timeAwareLayout.collectAsState()
    val hapticEnabled     by viewModel.hapticFeedback.collectAsState()
    val reduceMotion      by viewModel.reduceMotion.collectAsState()
    val showRecentLaunched by viewModel.showRecentlyLaunched.collectAsState()
    val showHomeGreeting by viewModel.showHomeGreeting.collectAsState()
    val showHomeSearch by viewModel.showHomeSearch.collectAsState()
    val showHomeWeather by viewModel.showHomeWeather.collectAsState()
    val showHomeAgenda by viewModel.showHomeAgenda.collectAsState()
    val showHomeDock by viewModel.showHomeDock.collectAsState()
    val showAppDrawer by viewModel.showAppDrawer.collectAsState()
    val workspaceTransition by viewModel.workspaceTransition.collectAsState()
    val hiddenHomeCategories by viewModel.hiddenHomeCategories.collectAsState()
    val privacyMode       by viewModel.privacyMode.collectAsState()
    val screenshotBlocked by viewModel.screenshotBlocked.collectAsState()
    val focusSession      by FocusSessionManager.activeSession.collectAsState()
    val activeAccent = if (goldAccentEnabled) CiyatoGold else CiyatoBlue

    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val homeContext = LocalContext.current

    // Real calendar events for the Today card, refreshed on each resume-ish recomposition.
    var agendaEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            homeContext, android.Manifest.permission.READ_CALENDAR,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            agendaEvents = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { readCalendarEvents(homeContext) }.getOrDefault(emptyList())
            }
        }
    }

    var interactionState by remember { mutableStateOf<LauncherInteractionState>(LauncherInteractionState.Browsing) }
    val isEditMode = interactionState.isEditing
    val showLauncherControls = (interactionState as? LauncherInteractionState.LayoutEditing)
        ?.isControlSheetVisible == true
    val contextMenuApp = (interactionState as? LauncherInteractionState.ItemSelected)
        ?.packageName
        ?.let { packageName -> apps.firstOrNull { it.packageName == packageName } }
    val selectedCustomCategory = (interactionState as? LauncherInteractionState.CategoryEditor)?.categoryKey
    val pendingCategoryRemoval = (interactionState as? LauncherInteractionState.Confirmation)
        ?.action as? LauncherConfirmation.RemoveCategory
    val categoryPendingDelete = pendingCategoryRemoval?.categoryKey
    val pendingAppRemovalFromDock = (interactionState as? LauncherInteractionState.Confirmation)
        ?.action as? LauncherConfirmation.RemoveAppFromDock
    var draggingCategory by remember { mutableStateOf<String?>(null) }
    var categoryDragOffset by remember { mutableStateOf(Offset.Zero) }
    var workspaceDraggingCategory by remember { mutableStateOf<String?>(null) }
    var workspaceCategoryDragOffset by remember { mutableStateOf(Offset.Zero) }
    var workspaceDraggingApp by remember { mutableStateOf<String?>(null) }
    var workspaceAppDragOffset by remember { mutableStateOf(Offset.Zero) }
    var workspaceDropTargetPage by remember { mutableStateOf<Int?>(null) }
    var workspaceDropReady by remember { mutableStateOf(false) }
    var workspaceAppGridDropTarget by remember { mutableStateOf<String?>(null) }
    var workspaceAppBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var upwardDrag by remember { mutableFloatStateOf(0f) }
    var launcherSurfaceHeight by remember { mutableFloatStateOf(0f) }
    var launcherSurfaceWidth by remember { mutableFloatStateOf(0f) }

    // ── Universal floating drag (grid ↔ workspace ↔ dock) ─────────────────────
    val dragController = remember { LauncherDragController() }
    var edgeFlipDir by remember { mutableIntStateOf(0) }   // -1 left, +1 right, 0 none

    // Root-coordinate bounds of the currently visible page's WorkspaceGrid —
    // the same Layout that reports cell bounds via onCellBounds, so this is
    // the real rendered canvas size/position, never a guessed screen size.
    // Free-canvas drop math (see commitGridDrop) reads this directly.
    var activeGridBounds by remember { mutableStateOf<Rect?>(null) }

    // Root-coordinate SAFE canvas bounds for the visible page's non-app
    // objects (greeting, weather, category cards…) — the same raw page area
    // WorkspaceGrid uses for apps, minus the status bar and the dock/gesture
    // strip, so a dropped object can never land somewhere it couldn't be
    // grabbed again (see requirement #6). Recomputed by each page's own
    // onGloballyPositioned and cleared on page flip, same lifecycle as
    // [activeGridBounds].
    var activeObjectCanvasBounds by remember { mutableStateOf<Rect?>(null) }

    // The long-press menu / remove-confirmation currently open for a canvas
    // object — shared by every object on every page (see CanvasObject below
    // and RemoveObjectDialog.kt).
    var objectMenuTarget by remember { mutableStateOf<ObjectMenuTarget?>(null) }
    var pendingObjectRemoval by remember { mutableStateOf<ObjectMenuTarget?>(null) }

    // Package that just landed from a drag, for WorkspaceGrid's brief settle
    // animation — cleared automatically a moment later.
    var settlingPackage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settlingPackage) {
        if (settlingPackage != null) {
            delay(220)
            settlingPackage = null
        }
    }

    // Custom categories & order
    val customCats by viewModel.customCategories.collectAsState()
    val customCategoryIcons by viewModel.customCategoryIcons.collectAsState()
    val customCategoryPresentations by viewModel.customCategoryPresentations.collectAsState()
    val appCategoryOverrides by viewModel.appCategoryOverrides.collectAsState()
    val customCatsList = remember(customCats) {
        customCats.split(",").map(String::trim).filter(String::isNotEmpty)
    }

    val categoryOrderVal by viewModel.categoryOrder.collectAsState()
    val homeSectionOrderVal by viewModel.homeSectionOrder.collectAsState()
    val categoryTilesSizesVal by viewModel.categoryTilesSizes.collectAsState()
    val expandedAppsVal by viewModel.expandedApps.collectAsState()
    val expandedAppsSet = remember(expandedAppsVal) {
        viewModel.parsePackageCsv(expandedAppsVal).toSet()
    }

    // Dialog state for creating a custom category
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryIcon by remember { mutableStateOf("folder") }
    var newCategoryPresentation by remember { mutableStateOf(CustomCategoryPresentation.GROUP) }
    var newCategoryAppSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newCategoryAppQuery by remember { mutableStateOf("") }

    // Dialog state for picking apps for custom pages
    var showPageAppPicker by remember { mutableStateOf(false) }
    var pickerPageIndex by remember { mutableStateOf(0) }
    var pageAppPickerQuery by remember { mutableStateOf("") }
    var pageAppPickerSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showWorkspaceCategoryPicker by remember { mutableStateOf(false) }
    var workspaceCategoryPickerIndex by remember { mutableStateOf(0) }
    var pendingWorkspaceDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingWorkspaceMoveDestination by remember { mutableStateOf<Int?>(null) }
    var showWorkspaceOverview by remember { mutableStateOf(false) }
    var showDockManager by remember { mutableStateOf(false) }
    var workspaceRenamePage by remember { mutableStateOf<Int?>(null) }
    var workspaceNameDraft by remember { mutableStateOf("") }
    var workspaceTemplatePage by remember { mutableStateOf<Int?>(null) }
    var pendingWorkspaceTemplate by remember { mutableStateOf<WorkspaceStarterTemplate?>(null) }
    var pendingWorkspaceTemplatePage by remember { mutableStateOf<Int?>(null) }
    var workspaceForNewCategory by remember { mutableStateOf<Int?>(null) }
    var showHomeCategoryPicker by remember { mutableStateOf(false) }
    var categoryEditorNameDraft by rememberSaveable { mutableStateOf("") }
    var categoryEditorIconDraft by rememberSaveable { mutableStateOf("folder") }
    var categoryEditorPresentationDraft by rememberSaveable {
        mutableStateOf(CustomCategoryPresentation.GROUP.name)
    }
    var browsingCustomCategory by remember { mutableStateOf<String?>(null) }
    var showCategoryAppPicker by remember { mutableStateOf(false) }
    var categoryAppPickerQuery by rememberSaveable { mutableStateOf("") }
    var categoryAppPickerSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var categoryMoveTarget by remember { mutableStateOf<String?>(null) }
    var categoryMergeSource by remember { mutableStateOf<String?>(null) }

    // Leaving edit mode KEEPS every change: each edit persists itself atomically
    // and shows its own Undo snackbar, so there is no session-level snapshot to
    // revert. (The old revert-on-exit snapshot was removed — it was the cause of
    // added apps vanishing.)
    fun enterLayoutEditing(showControls: Boolean) {
        interactionState = LauncherInteractionState.LayoutEditing(showControls)
    }

    fun openCategoryEditor(categoryKey: String) {
        categoryEditorNameDraft = categoryKey
        categoryEditorIconDraft = viewModel.getCustomCategoryIcon(categoryKey)
        categoryEditorPresentationDraft = viewModel.getCustomCategoryPresentation(categoryKey).name
        interactionState = LauncherInteractionState.CategoryEditor(
            categoryKey = categoryKey,
            returnState = interactionState,
        )
    }

    fun requestCategoryRemoval(categoryKey: String, isCustom: Boolean, workspaceIndex: Int? = null) {
        interactionState = LauncherInteractionState.Confirmation(
            action = LauncherConfirmation.RemoveCategory(categoryKey, isCustom, workspaceIndex),
            returnState = interactionState,
        )
    }

    // Removing a dock icon is a single tap on a small "x" badge — easy to hit
    // by accident — and used to unpin instantly with no way back (offerLayoutUndo
    // no-ops). Route it through the same confirm-before-destroy pattern as
    // category removal instead of unpinning directly.
    fun requestAppRemovalFromDock(app: InstalledApp) {
        interactionState = LauncherInteractionState.Confirmation(
            action = LauncherConfirmation.RemoveAppFromDock(app.packageName, app.label),
            returnState = interactionState,
        )
    }

    fun openPageAppPicker(pageIndex: Int) {
        pickerPageIndex = pageIndex
        pageAppPickerQuery = ""
        pageAppPickerSelection = emptySet()
        showPageAppPicker = true
    }

    fun clearWorkspaceDropPreview() {
        workspaceDropTargetPage = null
        workspaceDropReady = false
        workspaceAppGridDropTarget = null
    }

    LaunchedEffect(workspaceDropTargetPage, workspaceDraggingCategory, workspaceDraggingApp) {
        val target = workspaceDropTargetPage
        if (target == null || (workspaceDraggingCategory == null && workspaceDraggingApp == null)) {
            workspaceDropReady = false
            return@LaunchedEffect
        }
        delay(WORKSPACE_EDGE_HOVER_DELAY_MS)
        if (workspaceDropTargetPage == target &&
            (workspaceDraggingCategory != null || workspaceDraggingApp != null)
        ) {
            workspaceDropReady = true
        }
    }

    fun cancelLauncherInteraction() {
        interactionState = LauncherInteractionState.Browsing
        draggingCategory = null
        categoryDragOffset = Offset.Zero
        workspaceDraggingCategory = null
        workspaceCategoryDragOffset = Offset.Zero
        workspaceDraggingApp = null
        workspaceAppDragOffset = Offset.Zero
        clearWorkspaceDropPreview()
        upwardDrag = 0f
        showCreateCategoryDialog = false
        showPageAppPicker = false
        pageAppPickerQuery = ""
        pageAppPickerSelection = emptySet()
        showWorkspaceCategoryPicker = false
        pendingWorkspaceDeletion = null
        pendingWorkspaceMoveDestination = null
        showWorkspaceOverview = false
        showDockManager = false
        workspaceRenamePage = null
        workspaceNameDraft = ""
        workspaceTemplatePage = null
        pendingWorkspaceTemplate = null
        pendingWorkspaceTemplatePage = null
        showHomeCategoryPicker = false
        workspaceForNewCategory = null
        categoryEditorNameDraft = ""
        categoryEditorIconDraft = "folder"
        categoryEditorPresentationDraft = CustomCategoryPresentation.GROUP.name
        newCategoryPresentation = CustomCategoryPresentation.GROUP
        browsingCustomCategory = null
        showCategoryAppPicker = false
        categoryAppPickerQuery = ""
        categoryAppPickerSelection = emptySet()
        categoryMoveTarget = null
        categoryMergeSource = null
    }

    fun dismissHighestPriority() {
        when {
            showCreateCategoryDialog -> showCreateCategoryDialog = false
            showPageAppPicker -> showPageAppPicker = false
            showWorkspaceCategoryPicker -> showWorkspaceCategoryPicker = false
            showCategoryAppPicker -> showCategoryAppPicker = false
            categoryMoveTarget != null -> categoryMoveTarget = null
            categoryMergeSource != null -> categoryMergeSource = null
            browsingCustomCategory != null -> browsingCustomCategory = null
            pendingWorkspaceTemplate != null -> {
                pendingWorkspaceTemplate = null
                pendingWorkspaceTemplatePage = null
            }
            workspaceTemplatePage != null -> workspaceTemplatePage = null
            workspaceRenamePage != null -> workspaceRenamePage = null
            pendingWorkspaceDeletion != null -> pendingWorkspaceDeletion = null
            showWorkspaceOverview -> showWorkspaceOverview = false
            showDockManager -> showDockManager = false
            showHomeCategoryPicker -> showHomeCategoryPicker = false
            interactionState is LauncherInteractionState.Dragging ||
                interactionState is LauncherInteractionState.Resizing -> {
                draggingCategory = null
                categoryDragOffset = Offset.Zero
                workspaceDraggingCategory = null
                workspaceCategoryDragOffset = Offset.Zero
                workspaceDraggingApp = null
                workspaceAppDragOffset = Offset.Zero
                clearWorkspaceDropPreview()
                interactionState = interactionState.afterBack()
            }
            interactionState != LauncherInteractionState.Browsing -> {
                interactionState = interactionState.afterBack()
            }
        }
    }

    BackHandler(
        enabled = interactionState != LauncherInteractionState.Browsing ||
            showCreateCategoryDialog || showPageAppPicker || showWorkspaceCategoryPicker || showHomeCategoryPicker ||
            showCategoryAppPicker || categoryMoveTarget != null || categoryMergeSource != null ||
            browsingCustomCategory != null ||
            pendingWorkspaceDeletion != null || showWorkspaceOverview || workspaceRenamePage != null ||
            workspaceTemplatePage != null || pendingWorkspaceTemplate != null || showDockManager,
        onBack = ::dismissHighestPriority,
    )

    LaunchedEffect(viewModel) {
        viewModel.exitLauncherEditing.collect { cancelLauncherInteraction() }
    }

    // ── Live clock ─────────────────────────────────────────────────────────────
    var liveClock by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) {
            liveClock = currentTimeString()
            delay(1_000L)
        }
    }

    val dateStr = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }

    // ── Dock apps ─────────────────────────────────────────────────────────────
    LaunchedEffect(apps, dockInitialized) {
        // Seed once when the dock has never been set up. After that the person's
        // choices stand — an intentionally emptied dock stays empty.
        if (apps.isNotEmpty() && !dockInitialized) viewModel.ensureDefaultDock()
    }
    val dockApps = remember(apps, dockPackages) {
        val byPkg = apps.associateBy { it.packageName }
        val pinned = dockPackages.split(",").map(String::trim).filter(String::isNotEmpty)
        // No default fallback: the configured list (0–5) is authoritative once seeded.
        pinned
            .mapNotNull { byPkg[it] }
            .distinctBy { it.packageName }
            .take(5)
    }

    // ── Smart & Custom Categories ─────────────────────────────────────────────
    // isEditMode is in the keys because the body branches on it (empty custom
    // collections are shown only in edit mode); without it, entering edit mode
    // wouldn't reveal them.
    val displayCategories = remember(apps, timeAwareLayout, focusSession, customCatsList, hiddenHomeCategories, isEditMode, workspaceLayoutV2) {
        val timeCats = if (timeAwareLayout) {
            viewModel.timeAwareCategories().filter { it in APPROVED_HOME_CATEGORIES }
        } else {
            APPROVED_HOME_CATEGORIES
        }
        val bedtimeHide = viewModel.isBedtimeNow()
        val allVisible = (timeCats + APPROVED_HOME_CATEGORIES).distinct()

        // A category explicitly placed into a workspace (via "+ Category" or a
        // starter template) belongs there, not on Home too — otherwise every
        // workspace folder is duplicated onto the Home screen.
        val assignedToWorkspace = viewModel.workspaceOverview().flatMap { it.categoryKeys }.toSet()

        val standard = allVisible.filter { cat ->
            val hasApps = viewModel.byCategory(cat).isNotEmpty()
            val notBlocked = !FocusSessionManager.isBlocked(cat)
            val notBedtime = !bedtimeHide || cat !in listOf(AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.GAMES)
            hasApps && notBlocked && notBedtime &&
                cat.name !in hiddenHomeCategories.split(",") &&
                cat.name !in assignedToWorkspace
        }.map { it.name }

        val custom = customCatsList.filter { name ->
            name !in assignedToWorkspace && (isEditMode || viewModel.byCustomCategory(name).isNotEmpty())
        }

        (standard + custom)
    }

    val orderedCategories = remember(displayCategories, categoryOrderVal) {
        val order = categoryOrderVal.split(",").map(String::trim).filter(String::isNotEmpty)
        if (order.isEmpty()) {
            displayCategories
        } else {
            val sorted = displayCategories.filter { it in order }.sortedBy { order.indexOf(it) }
            val remaining = displayCategories.filter { it !in order }
            sorted + remaining
        }
    }

    // ── Recently launched ─────────────────────────────────────────────────────
    val recentApps = remember(apps) { viewModel.getRecentlyLaunchedApps() }

    // ── Movable Home sections (greeting, date/time, search, weather, today,
    // recent, categories) — THE PRINCIPLE: Home is a persistent, freeform
    // canvas of independent objects, not a stack of sections. Every id below
    // renders through CanvasObject/HomeCanvasItem (see further down this
    // file): flow order here is only the DEFAULT arrangement — a section the
    // person has actually dragged carries its own WorkspaceRecord.CanvasPos
    // and renders absolutely instead, on top of everything else, exactly
    // like an app icon. Sections currently hidden by their own toggle are
    // simply absent — re-enabling one re-inserts it at its remembered flow
    // slot (or its last free position, if it had one).
    val visibleHomeSections = remember(
        showHomeGreeting, showHomeSearch, showHomeWeather, showHomeAgenda,
        showRecentLaunched, recentApps, privacyMode, showSmartCategories, homeSectionOrderVal,
        homeHiddenObjects,
    ) {
        val present = buildList {
            if (showHomeGreeting) add("greeting")
            // The clock is its own object with its own remove action, so it
            // answers to its own switch only. Gating it on the greeting's
            // toggle meant removing the greeting silently took the clock with
            // it — while the Undo toast said "Greeting" and restored both.
            if ("datetime" !in homeHiddenObjects) add("datetime")
            if (showHomeSearch) add("search")
            if (showHomeWeather) add("weather")
            if (showHomeAgenda) add("today")
            if (showRecentLaunched && recentApps.isNotEmpty() && !privacyMode) add("recent")
            if (showSmartCategories) add("categories-heading")
        }
        val order = homeSectionOrderVal.split(",").map(String::trim).filter(String::isNotEmpty)
        if (order.isEmpty()) {
            present
        } else {
            val sorted = present.filter { it in order }.sortedBy { order.indexOf(it) }
            val remaining = present.filter { it !in order }
            sorted + remaining
        }
    }

    // ── Duplicate apps ────────────────────────────────────────────────────────
    // ── Toast / snackbar ──────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastEvent) {
        toastEvent?.consume()?.let { msg ->
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(msg)
        }
    }

    // ── Layout variables ───────────────────────────────────────────────────────
    val columns = when (homeLayoutMode) {
        "dense", "smart" -> 3
        else -> 2
    }
    val cardHeight: Dp = when (homeLayoutMode) {
        "dense" -> 108.dp
        "smart" -> 126.dp
        else -> 142.dp
    }
    val density = LocalDensity.current
    val categoryMoveThresholdPx = with(density) { cardHeight.toPx() * 0.52f }
    // Shared threshold for every swipe-up-to-open-drawer path (nested-scroll
    // bubbling over the page content, and the plain drag on the dock strip).
    val drawerOpenDistancePx = with(density) { 96.dp.toPx() }
    val spacing = when (homeLayoutMode) {
        "dense" -> 12.dp
        "smart" -> 16.dp
        else -> 22.dp
    }
    val topPad = when (homeLayoutMode) {
        "dense" -> 20.dp
        "smart" -> 28.dp
        else -> 36.dp
    }
    val greetingSize = when (homeLayoutMode) {
        "dense" -> 22.sp
        "smart" -> 25.sp
        else -> 28.sp
    }

    // ── Wallpaper & Background mode ───────────────────────────────────────────
    val useSystemWallpaper by viewModel.useSystemWallpaper.collectAsState()
    val ciyatoVideoWallpaper by viewModel.ciyatoVideoWallpaper.collectAsState()
    val ciyatoImageWallpaper by viewModel.ciyatoImageWallpaper.collectAsState()
    val wallpaperDim by viewModel.wallpaperDim.collectAsState()
    val wallpaperBlur by viewModel.wallpaperBlur.collectAsState()
    val wallpaperImageScale by viewModel.wallpaperImageScale.collectAsState()
    val wallpaperImageOffset by viewModel.wallpaperImageOffset.collectAsState()
    val hasCiyatoBackground = !useSystemWallpaper &&
        (ciyatoImageWallpaper.isNotBlank() || (ciyatoVideoWallpaper.isNotBlank() && !reduceMotion))
    val backgroundModifier = if (!hasCiyatoBackground) {
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    } else {
        Modifier
            .fillMaxSize()
            .background(CiyatoBg)
    }

    // ── Pager state for swiping screens ──────────────────────────────────────
    val workspaceOverview = remember(workspaceLayoutV2) { viewModel.workspaceOverview() }
    val homePageCount = workspaceCount.coerceIn(2, 11)
    val pagerState = rememberPagerState(
        // Restore the last page (survives returning from a category / app within
        // the session); defaults to the centre Home page (1) on a cold start.
        // workspaceCount = real workspaces + 1 (Home); min 2 = one workspace + Home.
        initialPage = viewModel.lastHomePage.coerceIn(0, homePageCount - 1),
        pageCount = { homePageCount },
    )
    // Persist the current page so a HomeScreen recreation lands back where the
    // person was, instead of snapping to a workspace.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { viewModel.lastHomePage = it }
    }
    val workspaceScope = rememberCoroutineScope()
    var undoSequence by remember { mutableIntStateOf(0) }

    fun currentLayoutSnapshot() = LayoutEditSnapshot(
        categoryOrder = categoryOrderVal,
        tileSizes = categoryTilesSizesVal,
        workspaceLayout = viewModel.workspaceLayoutSnapshot(),
        customCategories = customCats,
        customCategoryIcons = customCategoryIcons,
        customCategoryPresentations = customCategoryPresentations,
        appCategoryOverrides = appCategoryOverrides,
        hiddenHomeCategories = hiddenHomeCategories,
    )

    fun offerLayoutUndo(message: String, snapshot: LayoutEditSnapshot = currentLayoutSnapshot()) {
        // Layout edits save atomically without floating snackbar banners
    }

    /**
     * Hiding a Home section used to be a one-tap dead end: the card vanished and
     * the only way back was knowing to open the edit-mode control sheet. Every
     * removal now offers an immediate Undo so it's reversible and discoverable.
     */
    fun hideHomeSection(label: String, hide: () -> Unit, restore: () -> Unit) {
        hide()
        workspaceScope.launch {
            val action = snackbarHostState.showSnackbar(
                message = "$label hidden",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (action == SnackbarResult.ActionPerformed) restore()
        }
    }

    fun performLayoutChange(message: String, change: () -> Unit) {
        val snapshot = currentLayoutSnapshot()
        change()
        offerLayoutUndo(message, snapshot)
    }

    // ── Drag commit + edge-flip (STEP 5) ──────────────────────────────────────
    val dragMoveThresholdPx = with(density) { 18.dp.toPx() }

    fun endDrag() {
        dragController.reset()
        edgeFlipDir = 0
        if (isEditMode) {
            interactionState = LauncherInteractionState.LayoutEditing(isControlSheetVisible = false)
        } else {
            interactionState = LauncherInteractionState.Browsing
        }
    }

    // ── Displacement on drop (CanvasEngine.settle) ────────────────────────────
    // Last-measured ROOT rect of every top-level canvas object (section,
    // category card) and every FREE-positioned app tile, keyed "$pageIndex:$id" —
    // fed by CanvasObject's/WorkspaceGrid's onGloballyPositioned below, read
    // only from the settle-and-persist helpers right after. mutableStateMapOf
    // matches LauncherDragController.cellZones' own convention; nothing reads
    // these maps during composition, so writing them doesn't cost a
    // recomposition anyone would notice.
    val objectScreenRects = remember { mutableStateMapOf<String, Rect>() }
    val freeAppScreenRects = remember { mutableStateMapOf<String, Rect>() }

    /**
     * Runs a just-dropped canvas OBJECT (section or category card) through
     * [CanvasEngine.settle] against every other object currently visible on
     * [pageIndex] — default-arranged or already free, from [objectScreenRects]
     * — then persists every position [settle] actually moved. This is what
     * turns overlap from a silent bug into the shipped requirement: "if
     * there is already something, ... it should move itself away". An object
     * nudged aside by someone else's drop becomes explicitly free-positioned
     * itself (there's no other way to record "not at its default slot
     * anymore") — consistent with THE PRINCIPLE that a grid only ever
     * generates the default, never governs runtime layout.
     */
    fun settleAndPersistObject(pageIndex: Int, draggedId: String, fx: Float, fy: Float) {
        val bounds = activeObjectCanvasBounds
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
            viewModel.moveObjectToCanvasPos(pageIndex, draggedId, fx, fy)
            return
        }
        val prefix = "$pageIndex:"
        val fallbackPx = with(density) { 48.dp.toPx() }
        val items = objectScreenRects.entries
            .filter { it.key.startsWith(prefix) }
            .associate { (key, rect) ->
                val id = key.removePrefix(prefix)
                id to CanvasItem(
                    id = id,
                    x = ((rect.left - bounds.left) / bounds.width).coerceIn(0f, 1f),
                    y = ((rect.top - bounds.top) / bounds.height).coerceIn(0f, 1f),
                    w = (rect.width / bounds.width).coerceIn(0.01f, 1f),
                    h = (rect.height / bounds.height).coerceIn(0.01f, 1f),
                )
            }
            .toMutableMap()
        val draggedKnownSize = items[draggedId]
        items[draggedId] = CanvasItem(
            id = draggedId,
            x = fx.coerceIn(0f, 1f),
            y = fy.coerceIn(0f, 1f),
            w = draggedKnownSize?.w ?: (fallbackPx / bounds.width).coerceIn(0.01f, 1f),
            h = draggedKnownSize?.h ?: (fallbackPx / bounds.height).coerceIn(0.01f, 1f),
        )
        val before = items.toMap()
        CanvasEngine.settle(items.values.toList(), draggedId).forEach { item ->
            val prior = before[item.id]
            val moved = prior == null || abs(prior.x - item.x) > 0.001f || abs(prior.y - item.y) > 0.001f
            if (item.id == draggedId || moved) {
                viewModel.moveObjectToCanvasPos(pageIndex, item.id, item.x, item.y)
            }
        }
    }

    /** Same displacement contract as [settleAndPersistObject], for a FREE-
     *  positioned app tile dropped on [pageIndex]'s canvas — see
     *  [freeAppScreenRects] (fed by WorkspaceGrid's onFreeAppBounds) and
     *  [activeGridBounds], the exact rect [canvasFraction] already measures
     *  drops against. Grid-flowed apps aren't included: their own
     *  onGrid/no-overlap rules already prevent collision. */
    fun settleAndPersistApp(pageIndex: Int, draggedPkg: String, fx: Float, fy: Float) {
        val bounds = activeGridBounds
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
            viewModel.moveAppToCanvasPos(pageIndex, draggedPkg, fx, fy)
            return
        }
        val prefix = "$pageIndex:"
        val cols = gridCols.coerceAtLeast(1)
        val aspect = when { cols >= 6 -> 0.70f; cols == 5 -> 0.74f; else -> 0.78f }
        val fallbackW = (1f / cols).coerceIn(0.01f, 1f)
        val fallbackH = ((fallbackW * bounds.width / aspect) / bounds.height).coerceIn(0.01f, 1f)
        val items = freeAppScreenRects.entries
            .filter { it.key.startsWith(prefix) }
            .associate { (key, rect) ->
                val id = key.removePrefix(prefix)
                id to CanvasItem(
                    id = id,
                    x = ((rect.left - bounds.left) / bounds.width).coerceIn(0f, 1f),
                    y = ((rect.top - bounds.top) / bounds.height).coerceIn(0f, 1f),
                    w = (rect.width / bounds.width).coerceIn(0.01f, 1f),
                    h = (rect.height / bounds.height).coerceIn(0.01f, 1f),
                )
            }
            .toMutableMap()
        val draggedKnownSize = items[draggedPkg]
        items[draggedPkg] = CanvasItem(
            id = draggedPkg,
            x = fx.coerceIn(0f, 1f),
            y = fy.coerceIn(0f, 1f),
            w = draggedKnownSize?.w ?: fallbackW,
            h = draggedKnownSize?.h ?: fallbackH,
        )
        val before = items.toMap()
        CanvasEngine.settle(items.values.toList(), draggedPkg).forEach { item ->
            val prior = before[item.id]
            val moved = prior == null || abs(prior.x - item.x) > 0.001f || abs(prior.y - item.y) > 0.001f
            if (item.id == draggedPkg || moved) {
                viewModel.moveAppToCanvasPos(pageIndex, item.id, item.x, item.y)
            }
        }
    }

    /**
     * Root-coordinate finger position converted to a 0f..1f fraction of the
     * currently visible page's own WorkspaceGrid — the exact same rectangle
     * [CanvasPos] is measured against at render time (see WorkspaceGrid's
     * `layout {}` block), taken from the same Layout that already supplies
     * cell bounds via onCellBounds, never a guessed screen size. Falls back
     * to the origin if bounds haven't been measured yet; WorkspaceStore
     * clamps into range regardless.
     */
    fun canvasFraction(finger: Offset): Offset {
        val bounds = activeGridBounds ?: return Offset.Zero
        if (bounds.width <= 0f || bounds.height <= 0f) return Offset.Zero
        return Offset(
            ((finger.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
            ((finger.y - bounds.top) / bounds.height).coerceIn(0f, 1f),
        )
    }

    fun commitGridDrop(sourcePage: Int) {
        val pkg = dragController.activePackage ?: run { endDrag(); return }
        if (!dragController.movedFar(dragMoveThresholdPx)) {
            // Long-pressed and released without moving: that's a request for the
            // app's menu, not a move. (The tile's own long-press detector was
            // removed — it raced this one and kept stealing drags.)
            dragController.reset(); edgeFlipDir = 0
            interactionState = LauncherInteractionState.ItemSelected(pkg)
            return
        }
        val currentPage = pagerState.currentPage
        val snapshot = currentLayoutSnapshot()
        when {
            dragController.overDock -> {
                viewModel.pinToDockAt(pkg, dragController.dockDropIndex())
                viewModel.removeAppFromPage(sourcePage, pkg)
                offerLayoutUndo("Moved to dock", snapshot)
            }
            activeGridBounds?.contains(dragController.fingerRoot) == true -> {
                // Freeform: the object lands exactly where it was released,
                // never snapped to a cell — the canvas is a persistent
                // surface, not a section, and that rule holds on every
                // workspace page, not just Home.
                val (x, y) = canvasFraction(dragController.fingerRoot)
                if (currentPage != sourcePage) viewModel.moveAppBetweenWorkspaces(sourcePage, currentPage, pkg)
                settleAndPersistApp(currentPage, pkg, x, y)
                if (!reduceMotion) settlingPackage = pkg
                offerLayoutUndo(
                    if (currentPage != sourcePage) "Shortcut moved to workspace" else "Shortcut moved",
                    snapshot,
                )
            }
            currentPage != sourcePage -> {
                viewModel.moveAppBetweenWorkspaces(sourcePage, currentPage, pkg)
                offerLayoutUndo("Shortcut moved to workspace", snapshot)
            }
        }
        endDrag()
    }

    fun commitDockDrop() {
        val pkg = dragController.activePackage ?: run { endDrag(); return }
        if (!dragController.movedFar(dragMoveThresholdPx)) {
            // Same rule as the grid: a still long-press opens the app's menu.
            dragController.reset(); edgeFlipDir = 0
            interactionState = LauncherInteractionState.ItemSelected(pkg)
            return
        }
        val currentPage = pagerState.currentPage
        when {
            dragController.overDock -> viewModel.pinToDockAt(pkg, dragController.dockDropIndex())
            dragController.targetCell != null -> {
                val snapshot = currentLayoutSnapshot()
                viewModel.unpinFromDock(pkg)
                viewModel.placeAppAtCell(currentPage, pkg, dragController.targetCell!!)
                offerLayoutUndo("Moved from dock", snapshot)
            }
            else -> {
                val snapshot = currentLayoutSnapshot()
                viewModel.unpinFromDock(pkg)
                viewModel.addAppToPage(currentPage, pkg)
                offerLayoutUndo("Moved from dock", snapshot)
            }
        }
        endDrag()
    }

    fun onDragMove(amount: Offset) {
        dragController.fingerRoot += amount
        dragController.resolveTarget()
        val x = dragController.fingerRoot.x
        val newDir = when {
            launcherSurfaceWidth <= 0f -> 0
            x < launcherSurfaceWidth * 0.07f -> -1
            x > launcherSurfaceWidth * 0.93f -> 1
            else -> 0
        }
        if (newDir != edgeFlipDir) edgeFlipDir = newDir
    }

    // Hold at a screen edge during a drag → flip to the adjacent workspace.
    LaunchedEffect(edgeFlipDir) {
        if (edgeFlipDir != 0 && dragController.isActive) {
            delay(WORKSPACE_EDGE_HOVER_DELAY_MS)
            if (edgeFlipDir != 0 && dragController.isActive) {
                val target = pagerState.currentPage + edgeFlipDir
                if (target in 0 until pagerState.pageCount) pagerState.animateScrollToPage(target)
                edgeFlipDir = 0
            }
        }
    }
    // Cell zones (and the canvas bounds derived from the same grid) belong to
    // the visible page only; clear on flip so the incoming page re-registers
    // fresh bounds.
    LaunchedEffect(pagerState.currentPage) {
        dragController.cellZones.clear()
        activeGridBounds = null
        activeObjectCanvasBounds = null
    }


    /**
     * Wraps [content] as one freeform canvas object on [pageIndex]: shared
     * drag engine (HomeCanvasItem), safe-bounds clamped free positioning,
     * and the long-press menu (Reset position / Remove). [onRemove] is the
     * one thing every call site supplies itself, since the right hide/Undo
     * path differs per object (see [ObjectMenuTarget]).
     */
    @Composable
    fun CanvasObject(
        pageIndex: Int,
        objectId: String,
        label: String,
        hasPosition: Boolean,
        onRemove: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        HomeCanvasItem(
            objectId = "$pageIndex:$objectId",
            isEditMode = isEditMode,
            reduceMotion = reduceMotion,
            hapticEnabled = hapticEnabled,
            canvasBounds = activeObjectCanvasBounds,
            onMoved = { x, y -> settleAndPersistObject(pageIndex, objectId, x, y) },
            onTapHold = {
                objectMenuTarget = ObjectMenuTarget(
                    objectId = objectId,
                    label = label,
                    canReset = hasPosition,
                    onReset = { viewModel.resetObjectToFlowPos(pageIndex, objectId) },
                    onRemove = onRemove,
                )
            },
            modifier = modifier.onGloballyPositioned {
                if (pageIndex == pagerState.currentPage) {
                    objectScreenRects["$pageIndex:$objectId"] = it.boundsInRoot()
                }
            },
            content = content,
        )
    }

    /**
     * One category card (standard or custom) as a canvas object — shares
     * the same [CanvasObject] wrapper, and therefore the same drag engine,
     * safe-bounds clamping, and long-press menu as every other Home object.
     * [hasPosition] mirrors [HomeSectionBody]'s: false renders inline in the
     * default chunked-grid flow, true renders standalone in the free-object
     * overlay. Removal reuses [LauncherViewModel.removeCategoryFromHome] /
     * `restoreCategoryToHome` — the exact same hide-on-Home mechanism the
     * category editor's own "Remove from Home" action already used, now
     * additionally reachable via long-press → Remove with the shared
     * confirmation dialog.
     */
    @Composable
    fun CategoryCardObject(catKey: String, hasPosition: Boolean, pageIndex: Int = 1, cardModifier: Modifier = Modifier) {
        val standardCat = remember(catKey) { runCatching { AppCategory.valueOf(catKey) }.getOrNull() }
        val displayName = if (standardCat != null) viewModel.getCategoryDisplayName(standardCat) else catKey
        val catApps = if (standardCat != null) viewModel.byCategory(standardCat) else viewModel.byCustomCategory(catKey)
        val tileSize = viewModel.getCategoryTileSize(catKey)
        val isCustom = standardCat == null
        CanvasObject(
            pageIndex = pageIndex,
            objectId = "category:$catKey",
            label = displayName,
            hasPosition = hasPosition,
            onRemove = {
                // Home hides via hiddenHomeCategories (its long-standing
                // mechanism); a real workspace has no such list — removing
                // there means taking it out of that workspace's categoryKeys,
                // and Undo puts it right back.
                if (pageIndex == 1) {
                    hideHomeSection(displayName,
                        hide = { viewModel.removeCategoryFromHome(catKey) },
                        restore = { viewModel.restoreCategoryToHome(catKey) })
                } else {
                    hideHomeSection(displayName,
                        hide = { viewModel.removeCategoryFromWorkspace(pageIndex, catKey) },
                        restore = { viewModel.addCategoryToWorkspace(pageIndex, catKey) })
                }
            },
            modifier = cardModifier,
        ) {
            SmartCategoryCard(
                category = standardCat ?: AppCategory.CUSTOM,
                displayName = displayName,
                apps = catApps,
                onTap = {
                    if (isEditMode) {
                        openCategoryEditor(catKey)
                    } else if (standardCat != null) {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCategoryTap(standardCat)
                    } else {
                        browsingCustomCategory = catKey
                    }
                },
                customIcon = if (isCustom) {
                    remember(catKey, customCategoryIcons) { viewModel.getCustomCategoryIcon(catKey) }
                } else "folder",
                customPresentation = if (isCustom) {
                    remember(catKey, customCategoryPresentations) { viewModel.getCustomCategoryPresentation(catKey) }
                } else CustomCategoryPresentation.CARD,
                tileSize = tileSize,
                isEditMode = isEditMode,
                onResize = { newSize -> viewModel.setCategoryTileSize(catKey, newSize) },
                onAppTap = { tappedApp -> viewModel.launchApp(tappedApp) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    /**
     * Dispatches one Home section id to its content — the SAME content
     * whether it's rendering in the flow LazyColumn (default arrangement) or
     * in the free-positioned overlay (see the "1 ->" page branch below).
     * Each branch supplies its own [CanvasObject] wiring (label + the right
     * hide/Undo path for that section) so removal, positioning, and the
     * long-press menu are identical everywhere.
     */
    @Composable
    fun HomeSectionBody(sectionKey: String, hasPosition: Boolean) {
        when (sectionKey) {
            "greeting" -> CanvasObject(
                pageIndex = 1, objectId = "greeting", label = "Greeting", hasPosition = hasPosition,
                onRemove = {
                    hideHomeSection("Greeting",
                        hide = { viewModel.setShowHomeGreeting(false) },
                        restore = { viewModel.setShowHomeGreeting(true) })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // maxLines + ellipsis: an arbitrarily long custom greeting
                    // (see CustomGreetingScreen) used to wrap onto a second line
                    // with zero spacing before the date/time row directly under
                    // it — at larger font scales that second line could crowd or
                    // visually run into it. Greeting is now also a fully
                    // independent object with real spacing on every side, but
                    // this clamp is the real, permanent fix: unbounded text
                    // should never be able to grow into a neighbour at all.
                    Text(
                        if (!privacyMode) viewModel.greeting else "Welcome back",
                        color = CiyatoWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = greetingSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (focusSession != null) {
                        Spacer(Modifier.width(12.dp))
                        FocusBadge(focusSession!!, reduceMotion = reduceMotion)
                    }
                }
            }

            "datetime" -> CanvasObject(
                pageIndex = 1, objectId = "datetime", label = "Date & time", hasPosition = hasPosition,
                onRemove = {
                    hideHomeSection("Date & time",
                        hide = { viewModel.hideObjectOnPage(1, "datetime") },
                        restore = { viewModel.showObjectOnPage(1, "datetime") })
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dateStr, color = CiyatoSec, fontSize = if (denseLayout) 12.sp else 13.sp)
                    AnimatedContent(
                        targetState = liveClock,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "clock",
                    ) { time ->
                        Text("· $time", color = CiyatoMuted, fontSize = if (denseLayout) 12.sp else 13.sp)
                    }
                }
            }

            "search" -> CanvasObject(
                pageIndex = 1, objectId = "search", label = "Search", hasPosition = hasPosition,
                onRemove = {
                    hideHomeSection("Search",
                        hide = { viewModel.setShowHomeSearch(false) },
                        restore = { viewModel.setShowHomeSearch(true) })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                HomeSearchBar(isDense = denseLayout, onClick = onOpenSearch, modifier = Modifier.fillMaxWidth())
            }

            "weather" -> CanvasObject(
                pageIndex = 1, objectId = "weather", label = "Weather", hasPosition = hasPosition,
                onRemove = {
                    hideHomeSection("Weather",
                        hide = { viewModel.setShowHomeWeather(false) },
                        restore = { viewModel.setShowHomeWeather(true) })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                WeatherCard(
                    isDense = denseLayout,
                    weatherState = if (privacyMode) null else weatherState,
                    useFahrenheit = tempUnitPref == "F",
                    onTap = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onWeatherTap()
                    },
                    modifier = Modifier.fillMaxWidth().height(if (denseLayout) 160.dp else 190.dp),
                )
            }

            "today" -> CanvasObject(
                pageIndex = 1, objectId = "today", label = "Today", hasPosition = hasPosition,
                onRemove = {
                    hideHomeSection("Agenda",
                        hide = { viewModel.setShowHomeAgenda(false) },
                        restore = { viewModel.setShowHomeAgenda(true) })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AgendaCard(
                    isDense = denseLayout,
                    events = if (privacyMode) emptyList() else agendaEvents,
                    onTap = onAgendaTap,
                    modifier = Modifier.fillMaxWidth().height(if (denseLayout) 160.dp else 190.dp),
                )
            }

            "recent" -> CanvasObject(
                pageIndex = 1, objectId = "recent", label = "Recent", hasPosition = hasPosition,
                onRemove = {
                    hideHomeSection("Recent",
                        hide = { viewModel.setShowRecentlyLaunched(false) },
                        restore = { viewModel.setShowRecentlyLaunched(true) })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recent", color = CiyatoWhite, fontWeight = FontWeight.SemiBold,
                        fontSize = if (denseLayout) 14.sp else 16.sp)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(recentApps.take(8), key = { it.packageName }) { app ->
                            AppIconTile(app = app, iconSize = if (denseLayout) 44.dp else 50.dp,
                                onClick = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.launchApp(app)
                                },
                                onLongClick = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    interactionState = LauncherInteractionState.ItemSelected(app.packageName)
                                },
                                modifier = Modifier.width(if (denseLayout) 56.dp else 62.dp))
                        }
                    }
                }
            }

            "categories-heading" -> CanvasObject(
                pageIndex = 1, objectId = "categories-heading", label = "Categories", hasPosition = hasPosition,
                onRemove = {
                    hideHomeSection("Categories",
                        hide = { viewModel.setSmartCategories(false) },
                        restore = { viewModel.setSmartCategories(true) })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Categories",
                            color = CiyatoWhite, fontWeight = FontWeight.SemiBold,
                            fontSize = if (denseLayout) 17.sp else 20.sp,
                        )
                        if (focusSession != null) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp))
                                .background(CiyatoGold.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("Focus", color = CiyatoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (isEditMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { showCreateCategoryDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("+ New Category", color = CiyatoGold, fontSize = 13.sp)
                            }
                            TextButton(
                                onClick = { showHomeCategoryPicker = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("+ Category", color = CiyatoSec, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            else -> if (sectionKey.startsWith("category:")) {
                CategoryCardObject(catKey = sectionKey.removePrefix("category:"), hasPosition = hasPosition)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent, // Let system wallpaper or custom background show
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        Box(
            modifier = backgroundModifier
                .onSizeChanged {
                    launcherSurfaceHeight = it.height.toFloat()
                    launcherSurfaceWidth = it.width.toFloat()
                }
                .pointerInput(isEditMode) {
                    // Child app/card gestures consume their own taps. This only
                    // exits editing when the person double-taps empty Home space.
                    detectTapGestures(
                        onDoubleTap = {
                            if (isEditMode || showLauncherControls) cancelLauncherInteraction()
                        },
                    )
                },
        ) {
            if (!useSystemWallpaper && ciyatoImageWallpaper.isNotBlank()) {
                CiyatoImageBackground(
                    uri = ciyatoImageWallpaper,
                    scale = wallpaperImageScale,
                    verticalOffset = wallpaperImageOffset,
                    blurRadius = wallpaperBlur,
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = wallpaperDim / 100f)))
            } else if (!useSystemWallpaper && ciyatoVideoWallpaper.isNotBlank() && !reduceMotion) {
                CiyatoVideoBackground(uri = ciyatoVideoWallpaper)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = wallpaperDim / 100f)))
            }

            val homeDirectionalConnection = com.ciyato.launcher.ui.components.rememberDirectionalNestedScrollConnection()
            // Swipe-up-to-open-drawer: an ancestor of every page's LazyColumn,
            // so it sees whatever vertical drag/fling that column couldn't
            // itself consume (already scrolled to the end, or nothing to
            // scroll) — see DrawerSwipeNestedScrollConnection for why this
            // replaces the old root-level pointerInput.
            val drawerSwipeConnection = rememberDrawerSwipeNestedScrollConnection(
                thresholdPx = drawerOpenDistancePx,
                enabled = !isEditMode && showAppDrawer,
                onOpen = onOpenDrawer,
            )

            // Swipable layout area. Keep neighbours composed so a drag gesture
            // survives an edge-flip to an adjacent workspace (the source tile
            // isn't disposed the moment the page settles).
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 2,
                modifier = Modifier
                    .fillMaxSize()
                    .directionResetPointerInput(homeDirectionalConnection)
                    .nestedScroll(homeDirectionalConnection)
                    .nestedScroll(drawerSwipeConnection)
            ) { page ->
                // Include the live swipe fraction so transitions animate smoothly
                // across the whole gesture instead of snapping at the halfway point.
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val workspaceTransitionModifier = when (if (reduceMotion) "none" else workspaceTransition) {
                    "fade" -> Modifier.graphicsLayer {
                        alpha = 1f - abs(pageOffset).coerceIn(0f, 1f) * 0.55f
                    }
                    "scale" -> Modifier.graphicsLayer {
                        val scale = 1f - abs(pageOffset).coerceIn(0f, 1f) * 0.06f
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - abs(pageOffset).coerceIn(0f, 1f) * 0.25f
                    }
                    "depth" -> Modifier.graphicsLayer {
                        // Incoming page slides normally; outgoing page sinks back.
                        val progress = abs(pageOffset).coerceIn(0f, 1f)
                        val scale = 1f - progress * 0.14f
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - progress * 0.45f
                        translationX = pageOffset * size.width * 0.18f
                    }
                    "flip" -> Modifier.graphicsLayer {
                        val progress = pageOffset.coerceIn(-1f, 1f)
                        rotationY = progress * 24f
                        cameraDistance = 20f * this.density
                        alpha = 1f - abs(progress) * 0.30f
                    }
                    else -> Modifier
                }
                when (page) {
                    1 -> {
                        // Main home screen — ONE canvas surface (HomeCanvasSurface):
                        // every object, default-arranged or freely dragged, is
                        // measured and placed by the SAME Layout pass. There is no
                        // second (flow) container that can independently reflow
                        // around a gap this one already accounted for — see the
                        // shipped defect report this replaced (weather across
                        // Categories, greeting over the date/clock).
                        val topInsetPx = with(density) { scaffoldPadding.calculateTopPadding().toPx() }
                        val bottomInsetPx = with(density) { 140.dp.toPx() }
                        val topPadPx = with(density) { topPad.toPx() }
                        val horizontalPaddingPx = with(density) { 16.dp.toPx() }
                        val rowSpacingPx = with(density) { spacing.toPx() }
                        val columnSpacingPx = with(density) { 10.dp.toPx() }

                        // Categories are independent canvas objects too — a
                        // category with no free position falls back to this
                        // default chunked-row flow (THE PRINCIPLE: the grid
                        // generates the default layout, never a runtime
                        // constraint); one that's been dragged is pulled out of
                        // this flow entirely and rendered free instead.
                        val freeCategoryKeysHome = remember(homeObjectPositions) {
                            homeObjectPositions.keys
                                .filter { it.startsWith("category:") }
                                .map { it.removePrefix("category:") }
                                .toSet()
                        }
                        val flowCategoriesHome = remember(orderedCategories, freeCategoryKeysHome) {
                            orderedCategories.filterNot { it in freeCategoryKeysHome }
                        }
                        val homeFlowRows = remember(
                            homeTipDismissed, isEditMode, visibleHomeSections, homeObjectPositions,
                            isLoading, orderedCategories, flowCategoriesHome, columns,
                        ) {
                            buildList {
                                if (!homeTipDismissed && !isEditMode) add(listOf("tip-banner"))
                                visibleHomeSections.forEach { sectionKey ->
                                    if (sectionKey in homeObjectPositions) return@forEach
                                    add(listOf(sectionKey))
                                    if (sectionKey == "categories-heading") {
                                        when {
                                            isLoading -> add(listOf("categories-skeleton"))
                                            orderedCategories.isEmpty() -> add(listOf("categories-empty"))
                                            else -> flowCategoriesHome.chunked(columns)
                                                .forEach { rowCats -> add(rowCats.map { "category:$it" }) }
                                        }
                                    }
                                }
                                add(listOf("app-grid"))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(workspaceTransitionModifier)
                                .onGloballyPositioned { coords ->
                                    if (pagerState.currentPage == 1) {
                                        val raw = coords.boundsInRoot()
                                        activeObjectCanvasBounds = Rect(
                                            raw.left,
                                            raw.top + topInsetPx,
                                            raw.right,
                                            (raw.bottom - bottomInsetPx).coerceAtLeast(raw.top + topInsetPx),
                                        )
                                    }
                                },
                        ) {
                            val bounds = activeObjectCanvasBounds
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            enterLayoutEditing(showControls = true)
                                        },
                                    ),
                            ) {
                                HomeCanvasSurface(
                                    flowRows = homeFlowRows,
                                    freePositions = homeObjectPositions,
                                    canvasWidthPx = bounds?.width ?: 0f,
                                    canvasHeightPx = bounds?.height ?: 0f,
                                    flowStartYPx = topInsetPx + topPadPx,
                                    freeStartYPx = topInsetPx,
                                    horizontalPaddingPx = horizontalPaddingPx,
                                    rowSpacingPx = rowSpacingPx,
                                    columnSpacingPx = columnSpacingPx,
                                    bottomPaddingPx = bottomInsetPx,
                                ) { id ->
                                    when {
                                        id == "tip-banner" -> CiyatoTipBanner(
                                            text = "Swipe up for Apps. Long-press empty space to enter Edit mode and arrange your layout. Long-press any section to drag it to a new spot.",
                                            onDismiss = viewModel::dismissHomeTip,
                                            actionLabel = "Got it",
                                            onAction = viewModel::dismissHomeTip,
                                            accentColor = activeAccent,
                                        )
                                        id == "categories-skeleton" ->
                                            SkeletonCategoryGrid(columns = columns, rows = 2, cardHeight = cardHeight)
                                        id == "categories-empty" ->
                                            Text("No apps found", color = CiyatoMuted, modifier = Modifier.padding(16.dp))
                                        id == "app-grid" -> {
                                            val cellApps1 = remember(apps, workspaceLayoutV2, gridSizePref) {
                                                viewModel.cellAppsForPage(1)
                                            }
                                            val cellSpans1 = remember(apps, workspaceLayoutV2, gridSizePref) {
                                                viewModel.cellSpansForPage(1)
                                            }
                                            val canvasPos1 = remember(apps, workspaceLayoutV2, gridSizePref) {
                                                viewModel.canvasPosForPage(1)
                                            }
                                            WorkspaceGrid(
                                                // The Home grid's cells are placed against the same
                                                // authorColumns (gridCols, from the grid-size setting) every
                                                // workspace page uses — NOT `columns`, which is the smart
                                                // category card column count (2 or 3). Rendering with the
                                                // wrong column count would make a cell index resolve to a
                                                // different row/column than the one it was placed at.
                                                columns = gridCols,
                                                minRows = 2,
                                                cellApps = cellApps1,
                                                cellSpans = cellSpans1,
                                                canvasPositions = canvasPos1,
                                                settlingPackage = settlingPackage.takeIf { pagerState.currentPage == 1 },
                                                expandedPackages = expandedAppsSet,
                                                isEditMode = isEditMode,
                                                onAppTap = { app -> viewModel.launchApp(app) },
                                                onResize = { pkg, spanX, spanY -> viewModel.resizeAppTile(1, pkg, spanX, spanY) },
                                                hiddenPackage = dragController.activePackage,
                                                highlightCell = dragController.targetCell.takeIf {
                                                    dragController.isActive && !dragController.overDock && pagerState.currentPage == 1
                                                },
                                                modifier = Modifier.onGloballyPositioned {
                                                    if (pagerState.currentPage == 1) activeGridBounds = it.boundsInRoot()
                                                },
                                                onCellBounds = { cell, cellBounds ->
                                                    if (pagerState.currentPage == 1) {
                                                        dragController.cellZones[cell] = cellBounds
                                                    }
                                                },
                                                onFreeAppBounds = { pkg, appBounds ->
                                                    if (pagerState.currentPage == 1) freeAppScreenRects["1:$pkg"] = appBounds
                                                },
                                                tileGesture = { tileModifier, cell, gridApp ->
                                                    tileModifier.pointerInput(1, cell, gridApp.packageName) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = { touch ->
                                                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                val origin = dragController.cellZones[cell]?.topLeft ?: Offset.Zero
                                                                dragController.begin(
                                                                    pkg = gridApp.packageName,
                                                                    icon = gridApp.icon,
                                                                    fromPage = 1,
                                                                    fromDockIndex = null,
                                                                    start = origin + touch,
                                                                )
                                                            },
                                                            onDrag = { change, amount ->
                                                                change.consume()
                                                                onDragMove(amount)
                                                            },
                                                            onDragEnd = { commitGridDrop(1) },
                                                            onDragCancel = { endDrag() },
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                        id.startsWith("category:") -> CategoryCardObject(
                                            catKey = id.removePrefix("category:"),
                                            hasPosition = id in homeObjectPositions,
                                            pageIndex = 1,
                                        )
                                        else -> HomeSectionBody(id, hasPosition = id in homeObjectPositions)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val pageIndex = page
                        val pageApps = remember(apps, pageIndex, workspaceLayoutV2) {
                            viewModel.getAppsForPage(pageIndex)
                        }
                        val pageCategoryKeys = remember(pageIndex, workspaceLayoutV2) {
                            viewModel.getCategoriesForWorkspace(pageIndex)
                        }
                        // Same freeform-canvas treatment as Home (page 1): every
                        // category card on a real workspace is an independent,
                        // freely positionable object too (requirement #1 — "on
                        // Home AND every workspace page").
                        val pageObjectPositions = remember(pageIndex, workspaceLayoutV2) {
                            viewModel.objectPositionsForPage(pageIndex)
                        }
                        val pageTopInsetPx = with(density) { (scaffoldPadding.calculateTopPadding() + topPad + 20.dp).toPx() }
                        val pageBottomInsetPx = with(density) { 140.dp.toPx() }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(workspaceTransitionModifier)
                                .onGloballyPositioned { coords ->
                                    if (pageIndex == pagerState.currentPage) {
                                        val raw = coords.boundsInRoot()
                                        activeObjectCanvasBounds = Rect(
                                            raw.left,
                                            raw.top + pageTopInsetPx,
                                            raw.right,
                                            (raw.bottom - pageBottomInsetPx).coerceAtLeast(raw.top + pageTopInsetPx),
                                        )
                                    }
                                },
                        ) {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 20.dp, end = 20.dp,
                                top = scaffoldPadding.calculateTopPadding() + topPad + 20.dp,
                                bottom = 140.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if ((workspaceDraggingCategory?.startsWith("$pageIndex:") == true ||
                                            workspaceDraggingApp?.startsWith("$pageIndex:") == true) &&
                                        workspaceDropTargetPage != null
                                    ) {
                                        Modifier.border(
                                            width = if (workspaceDropReady) 2.dp else 1.dp,
                                            color = CiyatoGold.copy(alpha = if (workspaceDropReady) 0.88f else 0.42f),
                                            shape = RoundedCornerShape(18.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        enterLayoutEditing(showControls = true)
                                    }
                                )
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = viewModel.workspaceName(pageIndex),
                                        color = CiyatoWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp
                                    )
                                    if (isEditMode) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (workspaceCount < 11) {
                                                // Adds beside where you are: left pages grow leftward,
                                                // right pages grow rightward. No direction menu needed.
                                                TextButton(onClick = {
                                                    performLayoutChange("Workspace added") {
                                                        if (pageIndex == 0) viewModel.insertWorkspaceBeforePage(pageIndex)
                                                        else viewModel.insertWorkspaceAfterPage(pageIndex)
                                                    }
                                                }) {
                                                    Text("+ Workspace", color = CiyatoSec, fontSize = 12.sp)
                                                }
                                            }
                                            TextButton(
                                                onClick = {
                                                    workspaceCategoryPickerIndex = pageIndex
                                                    showWorkspaceCategoryPicker = true
                                                }
                                            ) {
                                                Text("+ Category", color = CiyatoSec, fontSize = 12.sp)
                                            }
                                            TextButton(
                                                onClick = {
                                                    openPageAppPicker(pageIndex)
                                                }
                                            ) {
                                                Text("+ Add App", color = CiyatoGold, fontSize = 12.sp)
                                            }
                                            TextButton(
                                                onClick = { pendingWorkspaceDeletion = pageIndex },
                                                enabled = workspaceOverview.size > 1,
                                            ) {
                                                Text("Delete", color = CiyatoSec, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            if (pageCategoryKeys.isNotEmpty()) {
                                item {
                                    val freeCategoryKeys = remember(pageObjectPositions) {
                                        pageObjectPositions.keys
                                            .filter { it.startsWith("category:") }
                                            .map { it.removePrefix("category:") }
                                            .toSet()
                                    }
                                    val flowCategoryKeys = remember(pageCategoryKeys, freeCategoryKeys) {
                                        pageCategoryKeys.filterNot { it in freeCategoryKeys }
                                    }
                                    if (flowCategoryKeys.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            flowCategoryKeys.chunked(2).forEach { rowCats ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    rowCats.forEach { categoryKey -> key(categoryKey) {
                                                        CategoryCardObject(
                                                            catKey = categoryKey,
                                                            hasPosition = false,
                                                            pageIndex = pageIndex,
                                                            cardModifier = Modifier.weight(1f),
                                                        )
                                                    } }
                                                    if (rowCats.size == 1) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val workspaceVisualIndex = if (pageIndex == 0) 0 else pageIndex - 1
                            val shouldShowWorkspaceStarter = pageApps.isEmpty() && pageCategoryKeys.isEmpty() &&
                                workspaceOverview.getOrNull(workspaceVisualIndex)?.starterDismissed != true
                            if (shouldShowWorkspaceStarter) {
                                item {
                                    WorkspaceStarterCard(
                                        onAddShortcut = {
                                            openPageAppPicker(pageIndex)
                                        },
                                        onAddCategory = {
                                            workspaceCategoryPickerIndex = pageIndex
                                            showWorkspaceCategoryPicker = true
                                        },
                                        onChooseTemplate = { workspaceTemplatePage = pageIndex },
                                        onStartClean = { viewModel.dismissWorkspaceStarter(pageIndex) },
                                    )
                                }
                            } else {
                                item {
                                    val cellApps = remember(apps, pageIndex, workspaceLayoutV2, gridSizePref) {
                                        viewModel.cellAppsForPage(pageIndex)
                                    }
                                    val cellSpans = remember(apps, pageIndex, workspaceLayoutV2, gridSizePref) {
                                        viewModel.cellSpansForPage(pageIndex)
                                    }
                                    val canvasPos = remember(apps, pageIndex, workspaceLayoutV2, gridSizePref) {
                                        viewModel.canvasPosForPage(pageIndex)
                                    }
                                    WorkspaceGrid(
                                        columns = gridCols,
                                        minRows = gridRows,
                                        cellApps = cellApps,
                                        cellSpans = cellSpans,
                                        canvasPositions = canvasPos,
                                        settlingPackage = settlingPackage.takeIf { pageIndex == pagerState.currentPage },
                                        isEditMode = isEditMode,
                                        onAppTap = { tapped -> viewModel.launchApp(tapped) },
                                        onResize = { pkg, spanX, spanY -> viewModel.resizeAppTile(pageIndex, pkg, spanX, spanY) },
                                        hiddenPackage = dragController.activePackage,
                                        highlightCell = dragController.targetCell.takeIf {
                                            dragController.isActive && !dragController.overDock
                                        },
                                        modifier = Modifier.onGloballyPositioned {
                                            if (pageIndex == pagerState.currentPage) activeGridBounds = it.boundsInRoot()
                                        },
                                        onCellBounds = { cell, bounds ->
                                            if (pageIndex == pagerState.currentPage) {
                                                dragController.cellZones[cell] = bounds
                                            }
                                        },
                                        tileGesture = { tileModifier, cell, gridApp ->
                                            tileModifier.pointerInput(pageIndex, cell, gridApp.packageName) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { touch ->
                                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        // Seed from the exact touch point (tile root origin + local touch).
                                                        val origin = dragController.cellZones[cell]?.topLeft ?: Offset.Zero
                                                        dragController.begin(
                                                            pkg = gridApp.packageName,
                                                            icon = gridApp.icon,
                                                            fromPage = pageIndex,
                                                            fromDockIndex = null,
                                                            start = origin + touch,
                                                        )
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        onDragMove(amount)
                                                    },
                                                    onDragEnd = { commitGridDrop(pageIndex) },
                                                    onDragCancel = { endDrag() },
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Free-positioned category cards on this workspace —
                        // same overlay treatment as Home's (see the "1 ->" branch).
                        pageObjectPositions.entries
                            .filter { it.key.startsWith("category:") }
                            .sortedBy { it.value.z }
                            .forEach { (objectId, pos) ->
                                val bounds = activeObjectCanvasBounds
                                if (bounds != null && bounds.width > 0f && bounds.height > 0f) {
                                    key(objectId) {
                                        Box(
                                            modifier = Modifier
                                                .offset {
                                                    androidx.compose.ui.unit.IntOffset(
                                                        (pos.x * bounds.width).roundToInt(),
                                                        (pageTopInsetPx + pos.y * bounds.height).roundToInt(),
                                                    )
                                                }
                                                .widthIn(max = with(density) { bounds.width.toDp() }),
                                        ) {
                                            CategoryCardObject(
                                                catKey = objectId.removePrefix("category:"),
                                                hasPosition = true,
                                                pageIndex = pageIndex,
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
            }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = scaffoldPadding.calculateBottomPadding() + 20.dp)
                    // The page-indicator/dock strip sits outside the pager
                    // (and therefore outside drawerSwipeConnection's subtree),
                    // so it needs its own plain swipe-up detector — nothing
                    // scrollable underneath it to compete for the gesture.
                    .pointerInput(isEditMode, showAppDrawer, drawerOpenDistancePx) {
                        detectVerticalDragGestures(
                            onDragStart = { upwardDrag = 0f },
                            onVerticalDrag = { _, amount ->
                                if (!isEditMode && showAppDrawer && amount < 0f) upwardDrag += amount
                            },
                            onDragEnd = {
                                if (!isEditMode && showAppDrawer && upwardDrag <= -drawerOpenDistancePx) onOpenDrawer()
                                upwardDrag = 0f
                            },
                            onDragCancel = { upwardDrag = 0f },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Workspace map: always visible so you know where you are and
                // how many workspaces exist. Home has a distinct marker.
                WorkspacePageIndicator(
                    pageCount = pagerState.pageCount,
                    currentPage = pagerState.currentPage,
                    homePage = 1,
                    onDotTap = { page ->
                        workspaceScope.launch { pagerState.animateScrollToPage(page) }
                    },
                )
                if (showHomeDock && (dockApps.isNotEmpty() || dragController.isActive || isEditMode)) {
                    Spacer(Modifier.height(10.dp))
                    BottomDock(
                        dockApps = dockApps,
                        onAppTap = {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.launchApp(it)
                        },
                        onRemoveApp = { unpinApp ->
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            requestAppRemovalFromDock(unpinApp)
                        },
                        isEditMode = isEditMode,
                        isDragActive = dragController.isActive,
                        // hoverDockSlot, not dockDropIndex(): calling the function here
                        // reads fingerRoot, which is rewritten on every pointer move, so
                        // this whole scope recomposed on every frame of every drag — the
                        // "movement is very slow" report. The derivedStateOf only emits
                        // when the RESOLVED slot actually changes, which is rare.
                        hoverSlotIndex = dragController.hoverDockSlot.value,
                        hiddenPackage = dragController.activePackage,
                        onDockBounds = { dragController.dockBounds = it },
                        onSlotBounds = { index, bounds -> dragController.dockSlots[index] = bounds },
                        tileGesture = { dockModifier, index, dockApp ->
                            dockModifier.pointerInput(index, dockApp.packageName) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { touch ->
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val origin = dragController.dockSlots[index]?.topLeft ?: Offset.Zero
                                        dragController.begin(
                                            pkg = dockApp.packageName,
                                            icon = dockApp.icon,
                                            fromPage = null,
                                            fromDockIndex = index,
                                            start = origin + touch,
                                        )
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        onDragMove(amount)
                                    },
                                    onDragEnd = { commitDockDrop() },
                                    onDragCancel = { endDrag() },
                                )
                            }
                        },
                    )
                }
            }

            // Floating drag icon — paints above the pager and the dock.
            DragOverlay(dragController)
        }

        objectMenuTarget?.let { target ->
            CanvasObjectMenu(
                label = target.label,
                canReset = target.canReset,
                onDismiss = { objectMenuTarget = null },
                onReset = target.onReset,
                onRemove = {
                    objectMenuTarget = null
                    pendingObjectRemoval = target
                },
            )
        }

        pendingObjectRemoval?.let { target ->
            RemoveObjectDialog(
                title = "Remove ${target.label}?",
                body = "This card will be removed from your Home Screen. You can add it again later.",
                reduceMotion = reduceMotion,
                onCancel = { pendingObjectRemoval = null },
                onConfirm = {
                    target.onRemove()
                    pendingObjectRemoval = null
                },
            )
        }

        if (contextMenuApp != null) {
            AppContextMenu(
                app = contextMenuApp!!,
                viewModel = viewModel,
                onDismiss = { interactionState = LauncherInteractionState.Browsing }
            )
        }

        browsingCustomCategory?.let { categoryName ->
            val categoryApps = viewModel.byCustomCategory(categoryName)
            AlertDialog(
                onDismissRequest = { browsingCustomCategory = null },
                containerColor = CiyatoBgEl,
                title = { Text(categoryName, color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (categoryApps.isEmpty()) {
                            Text("No apps are in this collection yet.", color = CiyatoMuted, fontSize = 13.sp)
                        } else {
                            categoryApps.forEach { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CiyatoBgEl2)
                                        .clickable {
                                            browsingCustomCategory = null
                                            viewModel.launchApp(app)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RealAppIcon(
                                        app.icon,
                                        size = 38.dp,
                                        cornerRadius = 10.dp,
                                        scale = app.iconScale,
                                        rotation = app.iconRotation,
                                        accentHex = app.iconAccent,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(app.packageName, color = CiyatoMuted, maxLines = 1, fontSize = 10.sp)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open ${app.label}", tint = CiyatoSec, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            openCategoryEditor(categoryName)
                            categoryAppPickerSelection = emptySet()
                            categoryAppPickerQuery = ""
                            showCategoryAppPicker = true
                            browsingCustomCategory = null
                        }) {
                            Text("+ Add Apps", color = CiyatoGold)
                        }
                        TextButton(onClick = { browsingCustomCategory = null }) { Text("Done", color = CiyatoSec) }
                    }
                },
            )
        }

        selectedCustomCategory?.let { categoryName ->
            val standardCategory = runCatching { AppCategory.valueOf(categoryName) }.getOrNull()
            val isCustomCategory = standardCategory == null && categoryName in customCatsList
            val categoryApps = standardCategory?.let(viewModel::byCategory)
                ?: viewModel.byCustomCategory(categoryName)
            AlertDialog(
                onDismissRequest = { interactionState = interactionState.afterBack() },
                containerColor = CiyatoBgEl,
                title = {
                    Text(
                        if (isCustomCategory) "Edit ${categoryEditorNameDraft.ifBlank { categoryName }}"
                        else "Edit ${standardCategory?.let(viewModel::getCategoryDisplayName) ?: categoryName}",
                        color = CiyatoWhite,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isCustomCategory) {
                            OutlinedTextField(
                                value = categoryEditorNameDraft,
                                onValueChange = { categoryEditorNameDraft = it.take(24) },
                                label = { Text("Collection name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Collection icon", color = CiyatoSec, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "folder" to Icons.Default.Folder,
                                    "bookmark" to Icons.Default.Bookmark,
                                    "star" to Icons.Default.Star,
                                ).forEach { (key, icon) ->
                                    IconButton(
                                        onClick = { categoryEditorIconDraft = key },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (categoryEditorIconDraft == key) CiyatoGold.copy(alpha = 0.16f) else CiyatoBgEl2)
                                            .border(1.dp, if (categoryEditorIconDraft == key) CiyatoGold else CiyatoSubtleBorder, CircleShape),
                                    ) {
                                        Icon(icon, contentDescription = key, tint = if (categoryEditorIconDraft == key) CiyatoGold else CiyatoSec)
                                    }
                                }
                            }
                            Text("Presentation", color = CiyatoSec, fontSize = 12.sp)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf(CustomCategoryPresentation.GROUP, CustomCategoryPresentation.CARD).forEachIndexed { index, presentation ->
                                    SegmentedButton(
                                        selected = categoryEditorPresentationDraft == presentation.name,
                                        onClick = { categoryEditorPresentationDraft = presentation.name },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                        label = { Text(if (presentation == CustomCategoryPresentation.GROUP) "Group" else "Card") },
                                    )
                                }
                            }
                            Text(
                                if (categoryEditorPresentationDraft == CustomCategoryPresentation.GROUP.name) {
                                    "Group keeps a compact set of individual shortcuts."
                                } else {
                                    "Card keeps a resizable visual preview on the workspace."
                                },
                                color = CiyatoMuted,
                                fontSize = 11.sp,
                            )
                        } else {
                            Text(
                                "This is a smart category. You can place or remove it from a workspace, but its classifier name remains stable.",
                                color = CiyatoSec,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Apps (${categoryApps.size})", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                            if (isCustomCategory) {
                                TextButton(onClick = {
                                    categoryAppPickerQuery = ""
                                    categoryAppPickerSelection = emptySet()
                                    showCategoryAppPicker = true
                                }) { Text("Add apps", color = CiyatoGold) }
                            }
                        }
                        if (categoryApps.isEmpty()) {
                            Text(
                                if (isCustomCategory) "Add installed apps to build this collection."
                                else "No installed apps currently match this category.",
                                color = CiyatoMuted,
                                fontSize = 13.sp,
                            )
                        } else {
                            categoryApps.forEach { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(CiyatoBgEl2)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    RealAppIcon(app.icon, size = 38.dp, cornerRadius = 10.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, color = CiyatoWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(app.packageName, color = CiyatoMuted, fontSize = 10.sp, maxLines = 1)
                                    }
                                    if (isCustomCategory) {
                                        IconButton(onClick = {
                                            val undoSnapshot = currentLayoutSnapshot()
                                            viewModel.setAppCustomCategoryOverride(app.packageName, null)
                                            offerLayoutUndo("App removed from collection", undoSnapshot)
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove ${app.label} from this collection", tint = CiyatoSec)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = CiyatoSubtleBorder)
                        TextButton(
                            onClick = { categoryMoveTarget = categoryName },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = CiyatoGold)
                            Spacer(Modifier.width(8.dp))
                            Text("Move to workspace", color = CiyatoGold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                        if (isCustomCategory && customCatsList.size > 1) {
                            TextButton(
                                onClick = { categoryMergeSource = categoryName },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, tint = CiyatoSec)
                                Spacer(Modifier.width(8.dp))
                                Text("Merge into another collection", color = CiyatoSec, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            }
                        }
                        TextButton(
                            onClick = {
                                val undoSnapshot = currentLayoutSnapshot()
                                viewModel.removeCategoryFromHome(categoryName)
                                offerLayoutUndo("Category removed from Home", undoSnapshot)
                                interactionState = interactionState.afterBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = CiyatoSec)
                            Spacer(Modifier.width(8.dp))
                            Text("Remove from Home", color = CiyatoSec, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val undoSnapshot = currentLayoutSnapshot()
                        val updatedName = categoryEditorNameDraft.trim().take(24)
                        if (isCustomCategory && updatedName.isNotBlank()) {
                            val presentation = runCatching {
                                CustomCategoryPresentation.valueOf(categoryEditorPresentationDraft)
                            }.getOrDefault(CustomCategoryPresentation.GROUP)
                            viewModel.renameCustomCategory(
                                currentName = categoryName,
                                requestedName = updatedName,
                                icon = categoryEditorIconDraft,
                                presentation = presentation,
                            )
                            if (updatedName != categoryName) {
                            val editor = interactionState as? LauncherInteractionState.CategoryEditor
                            if (editor != null) {
                                interactionState = editor.copy(categoryKey = updatedName)
                            }
                            }
                        }
                        if (isCustomCategory) offerLayoutUndo("Collection updated", undoSnapshot)
                        interactionState = interactionState.afterBack()
                    }) {
                        Text(if (isCustomCategory) "Save" else "Done", color = CiyatoGold)
                    }
                },
                dismissButton = {
                    if (isCustomCategory) {
                        TextButton(onClick = { requestCategoryRemoval(categoryName, isCustom = true) }) {
                            Text("Delete", color = CiyatoRed)
                        }
                    }
                }
            )
        }

        if (showCategoryAppPicker) {
            val allInstalledApps by viewModel.allApps.collectAsState()
            val categoryName = selectedCustomCategory
            val selected = categoryName?.let(viewModel::byCustomCategory).orEmpty().mapTo(mutableSetOf()) { it.packageName }
            val matches = remember(allInstalledApps, categoryAppPickerQuery, selected) {
                val available = allInstalledApps.filter { it.packageName !in selected }
                SearchRankingEngine.rankAppsByLabel(available, categoryAppPickerQuery)
            }
            AlertDialog(
                onDismissRequest = { showCategoryAppPicker = false },
                containerColor = CiyatoBgEl,
                title = { Text("Add apps to ${categoryName.orEmpty()}", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = categoryAppPickerQuery,
                            onValueChange = { categoryAppPickerQuery = it },
                            label = { Text("Search installed apps") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (matches.isEmpty()) Text("No additional installed apps match.", color = CiyatoMuted, fontSize = 13.sp)
                        matches.forEach { app ->
                            key(app.packageName) {
                                val checked = app.packageName in categoryAppPickerSelection
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        categoryAppPickerSelection = if (checked) categoryAppPickerSelection - app.packageName else categoryAppPickerSelection + app.packageName
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RealAppIcon(app.icon, size = 34.dp, cornerRadius = 9.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                                    Text(app.label, color = CiyatoWhite, modifier = Modifier.weight(1f))
                                    Checkbox(checked = checked, onCheckedChange = {
                                        categoryAppPickerSelection = if (it) categoryAppPickerSelection + app.packageName else categoryAppPickerSelection - app.packageName
                                    })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val undoSnapshot = currentLayoutSnapshot()
                            categoryName?.let { name ->
                                categoryAppPickerSelection.forEach { packageName ->
                                    viewModel.setAppCustomCategoryOverride(packageName, name)
                                }
                            }
                            if (categoryAppPickerSelection.isNotEmpty()) {
                                offerLayoutUndo("Apps added to collection", undoSnapshot)
                            }
                            categoryAppPickerSelection = emptySet()
                            categoryAppPickerQuery = ""
                            showCategoryAppPicker = false
                        },
                        enabled = categoryName != null && categoryAppPickerSelection.isNotEmpty(),
                    ) { Text("Add ${categoryAppPickerSelection.size}", color = CiyatoGold) }
                },
                dismissButton = { TextButton(onClick = { showCategoryAppPicker = false }) { Text("Cancel", color = CiyatoSec) } },
            )
        }

        categoryMoveTarget?.let { categoryName ->
            AlertDialog(
                onDismissRequest = { categoryMoveTarget = null },
                containerColor = CiyatoBgEl,
                title = { Text("Move $categoryName", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        workspaceOverview.forEachIndexed { visualIndex, workspace ->
                            val pageIndex = workspacePagerPage(visualIndex)
                            TextButton(
                                onClick = {
                                    val undoSnapshot = currentLayoutSnapshot()
                                    viewModel.moveCategoryToWorkspace(categoryName, pageIndex)
                                    offerLayoutUndo("Category moved", undoSnapshot)
                                    categoryMoveTarget = null
                                    workspaceScope.launch { pagerState.animateScrollToPage(pageIndex) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(workspace.name ?: "Workspace ${workspace.creationOrder}", color = CiyatoSec, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { categoryMoveTarget = null }) { Text("Cancel", color = CiyatoSec) } },
            )
        }

        categoryMergeSource?.let { sourceName ->
            val destinations = customCatsList.filterNot { it == sourceName }
            AlertDialog(
                onDismissRequest = { categoryMergeSource = null },
                containerColor = CiyatoBgEl,
                title = { Text("Merge $sourceName into", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        destinations.forEach { destinationName ->
                            TextButton(
                                onClick = {
                                    val undoSnapshot = currentLayoutSnapshot()
                                    viewModel.mergeCustomCategories(sourceName, destinationName)
                                    offerLayoutUndo("Collections merged", undoSnapshot)
                                    categoryMergeSource = null
                                    interactionState = interactionState.afterBack()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(destinationName, color = CiyatoSec, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { categoryMergeSource = null }) { Text("Cancel", color = CiyatoSec) } },
            )
        }

        categoryPendingDelete?.let { categoryName ->
            val removal = requireNotNull(pendingCategoryRemoval)
            val isWorkspaceRemoval = removal.workspaceIndex != null
            AlertDialog(
                onDismissRequest = { interactionState = interactionState.afterBack() },
                containerColor = CiyatoBgEl,
                title = {
                    Text(
                        if (isWorkspaceRemoval) "Remove category from workspace?" else "Delete category?",
                        color = CiyatoWhite,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        "$categoryName will be removed from this layout. Its apps stay installed and remain available in the App Library.",
                        color = CiyatoSec,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { interactionState = interactionState.afterBack() }) {
                        Text("Cancel", color = CiyatoSec)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val undoSnapshot = currentLayoutSnapshot()
                        when (val workspaceIndex = removal.workspaceIndex) {
                            null -> if (removal.isCustom) {
                                viewModel.removeCustomCategory(removal.categoryKey)
                            } else {
                                viewModel.removeCategoryFromHome(removal.categoryKey)
                            }
                            else -> viewModel.removeCategoryFromWorkspace(workspaceIndex, removal.categoryKey)
                        }
                        offerLayoutUndo(
                            if (isWorkspaceRemoval) "Category removed from workspace" else "Category removed",
                            undoSnapshot,
                        )
                        interactionState = when (val returnState = interactionState.afterBack()) {
                            is LauncherInteractionState.CategoryEditor -> returnState.afterBack()
                            else -> returnState
                        }
                    }) {
                        Text(if (isWorkspaceRemoval) "Remove" else "Delete", color = CiyatoRed)
                    }
                },
            )
        }

        pendingAppRemovalFromDock?.let { removal ->
            AlertDialog(
                onDismissRequest = { interactionState = interactionState.afterBack() },
                containerColor = CiyatoBgEl,
                title = {
                    Text("Remove ${removal.label} from Dock?", color = CiyatoWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "${removal.label} will be removed from the dock only. It stays installed and remains available in the App Library.",
                        color = CiyatoSec,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { interactionState = interactionState.afterBack() }) {
                        Text("Cancel", color = CiyatoSec)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val snapshot = currentLayoutSnapshot()
                        viewModel.unpinFromDock(removal.packageName)
                        offerLayoutUndo("Unpinned from dock", snapshot)
                        interactionState = interactionState.afterBack()
                    }) {
                        Text("Remove", color = CiyatoRed)
                    }
                },
            )
        }

        // Custom Category Dialog
        if (showCreateCategoryDialog) {
            val allInstalledApps by viewModel.allApps.collectAsState()
            val categoryMatches = remember(allInstalledApps, newCategoryAppQuery) {
                SearchRankingEngine.rankAppsByLabel(allInstalledApps, newCategoryAppQuery)
            }
            AlertDialog(
                onDismissRequest = { showCreateCategoryDialog = false },
                containerColor = CiyatoBgEl,
                title = { Text("New Custom Category", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it.take(24) },
                            singleLine = true,
                            label = { Text("Category name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Create as", color = CiyatoSec, fontSize = 12.sp)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(CustomCategoryPresentation.GROUP, CustomCategoryPresentation.CARD).forEachIndexed { index, presentation ->
                                SegmentedButton(
                                    selected = newCategoryPresentation == presentation,
                                    onClick = { newCategoryPresentation = presentation },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                    label = { Text(if (presentation == CustomCategoryPresentation.GROUP) "Group" else "Card") },
                                )
                            }
                        }
                        Text(
                            if (newCategoryPresentation == CustomCategoryPresentation.GROUP) {
                                "A compact collection of shortcuts."
                            } else {
                                "A resizable category card with an app preview."
                            },
                            color = CiyatoMuted,
                            fontSize = 11.sp,
                        )

                        Spacer(Modifier.height(4.dp))
                        Text("Select apps for category", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        OutlinedTextField(
                            value = newCategoryAppQuery,
                            onValueChange = { newCategoryAppQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("Search installed apps") },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (categoryMatches.isEmpty()) {
                                Text("No installed apps match.", color = CiyatoMuted, fontSize = 12.sp)
                            }
                            categoryMatches.forEach { app ->
                                val isChecked = app.packageName in newCategoryAppSelection
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            newCategoryAppSelection = if (isChecked) newCategoryAppSelection - app.packageName else newCategoryAppSelection + app.packageName
                                        }
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RealAppIcon(app.icon, size = 30.dp, cornerRadius = 8.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                                    Text(app.label, color = CiyatoWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            newCategoryAppSelection = if (checked) newCategoryAppSelection + app.packageName else newCategoryAppSelection - app.packageName
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = newCategoryName.trim()
                            if (name.isNotBlank()) {
                                val undoSnapshot = currentLayoutSnapshot()
                                viewModel.addCustomCategory(name, newCategoryPresentation)
                                if (newCategoryAppSelection.isNotEmpty()) {
                                    viewModel.setAppsForCustomCategory(name, newCategoryAppSelection)
                                }
                                workspaceForNewCategory?.let { workspaceIndex ->
                                    viewModel.addCategoryToWorkspace(workspaceIndex, name)
                                }
                                offerLayoutUndo("Category created", undoSnapshot)
                            }
                            showCreateCategoryDialog = false
                            newCategoryName = ""
                            newCategoryAppSelection = emptySet()
                            newCategoryAppQuery = ""
                            newCategoryPresentation = CustomCategoryPresentation.GROUP
                            workspaceForNewCategory = null
                        },
                        enabled = newCategoryName.isNotBlank()
                    ) {
                        Text("Create", color = CiyatoGold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreateCategoryDialog = false
                        newCategoryName = ""
                        newCategoryAppSelection = emptySet()
                        newCategoryAppQuery = ""
                        workspaceForNewCategory = null
                    }) {
                        Text("Cancel", color = CiyatoSec)
                    }
                }
            )
        }

        if (showHomeCategoryPicker) {
            val currentKeys = orderedCategories.toSet()
            val categoryChoices = (APPROVED_HOME_CATEGORIES.map { it.name } + customCatsList)
                .distinct()
                .filterNot { it in currentKeys }
            AlertDialog(
                onDismissRequest = { showHomeCategoryPicker = false },
                containerColor = CiyatoBgEl,
                title = { Text("Add category to Home", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (categoryChoices.isEmpty()) {
                            Text("Every available category is already on Home.", color = CiyatoMuted)
                        } else {
                            categoryChoices.forEach { categoryKey ->
                                val category = runCatching { AppCategory.valueOf(categoryKey) }.getOrNull()
                                TextButton(
                                    onClick = {
                                        viewModel.restoreCategoryToHome(categoryKey)
                                        showHomeCategoryPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(category?.let(viewModel::getCategoryDisplayName) ?: categoryKey, color = CiyatoWhite, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showHomeCategoryPicker = false
                        showCreateCategoryDialog = true
                    }) {
                        Text("New custom category", color = CiyatoGold)
                    }
                },
            )
        }

        if (showWorkspaceCategoryPicker) {
            LaunchedEffect(Unit) {
                showWorkspaceCategoryPicker = false
                workspaceForNewCategory = workspaceCategoryPickerIndex
                showCreateCategoryDialog = true
            }
        }

        // Custom Page App Picker
        if (showPageAppPicker) {
            val allInstalledApps by viewModel.allApps.collectAsState()
            val existingPackages = remember(pickerPageIndex, workspaceLayoutV2) {
                viewModel.cellAppsForPage(pickerPageIndex).values.map { it.packageName }.toSet()
            }
            val matchingApps = remember(allInstalledApps, existingPackages, pageAppPickerQuery) {
                val available = allInstalledApps.filterNot { it.packageName in existingPackages }
                SearchRankingEngine.rankAppsByLabel(available, pageAppPickerQuery)
            }
            AlertDialog(
                onDismissRequest = {
                    showPageAppPicker = false
                    pageAppPickerQuery = ""
                    pageAppPickerSelection = emptySet()
                },
                containerColor = CiyatoBgEl,
                title = { Text("Add shortcuts", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pageAppPickerQuery,
                            onValueChange = { pageAppPickerQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("Search installed apps") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (matchingApps.isEmpty()) {
                            Text("No installed apps match this search.", color = CiyatoMuted, fontSize = 13.sp)
                        }
                        matchingApps.forEach { app ->
                            key(app.packageName) {
                                val isSelected = app.packageName in pageAppPickerSelection
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pageAppPickerSelection = if (isSelected) {
                                                pageAppPickerSelection - app.packageName
                                            } else {
                                                pageAppPickerSelection + app.packageName
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RealAppIcon(app.icon, size = 36.dp, cornerRadius = 8.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                                    Text(app.label, color = CiyatoWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            pageAppPickerSelection = if (it) {
                                                pageAppPickerSelection + app.packageName
                                            } else {
                                                pageAppPickerSelection - app.packageName
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (pageAppPickerSelection.isNotEmpty()) {
                                val undoSnapshot = currentLayoutSnapshot()
                                // One atomic batch — the whole selection persists
                                // together, so nothing is lost to a write race.
                                viewModel.addAppsToPage(pickerPageIndex, pageAppPickerSelection)
                                offerLayoutUndo("Shortcuts added", undoSnapshot)
                            }
                            showPageAppPicker = false
                            pageAppPickerQuery = ""
                            pageAppPickerSelection = emptySet()
                        },
                        enabled = pageAppPickerSelection.isNotEmpty(),
                    ) { Text("Add ${pageAppPickerSelection.size}", color = CiyatoGold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPageAppPicker = false
                        pageAppPickerQuery = ""
                        pageAppPickerSelection = emptySet()
                    }) { Text("Cancel", color = CiyatoSec) }
                },
            )
        }

        pendingWorkspaceDeletion?.let { pageIndex ->
            val deletingVisualIndex = if (pageIndex == 0) 0 else pageIndex - 1
            val moveDestinations = workspaceOverview.mapIndexedNotNull { visualIndex, workspace ->
                if (visualIndex == deletingVisualIndex) null else workspacePagerPage(visualIndex) to workspace.name.orEmpty()
            }
            AlertDialog(
                onDismissRequest = {
                    pendingWorkspaceDeletion = null
                    pendingWorkspaceMoveDestination = null
                },
                containerColor = CiyatoBgEl,
                title = { Text("Delete ${viewModel.workspaceName(pageIndex)}", color = CiyatoWhite) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Applications stay installed. Choose whether Ciyato shortcuts move to another workspace or are removed from this launcher layout.",
                            color = CiyatoSec,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                        Text("Move shortcuts (optional)", color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(
                            onClick = { pendingWorkspaceMoveDestination = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Remove Ciyato shortcuts",
                                color = if (pendingWorkspaceMoveDestination == null) CiyatoGold else CiyatoSec,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        moveDestinations.forEach { (destinationPage, destinationName) ->
                            TextButton(
                                onClick = { pendingWorkspaceMoveDestination = destinationPage },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Move to ${destinationName.ifBlank { viewModel.workspaceName(destinationPage) }}",
                                    color = if (pendingWorkspaceMoveDestination == destinationPage) CiyatoGold else CiyatoSec,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        performLayoutChange("Workspace deleted") {
                            viewModel.removeWorkspace(pageIndex, pendingWorkspaceMoveDestination)
                        }
                        pendingWorkspaceDeletion = null
                        pendingWorkspaceMoveDestination = null
                    }) { Text("Delete workspace", color = CiyatoRed) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingWorkspaceDeletion = null
                        pendingWorkspaceMoveDestination = null
                    }) { Text("Cancel", color = CiyatoSec) }
                },
            )
        }

        if (showWorkspaceOverview) {
            WorkspaceOverviewDialog(
                workspaces = workspaceOverview,
                defaultWorkspace = { visualIndex -> viewModel.isDefaultWorkspace(workspacePagerPage(visualIndex)) },
                onDismiss = { showWorkspaceOverview = false },
                onOpen = { visualIndex ->
                    showWorkspaceOverview = false
                    workspaceScope.launch { pagerState.animateScrollToPage(workspacePagerPage(visualIndex)) }
                },
                onRename = { visualIndex ->
                    workspaceRenamePage = workspacePagerPage(visualIndex)
                    workspaceNameDraft = workspaceOverview.getOrNull(visualIndex)?.name.orEmpty()
                },
                onMove = { visualIndex, delta ->
                    performLayoutChange("Workspace reordered") {
                        viewModel.reorderWorkspace(visualIndex, visualIndex + delta)
                    }
                },
                onDuplicate = { visualIndex ->
                    performLayoutChange("Workspace duplicated") {
                        viewModel.duplicateWorkspace(workspacePagerPage(visualIndex))
                    }
                },
                onInsertAdjacent = { visualIndex ->
                    performLayoutChange("Workspace added") {
                        // Grow away from Home: the left workspace grows leftward,
                        // everything else grows rightward.
                        if (visualIndex == 0) viewModel.insertWorkspaceAt(0)
                        else viewModel.insertWorkspaceAt(visualIndex + 1)
                    }
                },
                onSetDefault = { visualIndex ->
                    performLayoutChange("Default workspace changed") {
                        viewModel.setDefaultWorkspace(workspacePagerPage(visualIndex))
                    }
                },
                onEditWallpaper = {
                    showWorkspaceOverview = false
                    cancelLauncherInteraction()
                    onOpenSystemWallpaper()
                },
                onDelete = { visualIndex ->
                    showWorkspaceOverview = false
                    pendingWorkspaceMoveDestination = null
                    pendingWorkspaceDeletion = workspacePagerPage(visualIndex)
                },
            )
        }

        workspaceRenamePage?.let { pageIndex ->
            AlertDialog(
                onDismissRequest = { workspaceRenamePage = null },
                containerColor = CiyatoBgEl,
                title = { Text("Rename workspace", color = CiyatoWhite) },
                text = {
                    OutlinedTextField(
                        value = workspaceNameDraft,
                        onValueChange = { workspaceNameDraft = it.take(40) },
                        singleLine = true,
                        label = { Text("Workspace name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            performLayoutChange("Workspace renamed") {
                                viewModel.renameWorkspace(pageIndex, workspaceNameDraft)
                            }
                            workspaceRenamePage = null
                        },
                        enabled = workspaceNameDraft.isNotBlank(),
                    ) { Text("Save", color = CiyatoGold) }
                },
                dismissButton = {
                    TextButton(onClick = { workspaceRenamePage = null }) { Text("Cancel", color = CiyatoSec) }
                },
            )
        }

        workspaceTemplatePage?.let { pageIndex ->
            AlertDialog(
                onDismissRequest = { workspaceTemplatePage = null },
                containerColor = CiyatoBgEl,
                title = { Text("Choose a workspace template", color = CiyatoWhite) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Templates add only category structure. They never pin apps automatically.",
                            color = CiyatoSec,
                            fontSize = 13.sp,
                        )
                        WORKSPACE_STARTER_TEMPLATES.forEach { template ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CiyatoBgEl2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        workspaceTemplatePage = null
                                        pendingWorkspaceTemplate = template
                                        pendingWorkspaceTemplatePage = pageIndex
                                    },
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(template.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                                    Text(template.description, color = CiyatoSec, fontSize = 12.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { workspaceTemplatePage = null }) { Text("Cancel", color = CiyatoSec) }
                },
            )
        }

        pendingWorkspaceTemplate?.let { template ->
            pendingWorkspaceTemplatePage?.let { pageIndex ->
                AlertDialog(
                    onDismissRequest = {
                        pendingWorkspaceTemplate = null
                        pendingWorkspaceTemplatePage = null
                    },
                    containerColor = CiyatoBgEl,
                    title = { Text("Apply ${template.title} template?", color = CiyatoWhite) },
                    text = {
                        Text(
                            "${template.description} You can remove or rearrange these categories later. No apps will be added automatically.",
                            color = CiyatoSec,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            performLayoutChange("Workspace template applied") {
                                viewModel.applyWorkspaceTemplate(pageIndex, template.categoryKeys)
                            }
                            pendingWorkspaceTemplate = null
                            pendingWorkspaceTemplatePage = null
                        }) { Text("Apply template", color = CiyatoGold) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            pendingWorkspaceTemplate = null
                            pendingWorkspaceTemplatePage = null
                        }) { Text("Cancel", color = CiyatoSec) }
                    },
                )
            }
        }

        if (showLauncherControls) {
            LauncherControlSheet(
                isEditMode = isEditMode,
                showGreeting = showHomeGreeting,
                // Date/time is the one canvas object with no global setting of its
                // own — its visibility lives in the workspace's hiddenObjects set,
                // so removing it needs a matching re-add path here or it's gone for good.
                showDateTime = "datetime" !in homeHiddenObjects,
                onShowDateTimeChanged = { visible ->
                    if (visible) viewModel.showObjectOnPage(1, "datetime")
                    else viewModel.hideObjectOnPage(1, "datetime")
                },
                showSearch = showHomeSearch,
                showWeather = showHomeWeather,
                showAgenda = showHomeAgenda,
                showRecent = showRecentLaunched,
                showCategories = showSmartCategories,
                showDock = showHomeDock,
                workspaceTransition = workspaceTransition,
                onDismiss = { enterLayoutEditing(showControls = false) },
                onEditLayout = {
                    enterLayoutEditing(showControls = false)
                },
                onAddToHome = {
                    enterLayoutEditing(showControls = false)
                    showHomeCategoryPicker = true
                },
                onOpenWallpaper = {
                    cancelLauncherInteraction()
                    onOpenSystemWallpaper()
                },
                onOpenSettings = {
                    cancelLauncherInteraction()
                    onOpenOrganizerSettings()
                },
                onOpenWorkspaces = {
                    enterLayoutEditing(showControls = false)
                    showWorkspaceOverview = true
                },
                onManageDock = {
                    enterLayoutEditing(showControls = false)
                    showDockManager = true
                },
                onShowGreetingChanged = viewModel::setShowHomeGreeting,
                onShowSearchChanged = viewModel::setShowHomeSearch,
                onShowWeatherChanged = viewModel::setShowHomeWeather,
                onShowAgendaChanged = viewModel::setShowHomeAgenda,
                onShowRecentChanged = viewModel::setShowRecentlyLaunched,
                onShowCategoriesChanged = viewModel::setSmartCategories,
                onShowDockChanged = viewModel::setShowHomeDock,
                onTransitionChanged = viewModel::setWorkspaceTransition,
            )
        }

        if (showDockManager) {
            DockManagerDialog(
                dockApps = dockApps,
                availableApps = apps.filterNot { app -> dockApps.any { it.packageName == app.packageName } },
                onDismiss = { showDockManager = false },
                onMove = viewModel::moveDockShortcut,
                onRemove = viewModel::unpinFromDock,
                onAdd = viewModel::pinToDock,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherControlSheet(
    isEditMode: Boolean,
    showGreeting: Boolean,
    showDateTime: Boolean,
    onShowDateTimeChanged: (Boolean) -> Unit,
    showSearch: Boolean,
    showWeather: Boolean,
    showAgenda: Boolean,
    showRecent: Boolean,
    showCategories: Boolean,
    showDock: Boolean,
    workspaceTransition: String,
    onDismiss: () -> Unit,
    onEditLayout: () -> Unit,
    onAddToHome: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onManageDock: () -> Unit,
    onShowGreetingChanged: (Boolean) -> Unit,
    onShowSearchChanged: (Boolean) -> Unit,
    onShowWeatherChanged: (Boolean) -> Unit,
    onShowAgendaChanged: (Boolean) -> Unit,
    onShowRecentChanged: (Boolean) -> Unit,
    onShowCategoriesChanged: (Boolean) -> Unit,
    onShowDockChanged: (Boolean) -> Unit,
    onTransitionChanged: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CiyatoBgEl,
        contentColor = CiyatoWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Home controls", color = CiyatoWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Changes are saved to this launcher. System wallpaper is managed by Android.", color = CiyatoMuted, fontSize = 12.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HomeControlAction(
                    icon = Icons.Default.Add,
                    label = "Add",
                    onClick = onAddToHome,
                    modifier = Modifier.weight(1f),
                )
                HomeControlAction(
                    icon = Icons.Default.Edit,
                    label = if (isEditMode) "Layout" else "Arrange",
                    onClick = onEditLayout,
                    modifier = Modifier.weight(1f),
                )
                HomeControlAction(
                    icon = Icons.Default.Wallpaper,
                    label = "Wallpaper",
                    onClick = onOpenWallpaper,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HomeControlAction(
                    icon = Icons.Default.ViewCarousel,
                    label = "Workspaces",
                    onClick = onOpenWorkspaces,
                    modifier = Modifier.weight(1f),
                )
                HomeControlAction(
                    icon = Icons.Default.Settings,
                    label = "Home settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
            }

            Text("Home sections", color = CiyatoSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            HomeControlToggle("Greeting", showGreeting, onShowGreetingChanged)
            HomeControlToggle("Date and time", showDateTime, onShowDateTimeChanged)
            HomeControlToggle("Search", showSearch, onShowSearchChanged)
            HomeControlToggle("Weather", showWeather, onShowWeatherChanged)
            HomeControlToggle("Agenda", showAgenda, onShowAgendaChanged)
            HomeControlToggle("Recently used", showRecent, onShowRecentChanged)
            HomeControlToggle("Categories", showCategories, onShowCategoriesChanged)
            HomeControlToggle("Dock", showDock, onShowDockChanged)
            TextButton(onClick = onManageDock, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Dock, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Manage dock", color = CiyatoGold)
            }

            Text("Workspace transition", color = CiyatoSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "slide" to "Slide", "fade" to "Fade", "scale" to "Scale",
                    "depth" to "Depth", "flip" to "Flip", "none" to "None",
                ).forEach { (value, label) ->
                    val selected = workspaceTransition == value
                    TextButton(
                        onClick = { onTransitionChanged(value) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) CiyatoGold.copy(alpha = 0.16f) else CiyatoBgEl2),
                    ) {
                        Text(label, color = if (selected) CiyatoGold else CiyatoSec, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockManagerDialog(
    dockApps: List<InstalledApp>,
    availableApps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shownApps = remember(availableApps, query) {
        availableApps
            .asSequence()
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .take(30)
            .toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        title = { Text("Dock", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Up to five app shortcuts. Changes are saved to this launcher.", color = CiyatoMuted, fontSize = 12.sp)
                dockApps.forEachIndexed { index, app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CiyatoBgEl2)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RealAppIcon(drawable = app.icon, size = 36.dp, cornerRadius = 10.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                        Spacer(Modifier.width(10.dp))
                        Text(app.label, color = CiyatoWhite, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onMove(app.packageName, -1) }, enabled = index > 0) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move ${app.label} left", tint = if (index > 0) CiyatoSec else CiyatoMuted)
                        }
                        IconButton(onClick = { onMove(app.packageName, 1) }, enabled = index < dockApps.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move ${app.label} right", tint = if (index < dockApps.lastIndex) CiyatoSec else CiyatoMuted)
                        }
                        IconButton(onClick = { onRemove(app.packageName) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${app.label}", tint = CiyatoSec)
                        }
                    }
                }
                if (dockApps.size < 5) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("Add an installed app") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CiyatoWhite,
                            unfocusedTextColor = CiyatoWhite,
                            focusedBorderColor = CiyatoGold,
                            unfocusedBorderColor = CiyatoSubtleBorder,
                            focusedLabelColor = CiyatoGold,
                            unfocusedLabelColor = CiyatoMuted,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(shownApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(app.packageName) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RealAppIcon(drawable = app.icon, size = 32.dp, cornerRadius = 9.dp, scale = app.iconScale, rotation = app.iconRotation, accentHex = app.iconAccent)
                                Spacer(Modifier.width(10.dp))
                                Text(app.label, color = CiyatoWhite, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.Add, contentDescription = "Add ${app.label}", tint = CiyatoGold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = CiyatoGold) } },
    )
}

@Composable
private fun WorkspaceOverviewDialog(
    workspaces: List<WorkspaceRecord>,
    defaultWorkspace: (Int) -> Boolean,
    onDismiss: () -> Unit,
    onOpen: (Int) -> Unit,
    onRename: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onInsertAdjacent: (Int) -> Unit,
    onSetDefault: (Int) -> Unit,
    onEditWallpaper: () -> Unit,
    onDelete: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        title = { Text("Workspaces", color = CiyatoWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Reorder, name and manage each saved workspace. Wallpaper applies across workspaces unless you choose a different source in Wallpaper Studio.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                TextButton(onClick = onEditWallpaper, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Wallpaper Studio", color = CiyatoGold)
                }
                workspaces.forEachIndexed { visualIndex, workspace ->
                    val isDefault = defaultWorkspace(visualIndex)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CiyatoBgEl2,
                        border = BorderStroke(1.dp, if (isDefault) CiyatoGold.copy(alpha = 0.45f) else CiyatoSubtleBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        workspace.name ?: "Workspace ${workspace.creationOrder}",
                                        color = CiyatoWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Workspace ${workspace.creationOrder} · ${workspace.appPackages.size} shortcuts · ${workspace.categoryKeys.size} categories",
                                        color = CiyatoMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isDefault) {
                                    Text(
                                        "Default",
                                        color = CiyatoGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            WorkspaceLayoutPreview(workspace = workspace)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onOpen(visualIndex) }) { Text("Open", color = CiyatoGold) }
                                TextButton(onClick = { onRename(visualIndex) }) { Text("Rename", color = CiyatoSec) }
                                if (!isDefault) {
                                    TextButton(onClick = { onSetDefault(visualIndex) }) { Text("Default", color = CiyatoSec) }
                                }
                                IconButton(
                                    onClick = { onMove(visualIndex, -1) },
                                    enabled = visualIndex > 0,
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move workspace left", tint = CiyatoSec)
                                }
                                IconButton(
                                    onClick = { onMove(visualIndex, 1) },
                                    enabled = visualIndex < workspaces.lastIndex,
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move workspace right", tint = CiyatoSec)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (workspaces.size < 10) {
                                    TextButton(onClick = { onInsertAdjacent(visualIndex) }) { Text("+ Workspace", color = CiyatoSec) }
                                    TextButton(onClick = { onDuplicate(visualIndex) }) { Text("Duplicate", color = CiyatoSec) }
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { onDelete(visualIndex) },
                                    enabled = workspaces.size > 1,
                                ) { Text("Delete", color = CiyatoRed) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = CiyatoGold) }
        },
    )
}

@Composable
private fun WorkspaceLayoutPreview(workspace: WorkspaceRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(CiyatoBg.copy(alpha = 0.72f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(workspace.categoryKeys.take(3).size.coerceAtLeast(1)) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (index == 0) CiyatoGold.copy(alpha = 0.34f) else CiyatoSec.copy(alpha = 0.18f)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(workspace.appPackages.take(5).size.coerceAtLeast(1)) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index % 2 == 0) CiyatoSec.copy(alpha = 0.18f) else CiyatoBlue.copy(alpha = 0.23f)),
                )
            }
        }
        Text(
            if (workspace.appPackages.isEmpty() && workspace.categoryKeys.isEmpty()) {
                "New workspace starter is ready"
            } else {
                "Layout summary based on saved shortcuts and categories"
            },
            color = CiyatoMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun WorkspaceStarterCard(
    onAddShortcut: () -> Unit,
    onAddCategory: () -> Unit,
    onChooseTemplate: () -> Unit,
    onStartClean: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CiyatoBgEl.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, CiyatoSubtleBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.SpaceDashboard, contentDescription = null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
            Text("Make this workspace yours", color = CiyatoWhite, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Start with a few shortcuts, add a category, preview a light template, or keep the space clean. Ciyato never fills it with apps on your behalf.",
                color = CiyatoSec,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onAddShortcut,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(CiyatoGold.copy(alpha = 0.14f)),
                ) { Text("Add app", color = CiyatoGold) }
                TextButton(
                    onClick = onAddCategory,
                    modifier = Modifier.weight(1f),
                ) { Text("Add category", color = CiyatoSec) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onChooseTemplate, modifier = Modifier.weight(1f)) {
                    Text("Preview template", color = CiyatoSec)
                }
                TextButton(onClick = onStartClean, modifier = Modifier.weight(1f)) {
                    Text("Start clean", color = CiyatoSec)
                }
            }
        }
    }
}

@Composable
private fun HomeControlAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CiyatoBgEl2)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = CiyatoSec, modifier = Modifier.size(20.dp))
        Text(label, color = CiyatoWhite, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun HomeControlToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = CiyatoWhite, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HomeSectionRemoveButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(CiyatoBg.copy(alpha = 0.84f)),
    ) {
        Icon(Icons.Default.Close, contentDescription = "Remove from Home", tint = CiyatoRed, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CiyatoVideoBackground(uri: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    var deviceInteractive by remember(powerManager) { mutableStateOf(powerManager?.isInteractive ?: true) }
    val canPlay = powerManager?.isPowerSaveMode != true && deviceInteractive
    val latestCanPlay by rememberUpdatedState(canPlay)

    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: android.content.Context?, intent: android.content.Intent?) {
                deviceInteractive = powerManager?.isInteractive ?: true
            }
        }
        val filter = IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // TextureView (not VideoView/SurfaceView): SurfaceView punches a hole in
    // the Compose layer stack and frequently shows black instead of the clip.
    var mediaPlayer by remember(uri) { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(canPlay, mediaPlayer) {
        val player = mediaPlayer ?: return@LaunchedEffect
        runCatching { if (canPlay) player.start() else player.pause() }
    }

    AndroidView(
        factory = { viewContext ->
            android.view.TextureView(viewContext).also { texture ->
                texture.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    // A MediaPlayer that hits MEDIA_ERROR_* moves into a permanently
                    // broken Error state — start()/pause() become silent no-ops from
                    // then on. Without a rebuild-and-retry here, one transient decode
                    // hiccup (common under memory pressure) leaves the background
                    // blank forever, which is exactly what "the video disappears
                    // after a while" describes. Bounded so a genuinely corrupt file
                    // gives up instead of retry-looping forever.
                    var retriesLeft = 2

                    fun startPlayback(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        val player = android.media.MediaPlayer()
                        runCatching {
                            player.setSurface(android.view.Surface(surfaceTexture))
                            player.setDataSource(viewContext, Uri.parse(uri))
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            player.setOnPreparedListener { prepared ->
                                // Center-crop the video into the screen.
                                val videoWidth = prepared.videoWidth.toFloat()
                                val videoHeight = prepared.videoHeight.toFloat()
                                if (videoWidth > 0f && videoHeight > 0f) {
                                    val scale = maxOf(width / videoWidth, height / videoHeight)
                                    val matrix = android.graphics.Matrix()
                                    matrix.setScale(
                                        videoWidth * scale / width,
                                        videoHeight * scale / height,
                                        width / 2f,
                                        height / 2f,
                                    )
                                    texture.setTransform(matrix)
                                }
                                if (latestCanPlay) prepared.start()
                            }
                            player.setOnErrorListener { broken, _, _ ->
                                runCatching { broken.release() }
                                if (mediaPlayer === broken) mediaPlayer = null
                                if (retriesLeft > 0 && texture.isAvailable) {
                                    retriesLeft--
                                    startPlayback(surfaceTexture, width, height)
                                }
                                true
                            }
                            player.prepareAsync()
                            mediaPlayer = player
                        }.onFailure { player.release() }
                    }

                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        retriesLeft = 2
                        startPlayback(surfaceTexture, width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(surfaceTexture: android.graphics.SurfaceTexture): Boolean {
                        mediaPlayer?.let { runCatching { it.stop(); it.release() } }
                        mediaPlayer = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) = Unit
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner, uri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (latestCanPlay) runCatching { mediaPlayer?.start() }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> runCatching { mediaPlayer?.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaPlayer?.let { runCatching { it.stop(); it.release() } }
            mediaPlayer = null
        }
    }
}

@Composable
private fun CiyatoImageBackground(
    uri: String,
    scale: Float,
    verticalOffset: Float,
    blurRadius: Int,
) {
    AsyncImage(
        model = Uri.parse(uri),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = size.height * verticalOffset * 0.18f
            }
            .blur(blurRadius.dp),
    )
}

private fun currentTimeString(): String =
    SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())

/**
 * Bottom workspace map. One dot per page; Home is a small house so the person
 * always knows their position without swiping around. Dots are tappable.
 */
@Composable
private fun WorkspacePageIndicator(
    pageCount: Int,
    currentPage: Int,
    homePage: Int,
    onDotTap: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        repeat(pageCount) { page ->
            val isCurrent = page == currentPage
            if (page == homePage) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = if (isCurrent) "Home, current page" else "Go to Home",
                    tint = if (isCurrent) CiyatoGold else CiyatoWhite.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDotTap(page) },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent) CiyatoGold else CiyatoWhite.copy(alpha = 0.4f))
                        .clickable { onDotTap(page) },
                )
            }
        }
    }
}

@Composable
private fun HomeSearchBar(isDense: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier.height(if (isDense) 50.dp else 56.dp)
        .clip(RoundedCornerShape(999.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(999.dp))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart) {
        Row(modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Search, null, tint = CiyatoMuted, modifier = Modifier.size(18.dp))
            Text("Search apps...", color = CiyatoMuted, fontSize = if (isDense) 14.sp else 15.sp)
        }
    }
}

@Composable
private fun FocusBadge(session: FocusSessionManager.FocusSession, reduceMotion: Boolean) {
    val pulse = if (reduceMotion) {
        1f
    } else {
        val animatedPulse by rememberInfiniteTransition(label = "focus_pulse").animateFloat(
            initialValue = 1f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        )
        animatedPulse
    }
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.scale(pulse).clip(RoundedCornerShape(10.dp))
            .background(CiyatoGold.copy(0.15f)).border(1.dp, CiyatoGold.copy(0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, null, tint = CiyatoGold, modifier = Modifier.size(14.dp))
            Text("%02d:%02d".format(session.remainingMin, session.remainingSec),
                color = CiyatoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
