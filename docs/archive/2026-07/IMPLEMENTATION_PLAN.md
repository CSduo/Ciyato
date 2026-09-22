# Ciyato Emergency Recovery Implementation Plan

This plan replaces the stale screenshot-copy plan. The recovery goal is a coherent Android launcher plus internal manager, with no `Ciyato` spelling, no launcher/internal layer confusion, and no visible dead UI.

## Phase 1: Stabilize Build And Naming

- Keep `assembleDebug` green.
- Replace every visible `Ciyato` with `Ciyato`.
- Rename generated APK copy output to `Ciyato.apk`.
- Normalize visible broken encoding where touched.

## Phase 2: Separate Launcher Layer From Internal App Layer

- Keep `LauncherHomeActivity` as the Android HOME entry.
- Keep `MainActivity` as the normal app icon/internal manager entry.
- Remove permanent internal bottom nav from launcher Home.
- Keep launcher shortcuts limited to home, drawer, search, edit, and a small settings/manager hand-off.

## Phase 3: Fix Home Interactions

- Ensure dock, recent apps, custom page apps, duplicate shortcuts, and category apps launch real apps.
- Wire Home category taps to category detail.
- Wire weather and agenda cards to their detail/explanation screens.
- Keep edit mode stable, visible, and reversible.
- Make remove/hide/category actions real through the app context menu.

## Phase 4: Fix App Library / Drawer Behavior

- Treat App Library as the launcher drawer, not an internal tab.
- Show real installed apps and real icons.
- Respect hidden and removed app states.
- Expand/collapse category sections.
- Make search, filters, duplicate management, and long-press app actions functional.
- Move default visual direction back to dark premium, with light drawer style only as a later theme option.

## Phase 5: Fix Category Expansion And Editability

- Ensure category cards open details.
- Ensure `+N` appears only when real overflow exists.
- Allow rename, restore default, hide/remove apps, and category reassignment.
- Persist category order and tile sizes.

## Phase 6: Implement Hidden And Removed Apps

- Keep hidden and removed states separate.
- Continue storing hidden/removed package sets in DataStore.
- Add an explicit `AppVisibilityState` model when the repository is refactored.
- Apply visibility consistently across Home, App Library, Search, categories, recent apps, duplicate shortcuts, and dock.
- Support restore individual and restore all.

## Phase 7: Fix Files Direction

- Make the actual Files screen a real SAF file browser first.
- Ask the user to choose a folder before claiming file access.
- Show folders and files, and open files with Android intents.
- Keep storage/cleanup dashboards only as secondary overview cards with honest staged copy.
- Remove fake cleanup, fake storage totals, fake file counts, and empty click handlers.

## Phase 8: Fix Search

- Guarantee installed-app search is fast and launches apps.
- Respect hidden/removed app states by default.
- Make category matches useful.
- Mark files/photos search as staged until permission-backed indexing exists.
- Improve keyboard/back behavior.

## Phase 9: Fix Theme Studio

- Use a phone-safe single-column responsive layout.
- Keep only controls that persist or visibly affect UI.
- Include layout mode, accent, icon shape, font, background blur, and reset controls.
- Make live preview read real settings instead of local-only mock state.

## Phase 10: Fix Settings

- Keep set-as-home and switch-back safety prominent.
- Expose Theme Studio, Hidden Apps, Removed Apps, Permissions, privacy, version/build info, onboarding reset, layout reset, and preference reset.
- Ensure every settings item opens or performs a real action.

## Phase 11: Polish UI, Insets, Spacing, And Safe Areas

- Respect status and navigation bars.
- Prevent bottom dock overlap.
- Avoid clipped text and horizontal overflow.
- Use the approved dark premium Ciyato palette consistently unless a light theme is selected.

## Phase 12: Test And Document

- Rebuild debug APK.
- Install/run on a physical Android device for final acceptance. Do not use emulator-based acceptance for this pass.
- Verify launcher, drawer, internal manager, files, theme, settings, search, hidden/removed, and switch-back flows.
- Update `TESTING.md`, `SECURITY.md`, and `README.md`.
