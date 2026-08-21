# Ciyato — Validation Record

Build, test, lint, device, performance and accessibility evidence. Failures are recorded as
failures; nothing here is marked passing on the strength of a green compile alone.

Baseline: `5773448cbd2d189055021b319d54c74ae2c779bf` · tag `baseline-audit-5773448`.

## API 36 migration status (branch `api36-migration`)

| Gate | Result |
|---|---|
| `compileDebugKotlin` on 36 | **PASS** |
| `testDebugUnitTest` on 36 | **PASS** — all suites |
| `assembleDebug` on 36 | **PASS** |
| `lintDebug` on 36 | see baseline section |
| Predictive Back (F-045/F-186) | **NOT VERIFIED** — both nav systems use custom back handling; needs migration + device test |
| Edge-to-edge / large screen (F-046/F-187) | **NOT VERIFIED** — device only |
| 16 KB page size (F-188) | **NOT VERIFIED** — device only |

One real source change was required, and it is a platform change rather than
configuration: `PackageInfo.applicationInfo` is nullable as of the API 36 SDK.

## Toolchain

| Item | Local | CI |
|---|---|---|
| JDK | `C:\Users\ADMIN\.jdks\ms-21.0.11` (21.0.11 LTS) | Temurin **17** |
| Gradle | wrapper at repo root | wrapper at repo root |
| compileSdk / targetSdk | 34 / 34 (Phase 1 raises to 36) | same |

**Toolchain discrepancy to resolve:** the specification asks for JDK 17; local builds have been
green on 21. Both are valid for AGP 8.x, but local and CI should not differ indefinitely — a
compile that passes on 21 and fails on 17 would only surface in CI. Aligning is queued for Phase 1.

## Scope note — emulator work stopped, 17 Aug

The owner instructed that emulator/simulation work is not wanted: device
verification is theirs to do, and effort here should go into making the code
correct instead. The API 36 AVD recipe is kept below because it cost real effort
to get working and may be wanted later, but **no further emulator runs are being
performed**.

What this means, stated plainly rather than glossed: every row in the device,
recreation, performance and accessibility sections stays **unverified by me**.
They are not assumptions to be quietly upgraded later — they are open until
someone runs them on hardware. Nothing in these tables will be marked passing on
the strength of a compile.

## Visual verification — one pass completed on API 36 before that instruction

An emulator matrix is running. The SDK had system images for API 36 and API 37
(the `_ps16k` 16 KB page-size variant, which F-188 needs) but no AVD and no
`avdmanager`, so the AVD was hand-authored at `~/.android/avd/Ciyato_API36`.
`image.sysdir.1` must use forward slashes and `hw.cpu.arch=x86_64` must be set,
or the emulator misreads the ABI as arm and refuses to start.

| Run | Result |
|---|---|
| Install debug APK on API 36 | **PASS** — streamed install, success |
| Launch `MainActivity` | **PASS** — process alive, `topResumedActivity` = `com.ciyato.launcher/.MainActivity`, **zero errors in its log** |
| First-frame render | **PASS** — onboarding renders; the near-black/graphite/silver direction holds up |

Environment note: the emulator raises "System UI isn't responding" under software
GPU. That ANR belongs to the emulator's own SystemUI, not Ciyato — confirmed by
process state and an empty Ciyato log. Do not record it as an app defect.

### Defects found by looking, not reading

| Observation | Finding | Notes |
|---|---|---|
| Onboarding preview card is clipped: the "Smart categories" row is cut mid-card by the pager edge, "Organized apps" is sliced through, and the page-indicator dots overlay the clipped content | new | Content overflowing instead of being bounded. Static analysis would not have caught this. |
| "AI Phone Organizer" appears twice in the very first frame (app subtitle + card title) | F-163 | Confirms the capability-inflation finding at the highest-visibility point in the product |

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
