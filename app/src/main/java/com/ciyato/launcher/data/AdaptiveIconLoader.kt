package com.ciyato.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * AdaptiveIconLoader — Suggestion #25
 * Loads AdaptiveIconDrawable with foreground + background layers,
 * and supports swapping icon packs by resolving themed icon packs
 * that declare "org.adw.launcher.THEMES" or "com.novalauncher.THEME".
 */
object AdaptiveIconLoader {

    data class AdaptiveIcon(
        val foreground: Drawable,
        val background: Drawable,
        val full: Drawable,
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun load(context: Context, packageName: String): AdaptiveIcon? {
        return try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            if (icon is AdaptiveIconDrawable) {
                AdaptiveIcon(
                    foreground = icon.foreground,
                    background = icon.background,
                    full = icon,
                )
            } else null
        } catch (_: PackageManager.NameNotFoundException) { null }
    }

    /** Returns the full icon drawable, preferring AdaptiveIconDrawable on API 26+. */
    fun loadIcon(context: Context, packageName: String): Drawable? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pm.getApplicationIcon(packageName)
            } else {
                pm.getApplicationInfo(packageName, 0).loadIcon(pm)
            }
        } catch (_: Exception) { null }
    }

    /**
     * Discovers installed icon-pack apps by checking their metadata.
     * Returns list of (packageName, label) pairs.
     */
    fun getInstalledIconPacks(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val results = mutableListOf<Pair<String, String>>()

        val intents = listOf(
            android.content.Intent("org.adw.launcher.THEMES"),
            android.content.Intent("com.novalauncher.THEME"),
            android.content.Intent("com.teslacoilsw.launcher.THEME"),
        )

        intents.forEach { intent ->
            pm.queryIntentActivities(intent, PackageManager.GET_META_DATA).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString()
                if (results.none { it.first == pkg }) {
                    results.add(pkg to label)
                }
            }
        }

        return results
    }
}
