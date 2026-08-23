package com.ciyato.launcher.data

import kotlin.math.max
import kotlin.math.min

/**
 * How a source image is placed onto a fixed PDF page.
 *
 * The export used the bitmap's own pixel dimensions as the page size and drew it
 * at 0,0 unscaled (F-027). Two consequences, both real: a modern phone photo is
 * roughly 4000x3000, so every page was a different physical size and printed or
 * displayed unpredictably; and each source was decoded at full resolution, which
 * is about 48 MB of heap for a single page before anything is drawn.
 *
 * A document has pages of one size. This computes that placement, and the sample
 * factor that keeps the decode bounded, as ordinary arithmetic - so the part that
 * decides memory use and page geometry can be tested without a device.
 */
object PdfPageLayout {

    /** A4 at 72 dpi, the unit android.graphics.pdf.PdfDocument works in. */
    const val A4_WIDTH_PT = 595
    const val A4_HEIGHT_PT = 842

    /** Uniform margin, in points. Roughly 12 mm. */
    const val MARGIN_PT = 34

    /**
     * Cap on the longest edge of a decoded source, in pixels.
     *
     * Chosen against the page, not the camera: an A4 page at 300 dpi is about
     * 2480x3508, so 2600 keeps print quality while bounding a decode to roughly
     * 27 MB instead of whatever the phone's sensor happens to produce.
     */
    const val MAX_DECODE_EDGE_PX = 2600

    /** Where a source of [srcWidth] x [srcHeight] lands on the page. */
    data class Placement(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    )

    /**
     * Fits the source inside the page's margins, preserving aspect ratio and
     * centring it. Never enlarges beyond the printable area, and never crops -
     * a scanned page that loses its edges is worse than one with white space.
     */
    fun placeOnPage(
        srcWidth: Int,
        srcHeight: Int,
        pageWidth: Int = A4_WIDTH_PT,
        pageHeight: Int = A4_HEIGHT_PT,
        margin: Int = MARGIN_PT,
    ): Placement {
        val printableW = (pageWidth - 2 * margin).coerceAtLeast(1)
        val printableH = (pageHeight - 2 * margin).coerceAtLeast(1)
        if (srcWidth <= 0 || srcHeight <= 0) {
            return Placement(margin.toFloat(), margin.toFloat(), printableW.toFloat(), printableH.toFloat())
        }
        val scale = min(printableW.toFloat() / srcWidth, printableH.toFloat() / srcHeight)
        val w = srcWidth * scale
        val h = srcHeight * scale
        return Placement(
            left = margin + (printableW - w) / 2f,
            top = margin + (printableH - h) / 2f,
            width = w,
            height = h,
        )
    }

    /**
     * The `inSampleSize` for decoding a source of [srcWidth] x [srcHeight].
     *
     * BitmapFactory only honours powers of two, so this returns one - the
     * smallest that brings the longest edge within [maxEdge]. Returns 1 for
     * anything already small enough, and never returns 0, which would throw.
     */
    fun sampleSize(srcWidth: Int, srcHeight: Int, maxEdge: Int = MAX_DECODE_EDGE_PX): Int {
        val longest = max(srcWidth, srcHeight)
        if (longest <= 0 || maxEdge <= 0) return 1
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        // The loop stops one step early for values just above the cap, so take
        // one more halving when the result is still over.
        if (longest / sample > maxEdge) sample *= 2
        return sample
    }

    /** Longest edge after sampling, for reporting and for tests. */
    fun sampledEdge(srcWidth: Int, srcHeight: Int, maxEdge: Int = MAX_DECODE_EDGE_PX): Int =
        max(srcWidth, srcHeight) / sampleSize(srcWidth, srcHeight, maxEdge)
}
