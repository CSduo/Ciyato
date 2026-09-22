package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every Settings row must lead somewhere real.
 *
 * Settings took twenty-eight parameters, twenty-six of them optional
 * `onNavigateToX: () -> Unit` callbacks, and the normal app-icon path supplied
 * only about half of them. The rows for the rest still rendered, looked
 * enabled, and did nothing at all when tapped, because the call was
 * `?.invoke()` (F-171). Making every destination a required field of
 * [com.ciyato.launcher.ui.screens.SettingsDestinations] removed that class of
 * defect: the compiler now refuses a host that forgets one.
 *
 * It replaced it with a smaller one. A required field can be satisfied with a
 * route string that no `composable()` declares, and that does not fail to
 * compile — it throws at the moment the person taps the row. This test is the
 * guard for that, and it has to read source: the navigation graph only exists
 * once a NavHost is composed, so there is nothing to inspect from a JVM test
 * without pulling in the whole Android UI stack.
 */
class SettingsNavigationTest {

    private fun source(relative: String): String {
        val file = File("src/main/java/com/ciyato/launcher/$relative")
        assertTrue(
            "expected to find $relative at ${file.absolutePath}; if the module layout moved, " +
                "this test needs updating rather than deleting - it is the only check that " +
                "Settings rows lead anywhere",
            file.exists(),
        )
        return file.readText()
    }

    private val destinationsBlock = Regex(
        """destinations = SettingsDestinations\((.*?)\n\s*\),""",
        RegexOption.DOT_MATCHES_ALL,
    )

    @Test
    fun `every route Settings navigates to is declared in MainActivity`() {
        val main = source("MainActivity.kt")
        val block = destinationsBlock.find(main)?.groupValues?.get(1)
            ?: error("MainActivity no longer builds a SettingsDestinations - check this test still applies")

        val requested = Regex("""navigate\("([^"]+)"\)""").findAll(block).map { it.groupValues[1] }.toSet()
        val declared = Regex("""composable\("([^"?/]+)""").findAll(main).map { it.groupValues[1] }.toSet()

        val missing = (requested - declared).sorted()
        assertTrue(
            "Settings navigates to routes MainActivity never declares: $missing. " +
                "Tapping one of those rows throws rather than opening anything.",
            missing.isEmpty(),
        )
        assertTrue("expected Settings to wire real routes", requested.size >= 20)
    }

    @Test
    fun `every launcher destination Settings can reach is actually handled`() {
        val launcher = source("LauncherHomeActivity.kt")
        val set = Regex("""dest = LauncherDest\.(\w+)""").findAll(launcher).map { it.groupValues[1] }.toSet()
        val handled = Regex("""LauncherDest\.(\w+)\s*->""").findAll(launcher).map { it.groupValues[1] }.toSet()

        val unhandled = (set - handled).sorted()
        assertTrue(
            "LauncherHomeActivity sets destinations it never renders: $unhandled. " +
                "Those rows would navigate to a blank screen.",
            unhandled.isEmpty(),
        )
    }

    @Test
    fun `both hosts wire the same destination contract`() {
        // The two activities are a deliberate product split - a HOME layer and
        // an organizer layer (F-173). What is not deliberate is two different
        // sets of capabilities behind the same Settings screen, which is how
        // F-171 happened in the first place. One data class with required
        // fields is what keeps them equal; this asserts neither host has
        // quietly grown its own.
        val fields = Regex("""val (open\w+): \(\) -> Unit""")
            .findAll(source("ui/screens/SettingsDestinations.kt"))
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("expected SettingsDestinations to declare destinations", fields.size >= 20)

        listOf("MainActivity.kt", "LauncherHomeActivity.kt").forEach { host ->
            val block = destinationsBlock.find(source(host))?.groupValues?.get(1)
                ?: error("$host no longer builds a SettingsDestinations")
            val wired = Regex("""(open\w+) =""").findAll(block).map { it.groupValues[1] }.toSet()
            assertEquals(
                "$host does not wire the same destinations as the contract declares",
                fields,
                wired,
            )
        }
    }
}
