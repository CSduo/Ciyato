package com.ciyato.launcher

import com.ciyato.launcher.data.UsageAnomalies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a day's usage is genuinely unusual.
 *
 * The old code compared the day in progress against the mean of the complete
 * days before it (F-128). At nine in the morning today holds two hours and the
 * history holds full days, so almost every app scored a large negative z and the
 * screen announced a wave of "unusual drops" that were nothing but the clock.
 *
 * These pin the property that makes the comparison meaningful: a partial day is
 * never the subject.
 *
 * Note on the fixtures: every history below varies day to day, because real
 * usage does. A perfectly flat history has zero standard deviation, and the
 * implementation deliberately refuses to judge against it — writing the first
 * draft of these tests with identical days proved that guard works, and proved
 * nothing else.
 */
class UsageAnomaliesTest {

    private val hour = 3_600_000L
    private fun mins(m: Long) = m * 60_000L

    /** Five ordinary days with ordinary variation: roughly two hours each. */
    private fun ordinaryWeek() = listOf(
        mins(110), mins(130), mins(95), mins(140), mins(105),
    )

    // -- the bug ---------------------------------------------------------------

    /**
     * An ordinary week, an ordinary finished day, and a today that has barely
     * begun. The old comparison called this a collapse in usage.
     */
    @Test
    fun `a barely started day is not reported as a collapse in usage`() {
        val verdict = UsageAnomalies.analyse(ordinaryWeek() + mins(120) + mins(5))
        assertNotNull("there is enough history to judge", verdict)
        assertFalse(
            "the day in progress must not be the subject of the comparison",
            verdict!!.isUnusual,
        )
    }

    @Test
    fun `the subject is the last complete day, not today`() {
        val hugeFinishedDay = 9 * hour
        val verdict = UsageAnomalies.analyse(ordinaryWeek() + hugeFinishedDay + mins(1))
        assertNotNull(verdict)
        assertTrue("a nine-hour day against two-hour days is unusual", verdict!!.isUnusual)
        assertTrue(verdict.isIncrease)
        assertEquals(hugeFinishedDay, verdict.dayMs)
    }

    @Test
    fun `a real drop on a finished day is still caught`() {
        val verdict = UsageAnomalies.analyse(ordinaryWeek() + mins(2) + mins(30))
        assertNotNull(verdict)
        assertTrue(verdict!!.isUnusual)
        assertFalse("two minutes against two hours is a drop", verdict.isIncrease)
    }

    // -- refusing to judge -----------------------------------------------------

    @Test
    fun `too little history yields no verdict rather than a confident one`() {
        assertNull(UsageAnomalies.analyse(listOf(hour, hour, hour)))
        assertNull(UsageAnomalies.analyse(listOf(hour)))
        assertNull(UsageAnomalies.analyse(emptyList()))
    }

    /**
     * Null means "no verdict", never "nothing unusual". The screen must not
     * collapse the two — that is the F-129 half of this work.
     */
    @Test
    fun `perfectly flat usage yields no verdict, because a deviation means nothing`() {
        assertNull(UsageAnomalies.analyse(List(7) { 2 * hour }))
    }

    @Test
    fun `near-flat usage below the noise floor is not called unusual`() {
        // Varies by seconds a day: a large z mathematically, nothing humanly.
        val days = listOf(3_600_000L, 3_601_000L, 3_599_000L, 3_600_500L, 3_602_000L, 3_640_000L)
        assertNull(UsageAnomalies.analyse(days + 1_000L))
    }

    // -- boundaries ------------------------------------------------------------

    @Test
    fun `zero-usage days are legitimate data, not missing data`() {
        // An app opened only occasionally, then used for hours.
        val sporadic = listOf(0L, mins(3), 0L, mins(1), 0L)
        val verdict = UsageAnomalies.analyse(sporadic + 5 * hour + 0L)
        assertNotNull(verdict)
        assertTrue("five hours after a near-silent week is unusual", verdict!!.isUnusual)
        assertTrue(verdict.isIncrease)
    }

    @Test
    fun `keeping the partial day can be requested explicitly`() {
        // The option exists so that dropping it is a decision rather than an
        // accident, and so this test can demonstrate the original bug.
        val days = ordinaryWeek() + mins(120) + mins(5)
        val withPartial = UsageAnomalies.analyse(days, dropPartialDay = false)
        assertNotNull(withPartial)
        assertTrue(
            "including the partial day is exactly what produced false drops",
            withPartial!!.isUnusual,
        )
        assertFalse("and it reads as a drop", withPartial.isIncrease)
    }

    @Test
    fun `the mean describes the history, not the day being judged`() {
        val history = listOf(mins(60), mins(60), mins(60), mins(120))
        val verdict = UsageAnomalies.analyse(history + 6 * hour + 0L)
        assertNotNull(verdict)
        assertEquals(history.average().toLong(), verdict!!.meanMs)
    }

    @Test
    fun `a verdict is produced for every ordinary week without throwing`() {
        for (spike in listOf(0L, mins(1), hour, 12 * hour)) {
            UsageAnomalies.analyse(ordinaryWeek() + spike + mins(10))
        }
    }
}
