package com.ciyato.launcher

import com.ciyato.launcher.data.PdfPageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Page geometry and decode bounds for PDF export.
 *
 * The export used each bitmap's own pixel dimensions as the page size and
 * decoded every source at full resolution (F-027). A 4000x3000 phone photo is
 * about 48 MB of heap per page, and a document whose pages are each a different
 * physical size prints unpredictably. Both of those are arithmetic, so both are
 * testable, which is the point of pulling them out of the screen.
 */
class PdfPageLayoutTest {

    // -- decode bounds ---------------------------------------------------------

    @Test
    fun `a modern phone photo is sampled down instead of decoded whole`() {
        // 4000x3000 at 4 bytes per pixel is ~48 MB. Sampled, it fits the budget.
        val sample = PdfPageLayout.sampleSize(4000, 3000)
        assertTrue("expected downsampling, got $sample", sample >= 2)
        assertTrue(PdfPageLayout.sampledEdge(4000, 3000) <= PdfPageLayout.MAX_DECODE_EDGE_PX)
    }

    @Test
    fun `a source already within budget is not degraded`() {
        assertEquals(1, PdfPageLayout.sampleSize(1600, 1200))
        assertEquals(1, PdfPageLayout.sampleSize(2600, 1000))
    }

    @Test
    fun `sample size is always a power of two`() {
        for (w in listOf(800, 2601, 4000, 8000, 12000, 20000)) {
            val s = PdfPageLayout.sampleSize(w, w / 2)
            assertTrue("$s is not a power of two", s > 0 && (s and (s - 1)) == 0)
        }
    }

    @Test
    fun `sampling brings even an absurd source within budget`() {
        for (edge in listOf(5000, 10000, 30000, 100000)) {
            assertTrue(
                "edge $edge still over budget",
                PdfPageLayout.sampledEdge(edge, edge) <= PdfPageLayout.MAX_DECODE_EDGE_PX,
            )
        }
    }

    @Test
    fun `degenerate dimensions never produce a zero sample size`() {
        // inSampleSize of 0 throws inside BitmapFactory.
        assertEquals(1, PdfPageLayout.sampleSize(0, 0))
        assertEquals(1, PdfPageLayout.sampleSize(-5, -5))
        assertTrue(PdfPageLayout.sampleSize(4000, 3000, maxEdge = 0) >= 1)
    }

    // -- page placement --------------------------------------------------------

    @Test
    fun `the image stays inside the margins`() {
        val p = PdfPageLayout.placeOnPage(4000, 3000)
        val m = PdfPageLayout.MARGIN_PT
        assertTrue(p.left >= m - 0.01f)
        assertTrue(p.top >= m - 0.01f)
        assertTrue(p.left + p.width <= PdfPageLayout.A4_WIDTH_PT - m + 0.01f)
        assertTrue(p.top + p.height <= PdfPageLayout.A4_HEIGHT_PT - m + 0.01f)
    }

    @Test
    fun `aspect ratio is preserved, so nothing is stretched`() {
        val p = PdfPageLayout.placeOnPage(4000, 3000)
        assertEquals(4000f / 3000f, p.width / p.height, 0.001f)
    }

    @Test
    fun `a portrait page and a landscape page both fit`() {
        for ((w, h) in listOf(3000 to 4000, 4000 to 3000, 1000 to 1000)) {
            val p = PdfPageLayout.placeOnPage(w, h)
            assertTrue("$w x $h overflowed width", p.width <= PdfPageLayout.A4_WIDTH_PT.toFloat())
            assertTrue("$w x $h overflowed height", p.height <= PdfPageLayout.A4_HEIGHT_PT.toFloat())
        }
    }

    @Test
    fun `the image is centred in the printable area`() {
        val p = PdfPageLayout.placeOnPage(1000, 1000)
        val leftGap = p.left
        val rightGap = PdfPageLayout.A4_WIDTH_PT - (p.left + p.width)
        assertEquals(leftGap, rightGap, 0.01f)
    }

    /** A page is never cropped: white space beats losing the edge of a document. */
    @Test
    fun `a very wide source is fitted rather than cropped`() {
        val p = PdfPageLayout.placeOnPage(10000, 500)
        assertTrue(p.width <= PdfPageLayout.A4_WIDTH_PT - 2 * PdfPageLayout.MARGIN_PT + 0.01f)
        assertEquals(10000f / 500f, p.width / p.height, 0.01f)
    }

    @Test
    fun `a tiny source is enlarged only up to the printable area`() {
        val p = PdfPageLayout.placeOnPage(100, 100)
        val printable = PdfPageLayout.A4_WIDTH_PT - 2 * PdfPageLayout.MARGIN_PT
        assertTrue(p.width <= printable + 0.01f)
    }

    @Test
    fun `an unreadable source falls back to the full printable area`() {
        val p = PdfPageLayout.placeOnPage(0, 0)
        assertEquals(PdfPageLayout.MARGIN_PT.toFloat(), p.left, 0.01f)
        assertEquals(
            (PdfPageLayout.A4_WIDTH_PT - 2 * PdfPageLayout.MARGIN_PT).toFloat(),
            p.width,
            0.01f,
        )
    }

    @Test
    fun `every page gets the same size, which is the whole point`() {
        val sizes = listOf(4000 to 3000, 1024 to 768, 3000 to 4000).map {
            PdfPageLayout.placeOnPage(it.first, it.second)
        }
        // Placement differs per source; the PAGE does not. Both constants are
        // fixed, which is what the old code got wrong by deriving page size
        // from the bitmap.
        assertEquals(595, PdfPageLayout.A4_WIDTH_PT)
        assertEquals(842, PdfPageLayout.A4_HEIGHT_PT)
        assertTrue(sizes.all { it.width > 0f && it.height > 0f })
    }
}
