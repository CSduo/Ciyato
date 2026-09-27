package com.ciyato.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process

/**
 * The shortcuts an app publishes for itself — Gmail's "Compose", Maps' "Home",
 * WhatsApp's recent chats — as offered on long-press.
 *
 * Both calls need API 25 and minSdk is 26, so no version gate.
 *
 * This file used to say "Called from AppContextMenu on long-press". It was not
 * called from anywhere: the whole object was unreachable, and the context menu's
 * own KDoc separately advertised "add shortcut" while offering nothing of the
 * kind. Two claims, in two files, for a feature that did not exist. It exists
 * now, which is why the comment is worth keeping — the code was fine, and the
 * only thing wrong with it was that nobody had connected it.
 */
object AppShortcutsManager {

    /**
     * What [packageName] publishes, or nothing.
     *
     * Empty deliberately covers both "this app publishes no shortcuts" and "we
     * cannot ask", because both render identically and correctly: no shortcuts
     * section in the menu. That is not the failed-count trap CoverageHonestyTest
     * guards against — nothing here counts toward a claim of completeness, and an
     * absent section asserts nothing. `hasShortcutHostPermission()` is false
     * whenever Ciyato is not the active Home app, which is a routine state
     * during onboarding rather than an error worth reporting.
     *
     * Binder call: do not run this on the main thread.
     */
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
            launcherApps.getShortcuts(query, Process.myUserHandle())
                .orEmpty()
                .filter { it.isEnabled }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Starts [shortcut], reporting whether it actually started.
     *
     * Returns Boolean rather than Unit because this genuinely fails in a way the
     * person needs to hear about. A ShortcutInfo is a snapshot: the app can
     * disable or unpublish it after the menu was built, and `startShortcut` then
     * throws IllegalStateException. It also throws if the app is suspended or in
     * a locked work profile. Swallowing that produced the defect this audit found
     * over and over — a control that looks enabled, is tapped, and does nothing
     * at all, so the person taps it again.
     */
    fun launchShortcut(context: Context, shortcut: ShortcutInfo): Boolean {
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(shortcut, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** The label an app gave a shortcut, preferring the short form. */
    fun labelOf(shortcut: ShortcutInfo): String =
        (shortcut.shortLabel ?: shortcut.longLabel)?.toString()?.takeIf { it.isNotBlank() }
            ?: shortcut.id
}
