package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest says `supportsRtl="true"`, so directional icons have to mirror.
 *
 * Eighteen usages across eleven files did not. Compose mirrors padding, row
 * direction and text alignment on its own, so the layout flipped correctly while
 * six icons kept pointing the way they point in English: an "open in new" arrow
 * aiming out of the screen edge it should aim away from, a chat bubble with its
 * tail on the wrong side, a document with its folded corner reversed.
 *
 * The compiler warns about every one of these, and eighteen of them accumulated
 * anyway, because a warning does not fail anything. This does.
 */
class RtlIconMirroringTest {

    /** Material's own judgement of which icons carry direction. */
    private val directional = listOf(
        "OpenInNew", "Chat", "Article", "InsertDriveFile", "TrendingUp", "StickyNote2",
        "ArrowBack", "ArrowForward", "Send", "Login", "Logout", "Help",
        "List", "Sort", "KeyboardBackspace", "NavigateBefore", "NavigateNext",
        "ExitToApp", "Undo", "Redo", "Reply", "Forward", "FormatListBulleted",
        "ManageSearch", "LibraryBooks", "Note", "Comment", "Message",
    )

    @Test
    fun `no directional icon is used unmirrored`() {
        val root = File("src/main/java/com/ciyato/launcher")
        assertTrue("sources not found at ${root.absolutePath}", root.isDirectory)

        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                directional.forEach { name ->
                    // Icons.Default.X or Icons.Filled.X, but not the AutoMirrored
                    // form, which contains the same trailing text.
                    val unmirrored = Regex("""(?<!AutoMirrored\.)\bIcons\.(?:Default|Filled)\.$name\b""")
                    if (unmirrored.containsMatchIn(line)) {
                        offenders += "${file.name}:${index + 1} $name"
                    }
                }
            }
        }

        assertTrue(
            "these directional icons will point the wrong way in a right-to-left " +
                "locale: $offenders. Use Icons.AutoMirrored.Filled.<name> instead. The " +
                "manifest declares supportsRtl=\"true\", so the layout around them " +
                "already mirrors and only the icon is left facing the wrong way.",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the RTL claim in the manifest is the reason this test exists`() {
        // If supportsRtl were ever turned off, this test would be enforcing a
        // constraint the app no longer makes - worth knowing rather than leaving
        // a test whose premise has quietly gone.
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest not found", manifest.exists())
        assertTrue(
            "the manifest no longer declares supportsRtl=\"true\". Either restore it " +
                "or delete this test, which exists only to keep that promise.",
            manifest.readText().contains("android:supportsRtl=\"true\""),
        )
    }

    @Test
    fun `layout direction is left to Compose rather than hardcoded`() {
        val root = File("src/main/java/com/ciyato/launcher")
        // absolutePadding and absoluteOffset opt out of mirroring by design, and
        // TextAlign.Left/Right and Alignment.TopLeft and friends are the pre-RTL
        // spellings. All four were absent when this was written; the point is to
        // keep them absent, because one of them reintroduces a hardcoded side in
        // a layout that mirrors around it.
        val forbidden = listOf(
            "absolutePadding", "absoluteOffset",
            "TextAlign.Left", "TextAlign.Right",
            "Alignment.TopLeft", "Alignment.TopRight",
            "Alignment.BottomLeft", "Alignment.BottomRight",
        )
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                forbidden.filter { text.contains(it) }.map { "${file.name}: $it" }
            }
            .toList()
        assertEquals("direction-absolute layout primitives: $found", emptyList<String>(), found)
    }
}
