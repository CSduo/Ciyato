package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No code comment may cite a suggestion number as its reason for existing.
 *
 * Eighty-nine comments across forty-four files carried markers like
 * `// PermissionAuditScreen — Suggestion #139`, pointing at a list of 150 ideas
 * generated in one sitting and now archived under `docs/archive/2026-07/`.
 *
 * The harm is specific, and it is not tidiness (F-169, F-208). A numbered
 * reference reads as provenance — as though the number were a requirement
 * somebody signed off — and that is how Settings became a catalogue of things
 * nobody had decided to ship. Several features were restored at some point purely
 * because a working implementation existed and was "only" unwired. A comment
 * should say why the code exists *now*, and if nothing can be said, that is
 * information too.
 *
 * The rule, from `docs/README.md`: a feature is reachable because
 * `docs/FEATURE_MATRIX.md` says it earns its place, not because a suggestion
 * exists.
 */
class NoSuggestionNumbersTest {

    @Test
    fun `no source file cites a suggestion number`() {
        val root = File("src/main/java/com/ciyato/launcher")
        assertTrue("main sources not found at ${root.absolutePath}", root.isDirectory)

        // "Suggestion" followed by an optional # and digits. The bare word is
        // legitimate and common - ContextualSuggestions, "Play Store suggestion",
        // the Frequent Apps copy - so only the numbered form is a finding.
        val marker = Regex("""Suggestion\s*#?\d+""")

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val relative = file.path.replace('\\', '/').substringAfter("com/ciyato/launcher/")
                file.readLines().withIndex()
                    .filter { (_, line) -> marker.containsMatchIn(line) }
                    .map { (i, line) -> "$relative:${i + 1}  ${line.trim().take(70)}" }
            }
            .toList()

        assertTrue(
            "these comments cite an archived suggestion list as their reason for existing:\n" +
                offenders.joinToString("\n") { "  $it" } +
                "\n\nSay why the code exists now instead. See docs/README.md.",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the archived list is still archived, not deleted`() {
        // The history is worth keeping - it records where ideas came from, which
        // is genuinely useful and completely different from a backlog. What must
        // not happen is code pointing at it as authority.
        val archived = File("../docs/archive/2026-07/SUGGESTIONS.md")
        assertTrue(
            "SUGGESTIONS.md should remain under docs/archive/ as history",
            archived.exists(),
        )
    }
}
