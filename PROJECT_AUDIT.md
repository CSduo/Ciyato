# Ciyato Project Audit

Audit date: 2026-07-06
Contract: `CIYATO_CODEX_FINAL_IMPLEMENTATION_CONTRACT.txt` version 1.0
Audited checkout: `C:\Users\ADMIN\StudioProjects\-nv-k-ik-` on `main`

## Executive summary

Ciyato is a real native Android project built with Kotlin and Jetpack Compose. Its core launcher path is genuine: Android can select it as the Home app, it queries installed launcher activities, renders their real icons, and starts their real packages. The current Android source builds a debug APK.

The project is not yet contract-complete. Several high-visibility features are mock or internally inconsistent:

- Hidden apps are filtered out but cannot be managed or restored in the UI.
- Removed apps do not exist as a distinct state.
- The Photos permission button grants no Android access and shows mock collections.
- Files can read a user-selected folder, but the overview numbers and cleanup claims are static; folder state is not restored consistently.
- AI Search is real for installed apps only. File and photo results are staged permission routes.
- Theme Studio persists some options, but only density visibly changes the launcher; dark mode, accent, and icon settings do not drive the theme.
- Multiple dashboard/home controls are visibly tappable but have empty actions.
- The manifest declares many permissions unrelated to the navigable beta.
- The initial unit-test suite did not compile against the categorizer API; this has now been repaired.

## Post-audit implementation update

The findings above describe the initial checkout. The current worktree now includes these corrections:

- Hidden and Removed are separate persisted states.
- A complete installed-app list is retained internally so hidden/removed apps can be restored with their real names and icons.
- Settings contains Hidden and Removed management screens.
- Long-press actions distinguish Hide, Remove from display, and system-confirmed uninstall.
- Category renames, per-app category overrides, and dock pins persist locally.
- Home opens the full Search surface and Theme Studio; dead notification UI was removed.
- Duplicate Smart Shortcuts were restored on Home and in the App Library.
- The App Library now uses the dark premium reference palette.
- Home has a persistent dismissible tip and reset guidance control.
- Photos now launches Android Photo Picker and displays real selected-media thumbnails.
- Files restores persisted folder access, passes the folder into collection details, uses real storage totals, and exposes ten heuristic Smart Collections.
- Static cleanup actions are labelled staged or disabled.
- Dashboard claims were replaced with live app/storage state and privacy-safe facts.
- The APK icon now uses the user-supplied black rounded tile with an ivory `C` and premium sparkle, including adaptive and density launcher resources.
- The manifest was reduced. The built APK now contains only launcher discovery, network/weather, coarse location, haptics, and Android's generated private receiver permission.
- The unit tests were repaired: 9 tests now pass.
- `testDebugUnitTest assembleDebug` succeeds and produces `app/build/outputs/apk/debug/app-debug.apk`.
- README, SECURITY, and TESTING now describe current behavior and limitations.

Remaining high-impact gaps are real-device/emulator interaction verification, recursive file indexing, persistent Photo Picker selections/collections, richer duplicate-placement editing, broader launcher customization, and full semantic file/photo search. Those are not claimed complete.

## Repository structure

| Area | Role | Audit status |
|---|---|---|
| `ciyato-android/` | Native Android launcher and organizer | Product deliverable |
| `artifacts/ciyato/` | React visual prototype | Design reference only |
| `artifacts/ciyato-mobile/` | Expo/React Native prototype | Legacy reference only |
| `artifacts/api-server/` | Server stub | Not used by the launcher beta |
| `attached_assets/` | UI references and chosen icon | Visual source material |
| `.github/workflows/` | CI definitions | Present; must be reverified after implementation |

The web and Expo artifacts must not replace the native Android application.

## Build and APK evidence

- `assembleDebug`: passes with Android Studio's bundled JDK 17.
- Exact audited command:

  ```powershell
  $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
  .\gradlew.bat assembleDebug
  ```

- Current generated APK: `app/build/outputs/apk/debug/Ciyato.apk`
- Project copy: `ciyato-android/Ciyato.apk`
- Provided APK: `C:\Users\ADMIN\Downloads\Ciyato(2).apk`
- The provided APK and current build have different hashes, so the supplied APK is not identical to the current dirty checkout.
- No Android device was connected during the audit. An AVD named `medium_phone` exists but runtime verification has not yet been completed.

