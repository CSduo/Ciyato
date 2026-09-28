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

## Superseded by the Home widget integration (F-138 / F-179 / F-180)

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `PlacedWidgetStore` (whole file, 31 lines) | **DELETED** | Its only caller was `WidgetHostScreen`, which now uses `WidgetPlacementStore`. `grep` over the tree returns no other reference. | It persisted a bare array of AppWidget IDs, which cannot carry a size, and a widget placed on Home needs one that survives a restart. Not a rename: `WidgetPlacementStore.parse` still reads the old `[1,2,3]` format, and a migration test pins that. An AppWidget ID is allocated against a host and is not re-derivable, so a parser that silently dropped the old format would have made every already-placed widget vanish from Home **and** stay allocated in the system, reachable by nothing. |
| `PlacedWidget` (data class in `WidgetHostScreen.kt`) | **SALVAGED** | Same file, one screen. | It held an `AppWidgetProviderInfo` alongside the ID, which forced every consumer to resolve provider info before it could hold a record at all. Replaced by `WidgetPlacement`, which stores only what is durable (ID and size) and resolves provider info live — a provider can change or vanish between sessions. |

## Unrouted screens, disposed of rather than wired (F-170 / F-208)

Five screens compiled, looked like features in the source tree, and could not be opened.
Leaving them was the risk: the obvious next task for anyone reading an unwired screen is to
wire it, which is how a deliberately-dropped feature comes back.

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `NetworkUsageScreen` (289 lines) | **ROUTED** | Referenced by no file but its own. | It works, and it runs on the same Usage access grant as everything else under Insights. F-130's rule is one door for everything that permission buys, so it became the fifth entry there rather than its own Settings row. Now reachable from both hosts and restorable after process death. |
| `CustomGreetingScreen` (182 lines) | **DELETED** | Zero call sites. | Not merely unrouted — it could not have worked. Its only state was a file-level private `MutableStateFlow` in `LauncherViewModelExtensions`, read through a plain getter: never persisted, so it died with the process; never collected, so Compose would not have recomposed on change; top-level rather than per-instance, so every ViewModel shared one value. Routing it would have shipped a screen where you type a greeting and nothing happens. The phantom state went with it. |
| `AiDailyAgendaScreen` (209 lines) | **DELETED** | Zero call sites. | A second “today's summary”, duplicating `AiChangelogScreen`, which is routed from Insights and has had the honesty work done on it (F-125, F-126). Keeping both would mean two screens to fix every time the definition of “today” changed — which it already did once. |
| `BulkDeleteFilesScreen` (278 lines) | **DELETED** | Zero call sites. | `PhotosLibraryScreen` already has multi-select with system delete consent, trash-versus-purge and undo, and it is the one people reach. A second bulk deleter is a second place for a destructive path to be wrong. |
| `StressFreeModeScreen` (216 lines) | **DELETED** | Zero call sites. | Once F-131 removed the CALM/MILD/STRESSED verdict it inferred from the clock and a recent-app count, what remained was a paced breathing exercise. It is a nice thing and it is not a launcher feature; `PLAY_POSITIONING.md` is explicit that breadth without hierarchy is what makes a restricted-permission argument implausible. Its KDoc still described the removed signals as though it acted on them, which was F-132. |

Restoring any of these means re-deciding the product question, not just reverting a commit.

## Accessibility helper, superseded (F-047)

| Component | Disposition | Reachability proof | Reasoning |
|---|---|---|---|
| `Modifier.appItemSemantics` | **DELETED** | Zero call sites anywhere in the tree — the only occurrence was its own declaration. | Not merely unused, wrong in two ways, which is worse: it appended "Double-tap to open. Long-press for options." to the content description, duplicating what TalkBack already announces for a Button role while leaving the long-press unreachable by switch access; and it did not merge descendants, so a tile's label `Text` announced a second time after the description. A dead helper with a wrong contract is a trap — the next person needing app-tile semantics would have found it, used it, and shipped both defects. Replaced by `appTileSemantics` / `AccessibleAppTile`, which is adopted at all five interactive surfaces. |

## Preserved infrastructure (do not delete while refactoring)

The audit is explicit that these are good decisions to keep: SAF-first storage, system-owned
MediaStore trash and consent flows, local-only crash logs, coarsened weather location, fail-closed
authentication, and the restrained near-black/graphite/silver visual direction.

## Unreachable code sweep — 16 files and 29 declarations

Rule 1 requires the searches, so here they are. Every name below was checked against:
every `.kt` file under `app/src/main` (with comments stripped, so a KDoc mention is not
counted as a call site), every file under `app/src/test` and `app/src/androidTest`,
`AndroidManifest.xml`, `app/src/main/res/`, the `:macrobenchmark` module, and
`proguard-rules.pro` for `-keep` rules. Nothing matched, and the compiler is the final
arbiter — which is the point of the next paragraph.

**The searches were not sufficient on their own, exactly as Rule 1 warns.** I read my own
sweep output wrongly and deleted `WeatherAgendaRow.kt`, which also declares `WeatherCard`
and `AgendaCard` — both live, both used by `HomeScreen`. The script had correctly excluded
that file; I overrode it. The build broke on the next compile and it was restored. Recorded
because the near-miss is the argument for the rule: a candidate list is a starting point,
and the only verdict that counts is a green build.

### Legacy from the V1 → V2 migration

