package com.ciyato.launcher

import com.ciyato.launcher.data.CleanupAnalysisResult
import com.ciyato.launcher.data.CleanupFileRef
import com.ciyato.launcher.data.DuplicateCleanupGroup
import com.ciyato.launcher.data.plannedDuplicateDeletions
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCleanupModelsTest {
    @Test
    fun reclaimableBytesCountsOnlyExtraVerifiedCopies() {
        val group = DuplicateCleanupGroup(
            bytesPerFile = 4_096L,
            files = listOf(
                CleanupFileRef("content://tree/one", "one.pdf", 4_096L),
                CleanupFileRef("content://tree/two", "two.pdf", 4_096L),
                CleanupFileRef("content://tree/three", "three.pdf", 4_096L),
            ),
        )
        assertEquals(8_192L, group.reclaimableBytes)

        val result = CleanupAnalysisResult(
            rootUri = "content://tree",
            inspectedEntries = 3,
            hashedFiles = 3,
            wasBounded = false,
            groups = listOf(group),
            completedAt = 1L,
        )
        assertEquals(8_192L, result.reclaimableBytes)
    }

    @Test
    fun plannedDeletionRetainsExactlyOneExplicitCopyPerGroup() {
        val first = DuplicateCleanupGroup(
            bytesPerFile = 10L,
            files = listOf(
                CleanupFileRef("content://one", "one.pdf", 10L),
                CleanupFileRef("content://two", "two.pdf", 10L),
            ),
        )
        val second = DuplicateCleanupGroup(
            bytesPerFile = 20L,
            files = listOf(
                CleanupFileRef("content://three", "three.jpg", 20L),
                CleanupFileRef("content://four", "four.jpg", 20L),
                CleanupFileRef("content://five", "five.jpg", 20L),
            ),
        )

        val result = plannedDuplicateDeletions(
            groups = listOf(first, second),
            keptUris = mapOf(0 to "content://two", 1 to "content://four"),
        )

        assertEquals(listOf("content://one", "content://three", "content://five"), result.map(CleanupFileRef::uri))
    }

    @Test
    fun plannedDeletionSkipsAGroupWithoutAValidKeepChoice() {
        val group = DuplicateCleanupGroup(
            bytesPerFile = 10L,
            files = listOf(
                CleanupFileRef("content://one", "one.pdf", 10L),
                CleanupFileRef("content://two", "two.pdf", 10L),
            ),
        )

        assertEquals(emptyList<CleanupFileRef>(), plannedDuplicateDeletions(listOf(group), mapOf(0 to "content://missing")))
    }
}
