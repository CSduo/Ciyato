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
| `ContextualSuggestionsScreen` | RENAME to Frequent Apps or rebuild buckets | PENDING | Reachable; claims time-of-day learning it does not measure | UsageStats plumbing |
| `AnomalyDetectionScreen` | MERGE into Usage Insights | PENDING | Reachable; statistically invalid (partial day vs full days) | Aggregation shell |
| `AiChangelogScreen` | MERGE into Usage Insights | **DATA FIXED** (B10); merge into one Insights surface still outstanding | Reachable; weekly "average" overwrites per package; fake 600 ms delay | Digest card UI |
| `AppUsageStatsScreen` | SALVAGE into Usage Insights | **REACHABLE + DATA FIXED** (B6, B10); merge still outstanding | Candidate orphan; well built | Per-app usage rendering |
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
| `HealthConnectWidget` | — | PENDING | A renderer, not a demonstrated Health Connect integration | — |
| `CiyatoNotificationListener` (in `NotificationBadge.kt`) | — | **DELETED** (16 Aug) | Second, duplicate `NotificationListenerService` shadowing the real one in `services/`; neither was declared | Real service kept and declared; `isNotificationListenerEnabled` repointed at it |

## Completed removals

| Component | Commit | Reachability proof | What was preserved |
|---|---|---|---|
| `TFLiteCategorizerHelper` (71 lines) | B11 | Referenced from exactly one place in the tree: a *comment* in `AppCategorizer`. Contains no TensorFlow Lite execution at all — the name was the entire feature. | Nothing. The rule-based categorizer it "delegated to" is untouched; the stale comment naming it was corrected. |
| `OnDeviceEmbeddingsHelper` (110 lines) | B11 | Zero references anywhere. | Nothing. It called TF-IDF bag-of-words "embeddings" and "semantic search", kept its index in memory only, and was never wired to a search path. |
| `AIOptimizerManager` (47 lines) | B11 | **RECLASSIFIED.** The audit called it "explicitly unreachable", and it is — but not for the stated reason: it *was* instantiated in `LauncherViewModel` and wrapped in `optimizeSystem()`. Nothing ever called that public entry point, so the chain compiled and looked live while being dead from the UI down. | Capability, not code: it deleted `.log`/`.tmp` files over 500KB from Ciyato's own cache. Storage Cleanup's Cache category already does this more thoroughly — internal *and* external cache dirs, with sizes shown and confirmation before deleting. |
| `GuestModeScreen` (188 lines) | B11 | Zero references outside its own file; no route in either activity. | Nothing. Its doc promised "No access to hidden apps, files, settings, or personal data", which a launcher screen cannot enforce — anything reachable from Recents, a notification or another launcher bypasses it entirely. Android's real multi-user Guest profile provides that boundary; imitating it in-app is a security claim with nothing behind it. |
| `CiyatoNotificationListener` duplicate class | `828f473` | Two `NotificationListenerService` subclasses existed; neither declared in the manifest, so Android bound neither and `badgeCounts` was permanently empty | `CiyatoNotificationListenerService` retained, declared in the manifest, and the enabled-check now targets it |

## Preserved infrastructure (do not delete while refactoring)

The audit is explicit that these are good decisions to keep: SAF-first storage, system-owned
MediaStore trash and consent flows, local-only crash logs, coarsened weather location, fail-closed
authentication, and the restrained near-black/graphite/silver visual direction.
