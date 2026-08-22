package com.ciyato.launcher.data

import org.json.JSONObject

/**
 * Every edit to a custom category, as pure transformations.
 *
 * A category is one concept spread across seven preference keys: its name, the
 * per-app overrides pointing at it, its icon, its presentation, its tile size,
 * its position in the order list and whether it is hidden. Renaming one used to
 * mean seven separate DataStore writes driven by JSON munging inline in the
 * ViewModel, which made two things impossible: applying the change atomically,
 * and testing whether it was even correct (F-042).
 *
 * The rules live here as functions from one [Snapshot] to another. Null means
 * "rejected, change nothing" — never a partially-applied result, because a half
 * edit is exactly the failure this finding is about.
 */
object CategoryMutations {

    /** The seven category preferences, read together and written together. */
    data class Snapshot(
        val names: String = "",
        val overrides: String = "{}",
        val icons: String = "{}",
        val presentations: String = "{}",
        val tileSizes: String = "{}",
        val order: String = "",
        val hidden: String = "",
    )

    fun add(
        s: Snapshot,
        name: String,
        presentation: CustomCategoryPresentation,
    ): Snapshot? {
        val current = csv(s.names)
        if (name.isBlank() || name in current) return null
        return s.copy(
            names = (current + name).joinToString(","),
            presentations = CustomCategoryPresentationStore.update(s.presentations, name, presentation),
        )
    }

    fun rename(
        s: Snapshot,
        current: String,
        replacement: String,
        icon: String? = null,
        presentation: CustomCategoryPresentation? = null,
    ): Snapshot? {
        val names = csv(s.names)
        if (current !in names) return null
        if (replacement.isBlank()) return null
        if (replacement != current && replacement in names) return null

        val overrides = json(s.overrides)
        overrides.keys().asSequence().toList().forEach { pkg ->
            if (overrides.optString(pkg) == current) overrides.put(pkg, replacement)
        }

        val icons = json(s.icons)
        val resolvedIcon = icon ?: icons.optString(current, "folder")
        icons.remove(current)
        icons.put(replacement, resolvedIcon)

        val renamedPresentations = if (replacement == current) {
            s.presentations
        } else {
            CustomCategoryPresentationStore.rename(s.presentations, current, replacement)
        }

        val tileSizes = json(s.tileSizes)
        val size = tileSizes.optString(current, "")
        tileSizes.remove(current)
        if (size.isNotBlank()) tileSizes.put(replacement, size)

        return s.copy(
            names = names.map { if (it == current) replacement else it }.joinToString(","),
            overrides = overrides.toString(),
            icons = icons.toString(),
            presentations = presentation
                ?.let { CustomCategoryPresentationStore.update(renamedPresentations, replacement, it) }
                ?: renamedPresentations,
            tileSizes = tileSizes.toString(),
            order = replaceInCsv(s.order, current, replacement),
            hidden = replaceInCsv(s.hidden, current, replacement),
        )
    }

    fun merge(s: Snapshot, source: String, destination: String): Snapshot? {
        val names = csv(s.names)
        if (source == destination || source !in names || destination !in names) return null

        val overrides = json(s.overrides)
        overrides.keys().asSequence().toList().forEach { pkg ->
            if (overrides.optString(pkg) == source) overrides.put(pkg, destination)
        }

        val icons = json(s.icons)
        icons.remove(source)

        val tileSizes = json(s.tileSizes)
        if (!tileSizes.has(destination) && tileSizes.has(source)) {
            tileSizes.put(destination, tileSizes.optString(source))
        }
        tileSizes.remove(source)

        return s.copy(
            names = names.filterNot { it == source }.joinToString(","),
            overrides = overrides.toString(),
            icons = icons.toString(),
            presentations = CustomCategoryPresentationStore.remove(s.presentations, source),
            tileSizes = tileSizes.toString(),
            order = replaceInCsv(s.order, source, destination),
            hidden = replaceInCsv(s.hidden, source, destination),
        )
    }

    fun remove(s: Snapshot, name: String): Snapshot? {
        val names = csv(s.names)
        if (name !in names) return null

        // Apps assigned to a deleted category must lose the assignment, or they
        // keep pointing at a category that no longer exists.
        val overrides = json(s.overrides)
        overrides.keys().asSequence().toList()
            .filter { overrides.optString(it) == name }
            .forEach { overrides.remove(it) }

        val icons = json(s.icons)
        icons.remove(name)

        val tileSizes = json(s.tileSizes)
        tileSizes.remove(name)

        return s.copy(
            names = names.filterNot { it == name }.joinToString(","),
            overrides = overrides.toString(),
            icons = icons.toString(),
            presentations = CustomCategoryPresentationStore.remove(s.presentations, name),
            tileSizes = tileSizes.toString(),
            // Never cleaned before: a deleted category kept its slot in the
            // order list and, if hidden, in the hidden set.
            order = removeFromCsv(s.order, name),
            hidden = removeFromCsv(s.hidden, name),
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun csv(raw: String): List<String> =
        raw.split(",").map(String::trim).filter(String::isNotEmpty)

    private fun json(raw: String): JSONObject = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

    private fun replaceInCsv(raw: String, from: String, to: String): String =
        csv(raw).map { if (it == from) to else it }.distinct().joinToString(",")

    private fun removeFromCsv(raw: String, name: String): String =
        csv(raw).filterNot { it == name }.joinToString(",")
}
