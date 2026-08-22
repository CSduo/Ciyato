package com.ciyato.launcher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps STORE_READINESS.md honest about what the app actually declares.
 *
 * The document previously stated the app had "no full-gallery media permission,
 * no broad storage permission, no fine location, no microphone, no calendar …
 * and no notification listener" while the manifest declared every one of those
 * (F-183). Nobody lied; the manifest simply moved on and the prose did not. Play
 * declarations and privacy text written from that file would have been wrong in
 * a way that matters.
 *
 * The audit's fix is that release documentation must be derived from shipping
 * configuration rather than maintained alongside it. Deriving it at build time
 * would mean generating Markdown nobody reads; enforcing it is cheaper and has
 * the same effect — the moment a permission is added or removed without the
 * document following, this fails.
 */
class StoreReadinessDocTest {

    private fun repoFile(relative: String): File {
        // Unit tests run with the module directory as the working directory.
        val fromModule = File(relative)
        return if (fromModule.exists()) fromModule else File("../$relative")
    }

    private val manifest: Element by lazy {
        val f = repoFile("src/main/AndroidManifest.xml")
        assertTrue("manifest not found at ${f.absolutePath}", f.exists())
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(f).documentElement
    }

    private val doc: String by lazy {
        val f = repoFile("../STORE_READINESS.md")
        assertTrue("STORE_READINESS.md not found at ${f.absolutePath}", f.exists())
        f.readText()
    }

    private fun declaredPermissions(): List<String> {
        val nodes = manifest.getElementsByTagName("uses-permission")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .sorted()
    }

    private fun declaredServices(): List<String> {
        val nodes = manifest.getElementsByTagName("service")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map { it.substringAfterLast('.') }
            .sorted()
    }

    @Test
    fun `every declared permission appears in the readiness document`() {
        val missing = declaredPermissions().filterNot { perm ->
            doc.contains("`" + perm.removePrefix("android.permission.") + "`")
        }
        assertEquals(
            "Permissions declared in the manifest but absent from STORE_READINESS.md. " +
                "Add a row explaining why each is needed and its Play implication.",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `the document does not claim permissions the app no longer has`() {
        // Guards the reverse drift: a permission removed from the manifest while
        // the document still promises it, which would over-declare in the data
        // safety form.
        val declared = declaredPermissions().map { it.removePrefix("android.permission.") }.toSet()
        val everRemoved = listOf("ACCESS_FINE_LOCATION", "FOREGROUND_SERVICE")
        for (perm in everRemoved) {
            if (perm in declared) continue
            val inTable = Regex("""^\| `$perm`""", RegexOption.MULTILINE).containsMatchIn(doc)
            assertTrue(
                "$perm is not in the manifest but STORE_READINESS.md still lists it as declared",
                !inTable,
            )
        }
    }

    @Test
    fun `the stated permission count matches the manifest`() {
        val stated = Regex("""## Declared permissions — all (\d+)""").find(doc)
            ?: error("STORE_READINESS.md is missing its 'Declared permissions — all N' heading")
        assertEquals(
            "STORE_READINESS.md states a permission count that the manifest contradicts",
            declaredPermissions().size,
            stated.groupValues[1].toInt(),
        )
    }

    @Test
    fun `every declared service appears in the readiness document`() {
        val missing = declaredServices().filterNot { doc.contains(it) }
        assertEquals(
            "Services declared in the manifest but absent from STORE_READINESS.md",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `the stated service count matches the manifest`() {
        val stated = Regex("""## Declared services — all (\d+)""").find(doc)
            ?: error("STORE_READINESS.md is missing its 'Declared services — all N' heading")
        assertEquals(declaredServices().size, stated.groupValues[1].toInt())
    }

    /**
     * The two highest-scrutiny declarations must never quietly lose their
     * "this needs a Play form" note — that note is the whole reason someone
     * reads this file before submitting.
     */
    @Test
    fun `high-scrutiny permissions keep their declaration warning`() {
        for (perm in listOf("QUERY_ALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE")) {
            if (declaredPermissions().none { it.endsWith(perm) }) continue
            val row = doc.lines().firstOrNull { it.startsWith("| `$perm`") }
                ?: error("$perm has no row in STORE_READINESS.md")
            assertTrue(
                "$perm is declared but its row does not mention the required Play declaration",
                row.contains("Declaration") || row.contains("declaration"),
            )
        }
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
