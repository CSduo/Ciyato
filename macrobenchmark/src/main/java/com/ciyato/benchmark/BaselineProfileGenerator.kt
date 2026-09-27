package com.ciyato.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile that makes the first run fast.
 *
 * Without a profile, everything on the startup path is interpreted on first
 * execution and only compiled later. For an ordinary app that costs one slow
 * launch. For a launcher it costs the slow launch *and* every Home press until
 * the profile is built from real use — which is the worst possible first
 * impression of a product whose whole claim is that it feels premium (F-166).
 *
 * The journey below is deliberately the boring one: open Home, open the drawer,
 * come back. Not a tour of every feature. A baseline profile is a list of
 * methods to compile ahead of time, and padding it with paths nobody takes on
 * first run makes the profile bigger, the install slower, and the important
 * methods no faster.
 *
 * Hardware only, and the output must be committed — see `ci/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        openHome()

        // Swipe up to the drawer and back. This is the single most common thing
        // anyone does with a launcher, so it belongs in the profile even though
        // it is not startup.
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, (height * 0.85).toInt(), width / 2, (height * 0.25).toInt(), 12)
        device.waitForIdle()
        device.wait(Until.hasObject(By.scrollable(true)), 3_000)
        device.pressBack()
        device.waitForIdle()

        // One workspace page swipe: it exercises the canvas layout pass and the
        // pager, which are the two most expensive things Home does.
        device.swipe((width * 0.85).toInt(), height / 2, (width * 0.15).toInt(), height / 2, 12)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openHome() {
        startActivityAndWait(
            Intent().apply {
                setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.LauncherHomeActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
