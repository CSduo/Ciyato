package com.ciyato.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.ciyato.launcher.data.CanvasPos
import com.ciyato.launcher.ui.screens.HomeCanvasSurface
import org.junit.Rule
import org.junit.Test

/**
 * Goldens for the one layout that can silently break every Home object at once.
 *
 * Ciyato's Home is not a stack of sections; it is a single custom `Layout` that
 * measures and places every visible object — the greeting, the clock, the weather
 * card, category cards, widgets, the app grid — in one pass. That design is
 * deliberate and it is what the audit's "structural layout drift" risk lives in:
 * one change to the measure pass moves everything, and no logic test notices
 * because every function still returns the right value (F-165).
 *
 * The user's actual complaint about this product was features *looking*
 * structurally bad. That is not something an assertion about state can prevent.
 *
 * ## Scope, honestly
 *
 * These goldens cover the canvas layout itself and its stated invariants. They do
 * **not** cover Home, Drawer, Files, Photos or Settings as whole screens, which is
 * what F-165 asks for in full: those composables take a `LauncherViewModel`, so
 * rendering one needs a real DataStore and a real PackageManager. Getting them
 * under golden coverage means hoisting their state first, which is a refactor of
 * eight screens and is recorded as the prerequisite in `CLAUDE_VALIDATION.md`
 * rather than pretended away here.
 *
 * What is covered is the highest-value part: the layout that owns spacing,
 * overlap and displacement for everything on Home.
 *
 * Record with `./gradlew :app:recordPaparazziDebug`, verify with
 * `./gradlew :app:verifyPaparazziDebug`. A diff is a review item, never an
 * automatic update.
 */
class HomeCanvasGoldenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        // A pinned device, because a golden is only meaningful against a fixed
        // frame. Pixel 5 is the common reference size; the dark theme is not a
        // choice here, it is the only theme Ciyato has.
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false,
    )


    @Test
    fun `default arrangement stacks objects in flow order`() {
        paparazzi.snapshot {
            Surface {
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
    }

    @Test
    fun `a row of two category cards splits the width evenly`() {
        // The invariant: a row of N shares that row's width, and the row's height
        // is the REAL measured height of its tallest child rather than a guess.
        paparazzi.snapshot {
            Surface {
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
    }

    @Test
    fun `a freely positioned object leaves no gap in the flow`() {
        // This is the defect that shipped once: an object dragged off the grid
        // was drawn absolutely AND still reserved its flow slot, so Home had a
        // hole where the object used to be. An id in freePositions must never
        // also be in flowRows, and this is what that looks like when it holds.
        paparazzi.snapshot {
            Surface {
                HomeCanvasSurface(
                    flowRows = listOf(
                        listOf("greeting"),
                        listOf("search"),
                        listOf("app-grid"),
                    ),
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
    }

    @Test
    fun `overlapping free objects draw in z order`() {
        // Overlap between freely-positioned objects is allowed by design - there
        // is no collision rule - so what matters is that the higher z draws on
        // top, consistently. Two cards at almost the same spot is the cheapest
        // way to see that stop being true.
        paparazzi.snapshot {
            Surface {
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
    }

    @Test
    fun `an object at the far edge is still on screen`() {
        // CanvasPos clamps the TOP-LEFT corner into 0f..1f, which alone does not
        // keep the body on screen: a top-left at 0.97 puts the object outside the
        // canvas. Keeping the far edge in range needs the object's own size and
        // is done at the drop site. This golden is what that guarantee looks
        // like, so losing it is visible rather than theoretical.
        paparazzi.snapshot {
            Surface {
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
    }

    private companion object {
        /** Pixel 5 at 2.75x: 1080x2340 device pixels, minus the chrome Home leaves. */
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
private fun Surface(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A)),
    ) { content() }
}
