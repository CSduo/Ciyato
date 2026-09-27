package com.ciyato.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ciyato.launcher.data.CanvasPos
import com.ciyato.launcher.ui.screens.HomeCanvasSurface
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the one layout that can silently break every Home object at once.
 *
 * Ciyato's Home is not a stack of sections; it is a single custom `Layout` that
 * measures and places every visible object — the greeting, the clock, the weather
 * card, category cards, widgets, the app grid — in one pass. That design is
 * deliberate, and it is exactly where the audit's "structural layout drift" risk
 * lives: one change to the measure pass moves everything, and no logic test
 * notices, because every function still returns the right value (F-165).
 *
 * The complaint that started this project was features *looking* structurally
 * bad. An assertion about state cannot prevent that.
 *
 * ## Why Robolectric, after three failed attempts at this
 *
 * Paparazzi cannot work here, and it took four attempts to establish why. 1.3.5
 * puts `kotlin-compiler-embeddable` 2.0.21 on the build classpath and breaks KSP;
 * 1.3.4 survives KSP and then fails every render because its layoutlib does not
 * know `compileSdk = 36`; 2.0.0-alpha05 would know it and requires Kotlin 2.3.0.
 * Roborazzi 1.75.0 fails for the same last reason — its metadata is 2.3.0.
 *
 * Two things make this version work where all of those failed. Roborazzi is a test
 * *dependency* rather than a Gradle plugin carrying its own compiler, so there is
 * nothing to conflict with KSP; and `@Config(sdk = ...)` picks the render SDK
 * independently of `compileSdk`, which is what defeated Paparazzi twice. Pinning
 * to the last release built against Kotlin 1.9.x metadata — which a 2.0.0 compiler
 * reads fine — closes the remaining gap without touching the toolchain.
 *
 * ## Scope, honestly
 *
 * These cover the canvas layout and its stated invariants. They do **not** cover
 * Home, Drawer, Files, Photos or Settings as whole screens, which is what F-165
 * asks for in full: each of those takes a `LauncherViewModel`, so rendering one
 * needs a real DataStore and a real PackageManager. Whole-screen coverage needs
 * their state hoisted first — a refactor of eight screens, recorded as the
 * prerequisite in `CLAUDE_VALIDATION.md`.
 *
 * What is covered is where spacing, overlap and displacement for every Home object
 * actually live.
 *
 * ## Running
 *
 * Record:  `./gradlew :app:testDebugUnitTest -Proborazzi.record.image=true`
 * Verify:  `./gradlew :app:testDebugUnitTest -Proborazzi.verify=true`
 *
 * Goldens live in `app/src/test/screenshots/` and are committed. **A diff is a
 * review item, never an automatic re-record** — the entire point of a golden is
 * that a person looks at it.
 */
