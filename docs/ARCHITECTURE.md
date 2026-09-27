# Architecture

Who owns what, and why there are two activities.

This file describes the code **as it is**. That is not a style note — the previous version of
this document listed "Settings behaviour depends on host", "launcher route state not saveable"
and "canonical Settings owner: to be decided" as current facts months after each was fixed,
which makes it exactly the kind of document `docs/README.md` exists to warn about. If
something here disagrees with the code, the document is the bug.

---

## 1. Two layers, on purpose

| Layer | What it is | Activity |
|---|---|---|
| **Launcher** | The real Android home screen: workspace canvas, dock, app library, widgets, glanceable weather and agenda | `LauncherHomeActivity` (HOME intent filter) |
| **Organizer** | The app-icon surface: Files, Photos, Search, Settings, and every feature reached from Settings | `MainActivity` (LAUNCHER intent filter) |

**North star:** *Ciyato is your Android home screen, with an organizer one tap away.*

### Why not one activity

Revision III is explicit and this project is bound by it: **two activities are not the defect**
(F-173). A HOME activity and an ordinary app activity are a platform role distinction, and
collapsing them to satisfy a "one Home" reading of an earlier audit would trade a real
information-architecture question for an artificial launcher-role problem.

The actual defect was **duplicate ownership**: the same feature implemented or routed twice, and
behaviour that changed depending on which host opened a screen. Every fix below attacks that,
not the activity count.

### The boundary, stated

- `LauncherHomeActivity` owns **Home**. Nothing else may draw a home screen.
- `MainActivity` owns **Overview**, and the app icon lands there — never on a second thing
  named "Home" (F-071).
- A feature has **one** implementation. If both layers can reach it, they reach the same
  composable with the same arguments.
- Crossing from the launcher into the organizer is a **typed** call, never a hand-built intent.

---

## 2. Navigation

Two mechanisms, one vocabulary. This is the part that used to be wired twice.

| Host | Mechanism | Route state survives |
|---|---|---|
| `LauncherHomeActivity` | `LauncherDest` sealed class, held in `rememberSaveable` | rotation **and** process death |
| `MainActivity` | Compose Navigation `NavHost` | NavHost's own saved state |

### Crossing between layers

`MainActivity.intentFor(context, OrganizerEntry.Files)`. That is the only in-app way.

Home used to build the intent by hand with a raw string extra, and the organizer resolved it
through a `when` listing six values that returned null for anything else. Passing `"insights"`
— a real route — landed the person on Overview. So did a typo. Adding a destination to one side
taught the other side nothing (F-044, F-074).

[`OrganizerEntry`](../app/src/main/java/com/ciyato/launcher/data/OrganizerEntry.kt) is now the
single vocabulary. `EXTRA_START_DESTINATION` stays public because an app shortcut or an external
deep link builds it by name, and `OrganizerEntry.fromExtra` is the one resolver — including the
aliases (`home`, `dashboard`, `shared`) that pinned shortcuts still carry.

`OrganizerEntryTest` fails the build if an entry names a route the NavHost does not declare, and
if `LauncherHomeActivity` mentions `EXTRA_START_DESTINATION` at all.

### Settings: one screen, one contract, two hosts

Both hosts render the same `SettingsScreen` and both must supply the same
[`SettingsDestinations`](../app/src/main/java/com/ciyato/launcher/ui/screens/SettingsDestinations.kt)
— 26 required fields, no defaults. A host that forgets one does not compile.

That is the answer to F-172's "adding a Settings feature requires one route declaration": the
declaration is the field, and the compiler collects both implementations of it. `SettingsNavigationTest`
additionally proves every route a row navigates to is declared, and that the two hosts wire the
identical set — because a required field can still hold a route string that nobody declared,
which type-checks and then throws on tap.

Two destination *registries* remain — `MainActivity`'s NavHost routes and `LauncherDest` — and
that is the accepted cost of keeping the platform boundary. What is not accepted is the two
disagreeing, which is what the tests are for.

### Process death

`LauncherDest` is saved as a string key through the saved-instance bundle.

`android:stateNotNeeded="true"` used to sit on the HOME activity and silently cancel that: the
attribute tells Android the activity can be restarted *without* its state, so the system stops
retaining the bundle across process death. Rotation kept your place; the system reclaiming
memory while you were three levels into Settings or the vault did not (F-079). It is gone.

**What is deliberately not restored:** any dialog, selection or in-flight confirmation. Only the
destination key is saved, so a half-finished delete cannot be resumed — you land on the screen
with nothing selected.

---

## 3. State owners

One owner per piece of state. A second writer is a bug.

| State | Owner | Persistence |
|---|---|---|
| Installed apps, categories, overrides | `LauncherRepository` | in-memory + Preferences |
| Workspace layout (cells, spans, canvas positions, hidden objects) | `WorkspaceStore`, written only through `LauncherViewModel.updateLayout` (Mutex-serialised single writer) | versioned JSON in a preference |
| Placed widgets | `WidgetPlacementStore` (ids + size); position lives in `WorkspaceRecord.objectPositions` | preference JSON |
| The AppWidgetHost itself | `LauncherWidgetHost`, process-level with a reference count | n/a — the host outlives every screen |
| Focus session | `FocusSessionManager`, derived entirely from a persisted end instant | Preferences |
| Weather | `LauncherViewModel.weatherState` | in-memory + cache |
| Vault | `VaultCrypto` + AndroidKeystore AES-GCM | encrypted files, atomic temp+rename |
| Photo labelling result | `PhotoAiCollectionStore` — URIs only, rebuilt against the live library on restore | preference JSON |
| Sensitive-capability contract | `PermissionRegistry` | source of truth in code |

