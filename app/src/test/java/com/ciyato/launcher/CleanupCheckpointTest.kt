package com.ciyato.launcher

import com.ciyato.launcher.data.FileCleanupResultStore.CheckpointEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a checkpointed hash may still be trusted.
 *
 * The cleanup worker checkpoints its progress so a cancelled scan resumes rather
 * than restarting. It keyed those hashes by URI alone and skipped any URI it
 * already held (F-060). A URI is not an identity: the document behind it can be
 * edited in place between the cancellation and the resume, and the stale hash
 * would then be reused - grouping a changed file with one that genuinely has
 * that content, inside a flow whose next step is deleting the "duplicate".
 *
 * A false duplicate in a delete flow costs a file, so the bar for reusing an
 * entry is deliberately high.
 */
class CleanupCheckpointTest {

    private val hash = "a".repeat(64)
    private fun entry(size: Long = 1000L, modified: Long = 5_000L) =
        CheckpointEntry(hash, size, modified)

    @Test
    fun `an unchanged file reuses its hash`() {
        assertTrue(entry().describes(currentSizeBytes = 1000L, currentLastModifiedMs = 5_000L))
    }

    /** The bug: edited in place, same URI, same size. */
    @Test
    fun `a file modified since the scan is re-hashed`() {
        assertFalse(entry().describes(1000L, 6_000L))
    }

    @Test
    fun `a file whose size changed is re-hashed`() {
        assertFalse(entry().describes(2000L, 5_000L))
    }

    @Test
    fun `an entry with no modification time is never reused`() {
        // Some providers report 0. With nothing to verify against, re-hashing
        // costs time; reusing costs a file.
        assertFalse(entry(modified = 0L).describes(1000L, 5_000L))
    }

    @Test
    fun `a file reporting no modification time is never matched`() {
        assertFalse(entry().describes(1000L, 0L))
        assertFalse(entry().describes(1000L, -1L))
    }

    @Test
    fun `both facts must match, not either`() {
        assertFalse(entry().describes(2000L, 6_000L))
        assertTrue(entry().describes(1000L, 5_000L))
    }

    @Test
    fun `a zero-length file is still a legitimate match`() {
        // Size 0 is valid data, distinct from "no size reported".
        assertTrue(entry(size = 0L).describes(0L, 5_000L))
        assertFalse(entry(size = 0L).describes(10L, 5_000L))
    }
}
