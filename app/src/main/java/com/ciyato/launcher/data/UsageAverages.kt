package com.ciyato.launcher.data

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Turns raw daily usage buckets into a per-app daily baseline.
 *
 * Extracted from the screen because this is arithmetic that decides what the
 * user is told, and it was wrong twice in the same expression. First
 * `associate {}` kept one arbitrary bucket per package and threw the rest away,
 * so a "weekly average" was one day's foreground time divided by seven (F-124).
 * Summing fixed that, but left `/ 7f` — which assumes the phone actually holds
 * seven days of history. A device three days past a factory reset has three, so
 * every average came out 2.3x too low and today looked like a surge against it.
 *
 * The divisor is now the number of days actually observed. Both mistakes shared
 * a cause: a denominator that was assumed rather than measured.
 */
object UsageAverages {

    /** One UsageStats daily bucket, reduced to the three fields that matter. */
    data class Bucket(
        val packageName: String,
        val foregroundMs: Long,
        /** Any timestamp inside the day this bucket covers. */
        val timestampMs: Long,
    )

    data class Baseline(
        /** Mean foreground ms per observed day, per package. */
        val averageMsPerDay: Map<String, Float>,
        /** Distinct days the data actually covers — the real denominator. */
        val daysCovered: Int,
    ) {
        /**
         * Whether the window holds enough days to call a single day unusual.
         *
         * Two days of history cannot establish what is normal. Comparing against
         * it produces confident percentages with nothing behind them, which is
         * the failure mode this whole finding is about.
         */
        val isComparable: Boolean get() = daysCovered >= MIN_DAYS_FOR_COMPARISON

        /** "your weekly average" only when it is actually a week. */
        fun periodLabel(): String = when {
            daysCovered >= 7 -> "weekly average"
            daysCovered == 1 -> "average from the single day recorded"
            else -> "average over the last $daysCovered days"
        }
    }

    /** Below this, there is no baseline worth comparing against. */
    const val MIN_DAYS_FOR_COMPARISON = 3

    /**
     * @param maxDays caps the divisor at the width of the query window, so
     *   duplicate or overlapping buckets cannot inflate the day count.
     */
    fun baseline(buckets: List<Bucket>, maxDays: Int = 7): Baseline {
        if (buckets.isEmpty()) return Baseline(emptyMap(), 0)

        val totals = HashMap<String, Long>()
        val days = HashSet<Long>()
        for (b in buckets) {
            // A package with no foreground time still proves the day was
            // observed, so the day is counted before the total is filtered.
            days.add(dayIndex(b.timestampMs))
            if (b.foregroundMs > 0L) {
                totals[b.packageName] = (totals[b.packageName] ?: 0L) + b.foregroundMs
            }
        }
        val covered = days.size.coerceIn(1, maxDays)
        return Baseline(totals.mapValues { (_, total) -> total / covered.toFloat() }, covered)
    }

    /**
     * Today measured against the baseline, or null when there is nothing to
     * measure against. Null rather than a sentinel like 0f or 1f: those are
     * legitimate ratios, and callers were treating "no baseline" as "used it
     * exactly as much as usual".
     */
    fun ratio(todayMs: Long, averageMsPerDay: Float?): Float? {
        if (averageMsPerDay == null || averageMsPerDay <= 0f) return null
        return todayMs / averageMsPerDay
    }

    /** Local-midnight bucket index, so two stamps on the same day collapse. */
    private fun dayIndex(timestampMs: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / TimeUnit.DAYS.toMillis(1)
    }
}
