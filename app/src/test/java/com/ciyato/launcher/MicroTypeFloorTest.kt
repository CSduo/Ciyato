package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing someone has to read is smaller than 11sp.
 *
 * The design system deliberately defined labels down to 9sp and dozens of
 * screens reached past the system to set 9sp and 10sp directly (F-038). The
 * problem was never density — density is a virtue in a launcher — it was what
 * ended up at that size: "Focus", a permission category, a duplicate count.
 * Status nobody can read is status that was not delivered, and a layout tuned
 * around 9sp text is a layout that clips at a 1.3 font scale.
 *
 * This is a source check because there is no runtime moment at which to assert
 * it: a `fontSize` literal in a composable is just a number until something
 * draws it, and catching it at review time is the point. A device pass at
 * fontScale 1.0 / 1.3 / 2.0 is still required and still outstanding — see
 * `CLAUDE_VALIDATION.md`. This test stops the regression; it does not replace
 * the device run.
 */
class MicroTypeFloorTest {

    /**
     * The only text allowed below the floor, with the reason attached.
     *
     * A badge numeral sits inside a fixed-size circle whose meaning is already
     * carried by the circle, its colour and its content description, and whose
     * geometry cannot absorb a larger glyph. That is the whole exemption.
     */
    private val allowed = mapOf(
        "ui/theme/Type.kt" to "defines labelXS itself, whose contract is the badge numeral",
    )

    @Test
    fun `no UI text is set below the readable floor`() {
        val uiRoot = File("src/main/java/com/ciyato/launcher/ui")
        assertTrue("ui sources not found at ${uiRoot.absolutePath}", uiRoot.isDirectory)

        val tooSmall = Regex("""fontSize = (\d+)\.sp""")
        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val relative = file.path.replace('\\', '/').substringAfter("com/ciyato/launcher/")
                if (relative in allowed) return@flatMap emptySequence<String>()
                tooSmall.findAll(file.readText())
                    .map { it.groupValues[1].toInt() }
                    .filter { it < 11 }
                    .map { "$relative uses ${it}sp" }
            }
            .toList()

        assertTrue(
            "text below the 11sp floor: $offenders. Use 11sp or larger, or - if it is " +
                "genuinely a glyph inside a fixed shape whose meaning is carried elsewhere - " +
                "add the file to this test's allowed map with the reason.",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the allowed list stays short and stays true`() {
        // An exemption list that grows is the floor being repealed one file at
        // a time. If this needs raising, that is a design decision worth an
        // argument, not a quiet edit.
        assertTrue("the sub-floor exemption list has grown: ${allowed.keys}", allowed.size <= 1)
        allowed.forEach { (path, reason) ->
            assertTrue("$path is exempt but no longer exists", File("src/main/java/com/ciyato/launcher/$path").exists())
            assertTrue("$path is exempt with no stated reason", reason.length > 12)
        }
    }
}
