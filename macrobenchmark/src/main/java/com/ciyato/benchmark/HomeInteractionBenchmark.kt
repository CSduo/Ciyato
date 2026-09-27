package com.ciyato.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for the three gestures a launcher lives or dies by.
 *
 * Swiping up to the drawer, swiping between workspace pages, and scrolling the
 * drawer are the interactions someone performs dozens of times a day. Jank there
 * is the difference between a launcher that feels premium and one that does not,
 * and it is invisible to every test that only checks what the code returns
 * (F-166).
 *
 * These are the paths most exposed to the risks the audit named: a single
 * 3,000-line Home composable, a custom canvas `Layout` measuring every object in
 * one pass, wallpaper overlays, and decorative animation.
 *
 * Hardware only. The gesture coordinates are derived from the real display size
 * rather than hard-coded, so this is not tuned to one phone.
 */
@RunWith(AndroidJUnit4::class)
class HomeInteractionBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Swipe up from Home to open the app drawer. */
    @Test
    fun openDrawer() = measureGesture { swipeUpFromBottom() }

    /** Swipe sideways between workspace pages. */
    @Test
    fun swipeWorkspacePages() = measureGesture {
        val centreY = device.displayHeight / 2
        val fromX = (device.displayWidth * 0.85).toInt()
        val toX = (device.displayWidth * 0.15).toInt()
        // Both directions: the pager keeps neighbours composed on one side only
        // when beyondBoundsPageCount is asymmetric, and a regression can appear
        // in one direction and not the other.
        device.swipe(fromX, centreY, toX, centreY, 12)
        device.waitForIdle()
        device.swipe(toX, centreY, fromX, centreY, 12)
        device.waitForIdle()
    }

    /** Scroll the drawer, which is where the icon raster cache earns its place. */
    @Test
    fun scrollDrawer() = measureGesture {
        swipeUpFromBottom()
        // The drawer is a scrollable grid; find it by scrollability rather than
        // by a resource id, which Compose does not publish.
        val scrollable = device.wait(Until.findObject(By.scrollable(true)), 5_000)
        if (scrollable != null) {
            scrollable.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                scrollable.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }
    }

    private fun measureGesture(block: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        // WARM, not COLD: these measure the gesture, and including process
        // startup in the same numbers would bury a jank regression under the
        // much larger startup cost.
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent().apply {
                    setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.LauncherHomeActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        },
    ) {
        block()
    }
}

/** A swipe up from the bottom edge, sized from the real display. */
private fun MacrobenchmarkScope.swipeUpFromBottom() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.85).toInt(),
        width / 2,
        (height * 0.25).toInt(),
        12,
    )
    device.waitForIdle()
}
