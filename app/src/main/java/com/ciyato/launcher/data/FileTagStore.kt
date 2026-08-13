package com.ciyato.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent per-file tag store, keyed by content URI string.
 *
 * Tags are written through [LauncherSettingsRepository.setFileTags] into the
 * same DataStore-backed JSON-string pattern the rest of the repository uses
 * (see fileSearchIndex/fileSearchHistory) — nothing here is kept only in
 * memory, so tags survive an app restart. Neither FileSearchIndexStore (a
 * bounded index of one SAF folder) nor FileSearchHistoryStore (search query
 * history) models file-to-tag associations, so this is a small dedicated
 * store next to them rather than a reuse of either.
 */
object FileTagStore {
    private const val MAX_TAGS_PER_FILE = 12
    private const val MAX_TAG_LENGTH = 24
    private const val MAX_TAGGED_FILES = 500

    /** uri string -> ordered, de-duplicated tag list (never empty lists — an emptied file is dropped). */
    fun parse(raw: String): Map<String, List<String>> = runCatching {
        val root = JSONObject(raw)
        buildMap {
            root.keys().forEach { uri ->
                val tags = (root.optJSONArray(uri) ?: JSONArray()).let { arr ->
                    buildList {
                        for (i in 0 until arr.length()) {
                            arr.optString(i).trim().takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }.distinct().take(MAX_TAGS_PER_FILE)
                if (uri.isNotBlank() && tags.isNotEmpty()) put(uri, tags)
            }
        }
    }.getOrDefault(emptyMap())

    fun serialize(map: Map<String, List<String>>): String = JSONObject().apply {
        map.entries.take(MAX_TAGGED_FILES).forEach { (uri, tags) ->
            val clean = tags.map { it.trim() }.filter(String::isNotBlank).distinct().take(MAX_TAGS_PER_FILE)
            if (uri.isNotBlank() && clean.isNotEmpty()) put(uri, JSONArray(clean))
        }
    }.toString()

    /** Returns updated JSON with [tag] attached to [uri] (case-insensitive de-dupe). */
    fun addTag(raw: String, uri: String, tag: String): String {
        val clean = tag.trim().take(MAX_TAG_LENGTH)
        if (clean.isBlank() || uri.isBlank()) return raw
        val map = parse(raw).toMutableMap()
        val existing = map[uri] ?: emptyList()
        if (existing.any { it.equals(clean, ignoreCase = true) }) return serialize(map)
        map[uri] = (existing + clean).take(MAX_TAGS_PER_FILE)
        return serialize(map)
    }

    /** Returns updated JSON with [tag] removed from [uri]; drops the file entry once it has no tags left. */
    fun removeTag(raw: String, uri: String, tag: String): String {
        val map = parse(raw).toMutableMap()
        val existing = map[uri] ?: return serialize(map)
        val updated = existing.filterNot { it.equals(tag, ignoreCase = true) }
        if (updated.isEmpty()) map.remove(uri) else map[uri] = updated
        return serialize(map)
    }

    fun allTags(raw: String): List<String> = parse(raw).values.flatten().distinct().sorted()
}
