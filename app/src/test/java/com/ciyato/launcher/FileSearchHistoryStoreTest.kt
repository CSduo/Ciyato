package com.ciyato.launcher

import com.ciyato.launcher.data.FileSearchHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSearchHistoryStoreTest {
    @Test
    fun recordingMovesExistingQueryToFrontAndBoundsHistory() {
        var history = "[]"
        (1..14).forEach { history = FileSearchHistoryStore.record(history, "query $it") }
        history = FileSearchHistoryStore.record(history, "QUERY 4")

        val parsed = FileSearchHistoryStore.parse(history)
        assertEquals("QUERY 4", parsed.first())
        assertEquals(12, parsed.size)
        assertEquals(1, parsed.count { it.equals("query 4", ignoreCase = true) })
    }
}
