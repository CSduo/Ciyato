package com.ciyato.launcher.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification Listener Service — Suggestion #81.
 *
 * Reads active notification counts per package so the home screen can
 * display unread-count badges on app icons.
 *
 * Permission model:
 *  - Declared in AndroidManifest with BIND_NOTIFICATION_LISTENER_SERVICE.
 *  - User must grant via Settings → Notification Access (system settings screen).
 *  - Never reads notification *content* — only package name and notification count.
 *
 * Usage:
 *   CiyatoNotificationListenerService.badgeCounts  // StateFlow<Map<String, Int>>
 *   CiyatoNotificationListenerService.countFor("com.whatsapp")  // 3
 */
class CiyatoNotificationListenerService : NotificationListenerService() {

    companion object {
        /** Live map of packageName → active notification count. */
        private val _badgeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badgeCounts: StateFlow<Map<String, Int>> = _badgeCounts.asStateFlow()

        /** Returns the unread notification count for [packageName], or 0 if none. */
        fun countFor(packageName: String): Int = _badgeCounts.value[packageName] ?: 0
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        rebuildCounts()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _badgeCounts.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        rebuildCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        rebuildCounts()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Counts active (non-ongoing) notifications grouped by package.
     * Uses runCatching so any security exception from getActiveNotifications()
     * on Android 14+ doesn't crash the service.
     */
    private fun rebuildCounts() {
        val counts = mutableMapOf<String, Int>()
        runCatching {
            activeNotifications?.forEach { sbn ->
                // Skip persistent notifications (e.g. music player, VPN) — they
                // are not actionable unread items and would inflate the badge count.
                if (!sbn.isOngoing) {
                    val pkg = sbn.packageName ?: return@forEach
                    counts[pkg] = (counts[pkg] ?: 0) + 1
                }
            }
        }
        _badgeCounts.value = counts
    }
}
