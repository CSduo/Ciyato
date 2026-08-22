package com.ciyato.launcher

import com.ciyato.launcher.data.TimeAwareLayout
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bedtime, and what Home features at each hour.
 *
 * These read the system clock through property getters until now, so testing
 * them meant waiting for the right time of day. That is how the bug below
 * survived: nobody runs the app at 1 a.m. and checks.
 */
class TimeAwareLayoutTest {

    // -- the bug ---------------------------------------------------------------

    /**
     * The old rule was `hour >= startHour`, so a 22:00 bedtime evaluated false
     * from midnight onward and the hidden apps came straight back.
     */
    @Test
    fun `bedtime survives midnight`() {
        for (hour in listOf(22, 23, 0, 1, 2, 3, 4, 5)) {
            assertTrue("bedtime should be active at $hour:00",
                TimeAwareLayout.isBedtime(hour, startHour = 22, enabled = true))
        }
    }

    @Test
    fun `bedtime ends in the morning rather than never`() {
        assertFalse(TimeAwareLayout.isBedtime(TimeAwareLayout.BEDTIME_END_HOUR, 22, true))
        assertFalse(TimeAwareLayout.isBedtime(9, 22, true))
        assertFalse(TimeAwareLayout.isBedtime(15, 22, true))
    }

    @Test
    fun `bedtime does not start before its hour`() {
        assertFalse(TimeAwareLayout.isBedtime(21, 22, true))
        assertTrue(TimeAwareLayout.isBedtime(22, 22, true))
    }

    @Test
    fun `disabled means never, at any hour`() {
        for (hour in 0..23) {
            assertFalse(TimeAwareLayout.isBedtime(hour, 22, enabled = false))
        }
    }

    /**
     * The slider only offers 18-23, but the rule should not depend on that: a
     * start at or before the morning boundary describes a window inside one day
     * and must not be treated as wrapping, or it would be active all day.
     */
    @Test
    fun `a non-wrapping window is not treated as wrapping`() {
        assertTrue(TimeAwareLayout.isBedtime(4, startHour = 3, enabled = true))
        assertFalse(TimeAwareLayout.isBedtime(12, startHour = 3, enabled = true))
        assertFalse(TimeAwareLayout.isBedtime(2, startHour = 3, enabled = true))
    }

    @Test
    fun `every hour of the day gets an answer`() {
        for (start in 0..23) {
            for (hour in 0..23) {
                TimeAwareLayout.isBedtime(hour, start, true)   // must not throw
            }
        }
    }

    @Test
    fun `the window label states both ends`() {
        assertEquals("22:00 to 06:00", TimeAwareLayout.bedtimeWindowLabel(22))
        assertEquals("18:00 to 06:00", TimeAwareLayout.bedtimeWindowLabel(18))
    }

    // -- featured categories ---------------------------------------------------

    @Test
    fun `every hour yields exactly three featured categories`() {
        for (hour in 0..23) {
            for (weekend in listOf(true, false)) {
                assertEquals(
                    "hour $hour weekend=$weekend",
                    3,
                    TimeAwareLayout.featuredCategories(hour, weekend).size,
                )
            }
        }
    }

    @Test
    fun `weekday afternoons favour work, weekends do not`() {
        val weekday = TimeAwareLayout.featuredCategories(15, isWeekend = false)
        val weekend = TimeAwareLayout.featuredCategories(15, isWeekend = true)
        assertTrue(weekday.any { it.name == "WORK" })
        assertFalse(weekend.any { it.name == "WORK" })
    }

    @Test
    fun `featured categories are never duplicated within a slot`() {
        for (hour in 0..23) {
            for (weekend in listOf(true, false)) {
                val cats = TimeAwareLayout.featuredCategories(hour, weekend)
                assertEquals("hour $hour", cats.size, cats.distinct().size)
            }
        }
    }

    @Test
    fun `weekend detection matches the calendar constants`() {
        assertTrue(TimeAwareLayout.isWeekend(Calendar.SATURDAY))
        assertTrue(TimeAwareLayout.isWeekend(Calendar.SUNDAY))
        assertFalse(TimeAwareLayout.isWeekend(Calendar.WEDNESDAY))
    }
}
