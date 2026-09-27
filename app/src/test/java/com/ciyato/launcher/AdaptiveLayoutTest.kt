package com.ciyato.launcher

import com.ciyato.launcher.ui.theme.TileSize
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No core screen may go back to one fixed column count for every device.
 *
 * Nine grids hard-coded a column count and used the same number on a 320dp phone,
 * a folded foldable, and a 10-inch tablet in landscape (F-109, F-168). Both ends
 * of that are visibly wrong: four columns at 320dp gives roughly 68dp thumbnails
 * with nothing left for a touch target, and four columns at 840dp gives 200dp
 * thumbnails and a layout that reads as placeholder art.
 *
 * `Fixed` is not banned outright, because there is one legitimate use of it in
 * this app — the Home workspace grid, where the column count is a **persisted
 * layout decision** the person made in Theme Studio, and where a cell index means
 * a specific row and column in saved data. Changing that with the window would
 * move someone's icons. So the rule is per file, and every exemption has to say
 * why.
 */
class AdaptiveLayoutTest {

    /**
     * Files allowed to use a fixed column count, with the reason.
     *
     * Short on purpose. A list that grows is the responsive layout being repealed
     * one screen at a time.
     */
    private val allowed = mapOf(
        "ui/components/DragDropAppGrid.kt" to
            "column count comes from the persisted grid-size setting; a cell index " +
            "resolves against it, so changing it with the window would move saved icons",
    )

    @Test
    fun `no screen hard-codes a column count`() {
        val root = File("src/main/java/com/ciyato/launcher")
        assertTrue("main sources not found at ${root.absolutePath}", root.isDirectory)

        val fixed = Regex("""(?:Staggered)?GridCells\.Fixed\(""")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val relative = file.path.replace('\\', '/').substringAfter("com/ciyato/launcher/")
                if (relative in allowed) return@mapNotNull null
                val hits = fixed.findAll(file.readText()).count()
                if (hits > 0) "$relative ($hits)" else null
            }
            .sorted()
            .toList()

        assertTrue(
            "these use a fixed column count: $offenders. Use adaptiveGrid(TileSize.X) so " +
                "the layout follows the window, or add the file to this test's allowed map " +
                "with a reason that survives being read aloud.",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the exemption list stays short and stays true`() {
        assertTrue("the fixed-grid exemption list has grown: ${allowed.keys}", allowed.size <= 1)
        allowed.forEach { (path, reason) ->
            assertTrue(
                "$path is exempt but no longer exists",
                File("src/main/java/com/ciyato/launcher/$path").exists(),
            )
            assertTrue("$path is exempt with no real reason", reason.length > 40)
        }
    }

    @Test
    fun `no screen pads a row out with invisible spacers`() {
        // Seven dashboard tiles in fixed three-column rows left a final row with
        // one card and two empty weights, which reads as missing content rather
        // than hierarchy (F-085). The audit is explicit: do not insert invisible
        // placeholders to fake alignment.
        //
        // The wallpaper picker is the one place this survives, and it is doing
        // something different - keeping fixed-ratio previews at a consistent size
        // in a short static list rather than making a dynamic list look complete.
        val root = File("src/main/java/com/ciyato/launcher")
        val padding = Regex("""repeat\(\d+ - row\w*\.size\)""")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.path.replace('\\', '/').substringAfter("com/ciyato/launcher/") to it.readText() }
            .filter { (_, text) -> padding.containsMatchIn(text) }
            .map { it.first }
            // Two legitimate uses, both keeping tiles a CONSISTENT size in a short
            // final row rather than making a dynamic list look complete. Both now
            // pad against a column count that follows the window, so neither is
            // hard-coding phone density any more.
            .filterNot {
                it == "ui/screens/WallpaperPickerScreen.kt" ||
                    it == "ui/screens/CategoryDetailScreen.kt"
            }
            .sorted()
            .toList()

        assertTrue("these pad rows with invisible spacers: $offenders", offenders.isEmpty())
    }

    // ── The sizes themselves ─────────────────────────────────────────────────

    @Test
    fun `every tile minimum leaves room for a touch target`() {
        // The accessibility half of the fix. A column count cannot promise a
        // touch target; a minimum size can. 48dp is the platform guidance, and a
        // thumbnail's row has to be reachable even when the image inside is
        // smaller.
        listOf(
            "PhotoThumb" to TileSize.PhotoThumb,
            "CollectionCard" to TileSize.CollectionCard,
            "AppTile" to TileSize.AppTile,
            "CategoryCard" to TileSize.CategoryCard,
            "Note" to TileSize.Note,
            "Preview" to TileSize.Preview,
        ).forEach { (name, size) ->
            assertTrue("$name is $size, below the 48dp touch target", size.value >= 48f)
        }
    }

    @Test
    fun `a thumbnail minimum still gives at least three columns on the narrowest phone`() {
        // The lower bound has to be a floor, not a cap: if PhotoThumb grew to
        // 120dp, a 320dp phone would get two enormous thumbnails and the gallery
        // would stop being a gallery.
        val narrowestPhoneDp = 320
        val columns = narrowestPhoneDp / TileSize.PhotoThumb.value.toInt()
        assertTrue("PhotoThumb gives only $columns columns at ${narrowestPhoneDp}dp", columns >= 3)
    }

    @Test
    fun `content-bearing tiles are larger than icon tiles`() {
        // A card with a title and a count needs more room than an icon with a
        // label. If these ever invert, something has been tuned by eye on one
        // device.
        assertTrue(TileSize.CollectionCard > TileSize.AppTile)
        assertTrue(TileSize.CategoryCard > TileSize.AppTile)
        assertTrue(TileSize.Note >= TileSize.CategoryCard)
    }
}
