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
            bytes >= GB -> String.format(l, "%.1f%sGB", bytes / GB, gap)
            bytes >= MB -> String.format(l, "%.1f%sMB", bytes / MB, gap)
            bytes >= KB -> String.format(l, "%.0f%sKB", bytes / KB, gap)
            else -> "$bytes${gap}B"
        }
    }
}
