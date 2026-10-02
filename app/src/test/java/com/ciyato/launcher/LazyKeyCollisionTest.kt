package com.ciyato.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Two lists in one LazyColumn must not key their items from the same values.
 *
 * Found on a real phone as a crash: the file Search screen showed recent searches and
 * a "Try these" list of examples in one LazyColumn, and keyed both with
 * `key = { it }` - the bare query text. Tapping an example runs it and saves it to
 * history, so the most natural first action on that screen put "large files" in both
 * lists. Compose throws on a duplicate key, and because the history persists, the
 * Search tab then crashed every time it was opened.
 *
 * A bare-value key is safe in a list of its own and dangerous the moment a second one
 * appears beside it, so the rule is per file: at most one `key = { it }`. Anything more
 * namespaces its keys - `"history:$it"`, `"example:$it"` - which costs nothing and
 * cannot collide.
 */
class LazyKeyCollisionTest {

    @Test
    fun `no file keys two lists by the bare item value`() {
        val root = File("src/main/java/com/ciyato/launcher/ui")
        val bare = Regex("""key\s*=\s*\{\s*it\s*\}""")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val n = bare.findAll(file.readText()).count()
                if (n > 1) "${file.name} ($n bare keys)" else null
            }
            .sorted()
            .toList()
        assertEquals(
            "these files key more than one list by the bare item value, so two lists " +
                "sharing a LazyColumn can produce the same key and crash Compose: " +
                "$offenders. Namespace them, e.g. key = { \"history:\$it\" }.",
            emptyList<String>(),
            offenders,
        )
    }
}
