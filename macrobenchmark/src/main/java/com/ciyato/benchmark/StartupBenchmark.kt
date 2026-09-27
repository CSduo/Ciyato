package com.ciyato.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long Ciyato takes to appear.
 *
 * This is the measurement that matters most for this product and the one code
 * review cannot do. A launcher is not an app you open occasionally — it is what
 * the Home button goes to, dozens of times a day, and every one of those is a
 * cold or warm start of `LauncherHomeActivity`. A regression there is felt
 * immediately and shows up in no functional test (F-166).
 *
 * Both activities are measured because they are genuinely different entry
 * points: `LauncherHomeActivity` is the HOME role and `MainActivity` is the app
 * icon. They load different state and they can regress independently.
 *
 * Runs on hardware only — see `ci/README.md` for the command. The target is
 * built from the app's `benchmark` build type, which is release-shaped and
 * profileable rather than debuggable, so these numbers describe a build that
 * actually exists.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * Cold start of the home screen: the process is dead and the Home button is
     * pressed. The slowest path, and the most common one after a reboot or an
     * overnight process kill.
     */
    @Test
    fun homeColdStart() = measureHome(StartupMode.COLD)

    /**
     * Warm start: the process is alive but the activity was destroyed. What
     * happens after Android reclaims memory while the phone is in a pocket.
     */
    @Test
    fun homeWarmStart() = measureHome(StartupMode.WARM)

    /**
     * Hot start: everything is resident. This is the common case during normal
     * use, and the one where a regression is most visible because the person
     * expects it to be instant.
     */
    @Test
    fun homeHotStart() = measureHome(StartupMode.HOT)

    /** The organizer, from the app icon. A different graph and different state. */
    @Test
    fun organizerColdStart() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        // Partial compilation with the baseline profile applied is what a real
        // first run looks like. Measuring fully-AOT code would flatter every
        // startup number and hide exactly the regressions a baseline profile
        // exists to prevent.
        compilationMode = CompilationMode.Partial(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    private fun measureHome(mode: StartupMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = mode,
        compilationMode = CompilationMode.Partial(),
    ) {
        pressHome()
        // An explicit intent, because startActivityAndWait() with no argument
        // launches the MAIN/LAUNCHER activity — which for a launcher is the
        // organizer, not the home screen. Measuring that and calling it "home
        // start" would be measuring the wrong thing under the right name.
        startActivityAndWait(
            Intent().apply {
                setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.LauncherHomeActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

internal const val TARGET_PACKAGE = "com.ciyato.launcher"

/**
 * Five is the documented minimum for a usable distribution and the most a person
 * will wait for while iterating. Raise it for a release gate, not for a local run.
 */
internal const val ITERATIONS = 5
