package com.ciyato.launcher.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Deciding whether a day's app usage is genuinely unusual.
 *
 * The old version compared `times.last()` — the day in progress — against the
 * mean of every earlier bucket, which are complete days (F-128). At nine in the
 * morning today holds two hours of usage and the history holds full days, so the
 * z-score is large and negative for almost everything: the screen reported a
 * wave of "unusual drops" every morning that were nothing but the clock.
 *
 * A partial day is not comparable to a complete one. The most recent *finished*
 * day is analysed instead, against the days before it.
 *
 * Pulled out of the screen so the arithmetic can be tested at an arbitrary hour
 * rather than only at whatever time the test happens to run — the same reason
 * the bedtime rule moved.
 */
object UsageAnomalies {

    /**
     * How many complete days are needed before calling anything unusual.
     *
     * A standard deviation from two points is arithmetic, not evidence.
     */
    const val MIN_HISTORY_DAYS = 3

    /** Below this spread, "unusual" is noise: the app is used about the same daily. */
    const val MIN_STD_MS = 60_000.0

    /** Conventional two-sigma threshold. */
    const val Z_THRESHOLD = 2.0f

    data class Verdict(
        val dayMs: Long,
        val meanMs: Long,
        val zScore: Float,
        val isUnusual: Boolean,
    ) {
        val isIncrease: Boolean get() = zScore > 0f
    }

    /**
     * Analyses [dailyMs], oldest first, where the LAST entry is the day still in
     * progress.
     *
     * @return null when there is not enough complete history, or when the app's
     *   usage is too flat for a deviation to mean anything. Null is "no verdict",
     *   never "nothing unusual" — the caller must not collapse the two.
     */
    fun analyse(dailyMs: List<Long>, dropPartialDay: Boolean = true): Verdict? {
        // The day in progress is discarded, not compared.
        val complete = if (dropPartialDay && dailyMs.isNotEmpty()) dailyMs.dropLast(1) else dailyMs
        if (complete.size < MIN_HISTORY_DAYS + 1) return null

        val subject = complete.last()
        val history = complete.dropLast(1)
        val mean = history.average()
        val std = sqrt(history.map { (it - mean) * (it - mean) }.average())
        if (std < MIN_STD_MS) return null

        val z = ((subject - mean) / std).toFloat()
        return Verdict(
            dayMs = subject,
            meanMs = mean.toLong(),
            zScore = z,
            isUnusual = abs(z) >= Z_THRESHOLD,
        )
    }

    /**
     * What the screen is showing, so a failure cannot be rendered as good news.
     *
     * A permission problem, a provider error and a genuinely quiet week all
     * produced the same empty list, and the empty list rendered as "Your usage
     * patterns look consistent" — a failed analysis presented as a healthy
     * conclusion (F-129).
     */
    sealed interface Outcome {
        /** The analysis ran. [anomalies] may legitimately be empty. */
        data class Analysed<T>(val anomalies: List<T>, val appsExamined: Int) : Outcome

        /** The analysis could not run. Never render this as "all consistent". */
        data class Unavailable(val reason: String) : Outcome

        /** It ran, but no app had enough complete days to judge. */
        data class NotEnoughHistory(val appsExamined: Int) : Outcome
    }
}
