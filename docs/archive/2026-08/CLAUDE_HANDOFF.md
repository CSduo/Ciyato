# Ciyato Handoff For Claude

## Purpose And Stop Point

This handoff transfers the Ciyato Android project to another implementation agent. The
only active product source is the 49-page V2 specification:

`C:\Users\ADMIN\Downloads\Ciyato_Comprehensive_Implementation_Specification_Dark_Expanded.pdf`

Do not use older recovery notes, change manuals, marketing images, or generated mockups
as product requirements. They may describe rejected or superseded UI.

The user asked work to stop at the already-created APK. Do not silently replace it or
claim the repository is fully complete. This document records the exact distinction
between the current APK and local source at handoff.

## Repository And Artifact

- Repository: `C:\Users\ADMIN\StudioProjects\-nv-k-ik-\ciyato-android`
- Branch: `agent/ciyato-v2-core-foundation`
- Last local commit: `532c439 Implement Ciyato V2 core flows`
- Remote: `https://github.com/xiyatosaanvi-creator/-nv-k-ik-.git`
- Latest built debug APK:
  `C:\Users\ADMIN\StudioProjects\-nv-k-ik-\ciyato-android\Ciyato.apk`
- APK bytes: `24,510,862`
- APK SHA-256:
  `3429FA733DA2F01AF79AE16369BE6270725EA7F467CF95601B15AE51FE99BA2F`
- APK verification at its build point: `assembleDebug` passed and 34 JVM tests passed.
- No emulator, simulator, or virtual device was used. This is intentional and must stay
  true unless the user explicitly reverses that instruction.
- This is a debug APK, suitable for the user's manual phone review. It is not a signed
  release and must not be described as Play Store-ready.

### Important Source State At Handoff

After the APK was assembled, the following local edits were made but are **not included
in that APK**:

1. `HomeScreen.kt` now offers layout-snapshot Undo for workspace insert, reorder,
   duplicate, rename, default selection, deletion and template application.
2. `WorkspaceStoreTest.kt` has two additional workspace behavior tests.
3. A unit-test compile run caught an accidental spacer insertion in an unrelated recent
   apps row. That line was removed immediately. The source has **not been rerun after
   that removal**, because the user requested the work stop. The next agent must first
   run the normal unit-test/build commands before treating these last local edits as
   verified or including them in a new APK.

All previously listed uncommitted files are intentional Ciyato V2 work. Do not discard
them with `git reset`, `git checkout`, or a wholesale revert.

## User Constraints And Product Direction

1. Do not start an emulator or simulate a phone. Build and JVM tests are permitted.
   Physical-device testing must be done by the user or explicitly approved hardware.
2. Keep the visual system premium, practical and coherent: black/charcoal/slate
   foundation, white/soft-gray typography, restrained silver/blue detail. Do not pivot
   into a yellow-dominant UI. The current gold token is a sparse accent, not a primary
   color system.
3. Ciyato is an AI-native Android launcher and organized local files/photos application.
   Surfaces must not claim storage, privacy, AI inference, weather, cleanup, wallpaper,
   or platform control that they cannot actually provide.
4. Onboarding must explain the product and permission boundaries clearly. Permissions
   should be contextual, not requested all at once during onboarding.
5. The user wants all 49 V2 pages addressed, not a subset presented as completion.
6. Do not claim Google Play or iPhone acceptance. This is an Android project; an iPhone
   release needs a separate iOS implementation. Play submission has independent API,
   policy, signing and physical-device gates described below.

## Canonical Documentation In This Repository

Read these before editing:

1. `V2_TRACEABILITY.md`: status for every V2 page 1-49. `partial` means it is not
   accepted, even if a related screen exists.
2. `V2_IMPLEMENTATION_AUDIT.md`: current architecture, dependency, policy and release
   audit.
3. `CIYATO_REMAINING_WORK_MANIFEST.md`: detailed completed work, remaining code work,
   test matrix, device gates and hour estimate.
4. This document: executable transfer context and known source state.

