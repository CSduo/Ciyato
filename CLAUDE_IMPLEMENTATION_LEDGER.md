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
| F-023 | P0 | CONFIRMED | `ui/screens/SafeBrowsingHelperScreen.kt:59` | — | `KNOWN_SAFE_DOMAINS.any { host.endsWith(it) }` — `evilgoogle.com` ends with `google.com`, so a hostile domain is reported safe. Same class at line 62 for suspicious TLDs. Needs the label-boundary matcher from recipe B, and the verdict copy must stop claiming safety at all (F-024). |

Remaining findings are appended as each is located and confirmed against current source, rather
than pre-filled from the document — an unverified row in this table would be exactly the kind of
false completion signal the audit warns about.

## Newly discovered defects (not in the audit)

Recorded here when implementation uncovers something the audit did not list, per the "no blind
checklist patching" rule.

| ID | Severity | Status | Location | Notes |
|---|---|---|---|---|
| — | — | — | — | none yet |
