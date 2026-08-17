# Ciyato — Validation Record

Build, test, lint, device, performance and accessibility evidence. Failures are recorded as
failures; nothing here is marked passing on the strength of a green compile alone.

Baseline: `5773448cbd2d189055021b319d54c74ae2c779bf` · tag `baseline-audit-5773448`.

## Toolchain

| Item | Local | CI |
|---|---|---|
| JDK | `C:\Users\ADMIN\.jdks\ms-21.0.11` (21.0.11 LTS) | Temurin **17** |
| Gradle | wrapper at repo root | wrapper at repo root |
| compileSdk / targetSdk | 34 / 34 (Phase 1 raises to 36) | same |

**Toolchain discrepancy to resolve:** the specification asks for JDK 17; local builds have been
green on 21. Both are valid for AGP 8.x, but local and CI should not differ indefinitely — a
compile that passes on 21 and fails on 17 would only surface in CI. Aligning is queued for Phase 1.

## Baseline capture — what CI will actually run

Command: `./gradlew --no-daemon testDebugUnitTest lintDebug`

| Gate | Result | Notes |
|---|---|---|
| `compileDebugKotlin` | PASS | verified repeatedly through 16 Aug |
| `testDebugUnitTest` | *capture in progress* | |
| `lintDebug` | *capture in progress* | **never run on this project before** — first honest measurement |
| `assembleDebug` | PASS | APK produced at `Ciyato.apk` |
| CI end-to-end | **FAIL at baseline** | died before Gradle on a non-existent `ciyato-android/` directory (F-001); workflow repaired, awaiting first green run |

Known test-quality problems already identified in the audit, to be fixed rather than counted as
coverage: `searchInput_filtersApps` is a vacuous assertion (F-051), the Home app-grid test is too
generic to fail meaningfully (F-052), and instrumentation coverage is far too sparse for a
platform-sensitive launcher (F-053). **A test that cannot fail is not evidence.**

## Device / emulator matrix

Not yet run. Static analysis cannot certify launcher gestures, OEM storage behaviour, biometric
enrolment, predictive back or foldable layout — the audit is explicit about this, and these stay
open rather than being quietly assumed.

| Dimension | Planned | Status |
|---|---|---|
| API levels | 26 (min), 30, 34, 36 | not run |
| Form factors | phone, tablet, foldable, windowed/split | not run |
| Navigation modes | gesture, three-button | not run |
| Font scale | 100%, 200% | not run |
| Theme | dark (only shipping mode today) | not run |

## Process recreation / reboot

| Scenario | Expected | Status |
|---|---|---|
| Rotation on Home mid-edit | edit state and selection survive | not run |
| Process death during a Focus session | session survives via absolute end-time | **known broken** (F-121) — in-memory only |
| Reboot with periodic backup scheduled | WorkManager restores the job | fix landed 16 Aug (`RECEIVE_BOOT_COMPLETED` restored); untested on device |
| Launcher recreation | route/subdestination restored | **known broken** (F-068, F-181) — `remember`-only |

## Performance

No measurements taken yet; the audit requires measured evidence rather than claims. Fixes already
landed whose effect must be *measured*, not asserted: process-wide icon raster cache, weather
overlay no longer animating at 60 fps forever, category cards no longer re-parsing JSON per
recomposition, workspace title no longer re-parsing the layout per recomposition.

| Metric | Target | Measured |
|---|---|---|
| Cold launcher start | — | not measured |
| Home first frame | — | not measured |
| Drawer open / scroll | no dropped frames | not measured |
| Workspace drag / edit | no dropped frames | not measured |
| 1k / 5k photo library | bounded, responsive | not measured |
| Large file scope / index | bounded, responsive | not measured |

## Accessibility

| Check | Status |
|---|---|
| TalkBack on canonical flows | not run |
| 48 dp primary touch targets | not audited |
| 200% font scale without truncation | not audited |
| Contrast (incl. over wallpaper extremes) | not audited |
| Reduce Motion honoured by decorative animation | partially — weather overlay fixed 16 Aug; other loops outstanding (F-167) |
| Non-gesture alternative to drag editing | **missing** (F-048) |

## Fault injection

| Injected fault | Required behaviour | Status |
|---|---|---|
| Revoked SAF grant | reconnect offered, state preserved, no raw-path fallback | not run |
| Denied / revoked media permission | distinct from empty; no false zero | not run |
| I/O failure mid-backup | idempotent retry; watermark never advances past failed data | fix landed; untested |
| Corrupt persisted workspace/vault state | never silently reset user data | not run |
| Cancelled worker | resumable | not run |
| No intent handler | explained, not silent | partially handled |
| Network failure | fresh/stale/unavailable; Home unaffected | not run |

## Remaining device- and account-only gaps

Recorded so they are never mistaken for completed code work: real-device gesture and OEM storage
behaviour, biometric enrolment paths, foldable/large-screen layout, 16 KB page-size validation on
a real device, Play Console declarations, and any closed-testing requirement tied to the developer
account's age.
