package com.ciyato.launcher

import com.ciyato.launcher.data.WidgetPlacement
import com.ciyato.launcher.data.WidgetPlacementStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where widgets live on Home, and at what size.
 *
 * The migration tests are the point of this file. Widgets used to persist as a
 * bare array of AppWidget IDs; they now persist as records carrying a size.
 * An AppWidget ID is allocated against a host and is not re-derivable — if the
 * new parser failed to read the old format, every widget anyone had already
 * placed would vanish from Home AND stay allocated in the system, reachable by
 * nothing. That is not a cosmetic migration failure, it is a permanent leak.
 */
class WidgetPlacementStoreTest {

    // ── Migration ────────────────────────────────────────────────────────────

    @Test
    fun `the old bare-id format still loads`() {
        val legacy = "[11,12,13]"
        val parsed = WidgetPlacementStore.parse(legacy)

        assertEquals(listOf(11, 12, 13), parsed.map { it.appWidgetId })
        assertTrue(
            "a migrated widget must keep provider-driven sizing, which is what it had",
            parsed.all { it.heightDp == 0 },
        )
    }

    @Test
    fun `a migrated layout round-trips into the new format without changing what it shows`() {
        val migrated = WidgetPlacementStore.parse("[11,12]")
        val reloaded = WidgetPlacementStore.parse(WidgetPlacementStore.serialize(migrated))
        assertEquals(migrated, reloaded)
    }

    @Test
    fun `an empty or absent store is empty, not a crash`() {
        assertTrue(WidgetPlacementStore.parse("[]").isEmpty())
        assertTrue(WidgetPlacementStore.parse("").isEmpty())
        assertTrue(WidgetPlacementStore.parse("{not json").isEmpty())
        assertTrue(WidgetPlacementStore.parse("{}").isEmpty())
    }

