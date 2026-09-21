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

## To apply it

From a shell that has a token with `workflow` scope (or from the GitHub web UI):

```bash
git rm .github/workflows/android-debug.yml
git mv ci/android-ci.yml .github/workflows/android-ci.yml
git rm ci/README.md
git commit -m "Repair CI: run from the repo root and gate on the release bundle"
git push
```

The old `android-debug.yml` is deliberately left in place until then. Removing it
here would have needed the same `workflow` scope, and leaving the repository with
no CI definition at all would be a worse state than leaving the broken one
visible next to its replacement.

To grant the scope instead: GitHub → Settings → Developer settings → Personal
access tokens → edit the token → tick **workflow**.
