package com.ciyato.launcher

import com.ciyato.launcher.data.PhotoCollection
import com.ciyato.launcher.data.PhotoLibraryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLibraryStoreTest {

    @Test
    fun `media URI storage removes duplicates and blank values`() {
        val stored = PhotoLibraryStore.serializeUris(
            listOf("content://media/one", "", "content://media/one", "content://media/two"),
        )

        assertEquals(
            listOf("content://media/one", "content://media/two"),
            PhotoLibraryStore.parseUris(stored),
        )
    }

    @Test
    fun `collection storage keeps only valid named collections`() {
        val stored = PhotoLibraryStore.serializeCollections(
            listOf(
                PhotoCollection("summer", "Summer", listOf("content://media/one", "content://media/one")),
            ),
        )

        assertEquals(
            listOf(PhotoCollection("summer", "Summer", listOf("content://media/one"))),
            PhotoLibraryStore.parseCollections(stored),
        )
    }

    @Test
    fun `malformed persisted photo data fails closed`() {
        assertTrue(PhotoLibraryStore.parseUris("not-json").isEmpty())
        assertTrue(PhotoLibraryStore.parseCollections("not-json").isEmpty())
    }
}