    @Test
    fun `junk entries are skipped rather than taking the whole list down`() {
        // A single unreadable entry must not cost someone every other widget.
        val parsed = WidgetPlacementStore.parse("""[11,null,{"id":0},{"id":12,"h":80},"nonsense"]""")
        assertEquals(listOf(11, 12), parsed.map { it.appWidgetId })
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    @Test
    fun `the same widget listed twice produces one placement`() {
        // Two host views sharing one AppWidget ID is a real corruption mode:
        // both would bind to the same id and fight over its updates.
        val parsed = WidgetPlacementStore.parse("""[{"id":11,"h":80},{"id":11,"h":200}]""")
        assertEquals(1, parsed.size)
        assertEquals(80, parsed.first().heightDp)
    }

    @Test
    fun `serialize also collapses a duplicate`() {
        val out = WidgetPlacementStore.serialize(
            listOf(WidgetPlacement(11, 80), WidgetPlacement(11, 200)),
        )
        assertEquals(1, WidgetPlacementStore.parse(out).size)
    }

    @Test
    fun `the canvas object id survives a round trip`() {
        val placement = WidgetPlacement(appWidgetId = 42)
        assertEquals("widget:42", placement.objectId)
        assertEquals(42, WidgetPlacement.appWidgetIdIn(placement.objectId))
    }

    @Test
    fun `a canvas id that is not a widget is not read as one`() {
        // Home mixes widget objects with greeting, weather and category cards.
        // A prefix test that said yes to any of those would render a widget in
        // place of a section.
        assertNull(WidgetPlacement.appWidgetIdIn("greeting"))
        assertNull(WidgetPlacement.appWidgetIdIn("category:Work"))
        assertNull(WidgetPlacement.appWidgetIdIn("widget:"))
        assertNull(WidgetPlacement.appWidgetIdIn("widget:abc"))
        assertNull(WidgetPlacement.appWidgetIdIn("widget:0"))
        assertNull(WidgetPlacement.appWidgetIdIn("widget:-3"))
    }

    // ── Size ─────────────────────────────────────────────────────────────────

    @Test
    fun `zero height means ask the provider, and is preserved`() {
        assertEquals(0, WidgetPlacementStore.clampHeight(0))
        assertEquals(
            140,
            WidgetPlacementStore.resolvedHeightDp(WidgetPlacement(1, heightDp = 0), providerMinHeightDp = 140),
        )
    }

    @Test
    fun `a chosen height wins over the provider minimum`() {
        // The person can see the widget; the provider cannot.
        assertEquals(
            200,
            WidgetPlacementStore.resolvedHeightDp(WidgetPlacement(1, heightDp = 200), providerMinHeightDp = 140),
        )
    }

    @Test
    fun `an absurd provider minimum cannot push Home off the screen`() {
        val resolved = WidgetPlacementStore.resolvedHeightDp(WidgetPlacement(1), providerMinHeightDp = 5000)
        assertEquals(WidgetPlacementStore.MAX_HEIGHT_DP, resolved)
    }

    @Test
    fun `a provider reporting no minimum still gets a usable height`() {
        assertEquals(120, WidgetPlacementStore.resolvedHeightDp(WidgetPlacement(1), providerMinHeightDp = 0))
    }

    @Test
    fun `resizing starts from what is on screen, not from a default`() {
        // The first tap after a provider-sized render must move from the size
        // the person is actually looking at, or the widget jumps.
        val provider = 140
        val bigger = WidgetPlacementStore.resized(WidgetPlacement(1), provider, steps = 1)
        assertEquals(140 + WidgetPlacementStore.HEIGHT_STEP_DP, bigger.heightDp)

        val smaller = WidgetPlacementStore.resized(WidgetPlacement(1), provider, steps = -1)
        assertEquals(140 - WidgetPlacementStore.HEIGHT_STEP_DP, smaller.heightDp)
    }

    @Test
    fun `resizing cannot leave the widget invisible or unbounded`() {
        var p = WidgetPlacement(1)
        repeat(20) { p = WidgetPlacementStore.resized(p, 140, steps = -1) }
        assertEquals(WidgetPlacementStore.MIN_HEIGHT_DP, p.heightDp)

        repeat(40) { p = WidgetPlacementStore.resized(p, 140, steps = 1) }
        assertEquals(WidgetPlacementStore.MAX_HEIGHT_DP, p.heightDp)
    }

    @Test
    fun `a stored size out of range is clamped on read, not trusted`() {
        val parsed = WidgetPlacementStore.parse("""[{"id":11,"h":99999},{"id":12,"h":1}]""")
        assertEquals(WidgetPlacementStore.MAX_HEIGHT_DP, parsed[0].heightDp)
        assertEquals(WidgetPlacementStore.MIN_HEIGHT_DP, parsed[1].heightDp)
    }

    @Test
    fun `the widget cap is enforced on both read and write`() {
        val many = (1..WidgetPlacementStore.MAX_WIDGETS + 10).map { WidgetPlacement(it) }
        assertEquals(WidgetPlacementStore.MAX_WIDGETS, WidgetPlacementStore.parse(WidgetPlacementStore.serialize(many)).size)

        val legacy = (1..WidgetPlacementStore.MAX_WIDGETS + 10).joinToString(",", "[", "]")
        assertEquals(WidgetPlacementStore.MAX_WIDGETS, WidgetPlacementStore.parse(legacy).size)
    }

    @Test
    fun `a size chosen by the person survives a save and reload`() {
        val chosen = listOf(WidgetPlacement(11, heightDp = 240), WidgetPlacement(12, heightDp = 0))
        assertEquals(chosen, WidgetPlacementStore.parse(WidgetPlacementStore.serialize(chosen)))
    }

    @Test
    fun `order is preserved, because it is the order Home lays them out in`() {
        val ordered = listOf(WidgetPlacement(31), WidgetPlacement(11), WidgetPlacement(21))
        assertEquals(
            listOf(31, 11, 21),
            WidgetPlacementStore.parse(WidgetPlacementStore.serialize(ordered)).map { it.appWidgetId },
        )
    }
}
