package com.ciyato.launcher

import com.ciyato.launcher.data.PhotoBackupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the photo-backup outcome contract (F-009, F-010).
 *
 * The defect these pin down was not in the copy loop's plumbing but in what the
 * result was allowed to claim. `copiedCount` was incremented as soon as a
 * destination file could be *created*, while the streams that move the bytes sit
 * inside `?.use` blocks that are silently skipped when either
 * `openInputStream` or `openOutputStream` returns null. A run could therefore
 * report "200 photos saved" having written nothing — and the watermark advanced
 * past all 200, so those photos were never looked at again.
 *
 * The watermark policy now hangs off [PhotoBackupResult.isComplete], so these
 * assertions are the guard on real data loss rather than on cosmetics.
 */
class PhotoBackupResultTest {

    @Test
    fun `run with no failures is complete`() {
        val result = PhotoBackupResult(copiedCount = 12, completedAtMs = 1_000L)
        assertTrue(result.isComplete)
    }

    @Test
    fun `any failure makes the run incomplete so the watermark cannot advance`() {
        val result = PhotoBackupResult(copiedCount = 11, completedAtMs = 1_000L, failed = 1)
        assertFalse(
            "A run that failed even one photo must not be treated as complete — " +
                "advancing the watermark past it loses that photo permanently.",
            result.isComplete,
        )
    }

    @Test
    fun `an error makes the run incomplete even when files were copied`() {
        val result = PhotoBackupResult(
            copiedCount = 5,
            completedAtMs = 1_000L,
            error = "Backup folder is no longer accessible.",
        )
        assertFalse(result.isComplete)
    }

    @Test
    fun `summary reports copied skipped and failed separately`() {
        val result = PhotoBackupResult(
            copiedCount = 3,
            completedAtMs = 1_000L,
            skippedExisting = 7,
            failed = 2,
        )
        assertEquals("3 copied · 7 already there · 2 failed", result.summary())
    }

    @Test
    fun `summary omits zero counts rather than printing noise`() {
        val result = PhotoBackupResult(copiedCount = 4, completedAtMs = 1_000L)
        assertEquals("4 copied", result.summary())
    }

    @Test
    fun `a re-run that skips everything is complete and says so`() {
        // The idempotency case: a second run over the same photos copies nothing
        // and must not read as an error or as work performed.
        val result = PhotoBackupResult(copiedCount = 0, completedAtMs = 1_000L, skippedExisting = 40)
        assertTrue(result.isComplete)
        assertEquals("40 already there", result.summary())
    }

    @Test
    fun `an empty run is described as nothing new rather than as a failure`() {
        val result = PhotoBackupResult(copiedCount = 0, completedAtMs = 1_000L)
        assertTrue(result.isComplete)
        assertEquals("Nothing new to back up.", result.summary())
    }

    @Test
    fun `error text is surfaced verbatim so the cause is not swallowed`() {
        val result = PhotoBackupResult(0, 1_000L, error = "Could not query photos.")
        assertEquals("Could not query photos.", result.summary())
    }
}
