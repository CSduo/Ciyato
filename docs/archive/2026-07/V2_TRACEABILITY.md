# Ciyato V2 Traceability Register

Active source of truth: `Ciyato_Comprehensive_Implementation_Specification_Dark_Expanded.pdf` (pages 1-49). This register deliberately excludes all older manuals and concepts. A status of `partial` means the code may contain a related surface, but it does not yet satisfy the V2 acceptance criteria.

| PDF page | V2 requirement | Current status | Verification required |
| --- | --- | --- | --- |
| 1 | V2 product, UX and engineering specification | active | Review against this register only |
| 2 | Use source hierarchy; reject defect and deprecated references | partial | Architecture and UI review |
| 3 | Cohesive dark design system, semantic type, spacing and surfaces | partial | Large-font and visual review |
| 4 | No clipping, unrelated styles, fake status or unsupported claims | partial | Visual audit and content review |
| 5 | Explicit launcher interaction state machine and exit precedence | partial | Back, Home, Recents and gesture tests |
| 6 | Focused Home editing, no wall of destructive controls | partial | Physical-device edit test |
| 7 | Safe removal semantics and configurable dock | partial | Undo, confirmation and persistence tests |
| 8 | Recent Apps rules and reliable exit/category movement | partial | Navigation and layout tests |
| 9 | Category editor and valid-grid movement model | partial | Drag, move and persistence tests |
| 10 | Live card resizing, reflow and cancel restoration | partial | Resize and undo tests |
| 11 | User-selected Home content, Review fallback and standalone apps | partial | Migration and classifier tests |
| 12 | Workspace architecture and manual organization | in progress | Workspace lifecycle tests |
| 13 | Continuous wallpaper and bidirectional workspace creation | partial | Swipe, contrast and creation tests |
| 14 | Workspace overview and non-empty new-workspace flow | partial | Physical-device workflow test |
| 15 | Remove deprecated drawer system while preserving reusable logic | partial | Reachability and dependency audit |
| 16 | Approved compact App Library groups and one search field | partial | Drawer UI and launch test |
| 17 | Safe group opening, overflow and bounded nesting | partial | Back/Home/Recents and grouping tests |
| 18 | Authoritative premium Home with six automatic categories | partial | Screenshot and interaction test |
| 19 | Home exclusions plus truthful Weather/Today behavior | partial | Permission and empty-state tests |
| 20 | Ciyato as the first standalone app on Workspace 1 | partial | First-run and migration tests |
| 21 | Functional internal control centre, no rejected dashboard cards | partial | Internal Home audit |
| 22 | Final internal navigation: Home, Files, Search, Photos, Settings | partial | Navigation and migration tests |
| 23 | Remove rejected shared/features UI without weakening privacy | partial | Route and privacy-flow audit |
| 24 | Approved Files Home and navigation bundle | partial | Authorized-root data test |
| 25 | Internal file/media Search concept | partial | Authorised-root result, empty-state and indexed-search tests |
| 26 | Real storage scope, collections, timeline and cleanup candidates | partial | Scanner and result-evidence tests |
| 27 | Truthful file categories and structured natural-language search | partial | Classifier and query tests |
| 28 | Two-layer Files experience with genuine folder browser | partial | SAF folder navigation test |
| 29 | Respect Android scoped-storage limits and limited-access mode | partial | Denied, revoked and selected-folder tests |
| 30 | Bounded, checkpointed, cancellable cleanup scan and duplicate safety | partial | Worker, cancellation and hash tests |
| 31 | Separate global launcher and internal file/media search | partial | Independent query/history tests |
| 32 | Local indexed search, privacy controls and separate histories | partial | Performance and history tests |
| 33 | Rebuilt Home editing bottom sheet | partial | Collapsed, half and expanded sheet tests |
| 34 | No overlapping controls; Add, Layout, Wallpaper, Workspaces | partial | Insets and touch-target tests |
| 35 | Functional Home section toggles and motion preferences | partial | Persistence and Reduce Motion tests |
| 36 | Replace broken route with Wallpaper Studio sources | partial | Source selection, system picker and Photo Picker tests |
| 37 | Safe, battery-aware Ciyato-only video wallpaper pipeline | partial | Video validation and lifecycle tests |
| 38 | Confidence-based categorisation, Review and user corrections | partial | Rule precedence, correction persistence and app-inventory tests |
| 39 | Audit every setting: implement, remove, or truthfully unavailable | partial | Per-setting behavioral tests |
| 40 | Real authorised Photos destination replacing Shared | partial | Picker, revocation and thumbnail tests |
| 41 | Component, intent, URI and on-device privacy audit | partial | Security review and automated tests |
| 42 | Lean architecture, indexed data and bounded background work | pending | Profiling and code review |
| 43 | TalkBack, large fonts, contrast, navigation and screen-size support | partial | Accessibility/device matrix |
| 44 | Preserve user intent through a versioned migration | in progress | Upgrade migration tests |
| 45 | Complete launcher, drawer, files, search, permission test matrix | pending | Device matrix results |
| 46 | Master acceptance criteria across the product | pending | Acceptance checklist |
| 47 | Execution discipline: replace deprecated UI, preserve useful logic | in progress | Traceability review |
| 48 | Requirements traceability evidence | in progress | This document plus test evidence |
| 49 | Do not sign off before real-device product integration | pending | Full physical-device validation |

