package com.ciyato.launcher.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The three Home density modes, in one place.
 *
 * These numbers lived inline in `HomeScreen` as a `when` over a preference
 * string, while Theme Studio's preview drew a 52dp-high sketch tile that had no
 * relationship to any of them. So the preview could not show what density does —
 * it showed a fixed-size box next to a label claiming to reflect spacing (F-069).
 *
 * That is the shape of defect the finding is about: a preview that *reimplements*
 * rather than *renders* can look right while production ignores the selected
 * value, and nothing catches it because the two have no common source.
 *
 * Now they share this. A change to the layout moves the preview with it, which is
 * the only version of "preview" that stays true without somebody remembering to
 * update two places.
 */
enum class HomeDensity(
    /** The persisted preference value. Do not change these strings. */
    val key: String,
    /** Height of a category card on Home. */
    val cardHeight: Dp,
    /** Category cards across. */
    val columns: Int,
    /** Vertical gap between Home objects. */
    val spacing: Dp,
) {
    /** The most content per screen. */
    Compact("dense", cardHeight = 108.dp, columns = 3, spacing = 8.dp),

    /** The default: three columns, more room to breathe. */
    Standard("smart", cardHeight = 126.dp, columns = 3, spacing = 12.dp),

    /** Two large cards across. */
    Spacious("spacious", cardHeight = 142.dp, columns = 2, spacing = 16.dp),
    ;

    companion object {
        /**
         * Resolves a stored preference value.
         *
         * Anything unrecognised resolves to [Standard] rather than throwing: the
         * preference is a free-form string on disk, and a Home screen that fails
         * to draw because of an unexpected value would be a far worse outcome
         * than one that draws at the default.
         */
        fun fromKey(key: String?): HomeDensity =
            entries.firstOrNull { it.key == key } ?: Standard
    }
}
