# Golden image tests — written, not wired

F-165 asks for a visual baseline, because Ciyato's real risk is structural layout
drift and no logic test can catch it. The suite in
[`HomeCanvasGoldenTest.kt`](HomeCanvasGoldenTest.kt) is that baseline for the
highest-value target: the single custom `Layout` that measures and places every
object on Home in one pass.

**It is not in the build.** This directory is not a source set, so nothing here
compiles or runs. That is a deliberate stop, and this file records exactly why so
the next attempt does not repeat it.

## What was tried

| Attempt | Result |
|---|---|
| Paparazzi **1.3.5** | Broke `:app:kspDebugKotlin` with an internal compiler error. It puts `kotlin-compiler-embeddable:2.0.21` on the build classpath while this project is on Kotlin 2.0.0 / KSP 2.0.0-1.0.21, and KSP cannot survive the mismatch. |
| Paparazzi **1.3.4** | KSP builds fine. Every snapshot then fails at `Renderer.configureBuildProperties` with `NoSuchElementException: Array contains no element matching the predicate` — the layoutlib it ships does not know `compileSdk = 36`. |

So the two versions fail for opposite reasons: the newer one is incompatible with
this project's Kotlin toolchain, and the older one is incompatible with its
`compileSdk`. There is no version of Paparazzi that satisfies both today.

## Why it was left out rather than forced

The available fix is to upgrade Kotlin to 2.0.21+ and KSP with it, then take a
Paparazzi that supports API 36. That is a compiler upgrade to a working
40-thousand-line Compose app — it can change codegen, the Compose compiler plugin,
lint output and Room's generated code — performed in order to add a *test harness*.

Lowering `compileSdk` is not an option either: API 36 is where the predictive-back
and large-screen work has to be verified (F-186, F-187).

Adding the plugin and leaving the suite failing would have been worse than both:
a red build that everyone learns to ignore.

## To enable it

1. Bump `kotlin` and `ksp` in `gradle/libs.versions.toml` together, and run the
   full suite plus `lintDebug` before anything else. Treat that as its own change.
2. Add Paparazzi at a version whose layoutlib supports API 36:
   ```toml
   paparazzi = { id = "app.cash.paparazzi", version.ref = "paparazzi" }
   ```
   declared `apply false` in the root `build.gradle.kts` and applied in
   `app/build.gradle.kts`.
3. Move `HomeCanvasGoldenTest.kt` to
   `app/src/test/java/com/ciyato/launcher/ui/`.
4. `./gradlew :app:recordPaparazziDebug` to create the goldens, then commit
   `app/src/test/snapshots/`.
5. `./gradlew :app:verifyPaparazziDebug` in CI. **A diff is a review item, never
   an automatic re-record** — the point of a golden is that someone looks.

## The larger prerequisite

F-165 asks for goldens covering Home, Drawer, Files, Photos, Settings, onboarding
and destructive dialogs. Those composables each take a `LauncherViewModel`, so
rendering one needs a real DataStore and a real `PackageManager`. Screenshot
coverage of whole screens therefore needs their state hoisted out first — a
refactor of eight screens, and a real piece of work rather than a detail.

The suite here deliberately targets what is already testable: `HomeCanvasSurface`
takes plain data, and it is where spacing, overlap and displacement for every Home
object actually live. Alongside it the obvious next candidates, all already
state-free: `WorkspaceGrid`, `RemoveObjectDialog`, `CiyatoEmptyState`,
`SmartCategoryCard`, `AppIconTile`.