## Implementation Order

1. State machine, design tokens, safe editing and the Home/workspace foundation (pages 3-14).
2. Approved App Library and internal navigation cleanup (pages 15-23).
3. Authorised Files/index/search/cleanup foundation (pages 24-32).
4. Edit sheet, Wallpaper Studio, app intelligence, settings and Photos (pages 33-40).
5. Security, performance, accessibility, migration and real-device acceptance (pages 41-49).

No entry moves to `complete` only because the project compiles. It requires the stated behavioral evidence, and device-only interactions remain for a physical Android device.

## Current Evidence

- `compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug` all passed on 23 July 2026. The 34 passing unit tests cover classifier confidence and ambiguity handling, launcher interaction transitions, picker-scoped URI persistence, workspace migration and in-workspace reorder, cleanup-result safety, independent file-search history/index behavior, persisted Group/Card category presentation behavior, and schema-checked manifest-category evidence.
- Fresh debug artifact: `Ciyato.apk` (24,510,862 bytes, SHA-256 `3429FA733DA2F01AF79AE16369BE6270725EA7F467CF95601B15AE51FE99BA2F`). This is a debug build, not a store-release artifact.
- Photos persists only explicit Photo Picker URI references, validates them before display, supports search, grid, timeline, collections, selection, removal from Ciyato, and Android-approved source deletion where the platform permits it.
- Workspace storage now uses a versioned `WorkspaceLayout` with stable IDs, creation order, visual order, and a one-time migration from the legacy page-indexed preferences. The UI and remaining workspace overview flows still require physical-device validation before completion.
- Files is limited to a user-selected Storage Access Framework root and Photos to explicit Photo Picker selections; both use truthful selected-access states. Cleanup and internal search are scoped to that root. A revoked SAF grant clears the local file index and blocks search results until the folder is selected again; device acceptance remains outstanding.
- Files cleanup now runs as a cancellable WorkManager job with a bounded, persisted checkpoint and full SHA-256 verification for same-size candidate files. It never deletes automatically; device workflows and broader indexing still require validation.
- Internal file search has a separate on-device history with a clear action and an opt-out that removes its stored history. It preferentially queries a bounded local metadata index for the selected folder and falls back to scoped scanning when no matching index exists. Workspace migration keeps Ciyato first on Workspace 1 and honors a persisted default workspace at launcher start.
- Onboarding now presents the full seven-step V2 guide, including the safe launcher-role handoff and feature-specific access model; it does not request file, photo, calendar, or location access during setup.
- Category editing now supports an explicit compact Group or resizable Card presentation, custom collection rename/icon updates, app membership changes, merge, removal without uninstall, an accessible move-to-workspace action, and Undo for creating a new category. Classification records evidence candidates and keeps low-confidence or tied results in Review until the user confirms a category.
- Launcher inventory refreshes are serialized and reuse the in-memory inventory on ordinary short Home resumes; explicit user corrections still recalculate from the package manager.
- Classification accepts only a validated local Android manifest category after package-seed evidence. Unknown or malformed metadata cannot overwrite user corrections or leave Review prematurely.
- Launcher app shortcuts now route their explicit Ciyato actions to Focus, Permission Audit, and App Library rather than silently opening the Home screen.
- Theme Studio now presents only implemented static-dark appearance settings, Files/Photos/Calendar settings route to their real destinations, Privacy Mode has an accurate scope description, and the Crash Reporting setting now controls whether the installed crash handler writes reports.
- Workspace editing now accepts only a valid internal grid destination, supports deterministic in-workspace reorder, and uses a hover-armed edge target before moving an app or category to an adjacent workspace. Each action is paired with the existing edit snapshot Undo path. This behavior still needs physical drag-and-gesture validation.
- Wallpaper Studio now imports selected images into Ciyato-private storage and previews image crop scale, vertical position, dim and blur before use. Video wallpaper remains Ciyato-only and is validated for supported type, duration, orientation, resolution and size; it intentionally does not claim to trim media. Physical lifecycle, codec and battery behavior remain outstanding.
- Play submission remains blocked by the API level: this build targets API 34 while the current new-app requirement is API 35 or higher. The machine lacks a complete API 35/36 base platform, and no physical-device verification has been run by request.
