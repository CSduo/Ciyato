package com.ciyato.launcher

import com.ciyato.launcher.data.CategoryMutations
import com.ciyato.launcher.data.CustomCategoryPresentation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A category lives in seven preference keys, and the bug class F-042 names is a
 * change that lands in some of them and not others.
 *
 * These pin the property that makes atomicity meaningful: every mutation either
 * updates all seven consistently, or returns null and touches nothing. A
 * function that could hand back a half-updated snapshot would defeat the
 * transaction wrapped around it, however correct that transaction is.
 */
class CategoryMutationsTest {

    private fun snapshot() = CategoryMutations.Snapshot(
        names = "Work,Games,Finance",
        overrides = JSONObject(mapOf("com.slack" to "Work", "com.steam" to "Games")).toString(),
        icons = JSONObject(mapOf("Work" to "briefcase", "Games" to "gamepad")).toString(),
        presentations = "{}",
        tileSizes = JSONObject(mapOf("Work" to "large")).toString(),
        order = "Games,Work,Finance",
        hidden = "Finance",
    )

    private fun names(s: CategoryMutations.Snapshot) =
        s.names.split(",").filter { it.isNotBlank() }

    private fun overrideOf(s: CategoryMutations.Snapshot, pkg: String) =
        JSONObject(s.overrides).optString(pkg, "")

    // -- rename ---------------------------------------------------------------

    @Test
    fun `rename updates every key that mentions the category`() {
        val r = requireNotNull(CategoryMutations.rename(snapshot(), "Work", "Job"))
        assertEquals(listOf("Job", "Games", "Finance"), names(r))
        assertEquals("Job", overrideOf(r, "com.slack"))
        assertEquals("briefcase", JSONObject(r.icons).optString("Job"))
        assertFalse(JSONObject(r.icons).has("Work"))
        assertEquals("large", JSONObject(r.tileSizes).optString("Job"))
        assertFalse(JSONObject(r.tileSizes).has("Work"))
        assertEquals("Games,Job,Finance", r.order)
    }

    @Test
    fun `rename carries the hidden flag with the category`() {
        val r = requireNotNull(CategoryMutations.rename(snapshot(), "Finance", "Money"))
        assertEquals("Money", r.hidden)
    }

    @Test
    fun `rename leaves other categories untouched`() {
        val r = requireNotNull(CategoryMutations.rename(snapshot(), "Work", "Job"))
        assertEquals("Games", overrideOf(r, "com.steam"))
        assertEquals("gamepad", JSONObject(r.icons).optString("Games"))
    }

    @Test
    fun `rename to an existing name is rejected outright`() {
        assertNull(CategoryMutations.rename(snapshot(), "Work", "Games"))
    }

    @Test
    fun `rename of a category that does not exist is rejected`() {
        assertNull(CategoryMutations.rename(snapshot(), "Nope", "Something"))
    }

    @Test
    fun `rename to blank is rejected`() {
        assertNull(CategoryMutations.rename(snapshot(), "Work", ""))
    }

    /** Renaming to the same name is legal, and is how the icon alone is changed. */
    @Test
    fun `renaming to itself is allowed and can set the icon`() {
        val r = requireNotNull(CategoryMutations.rename(snapshot(), "Work", "Work", icon = "hammer"))
        assertEquals("hammer", JSONObject(r.icons).optString("Work"))
        assertEquals(listOf("Work", "Games", "Finance"), names(r))
    }

    // -- merge ----------------------------------------------------------------

    @Test
    fun `merge reassigns members and drops only the source`() {
        val r = requireNotNull(CategoryMutations.merge(snapshot(), "Games", "Work"))
        assertEquals(listOf("Work", "Finance"), names(r))
        assertEquals("Work", overrideOf(r, "com.steam"))
        assertEquals("Work", overrideOf(r, "com.slack"))
        assertFalse(JSONObject(r.icons).has("Games"))
        assertTrue(JSONObject(r.icons).has("Work"))
    }

