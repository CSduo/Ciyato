package com.ciyato.launcher.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ciyato.launcher.data.BadgeTally

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

    /**
     * Incremental state, reconciled against the system on connection.
     *
     * Every post and removal used to call getActiveNotifications() and re-group
     * the whole list (F-034): a binder round trip plus an O(n) walk per event,
     * and notifications do not arrive one at a time. A group chat waking up
     * produced a burst, and every message in it paid for a full rebuild.
     *
     * See [BadgeTally] for why this is keyed rather than counted. The short
     * version: an UPDATE to a notification re-fires onNotificationPosted with
     * the same key, so a counter would drift upward forever with nothing to
     * correct it until the listener reconnected.
     */
    private val tally = BadgeTally()

    override fun onListenerConnected() {
        super.onListenerConnected()
        // The full reconcile, and the only place one is needed: nothing is known
        // about what was posted while the service was not listening, and
        // incremental updates are only correct relative to a known start.
        reconcileFromSystem()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (tally.clear()) publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val key = notification.key ?: return
        val pkg = notification.packageName ?: return
        // isOngoing is read per event rather than decided once, because an
        // update can move a notification in either direction: a finished
        // download stops being ongoing and starts counting.
        if (tally.post(key, pkg, notification.isOngoing)) publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        if (tally.remove(key)) publish()
    }

    // ---- Private helpers ----------------------------------------------------

    /**
     * Rebuilds from the system's own list.
     *
     * runCatching because getActiveNotifications() reaches into system_server and
     * can throw while the binding is being torn down. A launcher losing its
     * badges is a cosmetic failure; a launcher crashing is not.
     */
    private fun reconcileFromSystem() {
        val active = runCatching {
            activeNotifications
                ?.mapNotNull { sbn ->
                    val key = sbn.key ?: return@mapNotNull null
                    val pkg = sbn.packageName ?: return@mapNotNull null
                    Triple(key, pkg, sbn.isOngoing)
                }
                .orEmpty()
        }.getOrDefault(emptyList())
        if (tally.reconcile(active)) publish()
    }

    /**
     * Publishes only when membership actually changed.
     *
     * [BadgeTally] returns false for a no-op, and notification updates are
     * frequent while mostly not moving a count. Republishing an identical map
     * would recompose every badge on Home for nothing.
     */
    private fun publish() {
        _badgeCounts.value = tally.counts()
    }
}
