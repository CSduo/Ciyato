package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A failed count must never look like a small library.
 *
 * This one pattern produced four separate defects in this codebase, in four
 * different features, and every one of them shipped:
 *
 * - `DuplicatePhotoDetector.libraryTotal` returned 0 on a failed query, and
 *   `wasBounded` read `libraryTotal > scanned`. So `0 > 500` is false, a capped
 *   scan claimed to be exhaustive, and the screen that offers to DELETE photos
 *   said "Checked all 500 on this device".
 * - `PhotoDeviceLibrary.libraryCount` and `mediaCount` did the same, so Photos
 *   described a list capped at 3,000 as everything on the device.
 * - `FileCleanupWorker` did not count unreadable folders at all, so a scan that
 *   could not read half a tree reported complete coverage before saying "no
 *   duplicates".
 * - The notification listener reconciled a failed `activeNotifications` query to
 *   an empty list, wiping every badge on Home.
 *
 * The shape is always the same: a number whose entire job is to bound a claim,
 * defaulting to a value that makes the claim unbounded. `0` is the worst possible
 * default for a total, because it is smaller than every real answer and so always
 * reads as "we saw everything".
 *
 * A count that cannot be obtained is **unknown**, and unknown is a third state.
 * These functions return null.
 */
class CoverageHonestyTest {

    /**
     * Places `0` is a legitimate answer rather than a swallowed failure, with the
     * reason. Short by design.
     */
    private val allowed = mapOf(
        "data/PhotoAiCollectionStore.kt" to
            "scannedAt() returns 0 for an unparseable timestamp, which genuinely means " +
            "\"no scan recorded\" - the reading callers want, and pinned by its own test",
    )

    @Test
    fun `no data-layer count resolves a failure to zero`() {
        val root = File("src/main/java/com/ciyato/launcher/data")
        assertTrue("data sources not found at ${root.absolutePath}", root.isDirectory)

        // The two spellings that caused all four defects.
        val patterns = listOf(
            Regex("""it\.count\s*}\s*\?:\s*0"""),
            Regex("""getOrDefault\(0L?\)"""),
        )

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val relative = "data/" + file.name
                if (relative in allowed) return@mapNotNull null
                val text = file.readText()
                val hits = patterns.sumOf { it.findAll(text).count() }
                if (hits > 0) "$relative ($hits)" else null
            }
            .sorted()
            .toList()

        assertTrue(
            "these resolve a failed count to 0: $offenders. Return null instead - a count " +
                "that cannot be obtained is unknown, and 0 is smaller than every real answer " +
                "so it always reads as \"we saw everything\". If 0 is genuinely the right " +
                "answer, add the file to this test's allowed map with the reason.",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the coverage-reporting functions can express ignorance`() {
        // Checked at the source rather than by calling them, because every one
        // needs a ContentResolver. What matters is the return type: an Int cannot
        // say "I do not know", so the defect is reachable again the moment one of
        // these loses its question mark.
        val declarations = mapOf(
            "data/PhotoDeviceLibrary.kt" to listOf(
                "suspend fun libraryCount(context: Context): Int?",
                "suspend fun mediaCount(context: Context): Int?",
            ),
            "data/DuplicatePhotoDetector.kt" to listOf(
                "private fun libraryTotal(context: Context): Int?",
                "val libraryTotal: Int?",
            ),
        )

        declarations.forEach { (path, expected) ->
            val file = File("src/main/java/com/ciyato/launcher/$path")
            assertTrue("$path not found", file.exists())
            val text = file.readText()
            expected.forEach { declaration ->
                assertTrue(
                    "$path no longer declares `$declaration` - a coverage total that cannot " +
                        "be null cannot report a failed query, and this is how all four of " +
                        "those defects worked",
                    text.contains(declaration),
                )
            }
        }
    }

    @Test
    fun `the exemption list stays short and stays true`() {
        assertTrue("the zero-default exemption list has grown: ${allowed.keys}", allowed.size <= 2)
        allowed.forEach { (path, reason) ->
            assertTrue(
                "$path is exempt but no longer exists",
                File("src/main/java/com/ciyato/launcher/$path").exists(),
            )
            assertTrue("$path is exempt without a real reason", reason.length > 40)
        }
    }
}
