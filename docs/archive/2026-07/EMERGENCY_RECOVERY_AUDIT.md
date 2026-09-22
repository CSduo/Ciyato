# Ciyato Emergency Recovery Audit

Date: 2026-07-08

## Current Build State

- `assembleDebug` succeeds with the Android Studio bundled JBR.
- The generated debug APK builds, and the debug copy task writes `Ciyato.apk`.
- The only build warning observed is the Android SDK XML version warning from local tooling.

## What Currently Works

- `AndroidManifest.xml` separates the Android HOME entry (`LauncherHomeActivity`) from the normal app icon entry (`MainActivity`).
- `LauncherRepository` loads real launchable apps through `PackageManager`, stores real `Drawable` icons, launches apps with explicit launcher activities, and filters hidden/removed apps from the visible app list.
- `LauncherSettingsRepository` persists launcher preferences in DataStore, including hidden apps, removed apps, dock packages, layout density, category renames, category overrides, recent launches, and recent searches.
- Launcher Home has real app launching for dock, recent apps, custom page shortcuts, and category app surfaces.
- App long-press context menu can open, pin/unpin to dock, hide/unhide, remove from display, change category, open app info, and request uninstall.
- Category detail screens launch real apps, support search inside a category, support renaming/resetting standard categories, and use the app context menu.
- Hidden Apps and Removed Apps management screens restore individual apps and explain that apps are not uninstalled.
- Search filters real installed apps, launches results, stores recent searches, and groups results by category.
- Photos uses Android Photo Picker and describes the next phase instead of silently claiming full photo AI.
- `FileCollectionDetailScreen` already uses Android Storage Access Framework and can list/open files from a selected folder.

## What The Initial Audit Found Broken Or Confused

- The app used the wrong product spelling in strings, onboarding, dashboard, Files, Theme Studio, Settings, build output, and stale docs.
- Launcher Home rendered a permanent internal bottom navigation bar with `Library`, `Files`, `Theme`, and `Settings`. This violated the two-layer product model.
- `LauncherHomeActivity` included internal app destinations such as Files and Theme Studio directly inside the launcher layer. These should be small shortcuts or hand-offs, not a tabbed app structure.
- `HomeScreen` received `onWeatherTap`, `onAgendaTap`, `onDuplicatesTap`, and `onCategoryTap`, but `LauncherHomeActivity` did not wire weather, agenda, duplicate shortcuts, or category detail from Home.
- Standard category delete in Home edit mode was empty. Custom category tapping was also empty.
- The Home category card previews did not let individual mini-icons launch apps, and category edit affordances used text glyphs rather than robust action controls.
- App drawer used a light cream visual style by default while the approved direction is dark premium unless the user selected a light theme.
- App drawer filter icon had an empty click handler.
- App drawer `View all N apps` text was not clickable and only 12 apps were shown in expanded category sections.
- Search suggestion chips for files/photos opened staged Files/Photos flows even though files/photos search was not implemented. That risked overpromising search.
- Files screen was conceptually wrong: it was mostly a static dashboard with fake counts, fake storage totals, fake cleanup numbers, fake recent files, and many empty click handlers.
- Files screen passed `initialFolderUri = null` to collection detail, so selected folder state was not reused from the overview.
- Theme Studio had local-only controls that did not persist or affect the UI (`Ciyato Home Active`, glass mode, smart box style, dock style, dark mode tab). It also contained the wrong spelling.
- Settings was useful in several places, but the subtitle still used the wrong product name, and reset options were narrower than the recovery brief asks.
- Several screens still contain non-ASCII mojibake from previous edits, which makes some comments and labels look unprofessional in source and may leak into UI text.

## Visual Problems

- Launcher Home mixes a premium dark launcher with internal-app navigation.
- Home bottom dock and nav stack consume bottom space and can overlap navigation areas on smaller devices.
- App drawer palette was inconsistent with the approved black, gray, white, and silver Ciyato direction.
- Files screen feels like a static mockup instead of a real file manager.
- Theme Studio is split into narrow side-by-side panels that can clip on phones.
- Some legacy text contained broken encoding and had to be normalized where visible.

## Architecture Problems

