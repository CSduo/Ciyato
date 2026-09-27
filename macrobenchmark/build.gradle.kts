plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

/**
 * Macrobenchmarks and baseline-profile generation for Ciyato.
 *
 * A launcher is the one app on a phone that is opened and returned to
 * constantly, and its performance can regress while every functional test still
 * passes - a 3,000-line Home composable, media scans and animated effects are
 * all things code review does not measure (F-166).
 *
 * This module builds its own APK that drives the app through UiAutomator, so it
 * cannot run on the JVM. Everything here is configured and compiled in CI; the
 * numbers come from hardware. `ci/README.md` has the commands.
 */
android {
    namespace = "com.ciyato.benchmark"
    compileSdk = 36

    defaultConfig {
        // 24 is the floor for the macrobenchmark library itself. The app's own
        // minSdk is 26, so nothing here runs anywhere the app does not.
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Benchmarks must measure a release-shaped build. A debuggable app is
    // slower in ways that have nothing to do with the code being measured, and
    // the numbers from one are not comparable to anything a user experiences.
    targetProjectPath = ":app"

    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // Matches the app's `benchmark` build type: minified like release, but
        // signed with debug keys and profileable so it can actually be measured.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.junit)
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        // Only the benchmark variant is meaningful; building the others wastes
        // time and produces numbers nobody should trust.
        variant.enable = variant.buildType == "benchmark"
    }
}
