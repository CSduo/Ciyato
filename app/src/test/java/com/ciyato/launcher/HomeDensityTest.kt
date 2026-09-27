package com.ciyato.launcher

import com.ciyato.launcher.ui.theme.HomeDensity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home and its preview describe the same layout.
 *
 * Theme Studio's preview drew a fixed 52dp sketch tile regardless of the selected
 * density, under a caption claiming to reflect spacing (F-069). Home's real card
 * heights — 108, 126, 142dp — lived in an inline `when` over a preference string
 * that the preview had never heard of. So the two could not agree, and the preview
 * showed the same picture whichever density you picked.
 *
 * That is the defect the finding describes: a preview that *reimplements* instead
 * of *rendering* can look right while production ignores the setting, and nothing
 * catches it, because the two share no source.
 *
 * They share one now. These tests hold it there.
 */
class HomeDensityTest {

    // ── The stored contract ──────────────────────────────────────────────────

    @Test
    fun `the persisted keys are the ones already on disk`() {
        // These strings are in the preference store on real devices. Changing one
        // silently resets that person's Home to the default, which is the kind of
        // regression nobody notices in review and everybody notices on their phone.
        assertEquals("dense", HomeDensity.Compact.key)
        assertEquals("smart", HomeDensity.Standard.key)
        assertEquals("spacious", HomeDensity.Spacious.key)
    }

    @Test
    fun `an unknown stored value resolves to the default rather than throwing`() {
        // The preference is a free-form string on disk. A Home screen that fails to
        // draw because of an unexpected value would be far worse than one that
        // draws at the default.
        assertEquals(HomeDensity.Standard, HomeDensity.fromKey("something-else"))
        assertEquals(HomeDensity.Standard, HomeDensity.fromKey(null))
        assertEquals(HomeDensity.Standard, HomeDensity.fromKey(""))
    }

    @Test
    fun `every key round-trips`() {
        HomeDensity.entries.forEach { density ->
            assertEquals(density, HomeDensity.fromKey(density.key))
        }
    }

    // ── The values mean what their names say ─────────────────────────────────

    @Test
    fun `compact really is more compact`() {
        // If these ever invert, the setting is lying about itself - and because the
        // preview now derives from them, it would lie identically and look correct.
        assertTrue(HomeDensity.Compact.cardHeight < HomeDensity.Standard.cardHeight)
        assertTrue(HomeDensity.Standard.cardHeight < HomeDensity.Spacious.cardHeight)
        assertTrue(HomeDensity.Compact.spacing < HomeDensity.Spacious.spacing)
    }

    @Test
    fun `spacious trades a column for size`() {
        assertTrue(HomeDensity.Spacious.columns < HomeDensity.Compact.columns)
        assertTrue(HomeDensity.Spacious.cardHeight > HomeDensity.Compact.cardHeight)
    }

    @Test
    fun `no density collapses the card below something usable`() {
        HomeDensity.entries.forEach { density ->
            assertTrue(
                "${density.name} card is ${density.cardHeight}",
                density.cardHeight.value >= 96f,
            )
            assertTrue("${density.name} has ${density.columns} columns", density.columns in 2..4)
        }
    }

    // ── One source, not two ──────────────────────────────────────────────────

    @Test
    fun `neither Home nor the preview hard-codes a card height`() {
        // The specific numbers that used to be inline. Finding one of them back in
        // either file means the split has reopened, and the preview has gone back
        // to describing a layout that no longer exists.
        val root = File("src/main/java/com/ciyato/launcher/ui/screens")
        assertTrue("screens not found at ${root.absolutePath}", root.isDirectory)

        listOf("HomeScreen.kt", "ThemeStudioScreen.kt").forEach { name ->
            val file = File(root, name)
            assertTrue("$name not found", file.exists())
            val text = file.readText()
            listOf("108.dp", "126.dp", "142.dp", "52.dp").forEach { literal ->
                assertFalse(
                    "$name hard-codes $literal again - use HomeDensity so Home and its " +
                        "preview cannot drift apart",
                    // Only flag it where it is being used as a height, not in an
                    // unrelated padding or width.
                    Regex("""height\s*=?\s*\(?\s*${Regex.escape(literal)}""").containsMatchIn(text) ||
                        Regex("""->\s*${Regex.escape(literal)}""").containsMatchIn(text),
                )
            }
        }
    }

    @Test
    fun `the preview reads the shared source`() {
        // A preview that does not reference HomeDensity at all is a preview that
        // has stopped being tied to Home, however it happens to look.
        val preview = File("src/main/java/com/ciyato/launcher/ui/screens/ThemeStudioScreen.kt")
        assertTrue(
            "ThemeStudioScreen no longer reads HomeDensity",
            preview.readText().contains("HomeDensity"),
        )
    }
}
