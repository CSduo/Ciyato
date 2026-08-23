# Ciyato — Removal / Salvage Ledger

Every screen and helper considered for removal, merge, rewrite or retention, with the reachability
evidence behind the decision and what infrastructure was preserved.

Baseline: `5773448cbd2d189055021b319d54c74ae2c779bf`.

## Rules I am bound by

1. **Reachability must be proven, not assumed.** Compose routes are strings, manifest components
   are class names, and reflection hides usage — a zero-hit text search is a *candidate*, not a
   verdict. Every removal records the searches run.
2. **Delete the UI, keep the infrastructure** where the infrastructure is genuinely good. The
   inverse also holds: do not preserve bad UI because its plumbing is useful.
3. **One feature per removal commit** where practical, so a regression is bisectable.
4. A permission, dependency or resource existing *only* for a removed feature goes in the same
   commit.
5. Anything retained as Labs gets a capability statement and a test — not just a label.
6. **A feature is not kept because it compiles, or because it once had a "Suggestion #" number.**

## Status vocabulary

`KEEP` · `FIX` · `REDESIGN` · `MERGE` · `COMPLETE` · `LABS` · `ARCHIVE` · `DELETE` · `PENDING`

## Disposition register

Audit recommendation recorded first; my decision recorded only after I have read the code and
verified reachability myself. Where I disagree with the audit, the reasoning is stated.

| Component | Audit recommendation | My decision | Reachability evidence | Salvaged |
|---|---|---|---|---|
| `SecureFileVaultScreen` + `VaultCrypto` | REWRITE before routing, or archive | **COMPLETE** (B14) — export/open added, delete confirmed | Audit: absent from both root route graphs. My own scan agreed (0 callers). To re-verify. | AES-GCM/Keystore path, atomic temp+rename, migration concept |
| `AppLockScreen` / `AppLockGate` | ARCHIVE or relabel launcher-only | PENDING | `AppLockGate` referenced only at its own definition; `appLockPackages` never read; `launchApp` never checks it. Advertised in the changelog. | Biometric prompt helper, FragmentActivity unwrapping |
| `WidgetHostScreen` | REBUILD as Home CanvasItem integration | **REBUILD** — kept; ID leak + provider configuration fixed (B13), Home-placement claim corrected. Home integration still outstanding. | Reachable, but Home hosts no widgets, so its central claim is false | Provider listing, binding, persisted IDs |
| `PhotoCollectionsScreen` | MERGE into PhotosLibrary, then delete route | PENDING | Reachable duplicate Photos product | Video/month collection builders, action sheet |
| `ContextualSuggestionsScreen` | RENAME to Frequent Apps or rebuild buckets | **RENAMED** (B20) — surfaced as "Frequent Apps" under Insights, described as ordering not prediction | Reachable; claims time-of-day learning it does not measure | UsageStats plumbing |
| `AnomalyDetectionScreen` | MERGE into Usage Insights | **UNDER INSIGHTS** (B20) — surfaced as "Unusual Usage"; body-level tab merge outstanding | Reachable; statistically invalid (partial day vs full days) | Aggregation shell |
| `AiChangelogScreen` | MERGE into Usage Insights | **UNDER INSIGHTS** (B20) + data fixed (B10); body-level tab merge outstanding | Reachable; weekly "average" overwrites per package; fake 600 ms delay | Digest card UI |
| `AppUsageStatsScreen` | SALVAGE into Usage Insights | **UNDER INSIGHTS** (B20) — now reachable from BOTH hosts; body-level tab merge outstanding | Candidate orphan; well built | Per-app usage rendering |
| `AiDailyAgendaScreen` | MERGE useful summary into Agenda | PENDING | Candidate orphan; not AI | Summary card patterns |
| `StressFreeModeScreen` | REMOVE inference; maybe keep breathing tool | **DONE** (B10) — inference deleted, breathing kept, retitled "Breathing" | Candidate orphan. Inferring stress from usage heuristics is not defensible at any polish level. | Breathing exercise UI only |
| `GuestModeScreen` | REMOVE or rename with explicit allowlist | **DELETED** (B11) | Zero references confirmed by my own scan. Cannot provide the boundary its comments claim. | Restricted layout idea |
| `PrivacyDashboardScreen` | MERGE accurate logic into Permission Audit | **OFF MAIN THREAD** (B12); merge still outstanding | Candidate orphan; duplicates Permission Audit; enumerates packages on the UI thread | Granted-permission extraction (it does check the granted flag correctly) |
| `NetworkUsageScreen` | MOVE to Labs → **doing now** (B16) | stale "billing cycle" doc corrected (B12) | Candidate orphan; says "billing cycle" but queries 30 days | NetworkStats aggregation |
| `DocumentScannerScreen` | Integrate as Tool after fixes, or remove | PENDING | Candidate orphan; `TakePicturePreview` yields a thumbnail, not a scan | PDF assembly baseline |
| `TFLiteCategorizerHelper` | DELETE from production | **DELETED** (B11) | Contains no TensorFlow Lite execution at all | Design notes → docs only |
| `OnDeviceEmbeddingsHelper` | DELETE or rename | **DELETED** (B11) | TF-IDF bag-of-words called "embeddings"/"semantic"; index in memory only | Prototype value only |
| `AIOptimizerManager` | DELETE; fold cache cleanup into Storage Cleanup | **DELETED** (B11) | RECLASSIFIED — was instantiated and wrapped; the wrapper had no caller | Cache scanner/deleter |
| `MultiPageHomeScreen` | DELETE after verification | PENDING | Redundant pager beside the real Home pager | Generic pager snippets at most |
| `HealthConnectWidget` | — | **DELETED** (B26) | Resolved: zero references anywhere; no Health Connect dependency in the build file, no Health Connect permission in the manifest, and no code that reads a record. Its doc claimed "Steps and heart rate from Health Connect ... Requires READ_STEPS and READ_HEART_RATE" for an integration that does not exist in any form. | Nothing. It rendered a `HealthData` object whose fields default to zero. |
| `CiyatoNotificationListener` (in `NotificationBadge.kt`) | — | **DELETED** (16 Aug) | Second, duplicate `NotificationListenerService` shadowing the real one in `services/`; neither was declared | Real service kept and declared; `isNotificationListenerEnabled` repointed at it |

