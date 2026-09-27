package com.ciyato.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * NetworkClient — the one shared timeout + retry helper for every outbound
 * HTTP call in the app (Open-Meteo weather/AQI, Nominatim reverse-geocode,
 * HaveIBeenPwned breach check). Every caller reuses [fetchText] instead of
 * hand-rolling its own connection/timeout/retry/close logic — that duplication
 * is exactly how earlier network paths ended up with no timeout on one call
 * and a leaked stream on another.
 *
 * Retry policy lives in [RetryPolicy], which is where it can be tested.
 * In short: connect/read timeouts, IOExceptions (DNS hiccups, reset
 * connections), 5xx and **429** get up to [maxAttempts] tries. Every other 4xx
 * fails immediately, because the request itself is wrong and retrying it burns
 * battery to receive the same answer.
 *
 * Backoff is exponential with jitter, and a server's own `Retry-After` overrides
 * it — bounded, because that header is not something this app should trust
 * without a limit.
 */
object NetworkClient {

    /**
     * Non-2xx HTTP response.
     *
     * [retryAfter] is the raw header, kept rather than parsed here so the
     * decision stays in one place ([RetryPolicy]) and so a caller that wants to
     * surface "try again in a minute" has the original to work from.
     */
    class HttpStatusException(
        val code: Int,
        message: String,
        val retryAfter: String? = null,
    ) : IOException(message)

    private const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
    private const val DEFAULT_READ_TIMEOUT_MS = 8_000

    /**
     * Fetches [urlString] as text, retrying transient failures with capped
     * exponential backoff. Always runs off the calling thread. Throws on
     * final failure — callers map that to their own honest state (offline,
     * error, or "serve the stale cache and say so"), never to a silent empty
     * result.
     */
    suspend fun fetchText(
        urlString: String,
        headers: Map<String, String> = emptyMap(),
        maxAttempts: Int = 3,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    ): String = withContext(Dispatchers.IO) {
        var lastError: IOException? = null
        var retryAfter: String? = null
        repeat(maxAttempts) { attempt ->
            retryAfter = null
            try {
                return@withContext fetchOnce(urlString, headers, connectTimeoutMs, readTimeoutMs)
            } catch (e: HttpStatusException) {
                // 429 used to land in a blanket `400..499 -> throw`, on the
                // reasoning that a client error will always be wrong the same
                // way. True of 404; false of a rate limit, which is the server
                // saying NOT YET (F-033). It matters here specifically:
                // Nominatim's usage policy is one request per second and it
                // enforces it, so a weather refresh that also reverse-geocodes
                // could report a hard failure for a condition that clears in a
                // second.
                if (!RetryPolicy.isTransient(e.code)) throw e
                lastError = e
                retryAfter = e.retryAfter
            } catch (e: IOException) {
                lastError = e // timeout / DNS / reset — worth another try
            }
            if (attempt < maxAttempts - 1) {
                // The server's own Retry-After wins over the backoff curve: it
                // knows when its window resets and we are guessing. Bounded and
                // jittered - see RetryPolicy for why both are necessary.
                delay(RetryPolicy.delayMsFor(attempt, retryAfter))
            }
        }
        throw lastError ?: IOException("Request failed after $maxAttempts attempts")
    }

    private fun fetchOnce(
        urlString: String,
        headers: Map<String, String>,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream?.use { it.bufferedReader().readText() }
                throw HttpStatusException(
                    code = code,
                    message = "HTTP $code${body?.let { ": ${it.take(200)}" } ?: ""}",
                    retryAfter = conn.getHeaderField("Retry-After"),
                )
            }
            // use{} guarantees the socket stream is closed even on a parse
            // failure downstream — otherwise every call leaks a file descriptor.
            return conn.inputStream.use { it.bufferedReader().readText() }
        } finally {
            conn.disconnect()
        }
    }
}
