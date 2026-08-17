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
| `MANAGE_EXTERNAL_STORAGE` | whole-device file management in Files | **RETAIN, built to be removable** — see decision below (F-192) | restricted; declaration required |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Photos library | **RETAIN broad access** — owner's decision, 17 Aug (F-193) | core-functionality declaration |

### Decision: All-files access (F-192)

Owner's instruction was to choose whatever both avoids a Play flag *and* delivers
whole-device file management. Those two goals are in genuine tension, so the
decision is a risk-managed one rather than a clean win, and it is recorded here
honestly.

**Retain `MANAGE_EXTERNAL_STORAGE`, and engineer the product so that losing it is
a configuration change rather than a redesign.** Concretely:

1. **SAF stays the default and remains fully functional.** Every Files surface
   must work with a SAF folder grant alone. All-files is an enhancement, never a
   prerequisite — no screen may dead-end without it.
2. **It is requested only on explicit intent**, from Files, when the person tries
   to reach something SAF cannot grant. Never at onboarding, never pre-emptively.
3. **The rationale states the platform truth**: since Android 11 the folder picker
   cannot grant internal-storage root or `Download` to any app. That is why the
   permission exists here, and the card says so.
4. **Declaration**: filed as a file manager, which is an explicitly permitted use.
   Store positioning must present file management as core, not incidental — the
   audit's warning about breadth weakening a core-purpose claim is real.

**Why this and not a SAF-only build:** dropping the permission removes the exact
capability the owner asked for, and Ciyato is a genuine file manager, which is the
use case Google permits. **Why not bet everything on approval:** it is frequently
refused, so the fallback must be cheap. Because of (1), if the declaration is
rejected the response is one manifest line plus hiding one card — no feature
rewrite, no data-model change, no broken screens. That property is the actual
deliverable of this decision and must be preserved as the code evolves.

**Residual risk, stated plainly:** approval is not guaranteed, and no engineering
choice can guarantee it. What is guaranteed is that a refusal cannot break the app.

### Decision: broad photo access (F-193)

**Retain `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`.** Owner's explicit decision on
17 Aug: a photo organiser that can only see individually-picked photos cannot
group, deduplicate or report on a library, so the picker-first redesign was
rejected.

Obligations that come with keeping it, and are treated as requirements not
nice-to-haves:
- Android 14 partial access (`READ_MEDIA_VISUAL_USER_SELECTED`) must be a
  first-class state, not an error — already handled as `MediaAccess.PARTIAL`.
- Every count must state its scope, never implying the whole library from a
  partial grant or a capped scan (F-107, F-108).
- Denial must leave a usable screen, not a dead end.
- Core-functionality declaration filed for the Photos feature.
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
