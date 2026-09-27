# Golden image tests — how they got here

**These are live now.** The suite is at
`app/src/test/java/com/ciyato/launcher/ui/HomeCanvasGoldenTest.kt` and the goldens
are committed under `app/src/test/screenshots/`.

This file is kept because the four failed attempts are the useful part: they are
why the versions in `gradle/libs.versions.toml` are pinned, and anybody who
"helpfully" upgrades Robolectric or Roborazzi will break the build and need this.

## What was tried

Four attempts, and they converge on one cause.

| Attempt | Result |
|---|---|
| Paparazzi **1.3.5** | Breaks `:app:kspDebugKotlin` with an internal compiler error. It puts `kotlin-compiler-embeddable:2.0.21` on the build classpath against this project's Kotlin 2.0.0 / KSP 2.0.0-1.0.21. |
| Paparazzi **1.3.4** | KSP survives. Every render then fails in `Renderer.configureBuildProperties` — its layoutlib does not know `compileSdk = 36`. |
| Paparazzi **2.0.0-alpha05** | Would know API 36. Its POM requires **Kotlin 2.3.0**. |
| Roborazzi **1.75.0** + Robolectric 4.17 | Chosen because it is a test *dependency* rather than a Gradle plugin carrying a compiler, and `@Config(sdk = …)` decouples the render SDK from `compileSdk` — so neither Paparazzi failure applies to it. It fails anyway: `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.3.0, expected version is 2.0.0.` |

**The cause is not the tool.** Every current Compose screenshot-testing library has
moved to Kotlin 2.3.x metadata, and this project is on Kotlin 2.0.0, released
mid-2024. Two libraries chosen for opposite architectures fail at the same version
boundary.

So F-165 is blocked behind a **Kotlin toolchain upgrade**, which is a different and
much larger piece of work than adding a test harness.

## Why the obvious fix was the wrong one

The fix is to move this project from Kotlin 2.0.0 to 2.3.x. That is not a version
bump. Since Kotlin 2.0 the Compose compiler plugin is versioned *with* Kotlin, KSP
must match the compiler exactly, and Room's KSP processor has to support the new
version — so it moves the UI compiler, the annotation processor and the database
codegen at once, on a 40-thousand-line Compose app, in order to add a test harness.

It is worth doing on its own terms. Kotlin 2.0.0 will keep blocking things, and this
is the second time it has. But it is its own change with its own verification, and
it should not be carried out underneath a screenshot-test task — the risk that
matters is not a compile error, it is a KSP processor silently generating different
code.

Adding a plugin and leaving five tests red would have been worse than either. A red
build everyone learns to ignore is the exact failure `.github/workflows/` was just
repaired to stop.

## To enable it

Two independent pieces of work, in this order.

**1. Upgrade the Kotlin toolchain** — its own change, its own commit, verified before
anything else touches it:

```
kotlin  2.0.0          -> 2.3.x
ksp     2.0.0-1.0.21   -> the matching 2.3.x release
```

Then `./gradlew testDebugUnitTest lintDebug bundleRelease` must be green, and the
Compose compiler plugin and Room's generated code both need a real look.

**2. Add the harness.** Roborazzi is the better fit and the reasoning holds
regardless of version: a test dependency rather than a plugin embedding a compiler,
so it cannot conflict with KSP, and `@Config(sdk = [34])` pins the render SDK
independently of `compileSdk` — which is what defeated Paparazzi twice here.

```kotlin
testImplementation("org.robolectric:robolectric:4.17")
testImplementation("io.github.takahirom.roborazzi:roborazzi:1.75.0")
testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.75.0")
testImplementation(libs.androidx.ui.test.junit4)
```

plus `android { testOptions { unitTests { isIncludeAndroidResources = true } } }`,
because Robolectric inflates real resources and without it a golden is a picture of
nothing.

Move `HomeCanvasGoldenTest.kt` into `app/src/test/java/com/ciyato/launcher/ui/` and
annotate it `@RunWith(AndroidJUnit4::class)`, `@GraphicsMode(NATIVE)` — the default
mode does not rasterise, so every capture would be a blank image that passes
forever — and `@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")`.

Record with `-Proborazzi.record.image=true`, verify with `-Proborazzi.verify=true`,
and commit `app/src/test/screenshots/`. **A diff is a review item, never an automatic
re-record** — the entire point of a golden is that a person looks at it.

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
