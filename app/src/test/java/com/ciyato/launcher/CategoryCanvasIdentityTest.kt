package com.ciyato.launcher

import com.ciyato.launcher.data.CanvasPos
import com.ciyato.launcher.data.WorkspaceRecord
import com.ciyato.launcher.data.withCategoryRenamed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A collection's canvas placement has to follow its name.
 *
 * Home is a freeform canvas, so where somebody dragged a card IS their work. A
 * collection is stored in three places at once: `categoryKeys`, and — once its card has
 * been dragged off the flow — `objectPositions` and `hiddenObjects`, both keyed
 * `category:<name>`. Rename, merge and delete each rewrote the first and ignored the
 * other two.
 *
 * The visible result of renaming "Work" to "Office" was the card jumping back into the
 * grid (the new id has no placement) while a ghost of the old name stayed pinned where
 * it had been dropped (the old id's placement was never removed). Deleting was worse:
 * that path never touched the layout at all, so the placement outlived the collection
 * and nothing left in the app could ever clear it.
 */
class CategoryCanvasIdentityTest {

    private fun workspace(
        categories: List<String>,
        positions: Map<String, CanvasPos> = emptyMap(),
        hidden: Set<String> = emptySet(),
    ) = WorkspaceRecord(
        id = "home",
        creationOrder = 0,
        categoryKeys = categories,
        objectPositions = positions,
        hiddenObjects = hidden,
    )

    @Test
    fun `renaming moves the card's placement with it`() {
        val before = workspace(
            categories = listOf("Work", "Play"),
            positions = mapOf(
                "category:Work" to CanvasPos(0.25f, 0.4f, 2),
                "greeting" to CanvasPos(0.1f, 0.1f),
            ),
        )

        val after = before.withCategoryRenamed("Work", "Office")

        assertEquals(listOf("Office", "Play"), after.categoryKeys)
        assertNull(
            "the old id kept its placement, so a ghost card stays pinned under a name " +
                "that no longer exists",
            after.objectPositions["category:Work"],
        )
        assertEquals(
            "the renamed card lost its position and would fall back into flow",
            CanvasPos(0.25f, 0.4f, 2),
            after.objectPositions["category:Office"],
        )
        assertEquals(
            "an unrelated object was disturbed",
            CanvasPos(0.1f, 0.1f),
            after.objectPositions["greeting"],
        )
    }

    @Test
    fun `renaming carries a hidden card's hidden state`() {
        val before = workspace(
            categories = listOf("Work"),
            hidden = setOf("category:Work", "datetime"),
        )

        val after = before.withCategoryRenamed("Work", "Office")

        assertFalse("the old id stayed hidden", "category:Work" in after.hiddenObjects)
        assertTrue(
            "the renamed card became visible again, undoing a choice the person made",
            "category:Office" in after.hiddenObjects,
        )
        assertTrue("an unrelated hidden object was disturbed", "datetime" in after.hiddenObjects)
    }

    @Test
    fun `deleting removes the placement rather than orphaning it`() {
        val before = workspace(
            categories = listOf("Work", "Play"),
            positions = mapOf("category:Work" to CanvasPos(0.5f, 0.5f)),
            hidden = setOf("category:Work"),
        )

        val after = before.withCategoryRenamed("Work", null)

        assertEquals(listOf("Play"), after.categoryKeys)
        assertTrue(
            "a deleted collection left its canvas placement behind, and nothing in the " +
                "app could ever clear it again",
            after.objectPositions.isEmpty(),
        )
        assertTrue("a deleted collection stayed in hiddenObjects", after.hiddenObjects.isEmpty())
    }

    @Test
    fun `merging into a placed collection does not move the destination`() {
        // The destination card is somewhere the person put it. Merging another
        // collection into it must not relocate it to wherever the source happened to be.
        val before = workspace(
            categories = listOf("Work", "Office"),
            positions = mapOf(
                "category:Work" to CanvasPos(0.1f, 0.1f),
                "category:Office" to CanvasPos(0.8f, 0.8f),
            ),
        )

        val after = before.withCategoryRenamed("Work", "Office")

        assertEquals(listOf("Office"), after.categoryKeys)
        assertEquals(
            "the destination card was dragged to where the merged-away one used to be",
            CanvasPos(0.8f, 0.8f),
            after.objectPositions["category:Office"],
        )
        assertNull(after.objectPositions["category:Work"])
    }

    @Test
    fun `merging into an unplaced collection hands over the position`() {
        val before = workspace(
            categories = listOf("Work", "Office"),
            positions = mapOf("category:Work" to CanvasPos(0.1f, 0.2f, 3)),
        )

        val after = before.withCategoryRenamed("Work", "Office")

        assertEquals(CanvasPos(0.1f, 0.2f, 3), after.objectPositions["category:Office"])
        assertEquals(1, after.objectPositions.size)
    }

    @Test
    fun `a collection with no canvas placement is unaffected beyond its key`() {
        // The overwhelmingly common case: the card has never been dragged, so there is
        // nothing in objectPositions to move. This must stay a pure key rename.
        val before = workspace(categories = listOf("Work", "Play"))

        val after = before.withCategoryRenamed("Work", "Office")

        assertEquals(listOf("Office", "Play"), after.categoryKeys)
        assertTrue(after.objectPositions.isEmpty())
        assertTrue(after.hiddenObjects.isEmpty())
    }

    @Test
    fun `renaming to a name already present does not duplicate the key`() {
        val before = workspace(categories = listOf("Work", "Office"))

        val after = before.withCategoryRenamed("Work", "Office")

        assertEquals(listOf("Office"), after.categoryKeys)
    }
}