    @Test
    fun `merge does not overwrite a tile size the destination already has`() {
        val s = snapshot().copy(
            tileSizes = JSONObject(mapOf("Work" to "large", "Games" to "small")).toString(),
        )
        val r = requireNotNull(CategoryMutations.merge(s, "Games", "Work"))
        assertEquals("large", JSONObject(r.tileSizes).optString("Work"))
        assertFalse(JSONObject(r.tileSizes).has("Games"))
    }

    @Test
    fun `merging into a nonexistent destination is rejected`() {
        assertNull(CategoryMutations.merge(snapshot(), "Work", "Nope"))
        assertNull(CategoryMutations.merge(snapshot(), "Work", "Work"))
    }

    // -- remove ---------------------------------------------------------------

    @Test
    fun `remove clears the order and hidden entries too`() {
        // The bug: deleting a category left its name in the order list and in
        // the hidden set, pointing at something that no longer existed.
        val r = requireNotNull(CategoryMutations.remove(snapshot(), "Finance"))
        assertEquals(listOf("Work", "Games"), names(r))
        assertEquals("Games,Work", r.order)
        assertEquals("", r.hidden)
    }

    @Test
    fun `remove strips overrides pointing at the deleted category`() {
        val r = requireNotNull(CategoryMutations.remove(snapshot(), "Work"))
        assertEquals("", overrideOf(r, "com.slack"))
        assertEquals("Games", overrideOf(r, "com.steam"))
    }

    @Test
    fun `removing something absent is rejected rather than silently rewriting`() {
        assertNull(CategoryMutations.remove(snapshot(), "Nope"))
    }

    // -- add ------------------------------------------------------------------

    @Test
    fun `add appends and records a presentation`() {
        val r = requireNotNull(
            CategoryMutations.add(snapshot(), "Travel", CustomCategoryPresentation.CARD),
        )
        assertEquals(listOf("Work", "Games", "Finance", "Travel"), names(r))
        assertTrue(r.presentations.contains("Travel"))
    }

    @Test
    fun `add rejects a duplicate or blank name`() {
        assertNull(CategoryMutations.add(snapshot(), "Work", CustomCategoryPresentation.CARD))
        assertNull(CategoryMutations.add(snapshot(), "", CustomCategoryPresentation.CARD))
    }

    // -- the property that makes the transaction worth having -----------------

    @Test
    fun `a rejected mutation returns null so the transaction writes nothing`() {
        // Every rejection path must be null, never a partially-updated snapshot:
        // the caller commits whatever it is handed.
        assertNull(CategoryMutations.rename(snapshot(), "Work", "Games"))
        assertNull(CategoryMutations.merge(snapshot(), "Work", "Nope"))
        assertNull(CategoryMutations.remove(snapshot(), "Nope"))
        assertNull(CategoryMutations.add(snapshot(), "Work", CustomCategoryPresentation.CARD))
    }

    @Test
    fun `sequential edits compose without losing the earlier one`() {
        // What serialised transactions guarantee, stated as a test: applying two
        // edits in order leaves both applied. Under the old seven-writes-per-edit
        // shape, interleaving could lose one.
        val once = requireNotNull(CategoryMutations.rename(snapshot(), "Work", "Job"))
        val twice = requireNotNull(CategoryMutations.rename(once, "Games", "Play"))
        assertEquals(listOf("Job", "Play", "Finance"), names(twice))
        assertEquals("Job", overrideOf(twice, "com.slack"))
        assertEquals("Play", overrideOf(twice, "com.steam"))
        assertEquals("Play,Job,Finance", twice.order)
    }

    @Test
    fun `malformed stored json is survived rather than propagated`() {
        val corrupt = snapshot().copy(overrides = "{not json", icons = "]]]")
        val r = requireNotNull(CategoryMutations.rename(corrupt, "Work", "Job"))
        // The rename still lands; the unreadable blobs fall back to empty objects
        // instead of taking the whole edit down with them.
        assertEquals(listOf("Job", "Games", "Finance"), names(r))
        assertEquals("folder", JSONObject(r.icons).optString("Job"))
    }
}
