package com.ciyato.launcher.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification Listener Service — Suggestion #81.
 *
 * Reads the count of active notifications per package so Home can badge app
 * icons.
 *
 * Deliberately NOT called "unread". Android's notification listener reports
 * which notifications are currently posted — nothing more. An app that posts one
 * summary for forty messages reports one; an app the person has read but not
 * dismissed still reports its notification; an app that never posts reports
 * nothing however much is waiting inside it. There is no universal per-app
 * unread truth to read here, and calling this count "unread" claims one (F-035).
 * Real unread state would be a per-app integration, not an inference.
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

        /** Active notifications currently posted by [packageName], or 0. */
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
                // are not actionable items and would inflate the badge count.
                if (!sbn.isOngoing) {
                    val pkg = sbn.packageName ?: return@forEach
                    counts[pkg] = (counts[pkg] ?: 0) + 1
                }
            }
        }
        _badgeCounts.value = counts
    }
}
