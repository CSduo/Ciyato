package com.ciyato.launcher

import com.ciyato.launcher.ui.screens.NoticeBlock
import com.ciyato.launcher.ui.screens.parseNotices
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Apache-2.0 section 4(d) is a shipping obligation, so it gets a test.
 *
 * Almost every dependency Ciyato ships is Apache-2.0, and section 4(d) requires the
 * attribution notices to travel with the distributed work. A commercial app that
 * omits them is distributing those libraries outside their licence. Play does not
 * check; that does not make it optional.
 *
 * The usual mechanical answer is `com.google.android.gms:oss-licenses-plugin`, which
 * generates the screen from the resolved dependency graph and therefore cannot go
 * stale. It is the wrong tool for this app: Ciyato has no Google Play Services
 * dependency at all — ML Kit here is the bundled on-device variant — so the licences
 * screen would become the reason the app gains its first GMS dependency, in an app
 * whose entire positioning is that nothing leaves the device.
 *
 * The objection to the alternative is real though: a hand-written list drifts, and a
 * stale attribution list is the same breach as no list. This test is what replaces
 * the plugin's guarantee. A dependency added to `build.gradle.kts` and not to
 * `THIRD_PARTY_NOTICES.md` fails the build.
 */
class ThirdPartyNoticesTest {

    private val notices = File("../THIRD_PARTY_NOTICES.md")
    private val buildFile = File("build.gradle.kts")

    /**
     * Coordinates that ship but need no row of their own, each with a reason.
     *
     * Kept short on purpose — every entry here is a hole in the guarantee above.
     */
    private val notAttributableSeparately = mapOf(
        "androidx.compose:compose-bom" to
            "a version alignment BOM, not code; the Compose artifacts it governs are listed",
        "androidx.test" to "test-only, not in the release artifact",
        "junit" to "test-only, not in the release artifact",
        "org.mockito" to "test-only, not in the release artifact",
        "org.robolectric" to "test-only, not in the release artifact",
        "io.github.takahirom.roborazzi" to "test-only, not in the release artifact",
        "org.json" to "test-only JVM stub, not in the release artifact",
        "androidx.benchmark" to "benchmark module only, not in the release artifact",
    )

    private val versionCatalog = File("../gradle/libs.versions.toml")

