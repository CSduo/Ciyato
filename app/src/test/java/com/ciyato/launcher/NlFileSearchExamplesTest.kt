package com.ciyato.launcher

import com.ciyato.launcher.ui.screens.SEARCH_EXAMPLES
import com.ciyato.launcher.ui.screens.matchesMetadata
import com.ciyato.launcher.ui.screens.parseNlQuery
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every example the search screen offers must be one it can actually answer.
 *
 * The screen was called "Smart File Search" and its two headline examples were
 * "payment screenshot from yesterday" and "receipt photos" (F-097). It does not
 * read inside files — no OCR, no image labels, no document text — so keywords
 * are matched as substrings of the file NAME. "payment" could only ever match a
 * file literally named that, and Android names screenshots
 * `Screenshot_20260101_120000.png`. The two examples put in front of people
 * were the two the engine was least able to satisfy, which is exactly how a
 * feature that works comes to feel broken.
 *
 * So the rule is the audit's acceptance test, enforced: an example may appear
 * in the UI only if a fixture here proves the engine retrieves it, for the
 * reason the example implies.
 */
class NlFileSearchExamplesTest {

    private val now = System.currentTimeMillis()
    private fun daysAgo(n: Long) = now - TimeUnit.DAYS.toMillis(n)

    /** Runs the real engine end to end: parse, then match. */
    private fun matches(
        query: String,
        name: String,
        mimeType: String,
        modifiedAt: Long = now - TimeUnit.MINUTES.toMillis(5),
        sizeBytes: Long = 2L * 1024 * 1024,
    ): Boolean = matchesMetadata(
        name = name,
        mimeType = mimeType,
        modifiedAt = modifiedAt,
        sizeBytes = sizeBytes,
        parsed = parseNlQuery(query),
    )

    // ── The contract ─────────────────────────────────────────────────────────

    @Test
    fun `every advertised example retrieves a plausible real file`() {
        // One fixture per example, named the way Android really names these.
        val fixtures = mapOf(
            "screenshot from yesterday" to Triple(
                "Screenshot_20260101_120000.png", "image/png", daysAgo(1) + TimeUnit.HOURS.toMillis(2),
            ),
            "photos from last week" to Triple(
                "IMG_20260101_093000.jpg", "image/jpeg", daysAgo(3),
            ),
            "video from last month" to Triple(
                "VID_20251220_180000.mp4", "video/mp4", daysAgo(12),
            ),
            "pdf from today" to Triple(
                "statement.pdf", "application/pdf", now - TimeUnit.MINUTES.toMillis(30),
            ),
            "large files" to Triple(
                "backup.zip", "application/zip", daysAgo(40),
            ),
        )

        assertEquals(
            "every example in the UI needs a fixture here, and vice versa",
            SEARCH_EXAMPLES.toSet(),
            fixtures.keys,
        )

        SEARCH_EXAMPLES.forEach { example ->
            val (name, mime, modified) = fixtures.getValue(example)
            val size = if ("large" in example) 250L * 1024 * 1024 else 2L * 1024 * 1024
            assertTrue(
                "the UI offers \"$example\" but the engine does not retrieve $name",
                matches(example, name, mime, modified, size),
            )
        }
    }

    @Test
    fun `no example promises to read inside a file`() {
        // The removed examples, kept as the thing that must not come back.
        // "payment" and "receipt" are content words: they describe what is IN
        // the image, and nothing in this engine ever looks there.
        listOf("payment", "receipt", "invoice", "contract").forEach { contentWord ->
            assertFalse(
                "\"$contentWord\" describes file contents, which this engine never inspects - " +
                    "an example containing it teaches people to expect content search",
                SEARCH_EXAMPLES.any { contentWord in it.lowercase() },
            )
        }
    }

    // ── Why those examples were wrong ────────────────────────────────────────

    @Test
    fun `the old headline example could not have worked`() {
        // Pinned deliberately. This is the defect, in one assertion: the query
        // the screen put in its own placeholder returns nothing for a real
        // screenshot taken yesterday.
        assertFalse(
            matches(
                "payment screenshot from yesterday",
                "Screenshot_20260101_120000.png",
                "image/png",
                daysAgo(1) + TimeUnit.HOURS.toMillis(2),
            ),
        )
        // And it works only for a file someone had already named for them,
        // which is the case that did not need searching.
        assertTrue(
            matches(
                "payment screenshot from yesterday",
                "payment-screenshot.png",
                "image/png",
                daysAgo(1) + TimeUnit.HOURS.toMillis(2),
            ),
        )
    }

    // ── The grammar the UI now describes ─────────────────────────────────────

    @Test
    fun `type words select a type`() {
        assertEquals("image", parseNlQuery("photos from last week").mimeType)
        assertEquals("video", parseNlQuery("video from last month").mimeType)
        assertEquals("application/pdf", parseNlQuery("pdf from today").mimeType)
        assertEquals("audio", parseNlQuery("music from january").mimeType)
        assertEquals(null, parseNlQuery("holiday").mimeType)
    }

    @Test
    fun `type words are not also treated as name keywords`() {
        // "photos from last week" must not require the word "photos" in the
        // file name; it selects images. Getting this wrong would make every
        // typed example return nothing.
        assertTrue(parseNlQuery("photos from last week").keywords.isEmpty())
        assertTrue(parseNlQuery("screenshot from yesterday").keywords.isEmpty())
    }

    @Test
    fun `a name keyword really does filter by name`() {
        assertTrue(matches("holiday photos", "holiday-beach.jpg", "image/jpeg"))
        assertFalse(matches("holiday photos", "IMG_0001.jpg", "image/jpeg"))
    }

    @Test
    fun `date words use calendar days, so a morning search does not return last evening`() {
        val yesterdayEvening = startOfYesterdayEvening()
        assertFalse(
            "a file from yesterday evening is not from today",
            matches("pdf from today", "statement.pdf", "application/pdf", yesterdayEvening),
        )
        assertTrue(
            matches("pdf from yesterday", "statement.pdf", "application/pdf", yesterdayEvening),
        )
    }

    private fun startOfYesterdayEvening(): Long =
        com.ciyato.launcher.ui.screens.startOfToday() - TimeUnit.HOURS.toMillis(6)

    @Test
    fun `size words filter by size`() {
        assertTrue(matches("large files", "backup.zip", "application/zip", sizeBytes = 250L * 1024 * 1024))
        assertFalse(matches("large files", "backup.zip", "application/zip", sizeBytes = 2L * 1024 * 1024))
    }

    @Test
    fun `all keywords must match, not any`() {
        // Worth pinning: "all" is a deliberate choice and it is what makes a
        // two-word content query return nothing. If it ever becomes "any", the
        // UI copy about what search does has to change with it.
        assertTrue(matches("holiday beach", "holiday-beach-2026.jpg", "image/jpeg"))
        assertFalse(matches("holiday beach", "holiday-2026.jpg", "image/jpeg"))
    }
}
