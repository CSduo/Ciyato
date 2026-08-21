package com.ciyato.launcher

import com.ciyato.launcher.ui.screens.dateRangeForMonth
import com.ciyato.launcher.ui.screens.startOfToday
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Smart File Search date semantics (F-095, F-096).
 *
 * Both defects returned plausible-looking results, which is what made them
 * durable: "today" quietly meant "the last 24 hours", so a 9am search surfaced
 * yesterday afternoon's files and labelled them today's; and a month name always
 * resolved to the current year, so asking for "December" in August produced a
 * window entirely in the future and therefore always empty.
 */
class FileSearchDateRangeTest {

    @Test
    fun `startOfToday is local midnight, not 24 hours ago`() {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfToday() }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))

        // Same calendar day as now — a rolling window would fail this whenever
        // the test ran before noon.
        val today = Calendar.getInstance()
        assertEquals(today.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR))
        assertEquals(today.get(Calendar.YEAR), cal.get(Calendar.YEAR))
    }

    @Test
    fun `startOfToday is never in the future`() {
        assertTrue(startOfToday() <= System.currentTimeMillis())
    }

    @Test
    fun `a month later in the year resolves to last year, not the future`() {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        // Pick a month that is definitely still ahead of us this year.
        if (currentMonth == Calendar.DECEMBER) return
        val futureMonth = currentMonth + 1

        val (start, end) = dateRangeForMonth(futureMonth)
        assertTrue(
            "A bare month name must resolve to its most recent occurrence. " +
                "Resolving it in the current year puts the window in the future, " +
                "where it can never match a file.",
            end <= System.currentTimeMillis(),
        )
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(now.get(Calendar.YEAR) - 1, startCal.get(Calendar.YEAR))
        assertEquals(futureMonth, startCal.get(Calendar.MONTH))
    }

    @Test
    fun `a month already passed this year stays in this year`() {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        if (currentMonth == Calendar.JANUARY) return
        val pastMonth = currentMonth - 1

        val (start, _) = dateRangeForMonth(pastMonth)
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(now.get(Calendar.YEAR), startCal.get(Calendar.YEAR))
        assertEquals(pastMonth, startCal.get(Calendar.MONTH))
    }

    @Test
    fun `month range covers the whole first and last day`() {
        // Boundaries used to inherit the current time of day, silently dropping
        // files from the start of the 1st and the end of the last day.
        val (start, end) = dateRangeForMonth(Calendar.JANUARY)
        val s = Calendar.getInstance().apply { timeInMillis = start }
        val e = Calendar.getInstance().apply { timeInMillis = end }

        assertEquals(1, s.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, s.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, s.get(Calendar.MINUTE))
        assertEquals(0, s.get(Calendar.MILLISECOND))

        assertEquals(31, e.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, e.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, e.get(Calendar.MINUTE))
        assertEquals(59, e.get(Calendar.SECOND))
        assertTrue(start < end)
    }
}