    /**
     * Catalog alias as Kotlin spells it, to `group:name`.
     *
     * The catalog writes `coil-compose = { group = "io.coil-kt", name = "coil-compose" }`
     * and Kotlin accesses it as `libs.coil.compose`, so the alias is matched with dots.
     */
    private fun catalogLibraries(): Map<String, String> {
        if (!versionCatalog.exists()) return emptyMap()
        val out = mutableMapOf<String, String>()
        var inLibraries = false
        versionCatalog.readLines().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            when {
                line.startsWith("[") -> inLibraries = line == "[libraries]"
                !inLibraries || line.isEmpty() -> Unit
                else -> {
                    val alias = line.substringBefore("=").trim()
                    if (alias.isEmpty() || !line.contains("=")) return@forEach
                    val group = Regex("""group\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
                    val name = Regex("""name\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
                    // The `module = "group:name"` form too.
                    val module = Regex("""module\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
                    val coordinate = when {
                        group != null && name != null -> "$group:$name"
                        module != null -> module
                        else -> null
                    }
                    if (coordinate != null) out[alias.replace('-', '.')] = coordinate
                }
            }
        }
        return out
    }

    /**
     * Every `group:name` that ends up in the APK.
     *
     * This method is why the test is worth having, and the first version of it was
     * very nearly worthless. It read only string-literal coordinates and explicitly
     * skipped any line containing `libs.` — and 23 of the 24 dependencies here come
     * from the version catalog, so it was checking exactly one of them. It passed
     * while the Coil attribution row was deleted.
     *
     * That is the same defect this project keeps producing and the same one this file
     * exists to prevent: a check that reports success because it never looked. It was
     * caught only by insisting on proving the test could fail, which is the one habit
     * that finds this class of bug.
     */
    private fun shippedCoordinates(): List<String> {
        val catalog = catalogLibraries()
        val fromCatalog = mutableListOf<String>()
        val fromLiterals = mutableListOf<String>()
        val unresolved = mutableListOf<String>()

        buildFile.readLines().map { it.trim() }.forEach { line ->
            // Only the configurations that put code in the APK. testImplementation,
            // androidTestImplementation and debugImplementation do not ship.
            val ships = line.startsWith("implementation(") || line.startsWith("api(")
            if (!ships || line.contains("project(")) return@forEach

            val alias = Regex("""libs\.([A-Za-z0-9._]+)""").find(line)?.groupValues?.get(1)
            if (alias != null) {
                val coordinate = catalog[alias]
                if (coordinate != null) fromCatalog += coordinate else unresolved += alias
                return@forEach
            }
            val quoted = line.substringAfter("\"", "").substringBefore("\"", "")
            if (quoted.contains(":")) fromLiterals += quoted.split(":").take(2).joinToString(":")
        }

        // An alias the catalog cannot explain means the parsing has drifted from the
        // catalog format, and a silently shorter list is exactly how this check became
        // vacuous the first time.
        assertEquals(
            "these catalog aliases are used in build.gradle.kts but could not be resolved " +
                "from gradle/libs.versions.toml: $unresolved. Until they resolve, this test " +
                "is not checking them.",
            emptyList<String>(),
            unresolved,
        )

        return (fromCatalog + fromLiterals).distinct().sorted()
    }

    @Test
    fun `the coordinate list is actually populated from the catalog`() {
        // A guard on the guard. The first version of shippedCoordinates returned one
        // entry and every other assertion in this file passed trivially.
        val coordinates = shippedCoordinates()
        assertTrue(
            "only ${coordinates.size} shipped coordinates resolved (${coordinates.take(3)}). " +
                "Nearly every dependency here comes from gradle/libs.versions.toml, so a short " +
                "list means the catalog is not being read and this file proves nothing.",
            coordinates.size >= 15,
        )
        assertTrue(
            "the version catalog was not found at ${versionCatalog.absolutePath}",
            versionCatalog.exists(),
        )
    }

    @Test
    fun `the notices document exists where the build task expects it`() {
        assertTrue(
            "THIRD_PARTY_NOTICES.md not found at ${notices.absolutePath}. The " +
                "copyThirdPartyNotices task reads it from the root project, and without it " +
                "the app ships no attribution at all.",
            notices.exists(),
        )
    }

    @Test
    fun `every shipped dependency is attributed`() {
        val text = notices.readText()
        val missing = shippedCoordinates().filter { coordinate ->
            if (notAttributableSeparately.keys.any { coordinate.startsWith(it) }) return@filter false
            // The document lists coordinates in backticks, sometimes abbreviating the
            // sibling artifacts of a group as `-suffix` forms, so a group-level match
            // counts. The group is what carries the copyright.
            val group = coordinate.substringBefore(":")
            !text.contains(coordinate) && !text.contains(group)
        }
        assertEquals(
            "these ship in the APK and have no entry in THIRD_PARTY_NOTICES.md: $missing. " +
                "Add each one to the Components table. This is the check that replaces " +
                "oss-licenses-plugin, so skipping it means shipping unattributed code.",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `the shipped section is delimited and holds the attribution`() {
        val text = notices.readText()
        val begin = text.indexOf("<!-- SHIPPED:BEGIN")
        val end = text.indexOf("<!-- SHIPPED:END")
        assertTrue("no SHIPPED:BEGIN marker; the build task would fail", begin >= 0)
        assertTrue("no SHIPPED:END marker after BEGIN; internal notes would ship", end > begin)

        val body = text.substring(text.indexOf("-->", begin) + 3, end)
        assertTrue(
            "the shipped section does not name Apache License 2.0, which nearly every " +
                "dependency uses - the markers are around the wrong part of the document",
            body.contains("Apache License 2.0"),
        )
        assertTrue(
            "the shipped section does not carry the licence URL, so the notice is " +
                "incomplete on its own",
            body.contains("apache.org/licenses/LICENSE-2.0"),
        )
    }

    @Test
    fun `nothing internal leaks into what users see`() {
        val text = notices.readText()
        val begin = text.indexOf("<!-- SHIPPED:BEGIN")
        val end = text.indexOf("<!-- SHIPPED:END")
        val body = text.substring(text.indexOf("-->", begin) + 3, end)

        // The document doubles as an internal compliance memo: unresolved checkboxes,
        // the open question about wallpaper provenance, instructions for regenerating
        // the list. Shipping any of that would put Ciyato's own open questions in front
        // of a user, which is its own kind of dishonesty - it reads as fact.
        val internalMarkers = listOf("- [ ]", "- [x]", "provenance", "oss-licenses-plugin", "yours)")
        val leaked = internalMarkers.filter { body.contains(it) }
        assertEquals(
            "internal notes are inside the SHIPPED markers and would be shown to users: $leaked",
            emptyList<String>(),
            leaked,
        )
    }

    @Test
    fun `the parser turns the real document into readable blocks`() {
        // Against the real file rather than a fixture. A parser tested only on a string
        // its own author wrote proves nothing about the file that ships.
        val text = notices.readText()
        val begin = text.indexOf("<!-- SHIPPED:BEGIN")
        val end = text.indexOf("<!-- SHIPPED:END")
        val body = text.substring(text.indexOf("-->", begin) + 3, end).trim()

        val blocks = parseNotices(body)
        val components = blocks.filterIsInstance<NoticeBlock.Component>()
        val headings = blocks.filterIsInstance<NoticeBlock.Heading>()

        assertTrue("no headings parsed from the notices", headings.isNotEmpty())
        assertTrue(
            "only ${components.size} components parsed; the document lists far more, so the " +
                "table parsing has broken and the screen would under-report the attribution",
            components.size >= 10,
        )

        // The header row must not become a component - "Component | Coordinates | What
        // it does here" rendered as a library is the kind of detail nobody checks on a
        // screen nobody opens.
        assertTrue(
            "the table header row was parsed as a component",
            components.none { it.name.equals("Component", ignoreCase = true) },
        )
        // Nor the separator row.
        assertTrue(
            "a table separator row was parsed as a component",
            components.none { it.name.all { c -> c == '-' || c == ':' } },
        )
        // Markdown emphasis and code ticks must be stripped, or they render literally.
        assertTrue(
            "markdown ticks or asterisks survived into a rendered component",
            components.none { it.name.contains("`") || it.name.contains("**") },
        )
        assertTrue(
            "no component carries coordinates, so the screen would name libraries without " +
                "identifying them",
            components.any { it.coordinates.isNotBlank() },
        )
    }

    @Test
    fun `the build copies the notices into assets for every variant`() {
        val build = buildFile.readText()
        assertTrue(
            "copyThirdPartyNotices task is gone; the screen would read an asset that is " +
                "never generated and show its failure state",
            build.contains("copyThirdPartyNotices"),
        )
        assertTrue(
            "the generated assets directory is no longer produced, so there would be no " +
                "copied file to package",
            build.contains("generated/notices/assets"),
        )
        // The guarantee, not the spelling of it: the assets source set must be fed by the
        // task PROVIDER, so Gradle infers the dependency for every consumer. This
        // assertion previously pinned a hand-written `merge*Assets` hook, which named the
        // packaging tasks and missed the lint model tasks that read the same source set -
        // and a combined test+lint run raced the copy and failed intermittently. Passing
        // the provider is what makes the wiring complete rather than enumerated.
        assertTrue(
            "the assets source set no longer takes the copyThirdPartyNotices provider, so " +
                "Gradle cannot infer which tasks must wait for it. Do not go back to naming " +
                "consumer tasks by hand - that is what caused the intermittent lint failure.",
            build.contains("assets.srcDir(copyThirdPartyNotices)"),
        )
    }
}
