package com.ciyato.launcher

import com.ciyato.launcher.ui.screens.CleanupCategory
import com.ciyato.launcher.ui.screens.CleanupTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which cleanup categories are safe, and which need a look first.
 *
 * The six categories were presented as peers, which invited one mental action -
 * delete - across evidence as different as "Ciyato's own cache" and "everything
 * in your Downloads folder" (F-118). The tier is now part of the category, and
 * these pin the assignments that matter, because the cost of getting one wrong
 * is someone deleting a file they needed on Ciyato's encouragement.
 */
class CleanupTierTest {

    @Test
    fun `only regenerable or already-deleted things are marked safe`() {
        val safe = CleanupCategory.entries.filter { it.tier == CleanupTier.SAFE }.toSet()
        assertEquals(
            setOf(CleanupCategory.CACHE, CleanupCategory.APP_CACHES, CleanupCategory.TRASH),
            safe,
        )
    }

    /**
     * Zero bytes is not the same as disposable, and this test used to say it was.
     *
     * Empty Files sat in "Safe to clear" under the promise that nothing there could
     * hold your only copy of anything, and its scan took every zero-byte file -
     * including `.nomedia` markers, which are empty by design and exist to keep a
     * folder out of every gallery on the phone. Delete one and hidden media appears.
     * This was found the hard way, on a real phone, when 37 empty files were removed.
     */
    @Test
    fun `empty files are reviewed, not presumed safe, and markers are never offered`() {
        assertEquals(CleanupTier.REVIEW, CleanupCategory.EMPTY_FILES.tier)
        val scan = java.io.File("src/main/java/com/ciyato/launcher/ui/screens/StorageCleanupScreen.kt").readText()
        assertTrue(
            "the empty-files scan no longer excludes dot-files, so it would offer .nomedia " +
                "markers for deletion again",
            scan.contains("DISPLAY_NAME} NOT LIKE '.%'"),
        )
        assertTrue(
            "the empty-files scan no longer excludes other apps' own storage under Android/",
            scan.contains("NOT LIKE '%Android/%'"),
        )
    }

    /**
     * The two that must never be called safe. Downloads is an ordinary folder
     * that can hold the only copy of a document, and a file being large is not
     * evidence that it is unwanted.
     */
    @Test
    fun `downloads and large files always require a look`() {
        assertEquals(CleanupTier.SUGGESTION, CleanupCategory.DOWNLOADS.tier)
        assertEquals(CleanupTier.SUGGESTION, CleanupCategory.LARGE_FILES.tier)
    }

    @Test
    fun `trash is safe because the person already deleted it`() {
        assertEquals(CleanupTier.SAFE, CleanupCategory.TRASH.tier)
    }

    @Test
    fun `old screenshots sit between the two - real files, decent evidence`() {
        assertEquals(CleanupTier.REVIEW, CleanupCategory.OLD_SCREENSHOTS.tier)
    }

    @Test
    fun `every category has a tier`() {
        // Enum exhaustiveness means a category added later cannot skip this
        // decision, but a default would let it be skipped by accident.
        assertEquals(
            CleanupCategory.entries.size,
            CleanupCategory.entries.mapNotNull { it.tier }.size,
        )
    }

    @Test
    fun `tiers are ordered safest first`() {
        // The screen renders CleanupTier.entries in declaration order, so this
        // ordering is what puts the safest wins at the top.
        assertEquals(
            listOf(CleanupTier.SAFE, CleanupTier.REVIEW, CleanupTier.SUGGESTION),
            CleanupTier.entries.toList(),
        )
    }

    @Test
    fun `no tier promises more certainty than it has`() {
        // The riskier tiers must not use language that sounds like a verdict.
        assertTrue(CleanupTier.SUGGESTION.blurb.contains("not a verdict"))
        assertTrue(CleanupTier.REVIEW.blurb.contains("not about whether you still want them"))
    }

    @Test
    fun `the Downloads description warns that it may hold the only copy`() {
        assertTrue(
            "Downloads is the highest-consequence category; its description must say so",
            CleanupCategory.DOWNLOADS.description.contains("only copy"),
        )
    }
}
