# Ciyato — Claude Implementation Ledger

Tracks every finding from `Ciyato_FINAL_COHESIVE_Master_Audit_2026-08-16.docx` (Revision III)
from confirmation through to verified implementation.

| Field | Value |
|---|---|
| Audited SHA | `5773448cbd2d189055021b319d54c74ae2c779bf` |
| HEAD at start of implementation | `5773448cbd2d189055021b319d54c74ae2c779bf` — **identical**, so pinned locators apply directly |
| Baseline tag | `baseline-audit-5773448` |
| Repository | `CSduo/Ciyato` |
| Implementer | Claude, single context, no agents or delegated model calls |

## Status vocabulary

| Status | Meaning |
|---|---|
| `CONFIRMED` | Located in current source; the defect is real and reproducible from the code |
| `RECLASSIFIED` | Real, but the severity, cause or scope differs from the audit's description — reason recorded |
| `NOT APPLICABLE` | Code has changed or the premise does not hold; evidence recorded, no patch applied |
| `IMPLEMENTED` | Fixed, compiled, tests green |
| `VERIFIED` | Acceptance condition from the audit demonstrably met |
| `BLOCKED` | Needs a device, a Play account, or a decision only the publisher can make |
| `DUPLICATE-OF` | Part VII restatement of an earlier finding; fixed once, evidenced against the primary |

## Root-cause grouping

The audit's own instruction is explicit: *"If two findings point to the same root cause, fix the
root cause once and mark both findings with evidence. Do not make 210 disconnected cosmetic
edits."* Groups are recorded here as they are established, so that a single fix closes every
finding it genuinely resolves.

Initial observation, to be confirmed finding-by-finding: **F-171 through F-210 appear to be
Part VII traceability restatements** of findings in F-001–F-170 rather than 40 new defects — e.g.
F-185 restates F-002 (targetSdk), F-189 restates F-003 (debug signing), F-198 restates F-095
(rolling "today"), F-200 restates F-094 (400 ms delay), F-204 restates F-101 ("Keep Best"),
F-205 restates F-113 (double-counted bytes), F-206 restates F-005 (dead release URL). Each will
be marked `DUPLICATE-OF` only after the restatement is checked against its primary, never assumed.

## Findings

