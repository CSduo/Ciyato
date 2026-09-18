package com.ciyato.launcher

import com.ciyato.launcher.data.LocationFreshness
import com.ciyato.launcher.data.LocationFreshness.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a cached location may be trusted for a weather forecast.
 *
 * getLastKnownLocation has no upper bound on age, and the old code took whatever
 * it returned (F-030). The symptom is specific: land after a flight, open Home,
 * and see the city you left - named, next to a temperature, with nothing
 * indicating it was wrong.
 */
class LocationFreshnessTest {

    private val now = 1_800_000_000_000L
    private fun minutesAgo(m: Long) = now - m * 60_000L

    @Test
    fun `a recent accurate fix is used`() {
        assertEquals(Verdict.FRESH, LocationFreshness.judge(minutesAgo(2), now, 25f))
        assertTrue(LocationFreshness.isUsable(minutesAgo(2), now, 25f))
    }

    /** The flight case. */
    @Test
    fun `a fix from hours ago is refused`() {
        assertEquals(Verdict.TOO_OLD, LocationFreshness.judge(minutesAgo(180), now, 20f))
        assertFalse(LocationFreshness.isUsable(minutesAgo(180), now, 20f))
    }

    @Test
    fun `a day-old fix is refused however accurate it was`() {
        assertEquals(Verdict.TOO_OLD, LocationFreshness.judge(minutesAgo(60 * 24), now, 1f))
    }

    @Test
    fun `the age boundary is exact`() {
        val edge = now - LocationFreshness.MAX_AGE_MS
        assertEquals(Verdict.FRESH, LocationFreshness.judge(edge, now, 10f))
        assertEquals(Verdict.TOO_OLD, LocationFreshness.judge(edge - 1, now, 10f))
    }

    @Test
    fun `a wildly imprecise fix is refused`() {
        assertEquals(Verdict.TOO_VAGUE, LocationFreshness.judge(minutesAgo(1), now, 200_000f))
    }

    /**
     * Coordinates are coarsened to ~1.1 km before use, so the accuracy gate must
     * not reject ordinary coarse fixes - only pathological ones.
     */
    @Test
    fun `an ordinary coarse cell-tower fix is still accepted`() {
        assertEquals(Verdict.FRESH, LocationFreshness.judge(minutesAgo(1), now, 2_000f))
        assertEquals(Verdict.FRESH, LocationFreshness.judge(minutesAgo(1), now, 20_000f))
    }

    @Test
    fun `a missing accuracy does not by itself disqualify a fresh fix`() {
        assertEquals(Verdict.FRESH, LocationFreshness.judge(minutesAgo(1), now, null))
    }

    /**
     * A zero timestamp must not be read as the epoch and therefore "very old",
     * nor as "now" - it means the provider did not say, and assuming freshness
     * is the exact failure being fixed.
     */
    @Test
    fun `a missing timestamp is unusable rather than assumed fresh`() {
        assertEquals(Verdict.NO_TIMESTAMP, LocationFreshness.judge(0L, now, 10f))
        assertFalse(LocationFreshness.isUsable(0L, now, 10f))
    }

    @Test
    fun `a future timestamp is rejected instead of looking infinitely fresh`() {
        // Clock changes and provider bugs both produce these. Treating one as
        // fresh would pin Home to a stale place until the clock caught up.
        assertEquals(Verdict.NO_TIMESTAMP, LocationFreshness.judge(now + 3_600_000L, now, 10f))
    }

    @Test
    fun `small clock skew is tolerated rather than treated as a bad timestamp`() {
        assertEquals(Verdict.FRESH, LocationFreshness.judge(now + 5_000L, now, 10f))
    }

    @Test
    fun `age is checked before accuracy so the reason is the useful one`() {
        // Old AND vague: the age is what matters and what should be reported.
        assertEquals(Verdict.TOO_OLD, LocationFreshness.judge(minutesAgo(600), now, 900_000f))
    }

    @Test
    fun `the thresholds stay within defensible bounds`() {
        val minutes = LocationFreshness.MAX_AGE_MS / 60_000L
        assertTrue("$minutes min is too long to survive a journey", minutes <= 60)
        assertTrue("$minutes min would wake the radio constantly", minutes >= 5)
    }
}
