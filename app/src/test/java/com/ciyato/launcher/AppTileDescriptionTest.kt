package com.ciyato.launcher

import com.ciyato.launcher.ui.components.appTileDescription
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What TalkBack says about an app tile.
 *
 * Screen-reader strings are the text nobody proofreads, because looking at the
 * screen does not show them. Every surface that draws an app — Home, the dock,
 * the drawer, a category, the hidden-apps list — had its own answer, and the
 * dock had none at all: TalkBack reached five unnamed buttons in a row (F-047).
 *
 * There was a helper for this, `appItemSemantics`, with zero call sites and two
 * defects. It appended "Double-tap to open. Long-press for options." to the
 * description, which duplicates what TalkBack already announces for a Button and
 * still leaves the long-press unreachable by switch access. And it did not merge
 * descendants, so the label inside the tile announced a second time.
 */
class AppTileDescriptionTest {

    @Test
    fun `an ordinary app is just its name`() {
        assertEquals("Gmail", appTileDescription("Gmail"))
    }

    @Test
    fun `state is appended, in a fixed order`() {
        // Fixed order so the same tile always reads the same way. Locked first:
        // it changes what happens when you activate the tile, which matters more
        // than where the tile lives.
        assertEquals(
            "Gmail, locked, hidden, pinned to dock, 3 notifications",
            appTileDescription("Gmail", badgeCount = 3, isPinned = true, isHidden = true, isLocked = true),
        )
    }

    @Test
    fun `one notification is not plural`() {
        assertEquals("Gmail, 1 notification", appTileDescription("Gmail", badgeCount = 1))
        assertEquals("Gmail, 2 notifications", appTileDescription("Gmail", badgeCount = 2))
    }

    @Test
    fun `no badge means no mention of notifications`() {
        // A tile saying "0 notifications" would be noise on every icon on the
        // home screen.
        assertFalse("notification" in appTileDescription("Gmail", badgeCount = 0))
        assertFalse("notification" in appTileDescription("Gmail", badgeCount = -1))
    }

    @Test
    fun `a nameless app still announces as something`() {
        // A blank label is possible: a package with no loadable label, or an app
        // mid-install. An empty contentDescription makes an unnamed button,
        // which is the exact thing this is here to prevent.
        assertEquals("Unnamed app", appTileDescription(""))
        assertEquals("Unnamed app", appTileDescription("   "))
        assertEquals("Unnamed app, locked", appTileDescription("", isLocked = true))
    }

    @Test
    fun `the description does not tell the platform how to do its job`() {
        // TalkBack announces "double tap to activate" for a Button role itself.
        // Saying it again produces "Gmail. Double-tap to open. Long-press for
        // options. Double-tap to activate."
        val description = appTileDescription("Gmail", badgeCount = 2, isLocked = true)
        listOf("double-tap", "double tap", "long-press", "long press", "tap to").forEach { phrase ->
            assertFalse(
                "\"$phrase\" is the platform's sentence, not ours: $description",
                phrase in description.lowercase(),
            )
        }
    }

    @Test
    fun `the word app is not appended to every label`() {
        // The old helper produced "Gmail app". For a launcher, where every tile
        // is an app, that is a word repeated on every item in the list.
        assertFalse(appTileDescription("Gmail").endsWith(" app"))
    }

    @Test
    fun `the superseded helper is gone, not left for the next person to find`() {
        // A dead helper is not harmless. The next person needing app-tile
        // semantics would have found it, used it, and shipped both defects.
        val helpers = File("src/main/java/com/ciyato/launcher/ui/components/AccessibilityHelpers.kt")
        assertTrue("AccessibilityHelpers.kt not found", helpers.exists())
        val text = helpers.readText()
        assertFalse(
            "appItemSemantics is still declared - use appTileSemantics",
            Regex("""fun Modifier\.appItemSemantics""").containsMatchIn(text),
        )
    }
}
