package com.ciyato.launcher

import com.ciyato.launcher.data.BadgeTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notification counts that survive a storm without drifting.
 *
 * The listener used to call `getActiveNotifications()` and re-group the whole
 * list on every post and removal (F-034) — a binder round trip plus an O(n) walk
 * per event, and notifications do not arrive one at a time.
 *
 * The obvious incremental fix is wrong, which is the whole reason this class
 * exists and is tested. `onNotificationPosted` fires **again** when an existing
 * notification is updated: a chat app rewriting "2 new messages" to "3 new
 * messages" posts the same key a second time. A `count++` would read that as a
 * new notification and drift upward forever, with nothing to correct it until
 * the listener reconnected — so the badge on WhatsApp would climb all day and be
 * wrong in a way nobody could explain.
 *
 * The audit's acceptance test is the last one here: a thousand post/remove events
 * preserving exact counts with no negative values.
 */
class BadgeTallyTest {

    private fun tally() = BadgeTally()

    // ── The defect a counter would have ──────────────────────────────────────

    @Test
    fun `updating a notification does not count it twice`() {
        val t = tally()
        t.post("key1", "com.whatsapp", isOngoing = false)
        t.post("key1", "com.whatsapp", isOngoing = false)
        t.post("key1", "com.whatsapp", isOngoing = false)
        assertEquals(mapOf("com.whatsapp" to 1), t.counts())
    }

    @Test
    fun `a repeated post reports no change, so nothing republishes`() {
        // Updates are frequent and mostly do not move a count. Republishing an
        // identical map recomposes every badge on Home for nothing.
        val t = tally()
        assertTrue("the first post is a change", t.post("key1", "com.whatsapp", false))
        assertFalse("an identical repost is not", t.post("key1", "com.whatsapp", false))
    }

    @Test
    fun `removing a notification that was never posted changes nothing`() {
        val t = tally()
        assertFalse(t.remove("never-seen"))
        assertEquals(emptyMap<String, Int>(), t.counts())
    }

    @Test
    fun `a count never goes negative`() {
        // The failure mode of a bare counter: more removals than posts.
        val t = tally()
        t.post("key1", "com.whatsapp", false)
        t.remove("key1")
        t.remove("key1")
        t.remove("key1")
        assertEquals(emptyMap<String, Int>(), t.counts())
        assertEquals(0, t.trackedCount)
    }

    // ── Ongoing notifications ────────────────────────────────────────────────

    @Test
    fun `an ongoing notification is not counted`() {
        // A music player or a VPN is persistent status, not something waiting.
        // Counting it puts a permanent badge on Spotify.
        val t = tally()
        t.post("music", "com.spotify", isOngoing = true)
        assertEquals(emptyMap<String, Int>(), t.counts())
    }

    @Test
    fun `a notification that becomes ongoing stops counting`() {
        val t = tally()
        t.post("dl", "com.downloader", isOngoing = false)
        assertEquals(mapOf("com.downloader" to 1), t.counts())
        // The same key updated to ongoing. Deciding isOngoing once at first post
        // would have left this counted forever.
        t.post("dl", "com.downloader", isOngoing = true)
        assertEquals(emptyMap<String, Int>(), t.counts())
    }

    @Test
    fun `a finished download starts counting`() {
        // The other direction, which is the common one: ongoing while running,
        // then a normal notification saying it is done.
        val t = tally()
        t.post("dl", "com.downloader", isOngoing = true)
        assertEquals(emptyMap<String, Int>(), t.counts())
        t.post("dl", "com.downloader", isOngoing = false)
        assertEquals(mapOf("com.downloader" to 1), t.counts())
    }

    // ── Grouping ─────────────────────────────────────────────────────────────

    @Test
    fun `notifications group by package`() {
        val t = tally()
        t.post("a1", "com.whatsapp", false)
        t.post("a2", "com.whatsapp", false)
        t.post("b1", "com.gmail", false)
        assertEquals(mapOf("com.whatsapp" to 2, "com.gmail" to 1), t.counts())
    }

    @Test
    fun `a package with nothing posted is absent rather than zero`() {
        // Present-with-zero would let a consumer render a "0" badge.
        val t = tally()
        t.post("a1", "com.whatsapp", false)
        t.remove("a1")
        assertFalse("com.whatsapp" in t.counts())
    }

    @Test
    fun `a notification that moves package is not double counted`() {
        // Should not happen, but a key is unique and the package is data: if the
        // system ever reports the same key under a different package, the count
        // must follow rather than split.
        val t = tally()
        t.post("k", "com.old", false)
        t.post("k", "com.new", false)
        assertEquals(mapOf("com.new" to 1), t.counts())
    }

    @Test
    fun `blank keys and packages are ignored`() {
        val t = tally()
        assertFalse(t.post("", "com.whatsapp", false))
        assertFalse(t.post("key", "", false))
        assertEquals(emptyMap<String, Int>(), t.counts())
    }

    // ── Reconciliation ───────────────────────────────────────────────────────

    @Test
    fun `reconciling replaces everything, because the system is authoritative`() {
        val t = tally()
        t.post("stale1", "com.gone", false)
        t.post("stale2", "com.gone", false)
        t.reconcile(
            listOf(
                Triple("real1", "com.whatsapp", false),
                Triple("real2", "com.whatsapp", false),
                Triple("music", "com.spotify", true),
            ),
        )
        assertEquals(mapOf("com.whatsapp" to 2), t.counts())
    }

    @Test
    fun `reconciling to the same state reports no change`() {
        val t = tally()
        val active = listOf(Triple("k", "com.whatsapp", false))
        assertTrue(t.reconcile(active))
        assertFalse("an identical reconcile must not republish", t.reconcile(active))
    }

    @Test
    fun `disconnection forgets everything`() {
        val t = tally()
        t.post("k", "com.whatsapp", false)
        assertTrue(t.clear())
        assertEquals(emptyMap<String, Int>(), t.counts())
        assertFalse("clearing an empty tally is not a change", t.clear())
    }

    // ── The audit's acceptance test ──────────────────────────────────────────

    @Test
    fun `a thousand post and remove events keep exact counts`() {
        val t = tally()
        val packages = listOf("com.whatsapp", "com.gmail", "com.slack", "com.telegram")

        // 1,000 posts spread across four packages.
        repeat(1_000) { i ->
            t.post("key$i", packages[i % packages.size], isOngoing = false)
        }
        assertEquals(1_000, t.trackedCount)
        assertEquals(250, t.counts()["com.whatsapp"])
        assertEquals(1_000, t.counts().values.sum())

        // Every other one updated, which must move nothing.
        repeat(500) { i ->
            t.post("key${i * 2}", packages[(i * 2) % packages.size], isOngoing = false)
        }
        assertEquals("an update is not a new notification", 1_000, t.counts().values.sum())

        // Half removed, including some removed twice.
        repeat(500) { i -> t.remove("key$i") }
        repeat(100) { i -> t.remove("key$i") }
        assertEquals(500, t.counts().values.sum())
        assertTrue("no count may be negative", t.counts().values.all { it > 0 })

        // The rest removed, plus a hundred that never existed.
        repeat(1_000) { i -> t.remove("key$i") }
        repeat(100) { i -> t.remove("phantom$i") }
        assertEquals(emptyMap<String, Int>(), t.counts())
        assertEquals(0, t.trackedCount)
    }
}
