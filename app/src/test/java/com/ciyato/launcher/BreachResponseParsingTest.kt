package com.ciyato.launcher

import com.ciyato.launcher.ui.screens.BreachResult
import com.ciyato.launcher.ui.screens.parseBreachResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The breach check tells someone whether a password of theirs is compromised.
 * A wrong answer in either direction is harmful: a false "safe" leaves a broken
 * password in use, a false "breached" sends someone rotating credentials for
 * nothing. These pin the parsing rules that decide it.
 */
class BreachResponseParsingTest {

    private val suffix = "1E4C9B93F3F0682250B6CF8331B7EE68FD8"

    @Test
    fun `real hit returns its occurrence count`() {
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\n$suffix:3730471"
        val result = parseBreachResponse(body, suffix)
        assertEquals(BreachResult.Found(3730471), result)
    }

    @Test
    fun `absent suffix is not found`() {
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\n00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2"
        assertEquals(BreachResult.NotFound, parseBreachResponse(body, suffix))
    }

    /**
     * The reason this test exists: enabling Add-Padding made the API start
     * returning synthetic hashes with a count of zero. Before the count check
     * existed, one of those matching would have been reported as a breach.
     */
    @Test
    fun `padding entry with zero count is not a breach`() {
        val body = "$suffix:0"
        assertEquals(BreachResult.NotFound, parseBreachResponse(body, suffix))
    }

    @Test
    fun `real hit is still found among padding entries`() {
        val body = buildString {
            appendLine("A1B2C3D4E5F60718293A4B5C6D7E8F901234:0")
            appendLine("$suffix:42")
            appendLine("B1B2C3D4E5F60718293A4B5C6D7E8F901234:0")
        }
        assertEquals(BreachResult.Found(42), parseBreachResponse(body, suffix))
    }

    @Test
    fun `matching is case insensitive`() {
        val body = "${suffix.lowercase()}:9"
        assertEquals(BreachResult.Found(9), parseBreachResponse(body, suffix))
    }

    @Test
    fun `carriage returns from the wire do not break matching`() {
        // The API responds with CRLF line endings.
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n$suffix:5\r\n"
        assertEquals(BreachResult.Found(5), parseBreachResponse(body, suffix))
    }

    @Test
    fun `malformed line does not abort the scan`() {
        val body = "not-a-hash-line\n\n$suffix:7"
        assertEquals(BreachResult.Found(7), parseBreachResponse(body, suffix))
    }

    @Test
    fun `missing count is skipped rather than counted as zero breaches`() {
        val body = "$suffix\n$suffix:11"
        assertEquals(BreachResult.Found(11), parseBreachResponse(body, suffix))
    }

    @Test
    fun `empty body is not found rather than an error`() {
        assertTrue(parseBreachResponse("", suffix) is BreachResult.NotFound)
    }
}
