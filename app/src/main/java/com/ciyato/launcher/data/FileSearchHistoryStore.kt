package com.ciyato.launcher.data

import org.json.JSONArray

/** File/media search history is separate from launcher-wide app search history. */
object FileSearchHistoryStore {
    private const val MAX_ENTRIES = 12

    fun parse(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
    }.getOrDefault(emptyList())

    fun record(raw: String, query: String): String {
        val clean = query.trim().replace(Regex("\\s+"), " ").take(160)
        if (clean.isBlank()) return serialize(parse(raw))
        return serialize((listOf(clean) + parse(raw).filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_ENTRIES))
    }

    fun serialize(queries: Collection<String>): String = JSONArray(
        queries.asSequence().map(String::trim).filter(String::isNotBlank).distinct().take(MAX_ENTRIES).toList(),
    ).toString()
}
