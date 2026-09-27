package com.ciyato.launcher.data

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * When to try again, and how long to wait.
 *
 * Separated from [NetworkClient] because it is the part with the interesting
 * decisions and none of the I/O — so it can actually be tested. Every case below
 * is a real response one of Ciyato's four hosts returns.
 *
 * The defect this fixes: 429 fell into the `400..499` branch and was thrown
 * immediately with no retry, on the reasoning that a client error "will always be
 * wrong the same way" (F-033). That is true of 404 and false of 429 — a rate
 * limit is the server saying *not yet*, which is the most transient answer there
 * is. It matters here specifically: Nominatim's usage policy is one request per
 * second, and it enforces it, so a weather refresh that also reverse-geocodes
 * could hit 429 and report a hard failure for a condition that resolves in a
 * second.
 */
object RetryPolicy {

    /** Base backoff, doubled per attempt: 1s, 2s, 4s. */
    const val BASE_DELAY_MS = 1_000L

    /**
     * The longest this app will ever wait on a server's instruction.
     *
     * `Retry-After` is attacker- and accident-controllable: a misconfigured proxy
     * can answer `Retry-After: 86400`, and honouring that literally would park a
     * coroutine for a day holding whatever the caller is holding. Capped at
     * thirty seconds, which is longer than any user waits and shorter than
     * anything that looks like a hang.
     */
    const val MAX_RETRY_AFTER_MS = 30_000L

    /**
     * Jitter, as a fraction of the computed delay.
     *
     * Ciyato refreshes weather and air quality concurrently against the same
     * host. Without jitter a rate limit makes both back off by exactly the same
     * amount and retry in the same instant, which is the burst that caused the
     * limit. A spread of up to 25% is enough to decorrelate two callers.
     */
    const val JITTER_FRACTION = 0.25

    /**
     * Whether another attempt could plausibly succeed.
     *
     * 429 is transient: the server is saying not yet. 5xx is transient: the
     * server is broken and might not be in a second. Everything else in 4xx is
     * the request being wrong, and retrying it burns battery to receive the same
     * answer.
     */
    fun isTransient(statusCode: Int): Boolean = when {
        statusCode == 429 -> true
        statusCode in 500..599 -> true
        statusCode in 400..499 -> false
        // A 1xx or 3xx reaching here means the connection did something
        // unexpected rather than something wrong; one more attempt is cheap.
        else -> true
    }

    /**
     * How long to wait before attempt number [attempt] + 1.
     *
     * A server's own `Retry-After` wins over the backoff curve, because it knows
     * when its window resets and we are guessing. Jitter is applied either way.
     *
     * @param attempt zero-based index of the attempt that just failed.
     * @param retryAfterHeader raw header value, if the response carried one.
     * @param now reference instant for an HTTP-date header. Injected so this is
     *   testable; a date-form Retry-After is meaningless without a clock.
     * @param jitter value in 0f..1f, normally random. Injected for the same reason.
     */
    fun delayMsFor(
        attempt: Int,
        retryAfterHeader: String? = null,
        now: Instant = Instant.now(),
        jitter: Double = Math.random(),
    ): Long {
        val fromServer = parseRetryAfterMs(retryAfterHeader, now)
        val base = fromServer ?: (BASE_DELAY_MS shl attempt.coerceIn(0, 10))
        // Jitter only ever ADDS. Subtracting could retry before a stated
        // Retry-After window had elapsed, which earns a second 429.
        val spread = (base * JITTER_FRACTION * jitter.coerceIn(0.0, 1.0)).toLong()
        return (base + spread).coerceAtMost(MAX_RETRY_AFTER_MS + (MAX_RETRY_AFTER_MS / 4))
    }

    /**
     * `Retry-After` in milliseconds, or null when there is nothing usable.
     *
     * RFC 9110 allows two forms and both appear in the wild: a delay in seconds,
     * and an HTTP-date. Handling only the first would silently ignore the second
     * and fall back to the guess.
     *
     * Null rather than zero for anything unparseable, so the caller can tell
     * "no instruction" from "retry immediately" — a server CAN legitimately say
     * `Retry-After: 0`.
     */
    fun parseRetryAfterMs(header: String?, now: Instant = Instant.now()): Long? {
        val raw = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        // Delay-seconds form.
        raw.toLongOrNull()?.let { seconds ->
            if (seconds < 0) return null
            return (seconds * 1_000L).coerceAtMost(MAX_RETRY_AFTER_MS)
        }

        // HTTP-date form. A date already in the past means the window has
        // already elapsed, which is zero rather than negative.
        return try {
            val target = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            val millis = target.toEpochMilli() - now.toEpochMilli()
            millis.coerceIn(0L, MAX_RETRY_AFTER_MS)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
