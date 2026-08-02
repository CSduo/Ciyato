# Ciyato Remaining Work Manifest

Created 23 July 2026 from the active 49-page V2 specification:
`Ciyato_Comprehensive_Implementation_Specification_Dark_Expanded.pdf`.

This is an intentionally candid delivery ledger. It does not call a feature complete
because it compiles. The source mapping for every one of the 49 pages remains in
`V2_TRACEABILITY.md`.

## Current Delivered Build

- APK: `C:\Users\ADMIN\StudioProjects\-nv-k-ik-\ciyato-android\Ciyato.apk`
- Build type: debug, installable for hands-on review; not Play Store release-ready.
- Built: 23 July 2026 with `assembleDebug`, no emulator launched.
- Verification: 34 JVM unit tests passed, zero failures; `assembleDebug` passed.
- SHA-256: `3429FA733DA2F01AF79AE16369BE6270725EA7F467CF95601B15AE51FE99BA2F`
- Current implementation estimate: about 60% completed, about 40% remaining.

## What Is Already Implemented In Code

1. Launcher editing safely cancels when Back, Home, Recents or a double-tap exit action
   occurs, restoring the saved layout snapshot instead of leaving a half-edited Home.
2. Workspaces persist stable identifiers, creation order, visual order and a default
   workspace with a migration from the prior page-indexed storage.
3. App/category movement only commits to an actual workspace target. A valid grid drop
   reorders apps within a workspace; a hover-armed edge target moves an item to a
   neighboring workspace. The action is covered by the existing Undo snapshot.
4. Onboarding is a seven-step product-specific flow. It introduces launcher handoff and
   asks access only when the related capability is used.
5. The Home launcher, App Library and internal application navigation were reduced to
   the V2 destinations rather than preserving rejected dashboard/shared surfaces.
6. Files use an explicit user-selected SAF folder, bounded scanning and a separate
   private search history. Revoked folder access clears the index and shows a reselect
   state rather than displaying stale files.
7. Cleanup candidates are bounded, checkpointed and cancellable. They are never deleted
   automatically, and duplicate candidates use full SHA-256 confirmation.
8. Photos use Android Photo Picker URI access, not broad photo permissions.
9. Theme Studio and Settings contain implemented, persisted dark appearance, privacy and
   crash-reporting controls. Wallpaper controls now route through Wallpaper Studio.
10. Wallpaper Studio supports system wallpaper selection plus Ciyato-private image and
    video wallpapers. Images have live crop scale, vertical position, dim and blur
    controls. Videos are validated before use and pause for configured lifecycle/power
    conditions.

## Remaining Product Work

### A. Home Editing And Workspaces (V2 pages 5-14 and 33-35)

1. Complete grid and card-resize behavior.
   - Code area: `HomeScreen.kt`, `WorkspaceStore.kt`, `LauncherViewModel.kt`.
   - Remaining: define every occupied-cell collision/reflow rule for each supported card
     size, preserve it through rotation/process recreation, and expose clear accessible
     alternatives to drag operations.
   - Proof: unit tests for collision/reflow plus manual phone tests for drag, resize,
     cancel, Undo, Home, Recents and default-launcher transitions.

2. Complete manual group creation behavior.
   - Code area: Home edit interaction layer and category editor.
   - Remaining: intentional hover-to-create-group timing, explicit visual affordance,
     group naming/undo, nested-group limits and deterministic persistence.
   - Proof: tests that no accidental group is created, grouping can be undone, and it
     survives restart.

3. Complete workspace lifecycle and overview.
   - Code area: workspace overview UI and store.
   - Remaining: non-empty new-workspace flow, bidirectional swipe/creation flow,
     rename/delete/default behavior, safe last-workspace rules, content selection and
     migration of prior user layouts.
   - Proof: manual creation, reorder, deletion, default-workspace and migration matrix
     on a real device.

4. Complete editing sheet and motion acceptance.
   - Code area: Home bottom sheet and motion settings.
   - Remaining: verify collapsed/half/expanded states, insets, touch targets, live
     section toggles and Reduce Motion behavior against all V2 examples.
   - Proof: screen-size, gesture-navigation and large-font review.

### B. App Library, Launcher Intelligence And Internal Navigation (pages 15-23 and 38-39)

1. Finish App Library acceptance behavior.
   - Remaining: validate compact groups, one expanded group, overflow, bounded nesting,
     search results and Back/Home/Recents exit precedence with a genuine installed-app
     inventory.
   - Proof: real-device launch and navigation matrix.

