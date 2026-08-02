package com.ciyato.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi

/**
 * AppShortcutsManager — Suggestion #21
 * Queries and launches Android Shortcuts API (dynamic + static shortcuts)
 * for a given package. Called from AppContextMenu on long-press.
 */
object AppShortcutsManager {

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun getShortcuts(context: Context, packageName: String): List<ShortcutInfo> {
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()

            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun launchShortcut(context: Context, shortcut: ShortcutInfo) {
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(shortcut, null, null)
        } catch (_: Exception) {}
    }
}
