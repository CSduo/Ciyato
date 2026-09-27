package com.ciyato.launcher

import com.ciyato.launcher.data.FileAccess
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The all-files scan has to stop, and say that it stopped.
 *
 * Its documentation said it stopped at a limit of *entries*, and the stop
 * condition was `files.size >= limit` — so directories did not consume the budget
 * (F-093). A tree of 200,000 empty folders containing ten files satisfied "stop
 * at 2,000 entries" by walking all 200,010 of them, and the UI described that as
 * scanning the first 2,000. On a real phone that is a Files screen that appears
 * to hang.
 *
 * Testable with real directories because `java.io.File` works on the JVM — it is
 * `Environment` and `Uri` that are stubs. So this is the audit's acceptance test
 * run for real: a synthetic deep tree, stopping inside every budget, reporting
 * bounded.
 */
class DirectoryScanBudgetTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun scan(root: File, limit: Int) = runBlocking { FileAccess.scanDirectory(root, limit) }

    // ── The defect ───────────────────────────────────────────────────────────

    @Test
    fun `a directory-heavy tree cannot walk forever`() {
        // Wide and deep, with almost no files: the shape that defeated a
        // files-only budget. 40 x 40 nested is 1,600 directories from 80
        // mkdir calls.
        val root = temp.newFolder("dirheavy")
        repeat(40) { outer ->
            val mid = File(root, "outer$outer").also { it.mkdirs() }
            repeat(40) { inner -> File(mid, "inner$inner").mkdirs() }
        }
        File(root, "only.txt").writeText("x")

        val result = scan(root, limit = 2_000)

        // The point: inspected counts the directories, so the number the budget
        // bounds is now the number that was documented.
        assertTrue(
            "directories must consume the budget: inspected=${result.inspectedEntries}",
            result.inspectedEntries > 1_000,
        )
        assertEquals("only the real file is collected", 1, result.files.size)
    }

    @Test
    fun `the entry budget stops a pathological tree and says which budget`() {
        // Enough directories to exceed the entry budget without creating
        // anything like that many files.
        val root = temp.newFolder("huge")
        var made = 0
        var current = root
        // A chain deep enough to blow the budget by breadth at each level.
        repeat(80) { depth ->
            current = File(current, "d$depth").also { it.mkdirs() }
            repeat(60) { i ->
                File(current, "s$i").mkdirs()
                made++
            }
            if (made > FileAccess.MAX_INSPECTED_ENTRIES) return@repeat
        }

        val result = scan(root, limit = 100_000)
        assertTrue("a pathological tree must report bounded", result.reachedLimit)
        assertTrue(
            "inspected must never exceed the budget, was ${result.inspectedEntries}",
            result.inspectedEntries <= FileAccess.MAX_INSPECTED_ENTRIES + 1,
        )
        // Either budget is a pass. Which one trips first depends on how fast the
        // filesystem under the test is - on a slow one the wall clock wins, and
        // pinning ENTRY_BUDGET would make this test fail for the wrong reason on
        // exactly the hardware the time budget exists to protect.
        assertTrue(
            "a pathological tree must stop on a budget, was ${result.stoppedBecause}",
            result.stoppedBecause in setOf(
                FileAccess.ScanStop.ENTRY_BUDGET,
                FileAccess.ScanStop.TIME_BUDGET,
            ),
        )
    }

    // ── The ordinary cases ───────────────────────────────────────────────────

    @Test
    fun `a small tree is walked completely and reports no cap`() {
        // The case that must NOT say "showing the first N", because saying so
        // when nothing was truncated is its own false claim.
        val root = temp.newFolder("small")
        File(root, "a.txt").writeText("a")
        File(root, "b.pdf").writeText("b")
        val sub = File(root, "sub").also { it.mkdirs() }
        File(sub, "c.jpg").writeText("c")

        val result = scan(root, limit = 100)
        assertEquals(3, result.files.size)
        assertFalse("nothing was truncated", result.reachedLimit)
        assertNull("no budget tripped", result.stoppedBecause)
    }

    @Test
    fun `the file limit is reported as the file limit`() {
        val root = temp.newFolder("many")
        repeat(30) { i -> File(root, "f$i.txt").writeText("x") }

        val result = scan(root, limit = 10)
        assertEquals(10, result.files.size)
        assertTrue(result.reachedLimit)
        assertEquals(FileAccess.ScanStop.FILE_LIMIT, result.stoppedBecause)
    }

    @Test
    fun `breadth first means the top level is never missed`() {
        // A depth-first walk that hits the limit can burn the whole budget inside
        // one deep folder and report a scan containing nothing the person can
        // see at the top.
        val root = temp.newFolder("breadth")
        File(root, "top.txt").writeText("t")
        var deep = root
        repeat(12) { d ->
            deep = File(deep, "level$d").also { it.mkdirs() }
            File(deep, "buried$d.txt").writeText("b")
        }

        val result = scan(root, limit = 3)
        assertTrue(
            "the top-level file must be among the first found",
            result.files.any { it.name == "top.txt" },
        )
    }

    @Test
    fun `an unreadable directory is skipped rather than aborting the scan`() {
        val root = temp.newFolder("mixed")
        File(root, "readable.txt").writeText("r")
        val blocked = File(root, "blocked").also { it.mkdirs() }
        File(blocked, "hidden.txt").writeText("h")
        blocked.setReadable(false, false)
        try {
            val result = scan(root, limit = 100)
            assertTrue(
                "the readable file must survive an unreadable sibling",
                result.files.any { it.name == "readable.txt" },
            )
        } finally {
            blocked.setReadable(true, false)
        }
    }

    @Test
    fun `an empty root is an empty result, not a cap`() {
        val result = scan(temp.newFolder("empty"), limit = 100)
        assertTrue(result.files.isEmpty())
        assertFalse(result.reachedLimit)
        assertNull(result.stoppedBecause)
    }

    @Test
    fun `a zero limit collects nothing and says so`() {
        val root = temp.newFolder("zero")
        File(root, "a.txt").writeText("a")
        val result = scan(root, limit = 0)
        assertTrue(result.files.isEmpty())
        assertTrue("a zero limit truncates by definition", result.reachedLimit)
    }

    @Test
    fun `a root that does not exist does not throw`() {
        val result = scan(File(temp.root, "not-there"), limit = 100)
        assertTrue(result.files.isEmpty())
    }

    // ── Budgets are real numbers, not decoration ─────────────────────────────

    @Test
    fun `the budgets are set to something defensible`() {
        // Pinned so a future edit is a decision. The entry budget has to be well
        // above any real phone's file count and well below an amount of work that
        // reads as a hang; the time budget has to be longer than a scan anyone
        // waits for.
        assertTrue(FileAccess.MAX_INSPECTED_ENTRIES in 10_000..500_000)
        assertTrue(FileAccess.MAX_SCAN_MILLIS in 2_000..30_000)
    }
}
