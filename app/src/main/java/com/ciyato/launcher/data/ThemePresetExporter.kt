package com.ciyato.launcher.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * ThemePresetExporter - Suggestion #97
 * Exports and imports theme presets (dark mode, highlight accent, icon shape,
 * font, wallpaper blur, grid density) as a compact JSON string.
 * Shareable via clipboard or any Android share target.
 */
object ThemePresetExporter {

    data class ThemePreset(
        val name: String,
        val darkMode: String,        // "dark" | "light" | "auto"
        val goldAccent: Boolean,
        val iconShape: String,       // "squircle" | "circle" | "rounded_rect" | "square"
        val font: String,            // "inter" | "poppins" | "mono" | "serif"
        val wallpaperBlur: Int,      // 0-20
        val gridColumns: Int,        // 2 | 4 | 5 | 6
    )

    val BUILT_IN_PRESETS = listOf(
        ThemePreset("Silver Classic","dark",  true,  "squircle",     "inter",   0,  4),
        ThemePreset("Minimal White", "light", false, "circle",       "inter",   0,  5),
        ThemePreset("Dark Glass",    "dark",  false, "rounded_rect", "poppins", 8,  4),
        ThemePreset("Monochrome",    "dark",  false, "square",       "mono",    0,  5),
        ThemePreset("Soft Graphite", "dark",  true,  "squircle",     "serif",   4,  4),
    )

    fun export(preset: ThemePreset): String {
        return JSONObject().apply {
            put("name",         preset.name)
            put("darkMode",     preset.darkMode)
            put("goldAccent",   preset.goldAccent)
            put("iconShape",    preset.iconShape)
            put("font",         preset.font)
            put("wallpaperBlur",preset.wallpaperBlur)
            put("gridColumns",  preset.gridColumns)
            put("version",      1)
        }.toString()
    }

    fun import(json: String): ThemePreset? {
        return try {
            val obj = JSONObject(json)
            if (obj.optInt("version", 0) < 1) return null
            ThemePreset(
                name         = obj.optString("name", "Imported"),
                darkMode     = obj.optString("darkMode", "auto"),
                goldAccent   = obj.optBoolean("goldAccent", true),
                iconShape    = obj.optString("iconShape", "squircle"),
                font         = obj.optString("font", "inter"),
                wallpaperBlur= obj.optInt("wallpaperBlur", 0),
                gridColumns  = obj.optInt("gridColumns", 4),
            )
        } catch (_: Exception) { null }
    }

    fun copyToClipboard(context: Context, preset: ThemePreset) {
        val json = export(preset)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Ciyato Theme", json))
    }

    fun pasteFromClipboard(context: Context): ThemePreset? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
        return import(text)
    }

    fun sharePreset(context: Context, preset: ThemePreset) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ciyato Theme: ${preset.name}")
            putExtra(Intent.EXTRA_TEXT, export(preset))
        }
        context.startActivity(Intent.createChooser(intent, "Share Theme"))
    }
}
