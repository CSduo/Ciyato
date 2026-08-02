# Ciyato V2 Implementation Audit

Specification reviewed: `Ciyato_Comprehensive_Implementation_Specification_Dark_Expanded.pdf`, pages 1-49.

## Scope Rule

The 49-page V2 specification is the only active product specification. Earlier recovery
notes, promotional mockups, and previous change manuals are not implementation inputs.
Where existing code conflicts with the V2 document, V2 wins. Page-by-page status and
verification evidence live in `V2_TRACEABILITY.md`.

## Active Architecture

- `LauncherHomeActivity` is the Android HOME activity. It hosts Home, the App Library, global launcher search, weather, agenda and launcher settings using local sealed-state navigation.
- `MainActivity` is the internal Ciyato application. It now exposes exactly Home, Files, Search, Photos and Settings as its bottom navigation destinations.
- `LauncherViewModel` owns application inventory, category view state, launcher preferences, weather state and persistent layout helpers.
- `LauncherRepository` scans launchable activities through `PackageManager`, applies user overrides and launches applications.
- `LauncherSettingsRepository` is the current persistence layer. It uses DataStore preferences, not Room.
- Files use user-selected SAF roots and `DocumentFile`. Photos use Android Photo Picker selections. Weather uses foreground location and Open-Meteo. Revoked SAF access clears the internal file index and produces an explicit reselect-folder state rather than serving cached results.

## V2 Migration Completed In This Slice

- Removed the active `Shared` destination and deleted its unused presentation screens.
- Replaced the rejected dashboard quick actions, privacy summary and feature list with a factual internal Home based on local state.
- Kept global launcher search and internal file/media search as separate active routes.
- Replaced the active stacked/filter-chip App Library with compact group clusters and one expanded group surface.
- Removed the permanent App Library button from Home; the existing intentional upward gesture remains the entry point.
- Restricted automatic Home Smart Categories to Work, Social, Finance, Creativity, Utilities and Daily. Other classifications remain available to the App Library and manual workspaces.
- Restored system wallpaper as the default and removed the decorative continuous Home background animation.

## Data And Migration Notes

- Existing `dashboard` deep links map to internal `home`; legacy `shared` deep links map to `photos`.
- Existing DataStore preferences are preserved. No destructive preference migration was performed.
- Existing manual category, app, dock, workspace and appearance preferences remain intact.
- Workspace persistence is a versioned `WorkspaceLayout` JSON record in DataStore with stable IDs, immutable creation order, independent visual order, a default workspace, and legacy migration. Custom categories now persist an explicit `GROUP` or `CARD` presentation choice; legacy collections safely become Groups. Device migration evidence and full valid-grid behavior remain required before the workspace engine can be accepted.

## Dependency Audit

- Active and justified: Compose, Navigation, DataStore, DocumentFile, OkHttp, Coil, lifecycle and coroutines.
- WorkManager is active for bounded file-cleanup hashing. Room and Paging remain declared but are not active in the released data path; the current bounded file index, search history and workspace model remain DataStore-backed and need a scale/performance decision before release.
- Legacy/experimental presentation candidates remain in source and must be removed only after their dependencies and navigation references are mapped: `DuplicateShortcutStrip`, `SwipeableHomeDrawer`, `MultiPageHomeScreen`, old widget experiments and many disconnected feature screens.

## Permission And Policy Audit

- `QUERY_ALL_PACKAGES` is required for a general Android launcher but needs a matching Play Console declaration.
- Location is foreground-only and requests fine plus coarse together. Android can still grant approximate access.
- Calendar permission is declared but must have a contextual connection flow and a real event source before Today is production-ready.
- Photo Picker avoids broad photo access. SAF access is user-selected and persisted.
- There is no background location or all-files declaration. Cleartext network traffic is disabled, and active file viewers receive only read grants for the selected URI.
- The build currently targets API 34. Google Play requires API 35 for a new mobile submission today; a complete API 35/36 base platform must be installed and the behavior change physically tested before a Play submission.

## Remaining V2 Work

1. Validate the implemented valid-grid reorder and hover-armed cross-workspace movement on physical devices, then finish deliberate group-creation hover behavior and any remaining card-resize reflow rules.
2. Add denial, revocation, cancellation and deletion-result coverage to the SAF Files, cleanup and internal-search flows.
3. Finish Wallpaper Studio's optional native crop, contrast and per-workspace scope features, then validate image and video lifecycle behavior on a physical device.
4. Finish formal behavioral coverage for every Settings control, including the persisted appearance, privacy and crash-reporting effects already wired into the current UI.
5. Extend classifier discovery for managed work profiles and incremental package changes, then validate correction persistence on a physical device.
6. Install a complete Android API 35/36 base SDK, update the target API, and run the behavior compatibility pass.
7. Add accessibility and migration tests, then validate gestures, permission paths, wallpaper behavior and layout on physical Android devices.

## Verification In This Slice

- `assembleDebug`: passed without an emulator.
- `testDebugUnitTest`: passed without an emulator (34 JVM tests, zero failures).
- `git diff --check`: passed.
- Current debug artifact: `Ciyato.apk`, 24,510,862 bytes, SHA-256 `3429FA733DA2F01AF79AE16369BE6270725EA7F467CF95601B15AE51FE99BA2F`.
- Physical-device validation remains required before any completion claim.