### Test status

The initial `AppCategorizerTest.kt` treated the Kotlin `object AppCategorizer` as constructible and referenced removed APIs/categories. The suite has been rewritten against the current API; 9 tests now pass.

## What is real and working in source

### Launcher

- `LauncherHomeActivity` has `MAIN`, `HOME`, and `DEFAULT` intent handling.
- Onboarding uses `RoleManager.ROLE_HOME` where available and falls back to Android Home settings.
- `LauncherRepository` queries `ACTION_MAIN` plus `CATEGORY_LAUNCHER`.
- Installed labels, package names, activities, install/update times, system-app state, and actual drawables are loaded.
- `AppIconView` renders the real drawable.
- App launching uses the package launch intent.
- Home category cards navigate to a real category detail screen.
- App Library sections expand/collapse and launch apps.
- Long-press context menus are partially wired.
- DataStore persists many launcher settings.
- Settings exposes Android Home settings and app-info/uninstall settings.

### Organizer

- Dashboard navigation reaches Files, Photos, Search, Theme Studio, and Settings.
- Weather has an in-app explanation, foreground coarse-location request, Open-Meteo integration, refresh state, and no background-location request.
- Files uses Android's Storage Access Framework and can list/open files from a folder selected by the user.
- Search matches installed app label, package, and category, with fuzzy/category intent support.
- Category renames and per-app category overrides have persistence architecture.

## Mocked, staged, broken, or misleading behavior

### Home launcher

- The sparkle/assistant and notification buttons have no click behavior.
- The `Edit` label is not clickable.
- The current dirty changes remove the Duplicate Smart Shortcuts strip even though it is required by the contract and referenced visual.
- Home search is app-only inline search rather than the unified AI Search surface.
- No persistent dismissible help/tip is present.
- Dock contents use a hardcoded package priority and are not user-customizable.

### Smart App Library

- The current Suggested calculation is empty on a fresh install because it requires an in-memory launch count greater than zero.
- Launch counts are not persisted.
- Hidden apps are absent, but no management or restore route exists.
- Removed apps are not represented.
- The current dirty changes remove the duplicate-shortcut card from the drawer.

### Hidden and removed apps

- `hiddenApps` is a CSV preference.
- Hidden packages are removed from the only app list, which prevents a management screen from resolving their labels/icons.
- There is no removed-app preference or state.
- “Remove from Home” currently maps to hiding behavior; this violates the contract's distinction between Hidden and Removed.
- No UI lists hidden or removed apps for restoration.

### Files

- SAF folder selection and file opening are real.
- `FilesScreen` does not pass its selected folder into `FileCollectionDetailScreen`; the user is asked to choose again.
- Persisted URI permission is taken, but the selected URI is not restored into UI state.
- The text claiming access is revoked when leaving is inaccurate because persistable permission is requested.
- Storage totals, cleanup status, and file counts are static.
- Cleanup, Junk, and Vault controls look actionable but do nothing.
- Category-specific filtering and the complete Smart Collections set are missing.
- No automatic deletion occurs, which is correct and must remain true.

### Photos

- Collections and counts are mock data.
- “Enable Photos” only flips an in-memory Boolean; it does not open Photo Picker or request media access.
- No selected-media preview, search, collection editing, move/copy/remove action, or custom collection exists.
- No upload path was found.

### AI Search

- Installed-app search and launching are real.
- Prompt chips route to app categories or staged Files/Photos flows.
- File/photo/document/screenshot results are not indexed or searched.
- Locked states are present but need clearer “staged” labeling and real selected-data integration.
- No remote AI model is connected; the name “AI Search” currently describes deterministic local search.

### Theme Studio and customization

- Dense/spacious state changes home columns.
- Several preferences persist in DataStore.
- `CiyatoTheme` is a fixed dark color scheme and does not consume the dark-mode or custom-accent preference.
- Gold accent, icon style, icon shape, font, wallpaper blur, glass style, dock style, wallpaper selection, and live preview are incomplete or decorative.
- Category renaming has backend support but no complete editing flow.
- Reset coverage is partial.

### Dashboard and auxiliary screens

- “AI Active” is misleading because no AI model is active.
- Storage figures, cleanup history, photo organization, file cleaning, and “duplicate apps detected” activity are static claims.
- AI/notification top actions and all Quick Actions are dead.
- Many additional screen files exist but are not reachable from the main product navigation. Their existence is not evidence of a working feature.

