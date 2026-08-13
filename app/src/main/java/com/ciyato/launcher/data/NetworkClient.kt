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
 * Retry policy: transient failures only — connect/read timeouts, IOExceptions
 * (DNS hiccups, reset connections) and 5xx server errors get up to
 * [maxAttempts] tries with exponential backoff (1s, 2s, 4s…). 4xx client
 * errors are NOT retried: the request itself is wrong (or, for this app,
 * will always be wrong the same way), so retrying just burns battery and
 * adds latency for an identical failure.
 */
object NetworkClient {

    /** Non-2xx HTTP response. [code] is what callers use to decide retry-worthiness. */
    class HttpStatusException(val code: Int, message: String) : IOException(message)

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
        repeat(maxAttempts) { attempt ->
            try {
                return@withContext fetchOnce(urlString, headers, connectTimeoutMs, readTimeoutMs)
            } catch (e: HttpStatusException) {
                if (e.code in 400..499) throw e // non-transient — fail fast, no retry
                lastError = e // 5xx — worth another try
            } catch (e: IOException) {
                lastError = e // timeout / DNS / reset — worth another try
            }
            if (attempt < maxAttempts - 1) delay(1_000L shl attempt) // 1s, 2s, 4s
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
                throw HttpStatusException(code, "HTTP $code${body?.let { ": ${it.take(200)}" } ?: ""}")
            }
            // use{} guarantees the socket stream is closed even on a parse
            // failure downstream — otherwise every call leaks a file descriptor.
            return conn.inputStream.use { it.bufferedReader().readText() }
        } finally {
            conn.disconnect()
        }
    }
}
