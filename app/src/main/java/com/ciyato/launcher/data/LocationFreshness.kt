package com.ciyato.launcher.data

/**
 * Whether a cached location is fresh enough to hang a weather forecast on.
 *
 * `getLastKnownLocation` returns whatever the system last recorded, with no
 * upper bound on its age. The old code took that fix whenever one existed and
 * only requested a fresh one if none did (F-030). Ranking the providers by age
 * is not the same as rejecting a stale answer: if every provider's cache is a
 * day old, the freshest of them is still a day old.
 *
 * The symptom is specific and it is the one that makes a weather feature look
 * broken: land after a flight, open Home, and see the city you left. Ciyato
 * reports a place name next to the temperature, so a stale fix is not a slightly
 * wrong number - it is confidently the wrong place.
 *
 * Accuracy is gated too, and generously. Coordinates are rounded to roughly
 * 1.1 km before leaving the device, so anything inside a few kilometres is
 * indistinguishable once coarsened; the limit exists to reject a fix that is
 * uncertain by more than a city, not to demand precision the app then throws
 * away.
 */
object LocationFreshness {

    /**
     * How old a cached fix may be and still be trusted, in milliseconds.
     *
     * Thirty minutes. Long enough that opening Home repeatedly does not wake the
     * radio each time; short enough that no ordinary journey finishes inside it.
     */
    const val MAX_AGE_MS = 30L * 60L * 1000L

    /**
     * Worst accuracy worth using, in metres.
     *
     * 50 km is deliberately loose. A cell-tower fix in a rural area is routinely
     * tens of kilometres wide and still names the right region; this rejects the
     * pathological case, not the normal coarse one.
     */
    const val MAX_ACCURACY_M = 50_000f

    /** Why a fix was refused, for logging and for tests. */
    enum class Verdict { FRESH, TOO_OLD, TOO_VAGUE, NO_TIMESTAMP }

    /**
     * @param fixTimeMs the location's own timestamp (`Location.time`)
     * @param accuracyM reported accuracy, or null when the provider gave none
     */
    fun judge(
        fixTimeMs: Long,
        nowMs: Long,
        accuracyM: Float?,
        maxAgeMs: Long = MAX_AGE_MS,
        maxAccuracyM: Float = MAX_ACCURACY_M,
    ): Verdict = when {
        // A zero or future timestamp means the provider did not really tell us
        // when this was measured. Treated as unusable rather than as "now",
        // because assuming freshness is the failure being fixed.
        fixTimeMs <= 0L || fixTimeMs > nowMs + 60_000L -> Verdict.NO_TIMESTAMP
        nowMs - fixTimeMs > maxAgeMs -> Verdict.TOO_OLD
        accuracyM != null && accuracyM > maxAccuracyM -> Verdict.TOO_VAGUE
        else -> Verdict.FRESH
    }

    fun isUsable(fixTimeMs: Long, nowMs: Long, accuracyM: Float?): Boolean =
        judge(fixTimeMs, nowMs, accuracyM) == Verdict.FRESH
}
