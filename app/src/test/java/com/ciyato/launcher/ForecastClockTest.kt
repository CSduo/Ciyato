package com.ciyato.launcher

import com.ciyato.launcher.data.ForecastClock
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Picking "now" out of an hourly forecast, from a phone in a different timezone.
 *
 * Ciyato requests `timezone=auto`, so Open-Meteo's stamps are local time at the
 * forecast location. The old code compared them against the device's calendar,
 * which agrees only when the two zones match - and fell back to index 0,
 * midnight of the first forecast day, when they did not (F-032).
 *
 * Every test here deliberately sets the device zone to something *other* than
 * the forecast's, because that is the case the original code could not survive
 * and the only case worth testing.
 */
class ForecastClockTest {

    private val original = TimeZone.getDefault()

    @Before fun pinDeviceZone() {
        // Kolkata: +05:30, deliberately half-hour offset and far from the
        // forecast locations below.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    }

    @After fun restore() { TimeZone.setDefault(original) }

    /** 2026-09-18T09:00Z */
    private val nowUtc = Instant.parse("2026-09-18T09:00:00Z").toEpochMilli()

    private fun londonHours() = listOf(
        "2026-09-18T08:00", "2026-09-18T09:00", "2026-09-18T10:00",
        "2026-09-18T11:00", "2026-09-18T12:00",
    )

    // -- the bug ---------------------------------------------------------------

    @Test
    fun `the current hour is found at the forecast location, not on the phone`() {
        // London is UTC+1 in September, so 09:00Z is 10:00 local there.
        val idx = ForecastClock.currentHourIndex(londonHours(), nowUtc, utcOffsetSeconds = 3600)
        assertEquals(2, idx)
        assertEquals("2026-09-18T10:00", londonHours()[idx!!])
    }

    @Test
    fun `a different offset selects a different hour from the same data`() {
        // Same instant, forecast in UTC: 09:00 local.
        assertEquals(1, ForecastClock.currentHourIndex(londonHours(), nowUtc, 0))
        // Same instant, forecast at UTC+2: 11:00 local.
        assertEquals(3, ForecastClock.currentHourIndex(londonHours(), nowUtc, 7200))
    }

    /**
     * The old failure: no exact match meant index 0, which is midnight of the
     * first forecast day - so "current conditions" showed the middle of last
     * night.
     */
    @Test
    fun `a forecast that starts later today does not fall back to midnight`() {
        val laterToday = listOf(
            "2026-09-18T00:00", "2026-09-18T01:00", "2026-09-18T02:00",
            "2026-09-18T20:00", "2026-09-18T21:00",
        )
        // 09:00Z at UTC+11 is 20:00 local - present in the list.
        assertEquals(3, ForecastClock.hourlyStartIndex(laterToday, nowUtc, 39600))
    }

    @Test
    fun `when now is missing the strip starts at the next hour ahead, not at zero`() {
        val gapped = listOf("2026-09-18T06:00", "2026-09-18T07:00", "2026-09-18T15:00")
        // 09:00Z at UTC+1 is 10:00 local: absent. The next entry ahead is 15:00.
        assertEquals(2, ForecastClock.hourlyStartIndex(gapped, nowUtc, 3600))
        assertNull(ForecastClock.currentHourIndex(gapped, nowUtc, 3600))
    }

    // -- date boundaries -------------------------------------------------------

    @Test
    fun `crossing midnight at the forecast location picks the right day`() {
        val acrossMidnight = listOf(
            "2026-09-18T23:00", "2026-09-19T00:00", "2026-09-19T01:00",
        )
        // 09:00Z at UTC+15 would be 00:00 the next day. Use +15h (54000s).
        assertEquals(1, ForecastClock.currentHourIndex(acrossMidnight, nowUtc, 54000))
    }

    @Test
    fun `a negative offset can land on the previous day`() {
        val previousDay = listOf(
            "2026-09-17T22:00", "2026-09-17T23:00", "2026-09-18T00:00",
        )
        // 09:00Z at UTC-10 is 23:00 on the 17th.
        assertEquals(1, ForecastClock.currentHourIndex(previousDay, nowUtc, -36000))
    }

    @Test
    fun `half-hour and three-quarter-hour offsets are handled`() {
        val india = listOf("2026-09-18T13:00", "2026-09-18T14:00", "2026-09-18T15:00")
        // +05:30 -> 14:30 local, which truncates to the 14:00 entry.
        assertEquals(1, ForecastClock.currentHourIndex(india, nowUtc, 19800))
        val nepal = listOf("2026-09-18T14:00", "2026-09-18T15:00")
        // +05:45 -> 14:45 local, still the 14:00 entry.
        assertEquals(0, ForecastClock.currentHourIndex(nepal, nowUtc, 20700))
    }

    // -- degenerate input ------------------------------------------------------

    @Test
    fun `an empty forecast is null rather than index zero`() {
        assertNull(ForecastClock.currentHourIndex(emptyList(), nowUtc, 0))
        assertEquals(0, ForecastClock.hourlyStartIndex(emptyList(), nowUtc, 0))
        assertTrue(ForecastClock.isStale(emptyList(), nowUtc, 0))
    }

    @Test
    fun `an absurd offset cannot throw`() {
        // ZoneOffset rejects anything beyond +/-18h; a malformed response must
        // not take the weather card down with it.
        ForecastClock.currentHourIndex(londonHours(), nowUtc, Int.MAX_VALUE)
        ForecastClock.currentHourIndex(londonHours(), nowUtc, Int.MIN_VALUE)
    }

    @Test
    fun `a forecast entirely in the past reads as stale`() {
        val old = listOf("2026-09-17T01:00", "2026-09-17T02:00")
        assertTrue(ForecastClock.isStale(old, nowUtc, 3600))
        assertTrue(!ForecastClock.isStale(londonHours(), nowUtc, 3600))
    }

    @Test
    fun `the device zone never influences the result`() {
        val expected = ForecastClock.currentHourIndex(londonHours(), nowUtc, 3600)
        for (zone in listOf("UTC", "America/Los_Angeles", "Pacific/Kiritimati", "Asia/Kolkata")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            assertEquals(
                "device zone $zone changed the answer",
                expected,
                ForecastClock.currentHourIndex(londonHours(), nowUtc, 3600),
            )
        }
    }
}
