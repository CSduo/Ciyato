package com.ciyato.launcher.ui.theme

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide the window is, and what to do about it.
 *
 * Nine grids across the app hard-coded a column count — `Fixed(4)` for photo
 * thumbnails, `Fixed(2)` for collections, three-column rows padded out with
 * invisible spacers — and used the same number on a 320dp phone, a folded
 * foldable, a 10-inch tablet in landscape and everything between (F-109, F-168,
 * F-085).
 *
 * Both ends of that are bad in a way that shows. Four columns on a 320dp screen
 * gives roughly 68dp thumbnails with no padding left for a touch target; four
 * columns on an 840dp tablet gives 200dp thumbnails and a layout that reads as
 * placeholder art. For a product whose whole claim is a premium home screen,
 * looking wrong on a tablet is not a rounding error.
 *
 * ## Why this and not the material3 window-size-class artifact
 *
 * It would be one more dependency for one enum. `LocalConfiguration.screenWidthDp`
 * is the same number the official breakpoints are defined against, it is already
 * available in every composable, and it recomposes on rotation and on a foldable
 * being unfolded. The breakpoints below are Material 3's own.
 */
enum class CiyatoWidth {
    /** Phones in portrait, and a folded foldable. Under 600dp. */
    COMPACT,

    /** Large phones in landscape, small tablets, a half-open foldable. 600–839dp. */
    MEDIUM,

    /** Tablets, desktop windows, an unfolded foldable in landscape. 840dp and up. */
    EXPANDED,
}

@Composable
@ReadOnlyComposable
fun currentWidth(): CiyatoWidth {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> CiyatoWidth.COMPACT
        widthDp < 840 -> CiyatoWidth.MEDIUM
        else -> CiyatoWidth.EXPANDED
    }
}

/**
 * A grid sized by how much room a tile needs, not by a column count.
 *
 * `Adaptive` fits as many columns of at least [minTile] as the width allows, so
 * the same declaration gives three columns on a small phone and seven on a
 * tablet — and, critically, never gives a tile smaller than [minTile]. That
 * lower bound is the accessibility half of the fix: a column count cannot
 * promise a touch target, and a fixed one on a narrow screen silently breaks it.
 *
 * @param minTile the smallest a tile may be. Keep it at or above 48dp for
 *   anything tappable; a thumbnail whose *image* is smaller still needs its row
 *   to be reachable.
 */
fun adaptiveGrid(minTile: Dp): GridCells = GridCells.Adaptive(minTile)

/** The staggered equivalent, for the notes grid. */
fun adaptiveStaggeredGrid(minTile: Dp): StaggeredGridCells = StaggeredGridCells.Adaptive(minTile)

/**
 * Minimum tile sizes, named for what they hold.
 *
 * Collected here so the same kind of content is the same size everywhere, which
 * is the part a per-screen column count could never give.
 */
object TileSize {
    /** A photo or video thumbnail. Four across a 360dp phone, more as it grows. */
    val PhotoThumb = 84.dp

    /** A collection or album card, which carries a title and a count. */
    val CollectionCard = 150.dp

    /** An app icon with its label underneath. */
    val AppTile = 80.dp

    /** A dashboard or category card with an icon, a title and a subtitle. */
    val CategoryCard = 156.dp

    /** A sticky note, which is text-first and wants room to breathe. */
    val Note = 168.dp

    /** A wallpaper or theme preview, where aspect ratio matters more than size. */
    val Preview = 104.dp
}

/**
 * Caps content width on large screens.
 *
 * A single column of cards stretched across 1200dp is not a premium layout, it is
 * an unconstrained one — line lengths stop being readable and a list looks like a
 * spreadsheet. Grids handle width by adding columns; single-column content has to
 * be told to stop.
 *
 * Deliberately a no-op on compact and medium widths: a phone has no width to
 * waste, and centring content there would just add margins nobody asked for.
 */
@Composable
fun Modifier.readableWidth(max: Dp = 720.dp): Modifier =
    if (currentWidth() == CiyatoWidth.EXPANDED) this.widthIn(max = max) else this
