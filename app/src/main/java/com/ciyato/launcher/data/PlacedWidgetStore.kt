package com.ciyato.launcher.data

import org.json.JSONArray

/**
 * Persistent list of AppWidgetHost widget IDs placed on WidgetHostScreen,
 * written through [LauncherSettingsRepository.setPlacedWidgetIds] into the same
 * DataStore-backed JSON-string pattern the rest of the repository uses (see
 * FileTagStore/StickyNoteStore) — so placed widgets survive leaving the screen
 * and app restarts instead of living only in a `remember{}` list.
 *
 * Only the numeric IDs are persisted. Label and AppWidgetProviderInfo are
 * always re-read live from AppWidgetManager on load, since a provider can
 * change (or vanish, e.g. the providing app was uninstalled) between sessions.
 */
object PlacedWidgetStore {
    private const val MAX_WIDGETS = 40

    fun parse(raw: String): List<Int> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val id = array.optInt(i, -1)
                if (id > 0) add(id)
            }
        }
    }.getOrDefault(emptyList())

    fun serialize(ids: List<Int>): String = JSONArray().apply {
        ids.distinct().take(MAX_WIDGETS).forEach { put(it) }
    }.toString()
}
