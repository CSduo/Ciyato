package com.ciyato.launcher

import com.ciyato.launcher.data.ByteFormat
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A size on a storage screen is read at a glance, so it never shows four digits.
 *
 * The formatter switched units at exactly 1024, which printed "1010.8 MB" for
 * anything from 1000 to 1023 MB. Arithmetically correct and practically unreadable -
 * found on a real phone, where a Photos tile said "1010.8 MB" beside one saying
 * "1.3 GB" and the smaller-looking number was nearly as big.
 */
class ByteFormatTest {

    private lateinit var saved: Locale
    private val kb = 1024L
    private val mb = kb * 1024
    private val gb = mb * 1024

    @Before fun pinLocale() { saved = Locale.getDefault(); Locale.setDefault(Locale.US) }
    @After fun restoreLocale() { Locale.setDefault(saved) }

    @Test
    fun `the value that was found on the device now rolls over`() {
        // 1010.8 MB, exactly as the Photos tile showed it.
        assertEquals("1.0 GB", ByteFormat.format((1010.8 * mb).toLong()))
    }

    @Test
    fun `megabytes stop before four digits`() {
        assertEquals("999.9 MB", ByteFormat.format((999.9 * mb).toLong()))
        // 999.96 MB would round to "1000.0 MB" under %.1f, so it must already be GB.
        assertEquals("1.0 GB", ByteFormat.format((999.96 * mb).toLong()))
        assertEquals("1.0 GB", ByteFormat.format(1000 * mb))
        assertEquals("1.0 GB", ByteFormat.format(1024 * mb))
    }

    @Test
    fun `kilobytes stop before four digits`() {
        assertEquals("999 KB", ByteFormat.format(999 * kb))
        // 999.6 KB would round to "1000 KB" under %.0f.
        assertEquals("1.0 MB", ByteFormat.format((999.6 * kb).toLong()))
    }

    @Test
    fun `ordinary sizes are unchanged`() {
        assertEquals("227.2 MB", ByteFormat.format((227.2 * mb).toLong()))
        assertEquals("1.3 GB", ByteFormat.format((1.3 * gb).toLong()))
        assertEquals("44 KB", ByteFormat.format(44 * kb))
    }

    @Test
    fun `compact drops only the space`() {
        assertEquals("1.0GB", ByteFormat.format((1010.8 * mb).toLong(), compact = true))
    }
}
