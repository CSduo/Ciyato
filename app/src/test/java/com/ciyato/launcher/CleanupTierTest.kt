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
    fun `only regenerable or provably empty things are marked safe`() {
        val safe = CleanupCategory.entries.filter { it.tier == CleanupTier.SAFE }.toSet()
        assertEquals(
            setOf(CleanupCategory.CACHE, CleanupCategory.EMPTY_FILES, CleanupCategory.TRASH),
            safe,
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