The archived `V2_IMPLEMENTATION_AUDIT.md` named `SwipeableHomeDrawer` and
`MultiPageHomeScreen` as removal candidates "only after their dependencies and navigation
references are mapped". That mapping is the paragraph above, and both had zero references.
`MultiPageHomeScreen` is a *paged* home, which the canvas model supersedes outright.

| Removed | Lines | Why not retained |
|---|---|---|
| `ui/screens/MultiPageHomeScreen.kt` | 85 | A paged Home. The canvas replaced the model, not just the implementation. |
| `ui/components/SwipeableHomeDrawer.kt` | 108 | Superseded drawer; the live one is elsewhere and reachable. |

### Duplicate implementations of something already live

These are the ones worth noticing, because each was a second, unused answer to a question
the app had already answered — and in every case the live answer was better.

| Removed | Lines | Superseded by |
|---|---|---|
| `data/VideoThumbnailHelper.kt` | 96 | `PhotoDeviceLibrary.loadVideoThumbnail`, which Photos actually calls. |
| `data/AdaptiveIconLoader.kt` | 80 | `LauncherRepository`'s LRU icon cache. `ri.loadIcon(pm)` already returns an `AdaptiveIconDrawable` and Android masks it; the extra layer separation existed for icon-pack theming, which is not a shipped feature. |
| `ui/components/UndoSnackbar.kt` | 88 | `HomeScreen`'s own per-object Undo snackbar. |
| `ui/components/CiyatoErrorState` | 31 | Replaced by `QueryFailureState`, and the difference is the lesson — see below. |

### Suggestion-era polish with no setting behind it

Every one of these is referenced only from `docs/archive/` — the suggestion lists and
superseded plans F-169 dismantled. Nothing in a shipped document claims any of them.

| Removed | Lines | Note |
|---|---|---|
| `ui/components/ClockWidgetStylePicker.kt` | 181 | Ten clock renderers and a picker. Home already draws a live clock; adding a clock-style setting is a product decision, not a gap (F-056 precedent). |
| `ui/components/CoachMarkOverlay.kt` | 145 | Coach marks with no tour to attach them to. |
| `ui/components/WhatsNewSheet.kt` | 134 | A hardcoded changelog — including an entry advertising "Undo for Hide/Delete" — inside code nothing could open. |
| `ui/components/CiyatoIconography.kt` | 171 | Six unused icon treatments. |
| `ui/components/CiyatoDataViz.kt` | 96 | Sparkline and circular progress, never charted. |
| `ui/components/VpnStatusIndicator.kt` | 82 | A launcher reporting VPN state implies a security claim nothing backs. |
| `ui/components/Confetti.kt` | 60 | Suggestion 133, onboarding confetti. |
| `ui/components/ParticleEffect.kt` | 46 | Unused burst animation. |
| `ui/components/HapticFeedbackHelper.kt` | 75 | Haptics are applied directly where wanted. |
| `ui/components/GoldGradientText.kt` | 28 | Named for a gold the palette does not contain (F-039). |
| `data/CategoryColorManager.kt` | 81 | Per-category accent colours, Suggestion 8. Noted honestly: I fixed a real partial-application bug in this file before discovering nothing calls it. |

### Component library with no call sites — 29 declarations

`CiyatoCards` shipped eight components and three were used. Across eleven files, 29
top-level declarations had no call site anywhere: `CiyatoAIButton`, `CiyatoCompactButton`,
`CiyatoIconButton`, `CiyatoErrorState`, `CiyatoKPICard`, `CiyatoProgressCard`,
`CiyatoSectionHeader`, `CiyatoStatRow`, `CiyatoDialog`, `CiyatoPermissionCard`,
`CiyatoShimmerCard`, `CiyatoBreadcrumb`, `CiyatoFAB`, `EmptyAppsState`, `EmptyFilesState`,
`EmptyPhotosState`, `SkeletonAppTile`, `SkeletonWeatherCard`, `WeatherAgendaRow`,
`WeatherConditionBackdrop`, `AppLibraryGroupTile`, `StandaloneAppsTile`,
`HomeSectionRemoveButton`, `SettingsAction`, `SettingsToggle`, `AccentCard`,
`ElevatedDarkCard`, `GlassMorphCard`, `PillBadge`.

**The lesson, and it is not "delete dead code".** `CiyatoErrorState` took a single `message`
and offered a Retry. Earlier in this same session I hand-wrote two failure states and needed
a title *and* a contradicting detail — "this is not a quiet day", "waiting won't help" — and
Retry is wrong for a permission Android has refused. The unused component could not express
any of it, because it was shaped by what seemed reasonable rather than by what a screen
needed. It had been available the whole time and was useless the moment anyone tried.

So `QueryFailureState` was written *with* its two call sites, adopted in the same change, and
named for the failure rather than for a generic error so it is not reached for casually. That
is the rule this sweep earns: **a component and its first call site ship together.** A
component library written ahead of demand does not prevent duplication — it adds a shelf of
plausible-looking things that nobody can use, and screens hand-roll past it anyway.

### Retained

`PermissionRegistry` has no production caller by design and is staying. It is the source of
truth for the Play Data Safety form, the privacy policy and `DATA_INVENTORY.md`, and
`PermissionRegistryTest` enforces it against the merged manifest. A future sweep like this one
would flag it, so this row exists to say no. It now has production callers regardless — see
the implementation ledger for `SpecialAccessGate`.
