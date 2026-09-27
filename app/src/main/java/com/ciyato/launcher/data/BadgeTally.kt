package com.ciyato.launcher.data

/**
 * Per-package notification counts, maintained incrementally.
 *
 * The listener used to call `getActiveNotifications()` and re-group the whole
 * list on every single post and removal (F-034). That is a binder round trip plus
 * an O(n) walk per event, and notifications do not arrive one at a time — a
 * group chat waking up produces a burst, and each message in it paid for a full
 * rebuild.
 *
 * ## Why this needs keys and not a counter
 *
 * The obvious incremental version — `count++` on post, `count--` on removal — is
 * wrong, and wrong in a way that only shows up in use. `onNotificationPosted`
 * fires again when an existing notification is **updated**: a chat app rewriting
 * "2 new messages" to "3 new messages" posts the same key a second time. A
 * counter would treat that as a new notification and drift upward forever, with
 * nothing to correct it until the listener reconnected.
 *
 * So the state is a key→package map and the counts are derived from it. Posting a
 * key that is already known is then idempotent, which is the actual requirement.
 *
 * ## Ongoing notifications
 *
 * Ongoing ones — a music player, a VPN, a download — are excluded: they are
 * persistent status, not something waiting for you, and counting them would put a
 * permanent badge on Spotify. A notification can also *change* ongoing state on
 * update (a download finishing), so [post] has to handle a key moving in either
 * direction rather than only deciding once.
 *
 * Deliberately free of Android types, so all of the above is testable rather than
 * asserted.
 */
class BadgeTally {

    /** Notification key to the package that posted it. Keys are unique per notification. */
    private val packageByKey = LinkedHashMap<String, String>()

    /** Live counts, recomputed only when membership actually changes. */
    private var cachedCounts: Map<String, Int> = emptyMap()
    private var dirty = false

    /**
     * Records a posted or updated notification.
     *
     * @return true when the tally changed, so a caller can skip publishing.
     *   Notification updates are frequent and most of them do not move a count;
     *   republishing an identical map would recompose every badge on Home for
     *   nothing.
     */
    fun post(key: String, packageName: String, isOngoing: Boolean): Boolean {
        if (key.isBlank() || packageName.isBlank()) return false
        return if (isOngoing) {
            // Not a miscount to ignore: an update can turn a counted
            // notification into an ongoing one, and it has to stop counting.
            remove(key)
        } else {
            val previous = packageByKey.put(key, packageName)
            if (previous == packageName) {
                false
            } else {
                dirty = true
                true
            }
        }
    }

    /** Records a removal. Returns true when the tally changed. */
    fun remove(key: String): Boolean {
        if (packageByKey.remove(key) == null) return false
        dirty = true
        return true
    }

    /**
     * Replaces everything with the authoritative list from the system.
     *
     * Run on listener connection, because the tally has no idea what was posted
     * while it was not listening, and incremental updates can only ever be
     * correct relative to a known starting point.
     *
     * @param active key / package / isOngoing for every currently posted
     *   notification.
     */
    fun reconcile(active: List<Triple<String, String, Boolean>>): Boolean {
        val rebuilt = LinkedHashMap<String, String>()
        active.forEach { (key, pkg, isOngoing) ->
            if (!isOngoing && key.isNotBlank() && pkg.isNotBlank()) rebuilt[key] = pkg
        }
        if (rebuilt == packageByKey) return false
        packageByKey.clear()
        packageByKey.putAll(rebuilt)
        dirty = true
        return true
    }

    /** Forgets everything, for listener disconnection. */
    fun clear(): Boolean {
        if (packageByKey.isEmpty()) return false
        packageByKey.clear()
        dirty = true
        return true
    }

    /**
     * Counts per package. Packages with nothing posted are absent rather than
     * present with zero, so a consumer cannot render a "0" badge.
     */
    fun counts(): Map<String, Int> {
        if (dirty) {
            cachedCounts = packageByKey.values.groupingBy { it }.eachCount()
            dirty = false
        }
        return cachedCounts
    }

    /** How many notifications are being tracked, for diagnostics and tests. */
    val trackedCount: Int get() = packageByKey.size
}
