package com.ciyato.launcher

import com.ciyato.launcher.data.FileSearchIndex
import com.ciyato.launcher.data.FileSearchIndexEntry
import com.ciyato.launcher.data.FileSearchIndexStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileSearchIndexStoreTest {
    @Test
    fun indexRoundTripKeepsSelectedScopeAndDeduplicatesUris() {
        val raw = FileSearchIndexStore.serialize(
            FileSearchIndex(
                rootUri = "content://tree/root",
                indexedAt = 9L,
                reachedLimit = true,
                entries = listOf(
                    FileSearchIndexEntry("content://tree/root/1", "one.pdf", "application/pdf", 4L, 10L),
                    FileSearchIndexEntry("content://tree/root/1", "renamed.pdf", "application/pdf", 5L, 11L),
                ),
            ),
        )
        val decoded = requireNotNull(FileSearchIndexStore.parse(raw))
        assertEquals("content://tree/root", decoded.rootUri)
        assertEquals(1, decoded.entries.size)
        assertEquals("one.pdf", decoded.entries.single().name)
    }

    @Test
    fun malformedIndexFailsClosed() {
        assertNull(FileSearchIndexStore.parse("not json"))
    }
}
