package com.ciyato.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistent sticky-notes store.
 *
 * Notes are written through [LauncherSettingsRepository.setStickyNotes] into
 * the same DataStore-backed JSON-string pattern the rest of the repository
 * uses (see fileTags/fileSearchIndex) — nothing here is kept only in memory,
 * so notes survive an app restart.
 */
data class StickyNote(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val colorIdx: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

object StickyNoteStore {
    private const val MAX_NOTES = 300
    private const val MAX_TEXT_LENGTH = 4000

    /** Ordered list of notes, newest-first as stored. Malformed entries are skipped. */
    fun parse(raw: String): List<StickyNote> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val text = item.optString("text")
                if (id.isBlank() || text.isBlank()) continue
                add(
                    StickyNote(
                        id = id,
                        text = text,
                        colorIdx = item.optInt("colorIdx", 0),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun serialize(notes: List<StickyNote>): String = JSONArray().apply {
        notes.take(MAX_NOTES).forEach { note ->
            val text = note.text.take(MAX_TEXT_LENGTH)
            if (note.id.isBlank() || text.isBlank()) return@forEach
            put(
                JSONObject().apply {
                    put("id", note.id)
                    put("text", text)
                    put("colorIdx", note.colorIdx)
                    put("createdAt", note.createdAt)
                }
            )
        }
    }.toString()

    /** Returns updated JSON with [note] added to the front of the list. */
    fun addNote(raw: String, note: StickyNote): String = serialize(listOf(note) + parse(raw))

    /** Returns updated JSON with the note matching [id] replaced by [text]/[colorIdx]; other notes are untouched. */
    fun updateNote(raw: String, id: String, text: String, colorIdx: Int): String {
        val notes = parse(raw).map {
            if (it.id == id) it.copy(text = text, colorIdx = colorIdx) else it
        }
        return serialize(notes)
    }

    /** Returns updated JSON with the note matching [id] removed. */
    fun deleteNote(raw: String, id: String): String = serialize(parse(raw).filterNot { it.id == id })
}