## Completed removals

| Component | Commit | Reachability proof | What was preserved |
|---|---|---|---|
| `TFLiteCategorizerHelper` (71 lines) | B11 | Referenced from exactly one place in the tree: a *comment* in `AppCategorizer`. Contains no TensorFlow Lite execution at all — the name was the entire feature. | Nothing. The rule-based categorizer it "delegated to" is untouched; the stale comment naming it was corrected. |
| `OnDeviceEmbeddingsHelper` (110 lines) | B11 | Zero references anywhere. | Nothing. It called TF-IDF bag-of-words "embeddings" and "semantic search", kept its index in memory only, and was never wired to a search path. |
| `AIOptimizerManager` (47 lines) | B11 | **RECLASSIFIED.** The audit called it "explicitly unreachable", and it is — but not for the stated reason: it *was* instantiated in `LauncherViewModel` and wrapped in `optimizeSystem()`. Nothing ever called that public entry point, so the chain compiled and looked live while being dead from the UI down. | Capability, not code: it deleted `.log`/`.tmp` files over 500KB from Ciyato's own cache. Storage Cleanup's Cache category already does this more thoroughly — internal *and* external cache dirs, with sizes shown and confirmation before deleting. |
| `GuestModeScreen` (188 lines) | B11 | Zero references outside its own file; no route in either activity. | Nothing. Its doc promised "No access to hidden apps, files, settings, or personal data", which a launcher screen cannot enforce — anything reachable from Recents, a notification or another launcher bypasses it entirely. Android's real multi-user Guest profile provides that boundary; imitating it in-app is a security claim with nothing behind it. |
| `CiyatoNotificationListener` duplicate class | `828f473` | Two `NotificationListenerService` subclasses existed; neither declared in the manifest, so Android bound neither and `badgeCounts` was permanently empty | `CiyatoNotificationListenerService` retained, declared in the manifest, and the enabled-check now targets it |

