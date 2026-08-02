package com.ciyato.launcher

import com.ciyato.launcher.data.AppCategorizer
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.ClassificationSource
import com.ciyato.launcher.data.ReviewReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppClassificationTest {

    @Test
    fun `curated package seed has high confidence`() {
        val result = AppCategorizer.classify("com.google.android.gm", "Gmail")

        assertEquals(AppCategory.WORK, result.category)
        assertEquals(ClassificationSource.CURATED_SEED, result.source)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun `low confidence label evidence remains in review`() {
        val result = AppCategorizer.classify("com.example.unknown", "Northwind Budget")

        assertEquals(AppCategory.REVIEW, result.category)
        assertEquals(AppCategory.FINANCE, result.suggestedCategory)
        assertEquals(ClassificationSource.LABEL_RULE, result.source)
        assertTrue(result.confidence in 0.68f..0.84f)
        assertEquals(ReviewReason.LOW_CONFIDENCE, result.reviewReason)
    }

    @Test
    fun `unknown application is sent to review instead of a guessed category`() {
        val result = AppCategorizer.classify("com.example.opaque", "QZ-49")

        assertEquals(AppCategory.REVIEW, result.category)
        assertEquals(ClassificationSource.REVIEW_FALLBACK, result.source)
        assertEquals(0f, result.confidence)
        assertEquals(ReviewReason.NO_MATCH, result.reviewReason)
    }

    @Test
    fun `conflicting evidence remains in review instead of choosing an arbitrary category`() {
        val result = AppCategorizer.classify("com.example.opaque", "Northwind Budget Office")

        assertEquals(AppCategory.REVIEW, result.category)
        assertEquals(ReviewReason.AMBIGUOUS_EVIDENCE, result.reviewReason)
        assertTrue(result.candidates.size >= 2)
    }

    @Test
    fun `valid manifest category auto places unknown app while malformed metadata does not`() {
        val valid = AppCategorizer.classify(
            packageName = "com.example.unknown",
            label = "QZ-49",
            manifestCategoryHint = "PRODUCTIVITY",
        )
        val malformed = AppCategorizer.classify(
            packageName = "com.example.unknown",
            label = "QZ-49",
            manifestCategoryHint = "SYSTEM_OVERLORD",
        )

        assertEquals(AppCategory.PRODUCTIVITY, valid.category)
        assertEquals(ClassificationSource.MANIFEST_METADATA, valid.source)
        assertEquals(AppCategory.REVIEW, malformed.category)
        assertEquals(ReviewReason.NO_MATCH, malformed.reviewReason)
    }
}