Keep these documents honest and update their artifact/test figures only after a command
has actually passed. Do not mark a traceability row complete based solely on compilation.

## Build, Test And Artifact Commands

Run commands from the repository root in PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --offline --no-daemon --max-workers=1 --console=plain testDebugUnitTest
.\gradlew.bat --offline --no-daemon --max-workers=1 --console=plain assembleDebug
Get-FileHash .\Ciyato.apk -Algorithm SHA256
git diff --check
```

The Gradle commands can take 2-3 minutes. Do not launch an emulator. The SDK tooling may
report an XML-version warning; it was non-fatal in prior successful builds. Use one Gradle
worker because this environment is constrained.

## Source Architecture Map

## Complete Handoff File Map

The project contains 154 Kotlin production/test files. The paths below distinguish the
active V2 path from retained legacy/experimental candidates so a continuation agent does
not mistake source presence for an active product feature.

### Root And Build Files

| Path | Purpose | Continuation rule |
| --- | --- | --- |
| `settings.gradle.kts` | Gradle project/module declaration | Change only for a real module/dependency decision. |
| `build.gradle.kts` | Root Gradle configuration | Keep compatible with the installed Android/Gradle toolchain. |
| `app/build.gradle.kts` | Android SDK targets, signing/build types and app dependencies | Raise target SDK only with API 35+ installed and validated. |
| `app/src/main/AndroidManifest.xml` | Launcher role, package visibility, components, permissions, URI policy | Audit whenever adding a permission, receiver or service. |
| `V2_TRACEABILITY.md` | 49-page acceptance register | Update only with real implementation/test/device evidence. |
| `V2_IMPLEMENTATION_AUDIT.md` | Architecture/release audit | Keep all policy and release blockers truthful. |
| `CIYATO_REMAINING_WORK_MANIFEST.md` | Detailed remaining product/test work | Use as the implementation backlog. |
| `CLAUDE_HANDOFF.md` | This transfer document | Keep the artifact/source distinction current. |

### Every Local Change Awaiting Publication

| Path | Exact change at handoff | Included in current APK? |
| --- | --- | --- |
| `V2_IMPLEMENTATION_AUDIT.md` | Updated workspace/wallpaper remaining work and artifact evidence. | Documentation only. |
| `V2_TRACEABILITY.md` | Updated 34-test/artifact evidence and actual workspace/wallpaper capability notes. | Documentation only. |
| `CIYATO_REMAINING_WORK_MANIFEST.md` | New candid remaining-work and validation ledger. | No. |
| `CLAUDE_HANDOFF.md` | New complete continuation handoff. | No. |
| `app/src/main/java/com/ciyato/launcher/LauncherHomeActivity.kt` | Added wallpaper studio destination wiring. | Yes. |
| `app/src/main/java/com/ciyato/launcher/MainActivity.kt` | Added Wallpaper Studio internal route and Settings/Theme route wiring. | Yes. |
| `app/src/main/java/com/ciyato/launcher/data/LauncherSettingsRepository.kt` | Persists Ciyato image wallpaper URI, dim, scale and vertical position. | Yes. |
| `app/src/main/java/com/ciyato/launcher/data/WorkspaceStore.kt` | Deterministic in-workspace shortcut reorder helper. | Yes. |
| `app/src/main/java/com/ciyato/launcher/ui/screens/HomeScreen.kt` | Custom image background, drag/drop workspace behavior, valid grid target, hover-armed cross-workspace move and workspace Undo additions. | The workspace Undo additions made after the APK are not included. |
| `app/src/main/java/com/ciyato/launcher/ui/screens/SettingsScreen.kt` | Adds real Wallpaper Studio entry point. | Yes. |
| `app/src/main/java/com/ciyato/launcher/ui/screens/ThemeStudioScreen.kt` | Routes wallpaper work to Wallpaper Studio and removes misleading direct control. | Yes. |
| `app/src/main/java/com/ciyato/launcher/ui/screens/WallpaperPickerScreen.kt` | Photo Picker/private image import, preview controls and truthful video validation. | Yes. |
| `app/src/main/java/com/ciyato/launcher/viewmodel/LauncherViewModel.kt` | Wallpaper state/mutators and workspace reorder method. | Yes. |
| `app/src/test/java/com/ciyato/launcher/WorkspaceStoreTest.kt` | Workspace in-grid movement/default-order tests; two tests added after APK. | No, tests only. |

### Active V2 Production Files

| Directory/file | Responsibility |
| --- | --- |
| `app/src/main/java/com/ciyato/launcher/CiyatoApplication.kt` | Application process setup. |
| `.../LauncherHomeActivity.kt` | Android HOME launcher host. |
| `.../MainActivity.kt` | Internal organizer application host. |
| `.../viewmodel/LauncherViewModel.kt` | Primary UI state, preference mutations and inventory coordination. |
| `.../data/LauncherRepository.kt` | Launchable package discovery and launches. |
| `.../data/LauncherSettingsRepository.kt` | Persistent DataStore preference layer. |
| `.../data/WorkspaceStore.kt` | Versioned workspace storage/migration/mutation model. |
| `.../data/InstalledApp.kt` and `.../data/AppCategorizer.kt` | App model/category inference. |
| `.../data/FileSearchIndexStore.kt` and `.../data/FileSearchHistoryStore.kt` | Private bounded metadata index and separate search history. |
| `.../data/FileCleanupWorker.kt` | Bounded non-destructive SAF duplicate verification and result persistence. |
| `.../data/PhotoLibraryStore.kt` | Selected Photo Picker URI storage. |
| `.../data/WeatherRepository.kt` and `.../data/LocationHelper.kt` | Foreground weather/location only. |
| `.../data/CrashReporter.kt` | Opt-in crash report file behavior. |
| `.../ui/launcher/LauncherInteractionState.kt` | Explicit Home interaction/exit state machine. |
| `.../ui/screens/OnboardingScreen.kt` | Seven-step onboarding/launcher handoff. |
| `.../ui/screens/HomeScreen.kt` | Main launcher/Home, editing, workspaces, Home control sheet. |
| `.../ui/screens/AppDrawerScreen.kt` | Compact App Library. |
| `.../ui/screens/SearchScreen.kt` | Global launcher search. |
| `.../ui/screens/FilesScreen.kt` | Scoped Files Home/cleanup entry. |
| `.../ui/screens/FileCollectionDetailScreen.kt` | SAF picker, browser and user-controlled file actions. |
| `.../ui/screens/NlFileSearchScreen.kt` | Internal scoped file/media search. |
| `.../ui/screens/PhotosScreen.kt` | Photo Picker-only Photos destination. |
| `.../ui/screens/SettingsScreen.kt` | Settings audit surface and real routes. |
| `.../ui/screens/ThemeStudioScreen.kt` | Implemented appearance controls. |
| `.../ui/screens/WallpaperPickerScreen.kt` | Wallpaper Studio. |
| `.../ui/screens/PermissionAuditScreen.kt` | Permission explanation/audit. |
| `.../ui/screens/AppVisibilityScreen.kt` | Hidden/removed launcher app management. |
| `.../ui/screens/FocusSessionScreen.kt` | Focus route. |
| `.../ui/screens/CategoryDetailScreen.kt` | Category/app detail. |
| `.../ui/screens/CalendarAgendaScreen.kt` and `WeatherDetailScreen.kt` | Contextual agenda/weather routes. |
| `.../ui/components/AppIconView.kt`, `SmartCategoryCard.kt`, `BottomDock.kt`, `CiyatoNavigation.kt` | Shared active launcher UI primitives. |
| `.../ui/components/AccessibilityHelpers.kt` | Existing semantic helper foundation. |
| `.../ui/theme/Color.kt`, `Type.kt`, `Spacing.kt`, `Shapes.kt`, `Theme.kt`, `Animations.kt` | Design tokens and Compose styling. |
| `.../services/CiyatoFocusTileService.kt`, `CiyatoWeatherTileService.kt`, `CiyatoNotificationListenerService.kt` | Declared Android service integrations; policy audit individually. |

### Active Automated Tests

| Path | Coverage |
| --- | --- |
| `app/src/test/java/com/ciyato/launcher/AppCategorizerTest.kt` | Rule/category classification. |
| `.../AppClassificationTest.kt` | Confidence, tie and user-correction behavior. |
| `.../CustomCategoryPresentationStoreTest.kt` | Group/Card persistence. |
| `.../FileCleanupModelsTest.kt` | Cleanup result safety/model behavior. |
| `.../FileSearchHistoryStoreTest.kt` | Independent history privacy/retention. |
| `.../FileSearchIndexStoreTest.kt` | Bounded index serialization/deduplication. |
| `.../PhotoLibraryStoreTest.kt` | Selected URI persistence. |
| `.../ui/launcher/LauncherInteractionStateTest.kt` | Explicit launcher state transitions. |
| `.../WorkspaceStoreTest.kt` | Workspace migration/order/default/reorder behavior. |

### Retained, Non-Authoritative Source Candidates

The remaining Kotlin files in `data`, `ui/components` and `ui/screens` are legacy,
experimental, feature candidates or disconnected modules unless the current navigation
and V2 traceability explicitly prove otherwise. In particular, do not revive
`MultiPageHomeScreen.kt`, `DuplicateShortcutStrip.kt`, `SwipeableHomeDrawer.kt`, old
dashboard variants, broad cleanup/duplicate-photo helpers, promotional widgets, or
unrouted AI/privacy/backup screens to claim V2 completion. Map references before
deleting any of them; they may still contribute a reusable helper or declared dependency.

### Entry Activities And Navigation

- `LauncherHomeActivity.kt`: Android `HOME` activity. Hosts Home, App Library, global
  launcher search, weather, agenda and launcher settings with a sealed destination state.
  It cancels temporary Home editing on pause and regular Home intents.
- `MainActivity.kt`: internal Ciyato application. Bottom navigation only exposes Home,
  Files, Search, Photos and Settings. Settings, Wallpaper Studio, Theme Studio and
  operational routes are internal destinations.
- `ui/launcher/LauncherInteractionState.kt`: explicit browsing/editing/dragging/resizing
  and confirmation state. Extend this instead of introducing conflicting boolean state.

### Launcher, Home And Workspaces

- `ui/screens/HomeScreen.kt`: Home UI, edit snapshot, Home/Recents/Back exit handling,
  workspace pager, shortcut/category drag, Undo snackbar, workspace dialogs and Home
  control sheet.
- `viewmodel/LauncherViewModel.kt`: owns inventory, category state, preferences and all
  workspace mutators. Keep persistence changes here rather than writing DataStore from
  composables.
- `data/WorkspaceStore.kt`: versioned JSON workspace model. `WorkspaceRecord` uses stable
  `id` and immutable `creationOrder`; `visualOrder` is deliberately separate. It supports
  migration, insert, remove, reorder, rename, duplicate, default selection and in-grid
  shortcut movement.
- `data/LauncherSettingsRepository.kt`: DataStore preferences, including V2 workspace
  JSON, appearance controls and Ciyato wallpaper settings.

### Files And Search

- `ui/screens/FilesScreen.kt`: Files Home limited to the user-selected Storage Access
  Framework root. It scans a bounded scope, updates a private metadata index and starts
  non-destructive cleanup analysis.
- `ui/screens/FileCollectionDetailScreen.kt`: actual SAF browser and root picker flow.
- `ui/screens/NlFileSearchScreen.kt`: internal natural-language file/media search,
  separate from global launcher search.
- `data/FileCleanupWorker.kt` and `data/FileCleanupResultStore.kt`: bounded WorkManager
  duplicate analysis with checkpointing and result persistence. Never make it delete
  automatically.

### Wallpaper, Photos And Settings

- `ui/screens/WallpaperPickerScreen.kt`: Wallpaper Studio. Uses Photo Picker for images;
  imported image/video data is copied into Ciyato-private wallpaper storage. Image crop
  scale, vertical position, dim and blur are persisted. Video is Ciyato-only and is
  validated for type, duration, orientation, resolution and size.
- `ui/screens/ThemeStudioScreen.kt`: appearance controls and a route to Wallpaper Studio.
- `ui/screens/SettingsScreen.kt`: only exposes settings with an actual effect or a
  truthful operational route.
- `ui/screens/PhotosScreen.kt`: Photo Picker-backed photo collection. Do not replace it
  with broad media permissions without a new product and policy review.

### Product Truth And Safety

- `data/LauncherRepository.kt`: package inventory and app launch behavior.
- `data/AppClassifier.kt` plus classifier tests: confidence/evidence rules and user
  correction precedence.
- `data/CrashReporter.kt`: reporting preference controls logging.
- `AndroidManifest.xml`: launcher role and permissions. `QUERY_ALL_PACKAGES` must remain
  justified by the launcher product in any Play declaration.

## What Is Implemented And Included In The Existing APK

1. Seven-step onboarding with contextual access framing and launcher-role handoff.
2. Android HOME launcher flow with an explicit interaction state model.
3. Home edit cancellation on Back, Android Home, Recents/pause and a Home double-tap.
4. Layout snapshot restoration for canceled editing.
5. Versioned workspace persistence with stable identity, visual ordering, default
   workspace and legacy migration; Ciyato remains a standalone first app in Workspace 1.
6. Workspace overview with insert, rename, reorder, duplicate, default and safe deletion
   controls. Empty workspaces offer shortcut/category/template starter choices.
7. In-workspace shortcut reordering and hover-armed cross-workspace moves for shortcuts
   and categories, with valid-drop-only behavior and existing Undo support.
8. Approved compact App Library groups and separate launcher/global versus internal file
   search routes.
9. Internal Files limited to a selected SAF root, bounded scan, revocation-aware index
   reset, recent/category presentation and non-destructive cleanup analysis.
10. Separate internal search history with a clear action and privacy opt-out.
11. Photo Picker-based Photos destination with URI validation and Ciyato collection
    operations.
12. Wallpaper Studio with system picker, private image/video sources and image
    appearance controls. The visual theme is dark rather than yellow-dominant.
13. Settings/Theme wiring for implemented appearance, privacy and crash-reporting effects.
14. Cleartext traffic disabled and user-selected document/photo scopes used rather than
    all-files or broad photo access.

## Detailed Remaining Work, Ordered For Claude

### Phase 0: Establish A Clean, Verified Baseline

1. Inspect `git status --short` and `git diff --check`.
2. Review the post-APK edits described above. Keep them only after `testDebugUnitTest`
   passes. Do not update `Ciyato.apk` until the user asks to resume packaging.
3. Read the four canonical documents and map every non-complete V2 traceability row to
   source files and acceptance evidence.
4. Do not call the 49-page specification complete at this point.

### Phase 1: Finish Home Editing And Workspace Acceptance (V2 pages 5-14, 33-35)

1. Define and implement complete card-size collision/reflow rules. The persisted grid
   model must prevent overlap and give deterministic positions after drag/resize/restart.
2. Add intentional group-creation hover behavior. It needs a delay, target preview,
   naming, bounded nesting, explicit Undo and accessible non-drag alternatives. It must
   never create a group accidentally during normal movement.
3. Complete workspace lifecycle acceptance: create, rename, reorder, duplicate, set
   default, delete with/without moving contents, new-workspace starter and recovery from
   last-workspace requests. Preserve stable IDs and creation order.
4. Finish the Home edit sheet state contract: collapsed/half/expanded, insets, touch
   targets, section toggles and Reduce Motion. Avoid overlapping controls.
5. Add tests for collision/reflow, every cancel path, Undo, group creation and workspace
   persistence. Use physical-device validation for actual gesture behavior, not emulator.

### Phase 2: App Library, Classification And Settings (pages 15-23, 38-39)

1. Audit App Library groups against the V2 compact-group hierarchy, one expanded group,
   search, overflow and Back/Home/Recents behavior.
2. Support incremental package changes and managed work profiles if the V2 requirement is
   still relevant after reviewing current Android APIs. Preserve manual corrections.
3. Prove each setting's stored effect, reset behavior, explanatory copy and unavailable
   state. Remove any still-visible fake or disconnected settings rather than presenting a
   nonfunctional control.
4. Check that internal Home contains only approved V2 controls and no restored rejected
   dashboard/shared-feature surfaces.

### Phase 3: Files And Search (pages 24-32)

1. Add tests and UI states for canceled SAF selection, denied selection, revoked URI,
   provider failure, deleted/moved file and root re-selection. The app must never expose
   cached content after access is lost.
2. Validate genuine nested folder browser, collections, timeline, truthful type/category
   evidence, selection limits and empty/error states.
3. Finish cleanup interaction: WorkManager cancellation/restart/checkpoint recovery,
   full-hash results, explicit per-file user approval and deletion-result states. No
   automatic deletion.
4. Benchmark the bounded index and natural-language parsing; preserve strictly separate
   launcher-search and internal-file-search histories.

### Phase 4: Wallpaper, Photos, Policy And Accessibility (pages 36-49)

1. Consider native image crop, contrast and per-workspace wallpaper only if the V2 page
   mandates them. Do not claim video trimming when only validation is implemented.
2. Validate Photo Picker cancel/revoke/delete behavior and large collection performance.
3. Install a complete Android API 35 or newer SDK, raise `targetSdk`, analyze behavior
   changes and test on physical Android hardware. The current project targets API 34 and
   cannot be honestly described as eligible for a current new Google Play submission.
4. Complete intent/URI/security review, launcher package-visibility declaration, release
   signing and CI configuration.
5. Add TalkBack labels/traversal, keyboard support, large-font, contrast, RTL,
   small/large screen and migration tests. Verify real-device gesture and permission
   paths.

## Required Physical Phone Validation

These are not substitutes for JVM tests and should not be simulated:

1. Set Ciyato as default launcher. Exercise Home, Recents, Back, notification shade and
   gesture navigation while editing a layout.
2. Drag shortcuts/categories, reorder cards, create/delete/reorder workspaces and restart
   Ciyato to prove state restoration and Undo behavior.
3. Use Files root selection, cancellation, revocation and a real scoped folder corpus.
4. Use Photos Picker selection/cancellation/revocation on supported Android versions.
5. Exercise image/video wallpapers through lock/unlock, rotation, background/foreground
   and battery-saver events.
6. Test contextual permissions: launcher role, location, calendar, SAF and Photo Picker.
7. Test TalkBack, large fonts, contrast and different device sizes.
8. Repeat key paths after targeting API 35 or newer before release.

## Release And GitHub State

1. `gh auth status` previously reported no authenticated GitHub host. Do not say changes
   were pushed until this succeeds:

```powershell
gh auth login
git push -u origin agent/ciyato-v2-core-foundation
```

2. Commit intentionally after inspecting all local changes. Do not overwrite any user
   changes or use destructive reset/checkout commands.
3. A release requires a distinct signed release build, keystore management, API 35+
   target validation, policy declarations, physical-device acceptance and Play Console
   review. The debug APK is not that deliverable.

## Known Non-Product Issues To Keep In Mind

1. Some older source comments and a few display strings contain mojibake such as `Â·` or
   garbled divider comments. Correct visible text while touching its screen; use ASCII
   punctuation where possible. Avoid broad mechanical rewrites that create noisy diffs.
2. Some legacy experimental screens remain in source. Map navigation/dependencies before
   deleting them. Do not reintroduce them to satisfy V2 feature claims.
3. The source currently uses DataStore for bounded index/history/workspace state. Before
   scaling data volume, make an intentional performance/storage decision rather than
   silently treating it as a database.

## Honest Completion Rule

The project is only complete when all 49 traceability rows have evidence matching their
acceptance criteria, code and tests pass, required physical-device tests pass, policy/API
gates are met, and the requested artifact/push status is real. Compilation alone is not
proof of product completion.
