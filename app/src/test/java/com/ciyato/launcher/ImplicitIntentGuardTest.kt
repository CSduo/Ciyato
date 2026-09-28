package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An implicit Intent that nothing handles must never reach `startActivity` bare.
 *
 * In a launcher this is not an ordinary crash. `openAppInfo` and `uninstallApp` were
 * reached from the long-press menu on every app icon, and `openEventInCalendar` from
 * every row in Agenda, all three calling `startActivity` with no guard — so
 * `ActivityNotFoundException` killed the process that draws the home screen. The
 * person is then looking at their bare wallpaper.
 *
 * The failure is not hypothetical. `ACTION_DELETE` is refused on managed profiles and
 * on devices where uninstall is administrator-controlled; `ACTION_VIEW` on a calendar
 * event has no handler on a phone whose calendar app was removed, which does not stop
 * the events being readable through the provider; several settings actions are absent
 * from stripped OEM and enterprise images.
 *
 * Explicit intents are exempt and always will be: `Intent(context, Foo::class.java)`
 * targets a component declared in this app's own manifest, so there is nothing to
 * resolve and nothing to fail.
 */
class ImplicitIntentGuardTest {

    private val src = File("src/main/java/com/ciyato/launcher")

    /** The two helpers are where the guarding lives, so they are not subject to it. */
    private val helperFiles = setOf("SystemScreens.kt")

    /**
     * Marks an intent as targeting a component of this app.
     *
     * `MainActivity.intentFor(...)` is Ciyato's own factory and builds an explicit
     * intent; it is named here rather than pattern-matched because a reader should be
     * able to see exactly which spellings are trusted.
     */
    private val explicitMarkers = listOf(
        "::class.java",
        "MainActivity.intentFor",
    )

    /** Line indices (0-based) that sit inside a `runCatching {` or `try {` block. */
    private fun guardedLines(lines: List<String>): Set<Int> {
        val inside = mutableSetOf<Int>()
        val openDepths = ArrayDeque<Int>()
        var depth = 0
        lines.forEachIndexed { index, raw ->
            // Strings and line comments removed, so a brace inside either cannot move
            // the depth. Kotlin templates put real braces inside string literals, which
            // is what broke an earlier attempt at this kind of scan.
            val code = raw.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "\"\"").substringBefore("//")
            val opensGuard = (code.contains("runCatching") && code.contains("{")) ||
                Regex("""\btry\s*\{""").containsMatchIn(code)
            val depthBefore = depth
            code.forEach { ch ->
                when (ch) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        while (openDepths.isNotEmpty() && depth <= openDepths.last()) {
                            openDepths.removeLast()
                        }
                    }
                }
            }
            if (opensGuard) openDepths.addLast(depthBefore)
            if (openDepths.isNotEmpty()) inside += index
        }
        return inside
    }

    @Test
    fun `no implicit intent is launched without a guard`() {
        assertTrue("sources not found at ${src.absolutePath}", src.isDirectory)

        val offenders = mutableListOf<String>()
        src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in helperFiles }
            .forEach { file ->
                val lines = file.readText().split("\n")
                val guarded = guardedLines(lines)
                lines.forEachIndexed { index, raw ->
                    val code = raw.substringBefore("//")
                    if (!code.contains("startActivity")) return@forEachIndexed
                    if (code.trimStart().startsWith("*")) return@forEachIndexed
                    if (code.contains("fun ")) return@forEachIndexed
                    if (index in guarded) return@forEachIndexed

                    // The intent may be built over the following few lines.
                    val window = lines.subList(index, minOf(index + 5, lines.size))
                        .joinToString("\n")
                    if (explicitMarkers.any { window.contains(it) }) return@forEachIndexed

                    offenders += "${file.name}:${index + 1}  ${raw.trim().take(80)}"
                }
            }

        assertEquals(
            "these launch a possibly-implicit Intent with no guard, which crashes the " +
                "launcher process when nothing handles it: $offenders. Use " +
                "openSystemScreen() for a settings page or openWithApp() for another " +
                "app, both in ui/components/SystemScreens.kt. An explicit intent to one " +
                "of Ciyato's own components is fine and needs no guard.",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the helpers the rest of the app is told to use still exist`() {
        // Without these the failure message above sends people somewhere that is not
        // there, and the rule becomes unfollowable rather than enforced.
        val helpers = File("src/main/java/com/ciyato/launcher/ui/components/SystemScreens.kt")
        assertTrue("SystemScreens.kt is gone", helpers.exists())
        val text = helpers.readText()
        assertTrue("openSystemScreen is gone", text.contains("fun openSystemScreen("))
        assertTrue("openWithApp is gone", text.contains("fun openWithApp("))
        // Both must actually report the failure rather than only swallowing it - a
        // silent helper would satisfy this test while recreating the defect it exists
        // to prevent, which is precisely how the runCatching-with-no-else sites arose.
        assertTrue(
            "the helpers no longer tell the person anything when the Intent is refused, " +
                "which turns a crash into a tap that silently does nothing",
            text.split("Toast.makeText").size - 1 >= 2,
        )
    }
}
