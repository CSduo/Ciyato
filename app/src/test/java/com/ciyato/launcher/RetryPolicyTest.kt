package com.ciyato.launcher

import com.ciyato.launcher.data.RetryPolicy
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a failed request is worth repeating, and how long to wait.
 *
 * 429 used to land in a blanket `400..499 -> throw`, on the reasoning that a
 * client error "will always be wrong the same way" (F-033). That is true of 404
 * and false of a rate limit, which is the server saying *not yet* — the most
 * transient answer there is.
 *
 * It mattered for a specific reason. Nominatim's usage policy is one request per
 * second and it enforces it, so a weather refresh that also reverse-geocodes
 * could take a 429 and report a hard failure for something that clears in a
 * second. And because Ciyato fires weather and air quality at the same host
 * concurrently, an unjittered backoff would have had both retry in the same
 * instant — recreating the burst that caused the limit.
 */
class RetryPolicyTest {

    private val now: Instant = Instant.parse("2026-09-27T12:00:00Z")

    // ── What is worth repeating ──────────────────────────────────────────────

    @Test
    fun `a rate limit is transient`() {
        assertTrue("429 is the server saying not yet", RetryPolicy.isTransient(429))
    }

    @Test
    fun `server errors are transient`() {
        listOf(500, 502, 503, 504, 599).forEach {
            assertTrue("$it should be retried", RetryPolicy.isTransient(it))
        }
    }

    @Test
    fun `an ordinary client error is not`() {
        // Retrying these burns battery to receive the same answer. 400 and 404
        // are the two Open-Meteo returns for a malformed query.
        listOf(400, 401, 403, 404, 410, 422).forEach {
            assertFalse("$it must not be retried", RetryPolicy.isTransient(it))
        }
    }

    // ── Retry-After: seconds ─────────────────────────────────────────────────

    @Test
    fun `a delay in seconds is honoured`() {
        assertEquals(5_000L, RetryPolicy.parseRetryAfterMs("5", now))
        assertEquals(0L, RetryPolicy.parseRetryAfterMs("0", now))
    }

    @Test
    fun `an absurd delay is capped rather than obeyed`() {
        // A misconfigured proxy answering `Retry-After: 86400` would park a
        // coroutine for a day, holding whatever the caller is holding. The
        // header is not something this app should trust without a limit.
        assertEquals(RetryPolicy.MAX_RETRY_AFTER_MS, RetryPolicy.parseRetryAfterMs("86400", now))
    }

    @Test
    fun `a negative delay is not a delay`() {
        // Null, not zero: the caller can then fall back to its own backoff
        // instead of hammering immediately on a nonsense header.
        assertNull(RetryPolicy.parseRetryAfterMs("-1", now))
    }

    // ── Retry-After: HTTP-date ───────────────────────────────────────────────

    @Test
    fun `an HTTP-date is honoured`() {
        // RFC 9110 allows both forms and both appear in the wild. Handling only
        // seconds would silently ignore the other half and fall back to a guess.
        val target = now.plusSeconds(8)
        val header = DateTimeFormatter.RFC_1123_DATE_TIME
            .format(target.atZone(ZoneOffset.UTC))
        assertEquals(8_000L, RetryPolicy.parseRetryAfterMs(header, now))
    }

    @Test
    fun `a date already past means wait no longer`() {
        val header = DateTimeFormatter.RFC_1123_DATE_TIME
            .format(now.minusSeconds(60).atZone(ZoneOffset.UTC))
        // Zero, never negative - a negative delay would become an immediate
        // retry in one place and an exception in another.
        assertEquals(0L, RetryPolicy.parseRetryAfterMs(header, now))
    }

    @Test
    fun `a far-future date is capped like a large seconds value`() {
        val header = DateTimeFormatter.RFC_1123_DATE_TIME
            .format(now.plusSeconds(86_400).atZone(ZoneOffset.UTC))
        assertEquals(RetryPolicy.MAX_RETRY_AFTER_MS, RetryPolicy.parseRetryAfterMs(header, now))
    }

    @Test
    fun `an unusable header is ignored, not guessed at`() {
        assertNull(RetryPolicy.parseRetryAfterMs(null, now))
        assertNull(RetryPolicy.parseRetryAfterMs("", now))
        assertNull(RetryPolicy.parseRetryAfterMs("   ", now))
        assertNull(RetryPolicy.parseRetryAfterMs("soon", now))
        assertNull(RetryPolicy.parseRetryAfterMs("Tomorrow at noon", now))
    }

    // ── The delay that actually gets used ────────────────────────────────────

    @Test
    fun `backoff doubles per attempt`() {
        assertEquals(1_000L, RetryPolicy.delayMsFor(0, jitter = 0.0))
        assertEquals(2_000L, RetryPolicy.delayMsFor(1, jitter = 0.0))
        assertEquals(4_000L, RetryPolicy.delayMsFor(2, jitter = 0.0))
    }

    @Test
    fun `the server's instruction beats our guess`() {
        // It knows when its window resets; we are guessing.
        assertEquals(7_000L, RetryPolicy.delayMsFor(0, retryAfterHeader = "7", now = now, jitter = 0.0))
        // Even when the guess would have been longer.
        assertEquals(2_000L, RetryPolicy.delayMsFor(5, retryAfterHeader = "2", now = now, jitter = 0.0))
    }

    @Test
    fun `jitter only ever adds`() {
        // Subtracting could retry BEFORE a stated Retry-After window elapsed,
        // which earns a second 429 - the opposite of the point.
        val base = RetryPolicy.delayMsFor(1, jitter = 0.0)
        val jittered = RetryPolicy.delayMsFor(1, jitter = 1.0)
        assertTrue("jitter must not shorten the wait", jittered >= base)
        assertEquals(base + (base * RetryPolicy.JITTER_FRACTION).toLong(), jittered)
    }

    @Test
    fun `jitter is bounded to a quarter of the delay`() {
        // Enough to decorrelate two concurrent callers against the same host,
        // little enough that the wait stays predictable.
        val base = RetryPolicy.delayMsFor(2, jitter = 0.0)
        listOf(0.0, 0.3, 0.7, 1.0, 5.0, -5.0).forEach { j ->
            val delay = RetryPolicy.delayMsFor(2, jitter = j)
            assertTrue("jitter $j produced $delay", delay in base..(base + base / 4))
        }
    }

    @Test
    fun `a high attempt number cannot overflow into a negative wait`() {
        // `1000L shl 64` is 1000 again, and `shl` on a large attempt index is
        // exactly the kind of arithmetic that produces a negative delay and an
        // IllegalArgumentException from delay() at the call site.
        listOf(10, 31, 32, 64, 1000, Int.MAX_VALUE).forEach { attempt ->
            val delay = RetryPolicy.delayMsFor(attempt, jitter = 0.0)
            assertTrue("attempt $attempt produced $delay", delay > 0)
            assertTrue("attempt $attempt produced $delay", delay <= RetryPolicy.MAX_RETRY_AFTER_MS * 2)
        }
    }

    @Test
    fun `a negative attempt number is treated as the first`() {
        assertEquals(1_000L, RetryPolicy.delayMsFor(-1, jitter = 0.0))
    }
}
