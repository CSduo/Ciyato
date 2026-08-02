package com.ciyato.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ciyato.launcher.data.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * CategoryColorManager — Suggestion #96
 * Persists per-category color overrides using DataStore.
 * Each category maps to a hex color string (e.g. "#FFD700").
 * Default colors reflect category intent (Work=blue, Social=purple, etc.)
 */
object CategoryColorManager {

    private val KEY = stringPreferencesKey("category_color_map")

    val DEFAULT_COLORS = mapOf(
        AppCategory.WORK          to 0xFF1565C0.toInt(),  // Deep Blue
        AppCategory.SOCIAL        to 0xFF6A1B9A.toInt(),  // Purple
        AppCategory.FINANCE       to 0xFF1B5E20.toInt(),  // Dark Green
        AppCategory.CREATIVITY    to 0xFFE65100.toInt(),  // Orange
        AppCategory.UTILITIES     to 0xFF37474F.toInt(),  // Blue Gray
        AppCategory.DAILY         to 0xFF004D40.toInt(),  // Teal
        AppCategory.ENTERTAINMENT to 0xFFB71C1C.toInt(),  // Dark Red
        AppCategory.PRODUCTIVITY  to 0xFF0277BD.toInt(),  // Light Blue
        AppCategory.GAMES         to 0xFF4A148C.toInt(),  // Deep Purple
        AppCategory.TRAVEL        to 0xFF00695C.toInt(),  // Teal
        AppCategory.COMMUNICATION to 0xFF283593.toInt(),  // Indigo
        AppCategory.SHOPPING      to 0xFF880E4F.toInt(),  // Pink
        AppCategory.REVIEW        to 0xFF546E7A.toInt(),  // Blue Gray
        AppCategory.OTHER         to 0xFF455A64.toInt(),  // Blue Gray
    )

    fun colorMapFlow(context: Context): Flow<Map<AppCategory, Int>> =
        context.dataStore.data.map { prefs ->
            val json = prefs[KEY] ?: return@map DEFAULT_COLORS
            val result = DEFAULT_COLORS.toMutableMap()
            try {
                val obj = JSONObject(json)
                AppCategory.entries.forEach { cat ->
                    val colorStr = obj.optString(cat.name)
                    if (colorStr.isNotBlank()) {
                        result[cat] = android.graphics.Color.parseColor(colorStr)
                    }
                }
            } catch (_: Exception) {}
            result
        }

    suspend fun setCategoryColor(context: Context, category: AppCategory, colorInt: Int) {
        context.dataStore.edit { prefs ->
            val current = try { JSONObject(prefs[KEY] ?: "{}") } catch (_: Exception) { JSONObject() }
            current.put(category.name, "#%06X".format(colorInt and 0xFFFFFF))
            prefs[KEY] = current.toString()
        }
    }

    suspend fun resetToDefaults(context: Context) {
        context.dataStore.edit { prefs -> prefs.remove(KEY) }
    }

    fun colorForCategory(category: AppCategory, overrides: Map<AppCategory, Int>): Int =
        overrides[category] ?: DEFAULT_COLORS[category] ?: 0xFF455A64.toInt()
}