@RunWith(AndroidJUnit4::class)
// NATIVE graphics is required: the default mode does not rasterise, so every
// capture would be a blank image that passes forever.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pinned SDK and device, because a golden is only meaningful against a fixed
// frame. 34 rather than 36 deliberately: this layout has no API-dependent
// behaviour, and a long-supported level keeps the goldens stable across
// Robolectric upgrades.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HomeCanvasGoldenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { CiyatoBackdrop { content() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun `default arrangement stacks objects in flow order`() {
        capture("canvas_flow_default") {
            HomeCanvasSurface(
                flowRows = listOf(
                    listOf("greeting"),
                    listOf("datetime"),
                    listOf("search"),
                    listOf("weather"),
                    listOf("categories-heading"),
                    listOf("category:Work", "category:Social"),
                    listOf("app-grid"),
                ),
                freePositions = emptyMap(),
                canvasWidthPx = CANVAS_W,
                canvasHeightPx = CANVAS_H,
                flowStartYPx = 48f,
                freeStartYPx = 0f,
                horizontalPaddingPx = 32f,
                rowSpacingPx = 24f,
                columnSpacingPx = 16f,
                bottomPaddingPx = 48f,
            ) { id -> CanvasObject(id, if (id == "app-grid") 220 else 64) }
        }
    }

    @Test
    fun `a row of two category cards splits the width evenly`() {
        // The invariant: a row of N shares that row's width, and the row's height
        // is the REAL measured height of its tallest child rather than a guess.
        capture("canvas_row_split") {
            HomeCanvasSurface(
                flowRows = listOf(
                    listOf("categories-heading"),
                    listOf("category:Work", "category:Social"),
                    listOf("category:Media", "category:Tools"),
                ),
                freePositions = emptyMap(),
                canvasWidthPx = CANVAS_W,
                canvasHeightPx = CANVAS_H,
                flowStartYPx = 24f,
                freeStartYPx = 0f,
                horizontalPaddingPx = 32f,
                rowSpacingPx = 24f,
                columnSpacingPx = 16f,
                bottomPaddingPx = 24f,
            ) { id -> CanvasObject(id, if (id == "category:Work") 120 else 88) }
        }
    }

    @Test
    fun `a freely positioned object leaves no gap in the flow`() {
        // This one shipped as a bug: an object dragged off the grid was drawn
        // absolutely AND still reserved its flow slot, so Home had a hole where
        // the object used to be. An id in freePositions must never also be in
        // flowRows, and this is what that looks like when it holds.
        capture("canvas_free_no_gap") {
            HomeCanvasSurface(
                flowRows = listOf(listOf("greeting"), listOf("search"), listOf("app-grid")),
                freePositions = mapOf("weather" to CanvasPos(x = 0.45f, y = 0.08f, z = 1)),
                canvasWidthPx = CANVAS_W,
                canvasHeightPx = CANVAS_H,
                flowStartYPx = 48f,
                freeStartYPx = 24f,
                horizontalPaddingPx = 32f,
                rowSpacingPx = 24f,
                columnSpacingPx = 16f,
                bottomPaddingPx = 48f,
            ) { id -> CanvasObject(id, if (id == "app-grid") 220 else 64) }
        }
    }

    @Test
    fun `overlapping free objects draw in z order`() {
        // Overlap between freely-positioned objects is allowed by design — there
        // is no collision rule — so what matters is that the higher z draws on
        // top, consistently. Two cards at almost the same spot is the cheapest way
        // to see that stop being true.
        capture("canvas_z_order") {
            HomeCanvasSurface(
                flowRows = listOf(listOf("app-grid")),
                freePositions = mapOf(
                    "weather" to CanvasPos(x = 0.10f, y = 0.10f, z = 1),
                    "today" to CanvasPos(x = 0.18f, y = 0.16f, z = 2),
                ),
                canvasWidthPx = CANVAS_W,
                canvasHeightPx = CANVAS_H,
                flowStartYPx = 48f,
                freeStartYPx = 24f,
                horizontalPaddingPx = 32f,
                rowSpacingPx = 24f,
                columnSpacingPx = 16f,
                bottomPaddingPx = 48f,
            ) { id -> CanvasObject(id, if (id == "app-grid") 220 else 96) }
        }
    }

    @Test
    fun `an object at the far edge is still on screen`() {
        // CanvasPos clamps the TOP-LEFT corner into 0f..1f, which alone does not
        // keep the body on screen: a top-left at 0.97 puts the object outside the
        // canvas. Keeping the far edge in range needs the object's own size and
        // happens at the drop site. This golden is what that guarantee looks like,
        // so losing it is visible rather than theoretical.
        capture("canvas_far_edge") {
            HomeCanvasSurface(
                flowRows = listOf(listOf("app-grid")),
                freePositions = mapOf("weather" to CanvasPos(x = 0.97f, y = 0.9f, z = 1)),
                canvasWidthPx = CANVAS_W,
                canvasHeightPx = CANVAS_H,
                flowStartYPx = 48f,
                freeStartYPx = 24f,
                horizontalPaddingPx = 32f,
                rowSpacingPx = 24f,
                columnSpacingPx = 16f,
                bottomPaddingPx = 48f,
            ) { id -> CanvasObject(id, 96) }
        }
    }

    private companion object {
        const val CANVAS_W = 1080f
        const val CANVAS_H = 2000f
    }
}

/** One labelled block, standing in for whatever object the canvas is placing. */
@Composable
private fun CanvasObject(id: String, height: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Color(0xFF1A1A1A)),
    ) {
        Text(id, color = Color(0xFFBFBFBF))
    }
}

/** Ciyato's own background, so a golden shows what the person sees. */
@Composable
private fun CiyatoBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A)),
    ) { content() }
}