2. Extend application discovery and classification.
   - Remaining: managed work-profile discovery, incremental package add/remove/change
     updates, review queue UX validation and durable correction precedence in all edge
     cases.
   - Proof: test applications and physical device/work-profile checks.

3. Finish per-setting behavioral coverage.
   - Remaining: every visible settings control needs a focused test proving its stored
     effect, reset behavior, privacy explanation and unavailable state where applicable.
   - Proof: unit/UI tests plus manual settings audit.

### C. Files And Search (pages 24-32)

1. Complete Files authorization failure handling.
   - Remaining: test denied folder selection, canceled selection, revoked URI grant,
     deleted file, moved file and provider error paths throughout browsing, cleanup and
     internal search.
   - Proof: tests using controllable document-provider fixtures and real user-selected
     storage validation.

2. Complete the two-layer Files browser.
   - Remaining: validate genuine folder navigation, timeline/collections behavior,
     accurate category evidence, large-folder pagination/performance and every empty
     state under scoped storage.
   - Proof: physical storage test corpus and bounded-performance profiling.

3. Complete cleanup acceptance.
   - Remaining: validate WorkManager cancellation/resume, checkpoint recovery,
     hash-result presentation, user approval before deletion and deletion-result states.
   - Proof: real files on a phone, including storage-pressure and interruption cases.

4. Complete internal search acceptance.
   - Remaining: benchmark the private index, validate natural-language query parsing,
     keep global launcher search independent, and test history opt-out and recovery.
   - Proof: local-data performance and privacy test matrix.

### D. Wallpaper, Photos, Security And Accessibility (pages 36-49)

1. Finish optional Wallpaper Studio enhancements.
   - Remaining: native image crop editor, contrast adjustment, explicit per-workspace
     wallpaper scope and media trimming only if the product requires those controls.
     The current code intentionally validates video rather than pretending it trims it.
   - Proof: physical image/video codec, battery, background/foreground and screen-off
     lifecycle testing.

2. Complete Photos user flows.
   - Remaining: validate Photo Picker cancellation/revocation, thumbnails, selection,
     removal, permitted source deletion and large media collections.
   - Proof: Android-version and physical-gallery matrix.

3. Complete policy, security and release hardening.
   - Remaining: install the complete API 35 or newer platform, raise `targetSdk`, resolve
     behavior changes, create Play declarations for the launcher package visibility use
     case, complete intent/URI security review and create release signing/CI artifacts.
   - Proof: API-35+ device testing and Play Console policy review.

4. Complete accessibility and migration acceptance.
   - Remaining: TalkBack order/labels, keyboard access, large fonts, contrast, RTL,
     small/large screens, state restoration, upgrade migration and notification/gesture
     coexistence.
   - Proof: device matrix and accessibility audit.

## Tests Still Required

### Code Tests To Add

- Workspace collision, reflow, group creation, resizing and all Undo/cancel branches.
- SAF cancellation, revocation, deletion and provider-failure paths.
- Cleanup worker cancellation, restart and user-confirmed deletion result states.
- Settings persistence/effect tests for every exposed setting.
- Search privacy history opt-out, recovery and performance-bound tests.
- Accessibility semantics and migration upgrade tests.

### Physical Phone Tests Required

- Set Ciyato as the default launcher; test Home, Recents, Back and gesture navigation.
- Drag/reorder apps and categories; create/delete/reorder/default workspaces; then
  restart the device/app and confirm restoration.
- Select/cancel/revoke a Files folder and pick/cancel/revoke Photos media.
- Play validated image/video wallpapers through lock, unlock, background, battery saver
  and rotation changes.
- Test permissions only at their contextual entry points: launcher role, location,
  calendar, SAF and Photo Picker.
- Test TalkBack, large font, contrast and multiple screen sizes.
- Test target API 35 or newer before any Play upload.

## Delivery Gates

1. The current APK is ready for your hands-on installation and review.
2. The project is not ready to claim all 49 pages are complete.
3. The project is not ready to claim Google Play acceptance: it currently targets API 34,
   while a new submission requires API 35 or higher, and no physical-device acceptance
   pass has been run.
4. GitHub publishing is waiting for GitHub CLI authentication on this machine. Source
   changes can be committed locally after the remaining code slice is checked, but a
   remote push cannot be honestly reported until `gh auth login` succeeds.

## Work Estimate

- Remaining code and automated verification: 35-55 engineering hours.
- Required physical-device, API target and release validation: 6-10 additional hours.
- This estimate excludes waiting for external account review or Play Console review.
