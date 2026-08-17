package com.ciyato.launcher

import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.FocusSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for focus-session lifetime (F-120, F-121, F-175, F-176).
 *
 * The original design stored "a session is running" in an in-memory singleton and
 * relied on a 1-second ticker — launched in the *caller's* coroutine scope — to
 * ever clear it. Started from the Quick Settings tile, that scope was cancelled
 * with the tile service, so nothing cleared the session and apps stayed hidden
 * indefinitely. Process death lost the session entirely in the other direction.
 *
 * Deriving the session from a persisted absolute end instant removes both
 * failure modes, and these tests pin the properties that make that true:
 * expiry needs no timer, and reconstruction from storage is exact.
 */
class FocusSessionManagerTest {

    private val blocked = "SOCIAL,GAMES"

    @Test
    fun `a future end instant yields an active session`() {
        val session = FocusSessionManager.sessionOf(
            endsAt = System.currentTimeMillis() + 10 * 60_000L,
            durationMin = 25,
            blockedCatsCsv = blocked,
        )
        assertNotNull(session)
        assertTrue(session!!.isActive)
    }

    @Test
    fun `a past end instant yields no session, with no cleanup pass required`() {
        // This is the property that replaces the ticker: an expired session stops
        // existing on read, so nothing has to run to end it.
        val session = FocusSessionManager.sessionOf(
            endsAt = System.currentTimeMillis() - 1_000L,
            durationMin = 25,
            blockedCatsCsv = blocked,
        )
        assertNull(
            "An elapsed session must evaluate to null without any timer having run — " +
                "that is what stops a tile-started session lasting forever.",
            session,
        )
    }

    @Test
    fun `zero means no session`() {
        assertNull(FocusSessionManager.sessionOf(0L, 25, blocked))
    }

    @Test
    fun `session reconstructed from storage keeps its blocked categories`() {
        // Reconstruction after process death must be exact, or focus silently
        // changes which categories it applies to.
        val session = FocusSessionManager.sessionOf(
            endsAt = System.currentTimeMillis() + 60_000L,
            durationMin = 30,
            blockedCatsCsv = blocked,
        )
        assertEquals(
            listOf(AppCategory.SOCIAL, AppCategory.GAMES),
            session!!.blockedCategories,
        )
    }

    @Test
    fun `unknown category names are dropped rather than crashing`() {
        // A renamed or removed enum constant in a stored CSV must not take the
        // launcher down on the next read.
        val cats = FocusSessionManager.decodeCategories("SOCIAL,NOT_A_REAL_CATEGORY,GAMES")
        assertEquals(listOf(AppCategory.SOCIAL, AppCategory.GAMES), cats)
    }

    @Test
    fun `blank and malformed csv decode to empty`() {
        assertTrue(FocusSessionManager.decodeCategories("").isEmpty())
        assertTrue(FocusSessionManager.decodeCategories(" , , ").isEmpty())
    }

    @Test
    fun `encode and decode round-trip`() {
        val original = listOf(AppCategory.SOCIAL, AppCategory.GAMES)
        val csv = FocusSessionManager.encodeCategories(original)
        assertEquals(original, FocusSessionManager.decodeCategories(csv))
    }

    @Test
    fun `isBlocked only applies to categories in the session`() {
        val session = FocusSessionManager.sessionOf(
            endsAt = System.currentTimeMillis() + 60_000L,
            durationMin = 25,
            blockedCatsCsv = blocked,
        )
        assertTrue(FocusSessionManager.isBlocked(session, AppCategory.SOCIAL))
        assertFalse(FocusSessionManager.isBlocked(session, AppCategory.PRODUCTIVITY))
    }

    @Test
    fun `isBlocked is false when there is no session`() {
        assertFalse(FocusSessionManager.isBlocked(null, AppCategory.SOCIAL))
    }

    @Test
    fun `remaining time never goes negative`() {
        val session = FocusSessionManager.FocusSession(
            endsAt = System.currentTimeMillis() - 5_000L,
            durationMs = 60_000L,
            blockedCategories = emptyList(),
        )
        assertEquals(0L, session.remainingMs)
        assertFalse(session.isActive)
    }

    @Test
    fun `progress fraction is bounded and safe for a zero duration`() {
        val zero = FocusSessionManager.FocusSession(
            endsAt = System.currentTimeMillis() + 1_000L,
            durationMs = 0L,
            blockedCategories = emptyList(),
        )
        // Guards a division by zero that would otherwise produce NaN and render
        // as a broken progress ring.
        assertEquals(1f, zero.progressFraction, 0.0001f)

        val half = FocusSessionManager.FocusSession(
            endsAt = System.currentTimeMillis() + 30_000L,
            durationMs = 60_000L,
            blockedCategories = emptyList(),
        )
        assertTrue(half.progressFraction in 0f..1f)
    }
}
