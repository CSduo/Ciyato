# Grid Launcher Rework — Implementation Plan (from design panel wf_229731b3-2dc)

Solves 4 user issues (added apps vanish; dock rigid/one-way; apps trapped in one
workspace; no free grid positioning) as ONE positioned-grid + universal-drag rework.

## Step order (each independently buildable; 1-3 invisible/low-risk, 4-6 flip UI)

**STEP 1 — Vanish fix (atomic writes).** Root cause: every layout mutator is its
own `viewModelScope.launch` doing read-modify-write of the whole layout; concurrent
adds all read the same base and last-writer-wins clobbers the rest (multi-add
fan-out + cold-start migration TOCTOU). Fix: `layoutMutex: Mutex` + `updateLayout{
transform }` single writer in LauncherViewModel; route EVERY mutator through it; add
atomic `addAppsToPage(pageIndex, packages)`; HomeScreen confirm calls it once instead
of a forEach; wrap `ensureWorkspaceLayoutMigration` under the lock with re-check;
delete dead `layoutEditSnapshot`.

**STEP 2 — Dock <5 + no auto-repopulate.** `KEY_DOCK_INITIALIZED` bool distinguishes
"never seeded" from "intentionally emptied"; gate `ensureDefaultDock` on it; remove the
`defaultDockApps()` fallback; render dock when `isNotEmpty() || isEditMode`.

**STEP 3 — Positioned model (WorkspaceStore v2 superset).** `AppCell(packageName, cell)`;
`WorkspaceRecord.cells: List<AppCell>` is source of truth, `appPackages` becomes a
computed getter (sortedBy cell) so all readers keep compiling; `WorkspaceLayout.authorColumns`
(default 4) + optional `homeRecord`; `CURRENT_VERSION=2`; parse accepts v1..2 and upgrades
v1 by `mapIndexed{i,pkg->AppCell(pkg,i)}` (reproduces chunked(4) exactly — NO layout loss);
pure fns firstFreeCell/placeApp(swap on occupied)/removeApp/moveApp/addAppsAtFreeCells/reflow.

**STEP 4 — Grid dims + gap rendering.** `KEY_GRID_SIZE` "CxR" default "4x5"; VM
gridColumns/gridRows + setGridSize(reflow under updateLayout); new `WorkspaceGrid.kt`
custom non-lazy Column-of-Rows with REAL empty cells (drop targets, gaps); `cellAppsForPage`;
replace the `chunked(4)` block; same grid on Home page 1; 4x4/5x4/5x5/6x5 selector in Theme Studio.

**STEP 5 — Universal floating drag.** `LauncherDragController` hoisted at root +
`DragOverlay` drawn last (over pager+dock); DragOrigin {Workspace/Home/Dock}; single
zoneBounds registry (all cells incl. empty, dock slots, Home cells) in root coords;
pick-up on grid+dock icons; edge-hold page flip (reuse WORKSPACE_EDGE_HOVER_DELAY_MS);
`commitDrop(origin,target)` routes placeApp/moveApp/pinToDockAt+unpin through updateLayout;
<18.dp = open menu escape hatch; delete old per-tile drag + edge/nearest helpers.

**STEP 6 — Regression pass.** workspace add/remove/dup/reorder/rename via appPackages
accessor; Undo; browse tap launches / long-press menu; emptied dock persists at 0.

## Critical pitfalls
- parse MUST accept version 1..2 or every user's layout resets (fallback to legacy). Round-trip unit-check.
- appPackages computed getter → every `.copy(appPackages=...)` is a compile error (feature: surfaces write sites) → convert to cell helpers.
- Any mutator bypassing updateLayout races again — grep all persistWorkspaceLayout callers.
- Drag coords: standardize on boundsInRoot(); hide source tile (alpha 0); re-hit-test at drop.
- placeApp SWAP semantics on occupied cell; isValid rejects shared cells.
- reflow: rows are a MINIMUM (effectiveRows grows); never drop apps on shrink.