- The intended two-layer architecture exists in the manifest but is not cleanly enforced in UI routing.
- Launcher Home should expose drawer/search/edit/settings shortcuts, not a permanent internal bottom nav.
- Files functionality is split: the real SAF browser lives in detail, while the main Files tab is a static dashboard.
- Theme settings are persisted in DataStore but not consistently applied to `CiyatoTheme` or the live launcher UI.
- There is no explicit `AppVisibilityState` enum yet; hidden and removed states exist as separate CSV sets. Functionally useful, but less clear than the requested model.
- There is no dedicated search repository; search lives in `LauncherRepository` and `LauncherViewModel`. This is acceptable for now but should be clarified as shared local search state.

## Wrong Layer Items

- Launcher Home permanent bottom nav: `Library`, `Files`, `Theme`, `Settings`.
- Files and Theme Studio are accessible as full launcher destinations. They should be internal app surfaces, with launcher shortcuts only for safe settings/edit hand-off.
- App Library is correctly launched from the launcher, but it is styled and structured like an internal page in some places.

## Static, Mocked, Or Dead UI

- Files categories, storage overview, cleanup status, recent files, duplicates, large files, quick actions, notifications, and menu actions are static or empty.
- Theme Studio glass mode, smart box, dock style, `Ciyato Home Active`, and dark mode local tab are not persisted or applied.
- App drawer filter icon and `View all` label are dead.
- Home standard category delete callback is empty.
- Search chips imply files/photos semantic search even though only app search is implemented.

## Must Fix First

1. Fix every visible wrong product-name spelling, including APK copy output.
2. Remove the internal bottom nav from launcher Home and replace it with launcher-appropriate drawer/settings/edit affordances.
3. Wire Home category/weather/agenda/duplicate actions to real destinations.
4. Make Home category edit/delete actions real or remove unsupported actions.
5. Replace the Files dashboard-first experience with a real SAF file browser first screen, keeping overview/collections as secondary.
6. Remove or clearly stage Files cleanup/AI claims and empty actions.
7. Repair Theme Studio so only controls that persist or visibly affect UI are shown.
8. Update Settings copy and expose hidden/removed/reset/permission/switch-back flows cleanly.
9. Rebuild and update testing/security/readme documentation.

## Initial Recovery Pass Completed

- Product spelling is now consistent in source, strings, docs, and generated APK copy naming.
- Stale root requirement files were renamed with `ciyato_` filenames.
- The debug APK copy task now writes `Ciyato.apk`.
- Launcher Home no longer renders the internal app bottom nav. It now shows the dock plus compact launcher controls for App Library, edit mode, and launcher settings.
- Home category, weather, agenda, and duplicate shortcut taps are wired to real launcher destinations.
- Category preview icons can launch apps directly, and overflow badges appear only when there are more apps than the visible preview slots.
- App drawer default styling now follows the dark Ciyato palette, its sort/filter button cycles real sort modes, `View all` expands large sections, and long-press opens app actions.
- Search suggestions now stay within implemented installed-app/category search.
- Files now opens a real Storage Access Framework folder browser first, remembers the selected folder, supports nested folders, opens files through Android intents, and lets the user forget folder access.
- Theme Studio was replaced with phone-safe persisted controls and a live preview.
- Theme Studio now persists App Library drawer style, and the palette defaults to black/graphite glass with silver and white highlights instead of yellow-heavy accents.
- Hidden and Removed management now support search and restore-all.
- Onboarding is now a richer multi-step walkthrough covering Home setup, App Library modes, permissions, Files, Photos, Weather, privacy, and switch-back.
- `assembleDebug` completed successfully and produced `Ciyato.apk`.

## Files Planned For Initial Recovery Edits

- `app/src/main/res/values/strings.xml`
- `app/build.gradle.kts`
- `app/src/main/java/com/ciyato/launcher/LauncherHomeActivity.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/components/SmartCategoryCard.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/AppDrawerScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/SearchScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/FilesScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/FileCollectionDetailScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/ThemeStudioScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/OnboardingScreen.kt`
- `app/src/main/java/com/ciyato/launcher/ui/screens/DashboardScreen.kt`
- `README.md`
- `TESTING.md`
- `SECURITY.md`
- `IMPLEMENTATION_PLAN.md`
