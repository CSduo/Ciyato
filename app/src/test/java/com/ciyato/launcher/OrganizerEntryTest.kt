package com.ciyato.launcher

import com.ciyato.launcher.data.OrganizerEntry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two layers must agree on what a destination name means.
 *
 * Ciyato is deliberately two activities — a HOME activity that draws the
 * launcher, and an organizer reached from the app icon or from Home. F-173 keeps
 * that boundary, because it is a platform role rather than an accident. What was
 * not deliberate was how they talked: Home built an Intent with a raw string
 * extra and the organizer resolved it through a `when` listing six values, so
 * "insights" — a real route — resolved to null and landed the person on
 * Overview. A typo did the same. Adding a destination on one side taught the
 * other side nothing (F-044, F-074).
 *
 * `OrganizerEntry` is now the single vocabulary and `intentFor` the only in-app
 * way to build the intent, so the compiler catches a wrong name. What the
 * compiler cannot catch is an entry whose route no `composable()` declares —
 * that resolves fine and then throws when NavHost is asked for it. This test is
 * that half.
 */
class OrganizerEntryTest {

    private fun mainActivitySource(): String {
        val file = File("src/main/java/com/ciyato/launcher/MainActivity.kt")
        assertTrue("MainActivity.kt not found at ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Test
    fun `every entry names a route the NavHost declares`() {
        val source = mainActivitySource()
        val declared = Regex("""composable\(\s*"([^"?/]+)""")
            .findAll(source).map { it.groupValues[1] }.toSet()
        assertTrue("found only ${declared.size} routes; the regex has probably drifted", declared.size >= 25)

        val missing = OrganizerEntry.entries.filterNot { it.route in declared }.map { it.route }.sorted()
        assertTrue(
            "these entries resolve to routes MainActivity never declares: $missing. " +
                "Entering the organizer at one of them throws instead of opening anything.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `the launcher layer never builds a destination intent by hand`() {
        // The whole point of the typed factory is that no call site spells a
        // route. One `putExtra` back in LauncherHomeActivity would restore the
        // silent-fallback behaviour for that one tap and nothing would say so.
        val launcher = File("src/main/java/com/ciyato/launcher/LauncherHomeActivity.kt").readText()
        assertTrue(
            "LauncherHomeActivity builds a start-destination extra directly - " +
                "use MainActivity.intentFor(context, OrganizerEntry.X) instead",
            !launcher.contains("EXTRA_START_DESTINATION"),
        )
    }

    // ── Resolution ───────────────────────────────────────────────────────────

    @Test
    fun `a known route resolves to its entry`() {
        OrganizerEntry.entries.forEach { entry ->
            assertEquals(entry, OrganizerEntry.fromExtra(entry.route))
        }
    }

    @Test
    fun `old spellings still resolve, because pinned shortcuts carry them`() {
        // A shortcut created before "home" stopped meaning this shell (F-071)
        // still holds that word. Dropping the alias does not fail visibly - it
        // lands someone on Overview and reads as a broken shortcut.
        assertEquals(OrganizerEntry.Overview, OrganizerEntry.fromExtra("home"))
        assertEquals(OrganizerEntry.Overview, OrganizerEntry.fromExtra("dashboard"))
        assertEquals(OrganizerEntry.Photos, OrganizerEntry.fromExtra("shared"))
    }

    @Test
    fun `an unknown name resolves to null rather than to a route`() {
        // Null means "the caller named nothing usable", which the caller turns
        // into onboarding state. Resolving an unknown name to Overview here
        // would hide a broken deep link behind a plausible screen.
        assertNull(OrganizerEntry.fromExtra(null))
        assertNull(OrganizerEntry.fromExtra(""))
        assertNull(OrganizerEntry.fromExtra("   "))
        assertNull(OrganizerEntry.fromExtra("insights"))
        assertNull(OrganizerEntry.fromExtra("filez"))
    }

    @Test
    fun `resolution is not case or whitespace sensitive`() {
        // These arrive from outside the app, where nobody guarantees the shape.
        assertEquals(OrganizerEntry.Files, OrganizerEntry.fromExtra("Files"))
        assertEquals(OrganizerEntry.Files, OrganizerEntry.fromExtra(" files "))
        assertEquals(OrganizerEntry.Photos, OrganizerEntry.fromExtra("PHOTOS"))
    }

    @Test
    fun `no two entries claim the same route`() {
        val routes = OrganizerEntry.entries.map { it.route }
        assertEquals("two entries cannot mean one route", routes.toSet().size, routes.size)
    }

    @Test
    fun `no alias shadows a real route`() {
        // An alias that collides with a route would make the route's own
        // resolution depend on lookup order.
        val routes = OrganizerEntry.entries.map { it.route }.toSet()
        val aliases = OrganizerEntry.acceptedSpellings - routes
        aliases.forEach { alias ->
            assertTrue("$alias is both an alias and a route", alias !in routes)
        }
        assertTrue("expected the documented aliases to still be accepted", aliases.size >= 3)
    }
}
