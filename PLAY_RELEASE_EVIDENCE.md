# Ciyato — Google Play Release Evidence

Publication gate. Every item is either evidenced, or explicitly marked as requiring a decision or
action only the publisher can take. Nothing publisher-owned is marked done by me.

Baseline: `5773448cbd2d189055021b319d54c74ae2c779bf`.

## Build configuration

| Item | Current | Required | Status |
|---|---|---|---|
| `compileSdk` | 34 | 36 | **BLOCKER** (F-002 / F-185) |
| `targetSdk` | 34 | 36 by 31 Aug 2026 for new submissions | **BLOCKER** |
| `minSdk` | 26 | — | OK |
| AGP / Kotlin / Compose | current catalog | only as required by API 36 | Phase 1 |
| `versionCode` | 1 | must increment meaningfully | F-004 |
| Release signing | `signingConfigs.getByName("debug")` | fail-closed upload key, never debug | **BLOCKER** (F-003 / F-189) |
| R8 / minification | not verified under release | must build and run | not verified |
| AAB pipeline | none | required | not built |
| 16 KB page size | unverified | binary inspection + device validation | not verified (F-188) |

Confirmed directly in source: `app/build.gradle.kts:10` `compileSdk = 34`, `:15` `targetSdk = 34`,
`:59` `signingConfig = signingConfigs.getByName("debug")`.

## Merged release manifest

Not yet generated or archived. Source-manifest comments are **not** proof of the merged result
(F-184) — library manifests contribute permissions and components, and only the merged artifact is
authoritative. Generation is part of Phase 0/1 evidence.

`STORE_READINESS.md` in the repo root materially contradicts the shipping manifest (F-183) and must
not be cited as evidence.

## Sensitive and restricted permissions

Each needs a decision *and* a defensible declaration, or removal.

| Permission | Purpose | Decision | Play requirement |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | enumerate installed apps — genuinely core to a launcher | retain | core-purpose declaration + disclosure (F-191) |
| `MANAGE_EXTERNAL_STORAGE` | optional all-files Files mode | **UNDECIDED** — either a SAF/MediaStore-only Play flavour, or retain with a declaration proving SAF is insufficient | restricted; commonly rejected (F-192) |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Photos library | **UNDECIDED** — broad access vs picker-first | core-functionality declaration or redesign (F-193) |
| `ACCESS_FINE_LOCATION` / `COARSE` | weather | retain | prominent disclosure; coordinates already coarsened to ~1.1 km |
| `READ_CALENDAR` | agenda glance | retain | disclosure |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | badge counts | retain | user-granted in system settings |
| `RECORD_AUDIO` | voice commands | **UNDECIDED** — depends on Voice disposition (Phase 9) | disclosure; recognition may be a system service (F-144) |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | vault / hidden apps | retain | restored 16 Aug after being wrongly stripped |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | WorkManager | retain | restored 16 Aug; required for periodic backup to survive reboot |
| `FOREGROUND_SERVICE` | none | **removed** | nothing calls `startForeground`; no expedited work |

Note: the `MANAGE_EXTERNAL_STORAGE` and broad-media decisions are product decisions with real
user-facing consequences, not paperwork. Both are recorded as undecided rather than assumed.

## Data Safety inventory

No single reconciled map exists yet (F-195). Draft basis:

| Data | Collected? | Leaves device? | Destination |
|---|---|---|---|
| Installed app inventory | used locally | No | — |
| Workspace / layout | used locally | No | — |
| File and photo metadata | used locally | No | — |
| Vault contents | encrypted locally (Keystore AES-GCM) | No | — |
| Crash logs | local file, rotated | **No** | — |
| Approximate location | used for weather | **Yes**, coarsened to 2 dp | Open-Meteo, Open-Meteo AQI, Nominatim |
| Password hash prefix | breach check | **Yes**, k-anonymous SHA-1 prefix | breach API |

## Privacy policy

Required, with an in-app link. Draft must state the local-first reality *including* the optional
network features — the current onboarding claim that everything stays on the phone is false while
weather and breach checking exist (F-022 / F-196). URL and developer identity are publisher-owned.

## Publisher-owned items — I will not do these

- Play Console account access, production signing keys, upload-key generation secrets.
- Final privacy-policy URL and developer identity.
- Store listing assets and positioning copy sign-off.
- Closed-testing programme: production access may require 12 testers for 14 days depending on the
  account's age (F-197). **Requires checking the current policy at submission time**, not from this
  document.

## Policy freshness

All Play requirements above must be re-verified against official Google documentation immediately
before submission. Requirements and dates change; this file is evidence of the decisions taken, not
a substitute for the current policy.
