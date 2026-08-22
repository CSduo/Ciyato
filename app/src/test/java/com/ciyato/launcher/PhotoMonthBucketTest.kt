package com.ciyato.launcher

import com.ciyato.launcher.data.PhotoDeviceLibrary
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Month grouping for the gallery's "memories" collections.
 *
 * These replaced a whole second photo screen, so the bucketing has to hold up on
 * its own: unusable dates, month boundaries, and year rollover are all real
 * inputs from MediaStore rather than hypotheticals.
 */
class PhotoMonthBucketTest {

    private val originalLocale = Locale.getDefault()
    private val originalZone = TimeZone.getDefault()

    @Before
    fun pinEnvironment() {
        // Bucketing is deliberately device-local, and the label is localised, so
        // both are pinned here rather than left to whatever the runner uses.
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreEnvironment() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    private fun millisOf(year: Int, month1Based: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month1Based - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test
    fun `key is zero padded and sorts lexicographically by real time order`() {
        val september = PhotoDeviceLibrary.monthKey(millisOf(2025, 9, 15))
        val october = PhotoDeviceLibrary.monthKey(millisOf(2025, 10, 15))
        assertEquals("2025-09", september)
        assertEquals("2025-10", october)
        // The padding exists so string ordering matches chronological ordering.
        assertTrue(september < october)
    }

    @Test
    fun `same month different days share a bucket`() {
        assertEquals(
            PhotoDeviceLibrary.monthKey(millisOf(2026, 8, 1)),
            PhotoDeviceLibrary.monthKey(millisOf(2026, 8, 31)),
        )
    }

    @Test
    fun `adjacent months across a year boundary do not merge`() {
        val december = PhotoDeviceLibrary.monthKey(millisOf(2025, 12, 31))
        val january = PhotoDeviceLibrary.monthKey(millisOf(2026, 1, 1))
        assertEquals("2025-12", december)
        assertEquals("2026-01", january)
        assertNotEquals(december, january)
    }

    @Test
    fun `unusable timestamps produce an empty key so they are filtered out`() {
        // MediaStore returns 0 when it has neither DATE_TAKEN nor DATE_MODIFIED.
        // Bucketing those would create a bogus "January 1970" memory.
        assertEquals("", PhotoDeviceLibrary.monthKey(0L))
        assertEquals("", PhotoDeviceLibrary.monthKey(-1L))
    }

    @Test
    fun `label reads as a month and year`() {
        assertEquals("August 2026", PhotoDeviceLibrary.monthLabel("2026-08"))
        assertEquals("January 2025", PhotoDeviceLibrary.monthLabel("2025-01"))
        assertEquals("December 2025", PhotoDeviceLibrary.monthLabel("2025-12"))
    }

    @Test
    fun `malformed keys fall back to the raw key rather than crashing or lying`() {
        assertEquals("", PhotoDeviceLibrary.monthLabel(""))
        assertEquals("nonsense", PhotoDeviceLibrary.monthLabel("nonsense"))
        assertEquals("2026-13", PhotoDeviceLibrary.monthLabel("2026-13"))
        assertEquals("2026-00", PhotoDeviceLibrary.monthLabel("2026-00"))
        assertEquals("2026", PhotoDeviceLibrary.monthLabel("2026"))
    }

    @Test
    fun `key round trips through the label`() {
        val key = PhotoDeviceLibrary.monthKey(millisOf(2026, 2, 14))
        assertEquals("2026-02", key)
        assertEquals("February 2026", PhotoDeviceLibrary.monthLabel(key))
    }
}
