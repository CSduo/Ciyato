package com.ciyato.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * QuickSwitchManager — Suggestion #24
 * Tracks the last 2 launched apps and provides a one-tap/gesture "quick switch"
 * to jump back to the previously used app.
 */
object QuickSwitchManager {

    private val recentPackages = ArrayDeque<String>(2)

    fun recordLaunch(packageName: String) = synchronized(recentPackages) {
        recentPackages.remove(packageName)
        recentPackages.addFirst(packageName)
        if (recentPackages.size > 2) recentPackages.removeLast()
    }

    /** Returns the package that was open before the current one, if any. */
    fun getPreviousApp(): String? = synchronized(recentPackages) { recentPackages.getOrNull(1) }

    /** Launches the previous app via a normal launch intent. */
    fun switchToPrevious(context: Context): Boolean {
        val pkg = getPreviousApp() ?: return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent)
            true
        } catch (_: Exception) { false }
    }

    /**
     * Returns the top-most running task package name using ActivityManager.
     * Falls back to null on restricted environments.
     */
    fun currentForegroundPackage(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.runningAppProcesses
                ?.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                ?.pkgList?.firstOrNull()
        }
        return null
    }
}
