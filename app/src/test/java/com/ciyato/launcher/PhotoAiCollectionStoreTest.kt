package com.ciyato.launcher

import com.ciyato.launcher.data.PhotoAiCollectionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persisting an on-device photo-labelling pass, and restoring it safely.
 *
 * The scan ran ML Kit across hundreds of images and kept the result in
 * `remember`, so navigating away discarded seconds of CPU and battery and forced
 * a full rescan on return (F-105).
 *
 * The interesting half is restore, not save. A stored collection is a list of
 * URIs captured at some past moment, and photos get deleted between then and
 * now. Rebuilding against the live library is what stops a restored collection
 * listing something that no longer exists.
 *
 * Tested through the URI-level API on purpose. `android.net.Uri` is a
 * non-functional stub on the JVM, so testing through `DeviceImage` would have
 * meant adding Robolectric to every CI run in order to verify what is really
 * just string bookkeeping — and an earlier fix in this project shipped a test
 * that passed while proving nothing for exactly that reason.
 */
class PhotoAiCollectionStoreTest {

    private fun uri(id: Long) = "content://media/external/images/media/$id"

    private fun stored(vararg groups: Pair<String, List<Long>>): String =
        PhotoAiCollectionStore.serializeUris(
            groups.associate { (name, ids) -> name to ids.map(::uri) },
            scannedCount = groups.sumOf { it.second.size },
        )

    /** Library membership, as the real restore path computes it. */
    private fun present(vararg ids: Long): (String) -> Boolean {
        val live = ids.map(::uri).toSet()
        return { u -> u in live }
    }

    @Test
    fun `a scan survives a round trip`() {
        val raw = stored("Nature" to listOf(1L, 2L), "Documents" to listOf(3L))
        val restored = PhotoAiCollectionStore.rebuildUris(raw, present(1L, 2L, 3L, 4L))

        assertNotNull(restored)
        assertEquals(setOf("Nature", "Documents"), restored!!.collections.keys)
        assertEquals(2, restored.collections.getValue("Nature").size)
        assertEquals(3, restored.scannedCount)
    }

    /** The reason restore rebuilds against the live library at all. */
    @Test
    fun `a photo deleted since the scan does not come back`() {
        val raw = stored("Nature" to listOf(1L, 2L, 3L))
        // Photo 2 has since been deleted.
        val restored = PhotoAiCollectionStore.rebuildUris(raw, present(1L, 3L))

        assertNotNull(restored)
        val nature = restored!!.collections.getValue("Nature")
        assertEquals(2, nature.size)
        assertFalse("a deleted photo must not be restored", nature.contains(uri(2L)))
    }

    @Test
    fun `a collection emptied by deletions is dropped rather than shown empty`() {
        val raw = stored("Nature" to listOf(1L, 2L), "Pets" to listOf(9L))
        val restored = PhotoAiCollectionStore.rebuildUris(raw, present(1L, 2L))

        assertNotNull(restored)
        assertEquals(setOf("Nature"), restored!!.collections.keys)
    }

    /**
     * Null must read as "no scan yet", never as "the scan found nothing" — the
     * same distinction the anomaly screen needed.
     */
    @Test
    fun `nothing usable yields null rather than an empty result`() {
        val raw = stored("Nature" to listOf(1L))
        assertNull(PhotoAiCollectionStore.rebuildUris(raw, present()))
        assertNull(PhotoAiCollectionStore.rebuildUris("", present(1L)))
    }

    @Test
    fun `corrupt stored data does not take the screen down`() {
        val live = present(1L)
        assertNull(PhotoAiCollectionStore.rebuildUris("{not json", live))
        assertNull(PhotoAiCollectionStore.rebuildUris("[]", live))
        assertNull(PhotoAiCollectionStore.rebuildUris("{}", live))
        assertNull(PhotoAiCollectionStore.rebuildUris("   ", live))
    }

    @Test
    fun `a blank uri in stored data is ignored rather than matched`() {
        val raw = PhotoAiCollectionStore.serializeUris(mapOf("Nature" to listOf("", uri(1L))), 2)
        val restored = PhotoAiCollectionStore.rebuildUris(raw) { it.isNotBlank() }
        assertNotNull(restored)
        assertEquals(listOf(uri(1L)), restored!!.collections.getValue("Nature"))
    }

    @Test
    fun `the scan timestamp is recorded and readable`() {
        val before = System.currentTimeMillis()
        val raw = stored("Nature" to listOf(1L))
        assertTrue("timestamp should be set", PhotoAiCollectionStore.scannedAt(raw) >= before)
        assertEquals(0L, PhotoAiCollectionStore.scannedAt("garbage"))
        assertEquals(0L, PhotoAiCollectionStore.scannedAt(""))
    }

    @Test
    fun `an empty scan does not produce something that restores`() {
        val raw = PhotoAiCollectionStore.serializeUris(emptyMap(), 0)
        assertNull(PhotoAiCollectionStore.rebuildUris(raw, present(1L)))
    }

    @Test
    fun `collection order is preserved across a round trip`() {
        val raw = PhotoAiCollectionStore.serializeUris(
            linkedMapOf(
                "Nature" to listOf(uri(1L)),
                "Pets" to listOf(uri(2L)),
                "Documents" to listOf(uri(3L)),
            ),
            3,
        )
        val restored = PhotoAiCollectionStore.rebuildUris(raw, present(1L, 2L, 3L))
        assertNotNull(restored)
        assertEquals(listOf("Nature", "Pets", "Documents"), restored!!.collections.keys.toList())
    }
}
