package com.ciyato.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** A bounded local metadata index for the single SAF folder selected in Files. */
data class FileSearchIndexEntry(
    val uri: String,
    val name: String,
    val mimeType: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
)

data class FileSearchIndex(
    val rootUri: String,
    val indexedAt: Long,
    val reachedLimit: Boolean,
    val entries: List<FileSearchIndexEntry>,
)

object FileSearchIndexStore {
    private const val MAX_ENTRIES = 2_000

    fun parse(raw: String): FileSearchIndex? = runCatching {
        val root = JSONObject(raw)
        val rootUri = root.optString("rootUri")
        if (rootUri.isBlank()) return null
        val entries = root.optJSONArray("entries") ?: JSONArray()
        FileSearchIndex(
            rootUri = rootUri,
            indexedAt = root.optLong("indexedAt"),
            reachedLimit = root.optBoolean("reachedLimit"),
            entries = buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val uri = item.optString("uri")
                    val name = item.optString("name")
                    if (uri.isBlank() || name.isBlank()) continue
                    add(
                        FileSearchIndexEntry(
                            uri = uri,
                            name = name,
                            mimeType = item.optString("mimeType"),
                            modifiedAt = item.optLong("modifiedAt"),
                            sizeBytes = item.optLong("sizeBytes").coerceAtLeast(0L),
                        ),
                    )
                }
            }.distinctBy(FileSearchIndexEntry::uri).take(MAX_ENTRIES),
        )
    }.getOrNull()

    fun serialize(index: FileSearchIndex): String = JSONObject().apply {
        put("rootUri", index.rootUri)
        put("indexedAt", index.indexedAt)
        put("reachedLimit", index.reachedLimit)
        put("entries", JSONArray().apply {
            index.entries.distinctBy(FileSearchIndexEntry::uri).take(MAX_ENTRIES).forEach { entry ->
                put(JSONObject().apply {
                    put("uri", entry.uri)
                    put("name", entry.name)
                    put("mimeType", entry.mimeType)
                    put("modifiedAt", entry.modifiedAt)
                    put("sizeBytes", entry.sizeBytes)
                })
            }
        })
    }.toString()
}
