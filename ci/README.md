# CI workflow — needs a one-time push by the repository owner

`ci/android-ci.yml` is the repaired GitHub Actions workflow. It lives here
rather than in `.github/workflows/` because **the token this session uses lacks
the `workflow` scope**, and GitHub refuses any push that creates, updates or
deletes a file under `.github/workflows/` without it. That is a GitHub-side
permission rule, not something the code can work around.

## Why it needs replacing

`.github/workflows/android-debug.yml` has **never produced a successful run in
this repository**. Every step sets `working-directory: ciyato-android`, and that
folder does not exist here — the app module is at the repository root — so the
job fails before it reaches Gradle. On every push, since the audited baseline
(F-001, F-190).

A build that is always red is worse than no CI, because the badge stops carrying
information.

## What the replacement does

Runs from the actual root, and gates on what is shipped rather than only a debug
APK:

1. `testDebugUnitTest` — a failing assertion is a more useful first signal than a
   style violation, so tests run before lint.
2. `lintDebug` — configured to fail the build on errors; it has already caught
   two real ones (a `NewApi` attribute under minSdk 26, and an undeclared
   `QUERY_ALL_PACKAGES` justification).
3. `assembleDebug`.
4. `bundleRelease` — the one that matters most. R8 minification, resource
   shrinking and manifest merging are release-only, and a debug build exercises
   none of them.

Step 4 passes `-PciyatoAllowUnsignedRelease=true`. The signing guard in
`app/build.gradle.kts` correctly refuses to build a release without an upload
key, and CI must not hold that key. The opt-out is an explicit named property
rather than an automatic "skip when CI is detected", because the failure the
guard prevents — shipping an unsigned or debug-signed artifact — is much worse
than the inconvenience of naming the flag.

Verified locally before being committed: the release bundle assembles at 48.2 MB
with R8 and resource shrinking both running.

## Applied

The workflow now lives at [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml)
and the broken `android-debug.yml` is gone. It needed a token with `workflow` scope,
which is why it sat here for a while.

What it gates, in order, so a failure points at the smallest thing:

1. `testDebugUnitTest` — a failing assertion is a more useful first signal than a
   style violation.
2. `lintDebug` — configured to fail on errors, not just report them.
3. `:macrobenchmark:compileBenchmarkKotlin` — nothing else builds that module, and
   a performance suite that stops compiling is one nobody runs.
4. `assembleDebug`.
5. `bundleRelease` — release-only breakage (R8, resource shrinking, manifest
   merging) that a debug build never exercises. This also runs
   `verifyReleaseManifest`, which diffs the merged release manifest against
   `app/release-manifest-allowlist.txt` **in both directions**: an addition is a
   capability nobody reviewed, a removal is a feature gone silently inert.

Unsigned on CI by design. The upload keystore is not available to the workflow and
must not be; `build.gradle.kts` refuses to emit an unsigned release locally, so the
explicit `-PciyatoAllowUnsignedRelease=true` marks this as an assembly check rather
than a publishable artifact.

---

# Performance gates (F-166)

A launcher is the one app on a phone that gets opened and returned to constantly,
and its performance can regress while every functional test still passes. The
`:macrobenchmark` module measures the paths that matters: cold, warm and hot start
of the home screen, and frame timing for the three gestures people actually make.

**These run on hardware only.** A macrobenchmark drives the real app through
UiAutomator; there is no JVM equivalent. Everything is configured and compiles in
CI, and the numbers come from a device.

## Running them

A connected device or emulator on **API 29 or above**. The app's `benchmark`
build type is profileable rather than debuggable, so R8 and resource shrinking
stay real; profileable needs API 29, which is above the app's own minSdk 26. On
26-28 these cannot run, which is a stated limit rather than a wrong number.

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Results land in
`macrobenchmark/build/outputs/connected_android_test_additional_output/`. Each run
prints median and 90th-percentile timings; compare against the previous run rather
than against an absolute target, because the numbers are device-specific.

To run one class while iterating:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ciyato.benchmark.StartupBenchmark
```

## Generating the baseline profile

Without a profile, everything on the startup path is interpreted on first
execution. For an ordinary app that costs one slow launch; for a launcher it costs
that launch **and every Home press** until the profile builds itself from real
use.

```bash
./gradlew :app:generateBaselineProfile
```

That writes `app/src/benchmarkRelease/generated/baselineProfiles/`, and **the
output must be committed** - it is packaged into the release, not regenerated by
it. Regenerate whenever the startup path changes meaningfully: new work in
`onCreate`, a change to Home's first frame, or a navigation restructure.

Verify it actually shipped:

```bash
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -i baseline
```

A release without `assets/dexopt/baseline.prof` is a release that lost the
profile, which is silent and costs exactly the startup time the profile existed to
save.
