package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compiled is not the same as shipped.
 *
 * Ciyato accumulated screens that were complete, working and reachable from
 * nothing: a data-usage report, a bulk file deleter, a daily agenda, a
 * breathing exercise, and a custom-greeting editor wired to state that could
 * not hold a value. Each one compiled, each one appeared in the source tree as
 * if it were a feature, and none of them could be opened (F-170).
 *
 * That is worse than dead weight. It inflates what the product appears to do,
 * it makes Settings look like a catalogue, and it is actively dangerous at
 * handoff: the obvious next task for anyone — human or agent — reading an
 * unwired screen is to wire it, which is how a screen that was deliberately
 * dropped comes back.
 *
 * So the rule is: a screen composable is reachable from a route, or it is
 * listed below with a reason. There is no third state.
 */
class FeatureReachabilityTest {

    private val sourceRoot = File("src/main/java/com/ciyato/launcher")

    /**
     * Screens deliberately kept out of the navigation graph.
     *
     * Empty, and meant to stay that way. Anything added here needs a reason
     * that survives being read aloud — "we might use it later" is what produced
     * the five this test was written for.
     */
    private val intentionallyUnrouted = emptyMap<String, String>()

    private fun kotlinSources(): List<Pair<String, String>> =
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.path.replace('\\', '/').substringAfter("com/ciyato/launcher/") to it.readText() }
            .toList()

    @Test
    fun `every screen composable is reachable from somewhere`() {
        val sources = kotlinSources()
        assertTrue("no Kotlin sources found at ${sourceRoot.absolutePath}", sources.isNotEmpty())

        val screens = sources
            .filter { (path, _) -> path.startsWith("ui/screens/") }
            .flatMap { (path, text) ->
                Regex("""^fun (\w+Screen)\(""", RegexOption.MULTILINE)
                    .findAll(text)
                    .map { path to it.groupValues[1] }
            }
        assertTrue("expected to find screen composables", screens.size > 20)

        val orphans = screens.filter { (ownPath, name) ->
            if (name in intentionallyUnrouted) return@filter false
            // Referenced from any file other than the one declaring it. A
            // screen opened by another screen (a detail view) counts - the
            // question is whether a person can get there, not whether an
            // Activity names it directly.
            sources.none { (path, text) ->
                path != ownPath && Regex("""\b$name\s*\(""").containsMatchIn(text)
            }
        }.map { it.second }.sorted()

        assertTrue(
            "these screens are reachable from nothing: $orphans. Route them, delete them, " +
                "or add them to intentionallyUnrouted with a reason. Leaving a working screen " +
                "unrouted is how deliberately-dropped features come back.",
            orphans.isEmpty(),
        )
    }

    @Test
    fun `both hosts can reach every destination they declare`() {
        val launcher = File(sourceRoot, "LauncherHomeActivity.kt").readText()
        val declared = Regex("""(?:object|data class) (\w+)[^:\n]*: LauncherDest""")
            .findAll(launcher).map { it.groupValues[1] }.toSet()
        val rendered = Regex("""is LauncherDest\.(\w+)\s*->""")
            .findAll(launcher).map { it.groupValues[1] }.toSet()

        val unrenderable = (declared - rendered).sorted()
        assertTrue(
            "LauncherHomeActivity declares destinations it never renders: $unrenderable - " +
                "navigating to one shows a blank screen",
            unrenderable.isEmpty(),
        )
    }

    @Test
    fun `a destination that can be restored is a destination that can be rendered`() {
        // Process death restores the launcher to the destination it was on. A
        // destination missing from the restore list silently sends the person
        // back to Home instead; one in the list that cannot render sends them
        // to a blank screen. Both are failures nobody sees in testing, because
        // they need the system to kill the process first.
        val launcher = File(sourceRoot, "LauncherHomeActivity.kt").readText()
        val restoreList = launcher
            .substringAfter("ARGLESS_DESTS: List<LauncherDest> = listOf(")
            .substringBefore("\n)")
        assertTrue("could not find ARGLESS_DESTS - has it been renamed?", restoreList.length in 50..4000)
        val restorable = Regex("""LauncherDest\.(\w+)""")
            .findAll(restoreList)
            .map { it.groupValues[1] }.toSet()
        val rendered = Regex("""is LauncherDest\.(\w+)\s*->""")
            .findAll(launcher).map { it.groupValues[1] }.toSet()

        val broken = (restorable - rendered).sorted()
        assertTrue("restorable destinations that cannot render: $broken", broken.isEmpty())
    }

    @Test
    fun `the feature matrix accounts for every route`() {
        val matrix = File("../docs/FEATURE_MATRIX.md")
        assertTrue("docs/FEATURE_MATRIX.md is missing at ${matrix.absolutePath}", matrix.exists())
        val text = matrix.readText()

        val routes = Regex("""composable\(\s*"([^"?/]+)""")
            .findAll(File(sourceRoot, "MainActivity.kt").readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        // Without this the check passes vacuously if the route regex ever stops
        // matching - an empty set has nothing undocumented in it.
        assertTrue("found only ${routes.size} routes; the regex has probably drifted", routes.size >= 25)

        val undocumented = routes.filterNot { text.contains("`$it`") }.sorted()
        assertTrue(
            "routes with no row in docs/FEATURE_MATRIX.md: $undocumented. Every reachable " +
                "feature needs a recorded maturity, or the matrix stops being the answer to " +
                "\"is this shipped?\"",
            undocumented.isEmpty(),
        )
    }
}