### B37

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `DocumentScannerScreen` (9.3 KB) | **REBUILT and WIRED UP** as `PhotosToPdfScreen` | Zero references — unreachable, like the rest of B35's sweep. | Not deleted, because the *capability* is worth having and three of its four findings were fixable defects. The fourth was the name: `TakePicturePreview()` returns a thumbnail, and there was no edge detection or perspective correction, so "scanner" was a claim the code could not meet. The audit allows exactly this — "otherwise name it Photos to PDF". |

### B35 - the unreachable third of the UI

A whole-tree reachability sweep (every declared composable/object/class checked
against every other file, plus the manifest for classes Kotlin never names)
found **seven complete screens and seven widget components with no entry point**.

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `SecureFileVaultScreen` (16.4 KB) | **WIRED UP, not removed** | Zero Kotlin references, zero manifest references - unreachable since it was written. | The opposite call from the rest, and the important one. It is complete and working: AES-256-GCM under an AndroidKeystore key, biometric gate, import and decrypt. And `STORE_READINESS.md` and `data_extraction_rules.xml` **both describe it as shipping** - both written by me in B34, documenting a feature no user could open. Deleting it would have made the docs true by making the product poorer. Now reachable from Settings in both shells. |
| `PrivacyDashboardScreen` (11.5 KB) | **SALVAGED then DELETED** | Zero references. Duplicates `PermissionAuditScreen` (live, 2 routes): both group apps by declared sensitive permissions. | Same shape as the Photos duplication (F-077). Its unique element - a link to Android's own Privacy Dashboard - moved into the live screen first, and is the more useful half: the OS reports permissions actually *used*, where Ciyato can only read what is *declared*. |
| `HiddenVaultScreen` (6.6 KB) | **DELETED** | Zero references; superseded by `AppVisibilityScreen(mode = Hidden)`, which owns the route and the launcher destination. | Nothing unique. |
| `AgendaScreen` (5.7 KB) | **DELETED** | Zero references; the `agenda` route opens `CalendarAgendaScreen`. | Nothing unique. |
| 7 widget components (~25 KB) - Media controls, Battery, Stock/Crypto, Countdown, World clock, News headline, Daily affirmation | **DELETED** | Zero references each. `WidgetHostScreen` is routed and live and **hosts none of them**. | Rendered mock-ups: a stock widget with no market data, a news widget with no feed. Building the host to match would mean building seven integrations; keeping them claimed seven features that did not exist. |

### B26 - orphans surfaced by lint triage

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `RtlSupportHelper` (whole file) | **DELETED** | Zero references anywhere. | Also a false capability claim: `SUPPORTED_RTL_LANGUAGES` advertised Arabic, Hebrew, Farsi and Urdu with native display names, while the app ships exactly one English `values/strings.xml` and no translation folders. Switching locale would have produced English text in RTL layout. `android:supportsRtl="true"` already gives Compose real RTL from the system locale. |
| 9 unused strings in `values/strings.xml` | **DELETED** | Lint `UnusedResources`, which scans Kotlin and XML both. | Remnants of an abandoned string-resource approach - every screen hard-codes its copy (F-058, still open). Two mattered: `app_description` and `privacy_statement` held the exact text corrected under F-163 and F-022, so unreferenced copies risked a future wiring-up restoring claims already found untrue. |
| legacy launcher PNGs, `mipmap-anydpi-v26/`, `ic_launcher_art.png` | **DELETED** | `anydpi` outranks every density qualifier, and adaptive icons require API 26 which is minSdk - the density PNGs could not be selected on any supported device. | Replaced by one adaptive icon in `mipmap-anydpi/` (no version qualifier needed at minSdk 26) with a real monochrome layer. |