### Agenda

- The launcher opens Agenda.
- Agenda items are clearly sample data.
- Real calendar integration is not connected on the launcher route.

### Settings and switch-back

- Home settings and app-info routes are real.
- A “Close Ciyato Launcher” action only calls `finish()`. If Ciyato is the default Home, Android can immediately recreate it; this is not a genuine switch-back mechanism and is misleading.
- Hidden/Removed management, Theme Studio navigation, permission state summaries, tip reset, onboarding reset, granular reset options, and build/version details are incomplete.

## Permission and privacy audit

### Required for the current navigable beta

| Permission/capability | Current purpose |
|---|---|
| `QUERY_ALL_PACKAGES` | Launcher app discovery; must be justified for distribution |
| `INTERNET` | Open-Meteo weather |
| `ACCESS_NETWORK_STATE` | Weather/offline handling |
| `ACCESS_COARSE_LOCATION` | Foreground local weather after education and user action |
| `VIBRATE` | Optional haptic feedback |
| Storage Access Framework | User-selected folders; no manifest storage permission |
| Photo Picker | Selected photos; no broad media permission required |

### Overdeclared or not justified by the contract beta

`WRITE_CALENDAR`, broad media permissions, usage stats, notifications, foreground service, boot completed, exact alarms, and biometric/fingerprint permissions are declared despite not being required by the primary navigable beta. Notification-listener and quick-settings services also expand the security surface. These should be removed from the beta manifest unless their user-facing flow is fully wired, explained, and tested.

### Privacy positives

- No analytics SDK is initialized.
- App-list categorization is local.
- SAF data remains on-device.
- No photo upload path was found.
- No automatic file deletion is implemented.
- `allowBackup` is disabled.

### Privacy risks

- `CrashReporter.install()` is called while `CiyatoApplication` claims no crash reporter. Data destination and opt-in semantics need reconciliation.
- Several unused permissions and services create unnecessary trust and review risk.
- Static cleanup/security claims could mislead users even if no destructive code runs.

## Visual reference mapping

The actual ten provided files map as follows:

1. Ciyato Photos
2. AI Search
3. Before/After
4. Product Showcase
5. Lifestyle Home
6. Main Home Launcher
7. Smart App Library
8. Theme Studio
9. Ciyato Files
10. Smart File Collections

Common visual language: charcoal/black background, soft graphite elevation, restrained glass cards, ivory text, muted secondary text, silver/white accents, 20-30 dp rounded corners, real icons, compact but readable hierarchy, and a large soft dock.

Current Compose screens now target the black/gray/white/silver palette and rounded cards, but runtime parity is not proven without physical-device acceptance. Reference-critical gaps include the home composition, duplicate strip, true Theme Studio controls/preview, photo thumbnails, Files information hierarchy, and the dark App Library treatment.

## App icon audit

The launcher icon now uses the final supplied PNG: a black rounded tile with an ivory/cream `C` and premium sparkle. The white outside-corner background was made transparent, adaptive-icon foreground/background XML was wired to the bitmap artwork, and mdpi through xxxhdpi launcher PNGs were generated for legacy launchers.

## Documentation audit

- `PROJECT_AUDIT.md` was stale and contained incorrect claims.
- `TESTING.md` says some pending behavior is already wired and documents a custom APK name rather than the contract's expected default path.
- `SECURITY.md` omits several declared permissions and overstates feature behavior.
- Root `BUILD_PLAN.md` covers the React prototype, not the Android product contract.
- `README.md` and `IMPLEMENTATION_PLAN.md` are missing from `ciyato-android/`.
- Several files contain mojibake, which should be removed from user-facing strings and documents.

## Highest-priority risks

1. Restore the required Duplicate Smart Shortcuts UI and remove dead home controls.
2. Implement a durable Visible/Hidden/Removed model plus management and restore UI.
3. Replace the fake Photos permission toggle with Photo Picker and selected-media state.
4. Make Files state durable and remove fake storage/cleanup claims.
5. Wire unified Search navigation and label staged data honestly.
6. Make Theme Studio controls actually affect the UI or remove/defer them clearly.
7. Reduce the manifest to permissions required by tested beta flows.
8. Fix unit tests and perform emulator/device interaction testing.
9. Update all release, security, build, testing, and switch-back documentation.