| ID | Severity | Status | Current location | Batch | Evidence |
|---|---|---|---|---|---|
| F-001 | P0 | IMPLEMENTED | `.github/workflows/android-debug.yml` | B0 | Confirmed: `working-directory: ciyato-android` at lines 25/29 and stale APK path at 35; `git ls-files` shows **zero** tracked files under `ciyato-android/`, and `gradlew` is at the repo root. Every run died before Gradle, so no commit had automated proof of compilation. Workflow rewritten: stale working-directory removed, root `gradlew` used, and `testDebugUnitTest` + `lintDebug` + `assembleDebug` now run as separate named steps (previously only `assembleDebug` ran, so tests and lint were never executed at all). Reports upload on failure. Pending: green run on GitHub, then mark as required check. |
| F-002 | P0 | CONFIRMED | `app/build.gradle.kts:10,15` | — | `compileSdk = 34`, `targetSdk = 34`. Play submission requirement moves to API 36 on 31 Aug 2026. Scheduled for Phase 1. |
| F-003 | P0 | CONFIRMED | `app/build.gradle.kts:59` | — | `signingConfig = signingConfigs.getByName("debug")` inside the release block. Release artifacts are debug-signed. Phase 1. |
| F-007 | P0 | CONFIRMED | `data/FileAccess.kt:85-89` | — | `shareableUri` ends `.getOrDefault(uri)`, returning the raw `file://` URI when `FileProvider.getUriForFile` throws — i.e. it fails **open**, defeating the helper's entire purpose. Note: this line is my own work from 16 Aug; the audit caught a defect I introduced. Fix is the sealed `ShareableUriResult` in the audit's recipe C so callers must handle failure. |
| F-006 | P1 | IMPLEMENTED | `res/xml/file_paths.xml` + `data/FileAccess.kt` | B2 | RECLASSIFIED in approach: narrowing `<external-path>` would break opening ordinary files, since a file manager must reach wherever the person browsed. The constraint moved to the only code that mints these URIs — `shareableUri` canonicalises (resolving `..` and symlinks) and refuses anything outside shared storage or Ciyato's own dirs. Reasoning recorded in the XML so a later edit doesn't undo it. |
| F-007 | P0 | IMPLEMENTED | `data/FileAccess.kt` | B1 | Was `.getOrDefault(uri)` — failed **open**, returning the raw `file://` URI the helper existed to eliminate. Now a sealed `Shareable` result the compiler won't let a caller ignore. All five call sites delegate to a single new owner, `FileAccess.openExternally`, which also folded in three drifted copies of the same intent logic. **My own regression from 16 Aug.** |
| F-009 | P0 | IMPLEMENTED | `data/PhotoBackupWorker.kt` | B3 | Destination was `Ciyato_Backup_<timestamp>`, new folder per run, so any photo seen twice was copied twice into two places. One reused folder + per-file existence check (names read once, not a query per file). Re-running is now genuinely a no-op. |
| F-010 | P0 | IMPLEMENTED | `data/PhotoBackupWorker.kt` | B3 | `done++` sat outside the stream blocks; either stream can be null, so `?.use` was skipped silently and the count still rose — "200 photos saved" with zero bytes written, then the watermark advanced past all 200. Counts only after bytes move and flush; failed copies delete their empty placeholder (otherwise the next run skips the name forever); watermark advances only on a complete run; worker returns `retry()` not `failure()`. 8 regression tests. |
| F-012 | P1 | IMPLEMENTED | `data/DuplicatePhotoDetector.kt` | B4 | Cap kept (hashing is decode-bound) but no longer hidden: `SCAN_LIMIT` is public and `DuplicateScan` carries `scanned`/`libraryTotal`/`wasBounded`, which the UI states. |
| F-013 | P1 | IMPLEMENTED | `data/DuplicatePhotoDetector.kt` | earlier | Threshold 10→5 of 64 bits; renamed `pHash`→`averageHash` because there was never a DCT. Documented as the weaker thing it is. |
| F-022 | P0 | IMPLEMENTED | `ui/screens/OnboardingScreen.kt` | B2 | "Everything stays on this phone. No accounts, no uploads." was false — weather sends coarsened location to 3 hosts, breach check sends a hash prefix. Now local-first and names both exceptions. |
| F-023 | P0 | IMPLEMENTED | `ui/screens/SafeBrowsingHelperScreen.kt` | B2 | `endsWith` matched raw characters, not DNS label boundaries, so `evilgoogle.com` was declared safe. `hostMatchesDomain` requires a label boundary. A host merely *containing* a famous name is now itself a warning sign. |
| F-024 | P0 | IMPLEMENTED | `ui/screens/SafeBrowsingHelperScreen.kt` | B2 | The type could express a verdict it had no basis for: no heuristic hit → `Safe` → "No threats detected. Safe to open." `UrlCheck` replaces it and **cannot say safe**; signals are collected not short-circuited; the no-signal case states explicitly that no reputation service was contacted. |
| F-059 | P2 | IMPLEMENTED (partial) | `ui/screens/SafeBrowsingHelperScreen.kt` | B2 | Emoji removed from security verdicts — "✅ URL is Safe" reads as "white heavy check mark" or is skipped. Other screens still to sweep. |
| F-098 | P1 | IMPLEMENTED | `ui/screens/NlFileSearchScreen.kt` | B1 | Open was wrapped in a bare `runCatching` that discarded the exception, so an unopenable file looked like a dead row. Now reports the reason. |
| F-100 | P0 | IMPLEMENTED | `ui/screens/DuplicatePhotoCleanupScreen.kt` | B4 | Card rendered `take(3)`, button deleted `size - 1` — a group of 8 destroyed 5 photos never shown. All photos displayed; `trashAllExcept(group, keep)` takes the survivor as an argument so code and UI cannot drift again. |
| F-101 | P1 | IMPLEMENTED | `ui/screens/DuplicatePhotoCleanupScreen.kt` | B4 | "Keep Best" equated largest file with best photo. Survivor is now tappable, defaulted to largest, each candidate's size shown so the default is verifiable. Button reads "Trash N". |
| F-102 | P1 | IMPLEMENTED | `ui/screens/DuplicatePhotoCleanupScreen.kt` | B4 | "No duplicates found!" was a whole-library claim from a 500-photo scan. Every count now qualified with real coverage. |
| F-103 | P1 | IMPLEMENTED | detector + cleanup screen | B4 | Vocabulary separated: "look-alike", "Similar is not identical — check each group before trashing". Nothing claims proof of duplication, which an average hash cannot give. |
| F-163 | P1 | CONFIRMED | `ui/screens/OnboardingScreen.kt` | — | Confirmed **visually** on API 36: "AI Phone Organizer" appears twice in the very first frame a new user sees. |
| — (lint) | P0 | IMPLEMENTED | `res/values/themes.xml` → `res/values-v27/` | B1 | `android:windowLightNavigationBar` is API 27, `minSdk` 26 — a lint **error**, so the build failed. Real hazard: on API 26 that attribute id is unclaimed and OEMs have reused unclaimed ids. Moved to a versioned folder. |
| — (new) | P2 | CONFIRMED | onboarding preview card | — | Found by screenshot, not source: the preview card clips its own content — "Smart categories" cut mid-card by the pager edge, "Organized apps" sliced, page dots overlaying the clipped area. |

