package com.ciyato.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the result of an on-device photo-labelling pass.
 *
 * The scan ran ML Kit over hundreds of images — seconds of CPU and a measurable
 * bite of battery — and kept the result in `remember`, so navigating away threw
 * it out and coming back meant running the whole thing again (F-105). That makes
 * the feature a demo rather than an organizer: the organisation did not survive
 * leaving the screen.
 *
 * Only the grouping is stored, never image data: collection name to the list of
 * photo URIs in it. On restore those URIs are matched back against the live
 * library, which is what makes deleted photos disappear from their collection
 * for free instead of needing separate invalidation.
 */
object PhotoAiCollectionStore {

    private const val KEY_COLLECTIONS = "collections"
    private const val KEY_SCANNED = "scannedCount"
    private const val KEY_AT = "scannedAt"

    fun serialize(result: PhotoAiLabeler.AiScanResult): String = runCatching {
        val collections = JSONObject()
        result.collections.forEach { (name, images) ->
            collections.put(name, JSONArray().apply { images.forEach { put(it.uri.toString()) } })
        }
        JSONObject()
            .put(KEY_COLLECTIONS, collections)
            .put(KEY_SCANNED, result.scannedCount)
            .put(KEY_AT, System.currentTimeMillis())
            .toString()
    }.getOrDefault("")

    /**
     * Rebuilds a scan result against the CURRENT library.
     *
     * @param library the images visible right now. A stored URI with no match is
     *   dropped — the photo was deleted, moved, or is no longer permitted — so a
     *   restored collection can never list something that is not there.
     * @return null when nothing usable survives, which the caller must treat as
     *   "no scan yet" rather than "scan found nothing".
     */
    fun restore(
        raw: String,
        library: List<PhotoDeviceLibrary.DeviceImage>,
    ): PhotoAiLabeler.AiScanResult? {
        val byUri = library.associateBy { it.uri.toString() }
        val rebuilt = rebuildUris(raw) { uri -> uri in byUri }
            ?: return null
        val collections = LinkedHashMap<String, List<PhotoDeviceLibrary.DeviceImage>>()
        rebuilt.collections.forEach { (name, uris) ->
            collections[name] = uris.mapNotNull { byUri[it] }
        }
        return PhotoAiLabeler.AiScanResult(collections, rebuilt.scannedCount)
    }

    /** A restored grouping, still as URI strings. */
    data class RestoredUris(
        val collections: Map<String, List<String>>,
        val scannedCount: Int,
    )

    /**
     * The whole of restore that does not need Android.
     *
     * Split out so it can actually be tested: `android.net.Uri` is a
     * non-functional stub on the JVM, so a test built around DeviceImage would
     * have needed Robolectric on every CI run to verify string bookkeeping. The
     * same lesson as the backup-destination check, where a `Uri.parse` version
     * passed its tests while proving nothing.
     *
     * @param stillPresent decides whether a stored URI still exists in the
     *   library. Passed in rather than assumed, which is what keeps a deleted
     *   photo out of a restored collection.
     */
    fun rebuildUris(raw: String, stillPresent: (String) -> Boolean): RestoredUris? = runCatching {
        if (raw.isBlank()) return null
        val root = JSONObject(raw)
        val stored = root.optJSONObject(KEY_COLLECTIONS) ?: return null

        val rebuilt = LinkedHashMap<String, List<String>>()
        stored.keys().forEach { name ->
            val uris = stored.optJSONArray(name) ?: return@forEach
            val present = (0 until uris.length())
                .map { uris.optString(it) }
                .filter { it.isNotBlank() && stillPresent(it) }
            // An empty collection is not worth showing, and showing one implies
            // the label matched something that is still here.
            if (present.isNotEmpty()) rebuilt[name] = present
        }
        if (rebuilt.isEmpty()) return null
        RestoredUris(rebuilt, root.optInt(KEY_SCANNED, 0))
    }.getOrNull()

    /** Builds the stored form directly from URIs, for tests and for reuse. */
    fun serializeUris(collections: Map<String, List<String>>, scannedCount: Int): String =
        runCatching {
            val obj = JSONObject()
            collections.forEach { (name, uris) ->
                obj.put(name, JSONArray().apply { uris.forEach { put(it) } })
            }
            JSONObject()
                .put(KEY_COLLECTIONS, obj)
                .put(KEY_SCANNED, scannedCount)
                .put(KEY_AT, System.currentTimeMillis())
                .toString()
        }.getOrDefault("")

    /** When the stored scan ran, or 0 if there is none. */
    fun scannedAt(raw: String): Long =
        runCatching { JSONObject(raw).optLong(KEY_AT, 0L) }.getOrDefault(0L)
}
