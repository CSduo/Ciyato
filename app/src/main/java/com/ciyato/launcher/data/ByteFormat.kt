package com.ciyato.launcher.data

import java.util.Locale

/**
 * The one way Ciyato renders a byte count.
 *
 * There were four implementations, and they disagreed. The same 1,610,612,736
 * bytes rendered as "1.5 GB" in Storage Cleanup, "1.50 GB" in the Files scope
 * picker, and "1.5GB" in duplicate cleanup — one used Locale.US regardless of
 * the phone's language, the others used the default implicitly. Nothing was
 * broken enough to notice in one screen; it was only wrong across screens, which
 * is how a design system erodes.
 *
 * Locale is explicit and is the *default* locale on purpose. These are numbers
 * people read, and a phone set to German should show "1,5 GB" — the decimal
 * separator is part of reading a number correctly, not a detail. Locale.ROOT
 * would be right for a filename or a log line, and neither is what this is for.
 */
object ByteFormat {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    /**
     * @param compact drops the space before the unit ("1.5GB"), for dense UI
     *   where the label competes with a thumbnail for width.
     * @param zeroPlaceholder returned for a count of zero or less, so a caller
     *   showing "—" for "nothing here" does not have to special-case it.
     */
    fun format(
        bytes: Long,
        compact: Boolean = false,
        zeroPlaceholder: String? = null,
    ): String {
        if (bytes <= 0L && zeroPlaceholder != null) return zeroPlaceholder
        val gap = if (compact) "" else " "
        val l = Locale.getDefault()
        return when {
            // Roll over to the next unit before the number reaches four digits.
            // Switching at exactly 1024 MB printed "1010.8 MB" for anything between
            // 1000 and 1023 MB - correct, and unreadable at a glance, which on a
            // storage screen is the only way anyone reads it. 999.95 rather than 1000
            // because %.1f would round 999.96 up to "1000.0".
            bytes >= MB * 999.95 -> String.format(l, "%.1f%sGB", bytes / GB, gap)
            bytes >= KB * 999.5 -> String.format(l, "%.1f%sMB", bytes / MB, gap)
            bytes >= KB -> String.format(l, "%.0f%sKB", bytes / KB, gap)
            else -> "$bytes${gap}B"
        }
    }
}