Remaining findings are appended as each is located and confirmed against current source, rather
than pre-filled from the document — an unverified row in this table would be exactly the kind of
false completion signal the audit warns about.

## Newly discovered defects (not in the audit)

Recorded here when implementation uncovers something the audit did not list, per the "no blind
checklist patching" rule.

| ID | Severity | Status | Location | Notes |
|---|---|---|---|---|
| N-01 | **P0** | IMPLEMENTED (B22) | `ui/components/CiyatoInputs.kt` | **A password field that never masked its password.** `CiyatoPasswordField` set a password keyboard and drew a reveal toggle, but `CiyatoInputField` had no `visualTransformation` parameter, so the text rendered in the clear at all times and `showPassword` only chose which eye icon to draw. Both `PasswordVisualTransformation` and `VisualTransformation` were already imported in that file and never referenced — the masking was intended and lost. It had not shipped a visible password only because nothing called it. Parameter added to the primitive; the field now masks; the real screen was moved onto it so it cannot go dormant again. |
| N-02 | P1 | IMPLEMENTED (B22) | `ui/screens/DataBreachCheckerScreen.kt` | The HIBP request omitted `Add-Padding`, so every prefix returned a differently-sized response and an on-path observer could narrow the queried prefix from the byte count — while the screen told the user the check was privacy-safe. k-anonymity protects the hash; padding protects the query, and the claim needs both. Header added, plus the consequence it carries: padded responses contain synthetic hashes with a count of zero, which would otherwise have been reported as "breached 0 times". |
| - (lint triage) | P1 | IMPLEMENTED (B26) | tree-wide | The 142 untriaged warnings carried here since 16 Aug were categorised rather than counted. 60 were "a newer version exists" notices carrying no defect. The other 81 collapsed into five causes, all fixed. **141 -> 62 warnings; all 62 remaining are dependency-version notices.** |
| N-06 | P1 | IMPLEMENTED (B26) | `res/drawable/` -> `res/drawable-nodpi/` | 7.5 MB of wallpaper JPEGs sat in the densityless `drawable/` folder, so Android treated them as mdpi and scaled to device density: a 4.2 MB decode becomes 67.6 MB on an xxxhdpi phone, 16x the pixels. Coil downsampling is why this was survivable rather than an OOM. |
| N-07 | P1 | IMPLEMENTED (B26) | `res/mipmap-anydpi/`, `res/drawable-nodpi/ic_launcher_*.png` | Launcher icon mis-authored: logo spanned 65.4% of the canvas against a 61% safe zone **and sat 38px off-centre**, so a circular mask clipped it. Foreground was ink premultiplied onto black rather than a real alpha channel, and there was no `<monochrome>` layer - Ciyato, a launcher, was the one icon that would not theme on Android 13+. Alpha recovered, logo fitted by measured radius to the 66dp safe circle, monochrome generated, verified against circle/squircle/square/themed masks. |
| N-08 | P1 | IMPLEMENTED (B26) | `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml` | `allowBackup="false"` answers the cloud question only. Android 12+ device-to-device transfer defaults to copying everything, and the secure vault is AES-GCM ciphertext under a hardware-bound Keystore key that cannot leave the device - a transfer would have delivered permanently unopenable files while reporting success. Vault and crash logs excluded on both routes. |
| N-09 | P2 | IMPLEMENTED (B26) | `data/ByteFormat.kt` (new) | Four byte formatters disagreed: the same size rendered "1.5 GB", "1.50 GB" or "1.5GB" by screen, one pinned to `Locale.US` regardless of phone language. One implementation now; the previous four delegate, unifying ~40 call sites without editing any. |
| N-10 | P2 | IMPLEMENTED (B26) | `ui/screens/WallpaperPickerScreen.kt` | Header read "Featured 4K Ciyato Wallpapers". Measured: every one is 768x1376 - below 1080p, and below the screen of the phone showing it. |
| F-021 / F-148 | P0 | IMPLEMENTED (B25) | `viewmodel/LauncherViewModel.kt`, `ui/screens/AppLockScreen.kt`, `ui/screens/LockedAppsScreen.kt` (new), `ui/components/AppContextMenu.kt` | **Built, not archived.** `AppLockGate` had no caller, no preference, and no way to mark an app locked. The audit's acceptance test — "every Ciyato launch path shares one policy; copy never implies OS-wide lock" — is met by gating in `launchApp`, which all 16 launch sites already call, so a seventeenth added later is gated by default. Scope is stated before enabling, on the gate, and again on the management screen. |
| N-05 | P1 | IMPLEMENTED (B25) | `ui/screens/ContextualSuggestionsScreen.kt`, `data/WeatherRepository.kt`, `LauncherHomeActivity.kt` | Three launch paths built their own intents and so bypassed **Focus blocking as well as** App Lock — a suggestion card would open an app the rest of Ciyato was actively refusing to open. All now go through `launchPackage()`. `launchSystemWeatherApp` became `findSystemWeatherPackage`, moving the launch decision to the one place that owns it. |
| F-036 / F-142 / F-177 | P1 | IMPLEMENTED (B24) | `ui/theme/Theme.kt`, `ui/theme/MaterialYouSupport.kt`, `ui/screens/VoiceCommandScreen.kt`, `LauncherViewModel`, `LauncherSettingsRepository` | The audit's own prescription — "prefer one honest dark-only contract unless full theme coverage is funded" — applied rather than a half-wired light mode. Both roots hard-coded dark; `viewModel.darkMode` had **zero readers**; the only writers were two voice commands, one of which announced "Light mode enabled." while nothing changed. `CiyatoTheme` no longer takes `darkMode`/`dynamicColor`, the unreachable light and Material You branches are gone, the preference is removed end to end, and the voice commands answer honestly. Full light coverage would mean theming ~150 palette references across every screen — recorded as a real project, not a flag. |
| N-04 | P1 | IMPLEMENTED (B24) | `ui/components/SearchBar.kt`, `ui/screens/AppDrawerScreen.kt` | Found while closing F-036, and shipping: `CiyatoSearchBar` defaulted to a warm cream fill with near-black text for a light drawer that does not exist. AI Search takes the defaults, so it rendered a cream pill inside the near-black screen. The App Drawer overrode all five colours — what a wrong default looks like when one caller has noticed. Defaults are the dark palette now; the drawer's overrides were deleted as redundant. |
| F-077 / F-112 | P1 | IMPLEMENTED (B23) | `data/PhotoDeviceLibrary.kt`, `ui/screens/PhotosLibraryScreen.kt`, `ui/screens/PhotoCollectionsScreen.kt` (deleted) | Two separate photo products, each with its own permission handling, its own partial-access logic, its own collection building. Root cause was the model, not the screens: `PhotoDeviceLibrary` carried photos only, so anything wanting videos had to build a parallel gallery. `DeviceImage` now represents photos and videos alike, `collections()` gained Videos and month buckets — the two things the second screen existed for — and `PhotoCollectionsScreen` is gone, with all three entry points folded into Photos. |
| N-03 | P1 | IMPLEMENTED (B22) | `ui/screens/SettingsScreen.kt` | Bedtime slider called `setBedtimeHour()` on every drag event, so one adjustment wrote to DataStore dozens of times — and "Done" had nothing to confirm, since the value was already saved and dismissing the dialog kept a change the user never accepted. Drag is local; Done commits; Cancel discards. |
