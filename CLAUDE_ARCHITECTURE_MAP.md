# Ciyato — Architecture Map

Current, verified ownership of every surface. Updated as consolidation lands, so that this file
always describes the code as it is — not as it is intended to become. Target states are marked
explicitly as targets.

Baseline: `5773448cbd2d189055021b319d54c74ae2c779bf`.

## Product layers (governing intent)

| Layer | What it is | Primary? |
|---|---|---|
| 1 — Android HOME | The real launcher surface: workspaces, dock, app library, glanceable weather/agenda | Yes. This is the product identity. |
| 2 — Organizer | The app-icon surface: Files, Photos, Search, Settings | Secondary, intentional |
| 3 — Labs / optional | Focus, insights, backup, security tools — only when mature and honest | Never dominant |

**North star:** *Ciyato is your Android home screen, with an organizer one tap away.*

Revision III correction I am bound by: **two activities are not the defect.** `LauncherHomeActivity`
(HOME role) and `MainActivity` (Organizer) are a legitimate platform boundary. The defect is
duplicate *ownership* — the same feature implemented or routed twice, and behaviour that changes
depending on which host opened a screen. Do not merge the activities merely because Revision II's
wording implied it.

## Host ownership — current

| Host | Navigation mechanism | Currently owns |
|---|---|---|
| `LauncherHomeActivity` | Manual `LauncherDest` sealed class, `remember`-only | Home, Drawer, and a second Settings graph wired to ~24 callbacks |
| `MainActivity` | Compose Navigation `NavHost` | Onboarding, `home` (actually a Files dashboard), `files`, `search`, `photos`, `settings`, plus feature routes |

### Known ownership defects at baseline

| Defect | Evidence | Finding |
|---|---|---|
| Two screens both presented as "Home" | `MainActivity` bottom-nav `home` → `DashboardScreen` (a Files dashboard); Android HOME → `HomeScreen` | F-071, F-073 |
| Settings behaviour depends on host | `SettingsScreen` takes ~24 nullable callbacks; `MainActivity` passes 10, `LauncherHomeActivity` passes all | F-072, F-075, F-080, F-171 |
| Second Settings graph orphaned | `LauncherHomeActivity`'s richer Settings is unreachable from Home; Home's settings button starts `MainActivity` | F-172 |
| Launcher route state not saveable | `LauncherDest` held in `remember`, lost on recreation | F-068, F-079, F-181 |
| Warm-start intent ignored | `MainActivity` is `singleTop` with no `onNewIntent` handling of `start_destination` | F-182 |

### Target ownership

One canonical owner per feature; both entry points deep-link to the same implementation. The
app icon lands on an **Organizer Overview**, never on a second thing named "Home".

```
LauncherHomeActivity  (Android HOME role)
  Home / Workspaces / Dock
    -> App Library / launcher Search
    -> Organizer  (deep-link into MainActivity)
    -> Settings   (deep-link to the ONE canonical Settings)

MainActivity  (Organizer host)
  Overview
    -> Files    (one domain, one scope model)
    -> Search
    -> Photos   (one product)
    -> Settings (canonical owner)
         -> Appearance | Permissions & Privacy | Accessibility | Labs
```

## State owners

| State | Owner | Persistence | Baseline issue |
|---|---|---|---|
| Installed apps, categories, overrides | `LauncherRepository` | in-memory + Preferences | reload on every resume past 30 s |
| Workspace layout | `WorkspaceStore` via `LauncherViewModel.updateLayout` (Mutex-serialised) | Preferences DataStore, JSON string | structured data in a preference string (F-042) |
| Grid columns | split brain: renderer reads `gridSize` setting, model reads `authorColumns` | Preferences + layout JSON | reconciled at startup 16 Aug; still two sources |
| Focus session | `FocusSessionManager` | **in-memory only**, plus an orphan persisted `focusModeActive` flag | dies on process death; QS tile uses hard-coded defaults (F-120, F-121, F-175, F-176) |
| Weather | `LauncherViewModel.weatherState` | in-memory + cache | refresh duplicated at both activity roots (F-050) |
| Vault | `VaultCrypto` + AndroidKeystore | encrypted files | key not bound to fresh auth (F-015); no relock policy (F-018) |
| Appearance / theme | Preferences | Preferences | `darkMode` written but both roots hardcode dark (F-036, F-142, F-177) |

## Persistence stores

| Store | Backing | Notes |
|---|---|---|
| `LauncherSettingsRepository` | Preferences DataStore | very wide; structured JSON/CSV encoded in strings |
| `WorkspaceStore` | JSON inside a preference | versioned, lossless v1→v2 upgrade |
| `StickyNoteStore` / `FileTagStore` | JSON inside a preference | silently truncate at 300 / 500 entries |
| `FileSearchIndex` | JSON inside a preference | reports `reachedLimit` honestly |
| `VaultCrypto` | files + AndroidKeystore AES-GCM | atomic temp+rename |
| `FileCleanupResultStore` | file | checkpointed, keyed by URI only (F-060) |

## Services, workers, receivers

| Component | Declared? | Notes |
|---|---|---|
| `CiyatoNotificationListenerService` | Yes (declared 16 Aug) | badge counts; needs user grant in system settings |
| `CiyatoFocusTileService` | Quick Settings tile | starts a session in the wrong scope (F-120) |
| `PhotoBackupWorker` | WorkManager, 24 h periodic | creates a new folder per run (F-009); increments success on null streams (F-010) |
| `FileCleanupWorker` | WorkManager | bounded, checkpointed, never deletes |

## Permissions and data flows

| Permission | Used by | Off-device? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | app inventory (core to a launcher) | No — needs Play declaration (F-191) |
| `MANAGE_EXTERNAL_STORAGE` | optional all-files Files mode | No — Play decision required (F-192) |
| `READ_MEDIA_IMAGES` / `VIDEO` | Photos | No — declaration or picker-first redesign (F-193) |
| Location (coarse/fine) | weather | **Yes** — coarsened to 2 dp before leaving device |
| Calendar | agenda | No |
| Notification listener | badges | No |
| Microphone | voice commands | Recognition may be a system service (F-144) |

**Network hosts:** `api.open-meteo.com`, `air-quality-api.open-meteo.com`,
`nominatim.openstreetmap.org` (coarsened coordinates); breach checker (k-anonymous SHA-1 prefix).
Crash logs are local only, never transmitted.

**Trust rule:** copy must describe the real boundary. "Everything stays on this phone" is false
while weather and breach checking exist (F-022, F-196).

## Canonical feature owners — decision register

Filled in as each consolidation lands. A feature with two owners listed here is a bug.

| Feature | Canonical owner | Merged/removed from |
|---|---|---|
| Settings | *to be decided in Phase 2* | — |
| Files | *to be consolidated in Phase 7* | — |
| Photos | *to be consolidated in Phase 8* | — |