**Known open item:** weather and agenda refresh is still triggered at both activity roots rather
than centralised (F-050). It is idempotent and cached, so the cost is a duplicated check rather
than a duplicated request.

---

## 4. Persistence

| Store | Backing | Notes |
|---|---|---|
| `LauncherSettingsRepository` | Preferences DataStore | wide; structured data is JSON inside string preferences |
| `WorkspaceStore` | JSON in a preference | versioned, lossless v1→v2 upgrade |
| `WidgetPlacementStore` | JSON in a preference | still parses the pre-size bare-id array |
| `StickyNoteStore` / `FileTagStore` | JSON in a preference | bounded, and say so |
| `FileSearchIndexStore` | JSON in a preference | reports `reachedLimit` honestly |
| `VaultCrypto` | files + Keystore | `flush()` + `fd.sync()` before rename |

`edit {}` on a Preferences DataStore is atomic across every key touched inside it and serialises
callers, which is why a multi-key mutation belongs in one block rather than several.

---

## 5. Services, workers, receivers

| Component | Kind | Note |
|---|---|---|
| `CiyatoNotificationListenerService` | system-bound service | `exported="true"` is **required** — system_server binds it, and `BIND_NOTIFICATION_LISTENER_SERVICE` is what protects it |
| `CiyatoFocusTileService` | Quick Settings tile | reads and writes the same persisted session the UI does |
| `CiyatoWeatherTileService` | Quick Settings tile | — |
| `PhotoBackupWorker` | WorkManager, 24 h periodic | network constraint tightens when the destination does not look local |
| `FileCleanupWorker` | WorkManager | bounded, checkpointed, never deletes |

The merged release manifest is checked against
[`release-manifest-allowlist.txt`](../app/release-manifest-allowlist.txt) by
`verifyReleaseManifest`, which blocks `bundleRelease` on any difference in either direction. It
exists because three features in this project went silently inert through a manifest omission.

---

## 6. Permissions and data flows

The authoritative documents are
[`DATA_INVENTORY.md`](../DATA_INVENTORY.md) (what leaves the device) and
[`PLAY_POSITIONING.md`](../PLAY_POSITIONING.md) (why each restricted permission is core). The
machine-readable source behind both is
[`PermissionCapability.kt`](../app/src/main/java/com/ciyato/launcher/data/PermissionCapability.kt).

Four hosts, total: `api.open-meteo.com`, `air-quality-api.open-meteo.com`,
`nominatim.openstreetmap.org` (coordinates rounded to 2 dp before the request is built), and
`api.pwnedpasswords.com` (a 5-character SHA-1 prefix). `PermissionRegistryTest` fails the build
if a fifth appears in the source without a disclosure row behind it.

**Trust rule:** copy describes the real boundary. Layered, never blanket — see
`CLAUDE_VALIDATION.md` for the string audit that established this.

---

## 7. Canonical feature owners

The register F-172 asks for. A feature with two owners listed here is a bug.

| Feature | Canonical owner | Reachable from |
|---|---|---|
| Home | `LauncherHomeActivity` / `HomeScreen` | HOME intent only |
| Overview | `MainActivity` / `DashboardScreen` | app icon, `OrganizerEntry.Overview` |
| Settings | `SettingsScreen`, one implementation | both hosts, via `SettingsDestinations` |
| Files | `FilesScreen` | organizer; launcher deep-links via `OrganizerEntry.Files` |
| Photos | `PhotosLibraryScreen` | organizer; launcher deep-links via `OrganizerEntry.Photos` |
| Agenda | `CalendarAgendaScreen` | both; launcher deep-links via `OrganizerEntry.Agenda` |
| Search | `SearchScreen` (launcher), `NlFileSearchScreen` (files) | two surfaces, two jobs — app search and file search are not the same feature |
| Widgets | `WidgetHostScreen` manages; `HomeScreen` hosts | placement is Home's, management is Settings' |
| Usage intelligence | `InsightsScreen` is the only door | everything behind one Usage access grant |

Per-feature maturity, permissions, tests and source paths live in
[`FEATURE_MATRIX.md`](FEATURE_MATRIX.md). This table says who owns a feature; that one says
whether it ships.

---

## 8. What the build enforces

Documents drift. These do not.

| Test / task | Fails when |
|---|---|
| `OrganizerEntryTest` | an entry names an undeclared route, or the launcher hand-builds a destination intent |
| `SettingsNavigationTest` | a Settings row points at a route nobody declared, or the two hosts wire different contracts |
| `FeatureReachabilityTest` | a screen is reachable from nothing, a destination is declared but never rendered, a restorable destination cannot render, or a route has no matrix row |
| `PermissionRegistryTest` | the manifest and the permission registry disagree, or a network host has no disclosure |
| `MicroTypeFloorTest` | UI text drops below 11sp |
| `StoreReadinessDocTest` | the manifest and `STORE_READINESS.md` disagree |
| `verifyReleaseManifest` | the merged release manifest differs from its allowlist in either direction |