### B25

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `AppLockGate` | **SALVAGED — feature built** | Zero callers, and no `lockedApps` preference existed at all. | Archiving was the audit's fallback, not its preference. The gate itself was sound; what was missing was a policy, storage and an entry point. Now wired through one launch gate. |
| `QuickSwitchManager` (whole file) | **DELETED** | Zero references anywhere. | An unreachable "switch to previous app" helper that also launched by raw intent, so wiring it up later would have quietly reintroduced a launch path outside the policy. |

### B24 — the theming that had no consumer

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `SeasonalThemeManager` (62 lines) | **DELETED** | Zero references anywhere in the tree. | A dead chain of the same shape as `AIOptimizerManager`: it looked live because it called `ThemePresetExporter`, but nothing called it. It also promised appearances the app cannot render — "Spring Fresh — Light, airy, and calm" and "Summer Vibes — Clean and bright" set `darkMode = "light"`, covering roughly half the calendar year. |
| `ThemePresetExporter` (84 lines) | **DELETED** | Referenced from exactly one place: `SeasonalThemeManager`, itself dead. | Serialisation for a theme model the app does not have — presets carry `darkMode` (no effect), fonts "inter"/"poppins" (only sans/serif/mono exist), and a "Minimal White" preset that cannot render. **Not a lost capability so much as an unbuilt one**: shareable theme presets are a reasonable future feature, and building it should start from the settings that exist rather than from this. |
| `CiyatoLightColorScheme` + dynamic colour selection | **DELETED** | `ciyatoColorScheme` had one caller, which passed a hard-coded "dark" and `dynamicColor = false`, so the selector had exactly one possible outcome. | `CiyatoDarkColorScheme` retained — Material 3's own components read it even though no Ciyato screen does. |
| `CiyatoLightBg/Card/Border/Text/Sec` palette | **DELETED** | Three had zero references; the other two survived only as wrong defaults on `CiyatoSearchBar` and a stale comment in `AppIconView`. | Nothing. See N-04 — leaving them was actively producing a visual defect. |

### B23 — the second photo gallery

| Component | Disposition | Reachability proof | What was preserved |
|---|---|---|---|
| `PhotoCollectionsScreen` (24 KB) | **DELETED** | Three live entry points — a Settings row, a launcher destination, and a nav route — all of which now open Photos. Reachable, but redundant: it duplicated permission handling, partial-access detection and collection building against the same MediaStore. | Its two genuine capabilities. Videos are ordinary library items now, and month buckets ("Memories") are built by `PhotoDeviceLibrary.collections()`. The video-thumbnail decode path it introduced is reused by the Photos grid. |

### B22 — the unused half of the input design system

Reachability was measured per component rather than per file, because the file itself is live:
`CiyatoSettingSwitch` has 10 call sites and `CiyatoSwitch` is used internally by it.

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `CiyatoSlider` | **DELETED** (43 lines) | Zero call sites anywhere in the tree. | Not merely unused — a trap. Its contract fires `onValueChange` on every drag frame, which is precisely the defect fixed in the two real sliders this batch. The next screen to adopt it would have reintroduced per-frame DataStore writes. The two live sliders have materially different layouts, so there was nothing to absorb. |
| `SettingsSlider` (private, `SettingsScreen.kt`) | **DELETED** (16 lines) | Zero call sites; private, so the file itself is proof. | Same per-frame contract, dead. |
| `CiyatoPasswordField` | **SALVAGED** | Zero call sites, while `DataBreachCheckerScreen` hand-rolled its own password field — the duplication the design system exists to prevent. | Deleting it would have left the duplicate and buried a P0: the component did not mask its input (see N-01). Fixed and adopted by the breach screen instead, so the design-system field is now exercised by a real screen. |

## Preserved infrastructure (do not delete while refactoring)

The audit is explicit that these are good decisions to keep: SAF-first storage, system-owned
MediaStore trash and consent flows, local-only crash logs, coarsened weather location, fail-closed
authentication, and the restrained near-black/graphite/silver visual direction.
