package com.ciyato.launcher

import com.ciyato.launcher.data.UsageAverages
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The audit's acceptance test for F-124, plus the cases that made the fix
 * necessary twice.
 *
 * This arithmetic decides whether someone is told their usage "surged 240%".
 * Getting it wrong does not crash anything — it just prints a confident number
 * that is not true, which is why it survived two rounds of being looked at.
 */
class UsageAveragesTest {

    private val originalZone = TimeZone.getDefault()

    @Before fun pinZone() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreZone() { TimeZone.setDefault(originalZone) }

    private fun dayStart(daysAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 3); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun week(pkg: String, perDayMs: Long) =
        (1..7).map { UsageAverages.Bucket(pkg, perDayMs, dayStart(it)) }

    @Test
    fun `seven daily entries average to the per-day figure`() {
        val b = UsageAverages.baseline(week("com.a", 60_000L))
        assertEquals(7, b.daysCovered)
        assertEquals(60_000f, b.averageMsPerDay.getValue("com.a"), 0.01f)
    }

    /** The original F-124 bug: only one bucket survived, then it was /7. */
    @Test
    fun `every daily entry contributes rather than only the last`() {
        val buckets = (1..7).map { UsageAverages.Bucket("com.a", it * 1000L, dayStart(it)) }
        val b = UsageAverages.baseline(buckets)
        // 1000+2000+...+7000 = 28000 over 7 days
        assertEquals(4000f, b.averageMsPerDay.getValue("com.a"), 0.01f)
    }

    /**
     * The second bug, one layer down: the divisor was hard-coded to 7 even when
     * the phone held fewer days. A three-day-old device reported averages 2.3x
     * too low, so an ordinary day looked like a surge.
     */
    @Test
    fun `divisor is days actually observed, not a hard-coded seven`() {
        val buckets = (1..3).map { UsageAverages.Bucket("com.a", 30_000L, dayStart(it)) }
        val b = UsageAverages.baseline(buckets)
        assertEquals(3, b.daysCovered)
        assertEquals(30_000f, b.averageMsPerDay.getValue("com.a"), 0.01f)
        // Under the old hard-coded divisor this would have been 90000/7 = 12857,
        // making a normal 30s day read as 2.3x "above average".
    }

    @Test
    fun `several buckets on one day count as one day`() {
        val day = dayStart(1)
        val b = UsageAverages.baseline(
            listOf(
                UsageAverages.Bucket("com.a", 10_000L, day),
                UsageAverages.Bucket("com.a", 20_000L, day + 3_600_000L),
                UsageAverages.Bucket("com.a", 30_000L, day + 7_200_000L),
            ),
        )
        assertEquals(1, b.daysCovered)
        assertEquals(60_000f, b.averageMsPerDay.getValue("com.a"), 0.01f)
    }

    @Test
    fun `a day with no foreground time still counts toward the divisor`() {
        // Otherwise an app used on 2 of 7 days averages as if the week were two
        // days long, overstating its normal usage.
        val buckets = listOf(
            UsageAverages.Bucket("com.a", 70_000L, dayStart(1)),
            UsageAverages.Bucket("com.a", 0L, dayStart(2)),
            UsageAverages.Bucket("com.a", 0L, dayStart(3)),
            UsageAverages.Bucket("com.a", 0L, dayStart(4)),
        )
        val b = UsageAverages.baseline(buckets)
        assertEquals(4, b.daysCovered)
        assertEquals(17_500f, b.averageMsPerDay.getValue("com.a"), 0.01f)
    }

    @Test
    fun `divisor is capped at the query window`() {
        val buckets = (1..12).map { UsageAverages.Bucket("com.a", 1_000L, dayStart(it)) }
        assertEquals(7, UsageAverages.baseline(buckets, maxDays = 7).daysCovered)
    }

    @Test
    fun `packages are kept separate`() {
        val b = UsageAverages.baseline(week("com.a", 7_000L) + week("com.b", 14_000L))
        assertEquals(7_000f, b.averageMsPerDay.getValue("com.a"), 0.01f)
        assertEquals(14_000f, b.averageMsPerDay.getValue("com.b"), 0.01f)
    }

    @Test
    fun `too little history is not comparable`() {
        assertFalse(UsageAverages.baseline(emptyList()).isComparable)
        assertFalse(UsageAverages.baseline(listOf(UsageAverages.Bucket("com.a", 1L, dayStart(1)))).isComparable)
        val twoDays = listOf(
            UsageAverages.Bucket("com.a", 1L, dayStart(1)),
            UsageAverages.Bucket("com.a", 1L, dayStart(2)),
        )
        assertFalse(UsageAverages.baseline(twoDays).isComparable)
        assertTrue(UsageAverages.baseline(week("com.a", 1L)).isComparable)
    }

    @Test
    fun `ratio is null when there is no baseline rather than a misleading zero`() {
        assertNull(UsageAverages.ratio(500_000L, null))
        assertNull(UsageAverages.ratio(500_000L, 0f))
        assertEquals(2.0f, UsageAverages.ratio(120_000L, 60_000f)!!, 0.001f)
    }

    @Test
    fun `period label never claims a week it does not have`() {
        assertEquals("weekly average", UsageAverages.baseline(week("com.a", 1L)).periodLabel())
        val four = (1..4).map { UsageAverages.Bucket("com.a", 1L, dayStart(it)) }
        assertEquals("average over the last 4 days", UsageAverages.baseline(four).periodLabel())
    }

    @Test
    fun `empty input yields no averages and no days`() {
        val b = UsageAverages.baseline(emptyList())
        assertEquals(0, b.daysCovered)
        assertTrue(b.averageMsPerDay.isEmpty())
    }
}
