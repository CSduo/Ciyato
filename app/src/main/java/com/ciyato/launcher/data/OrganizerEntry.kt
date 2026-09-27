package com.ciyato.launcher.data

/**
 * Every point the organizer can be entered at from outside itself.
 *
 * Ciyato is deliberately two layers: a HOME activity that draws the launcher and
 * an organizer activity reached from the app icon or from Home (F-173 keeps that
 * boundary; it is a platform role, not an accident). What was not deliberate is
 * how they talked to each other. Home built an Intent with a raw string extra -
 * `putExtra(EXTRA_START_DESTINATION, "files")` - and the organizer resolved it
 * through a `when` that recognised six values and returned null for everything
 * else (F-044, F-074).
 *
 * That is a silent failure with no compiler involvement anywhere in it. Passing
 * "insights", a real route, resolved to null and landed the person on Overview;
 * a typo did the same; adding a destination to one side taught the other side
 * nothing. Every route worked or did not depending on which of two lists you had
 * remembered to edit.
 *
 * This is the one list. A caller names an entry, the compiler checks it exists,
 * and `OrganizerEntryTest` checks the route behind it is actually declared in the
 * NavHost - so the remaining way to get this wrong is caught by the build rather
 * than by a person tapping Files and staying where they were.
 */
enum class OrganizerEntry(val route: String) {
    Overview("overview"),
    Files("files"),
    Photos("photos"),
    Search("search"),
    Settings("settings"),
    Agenda("agenda"),
    ;

    companion object {
        /**
         * Spellings that used to mean something, kept working on purpose.
         *
         * A pinned app shortcut or a saved intent carries the route name from
         * whenever it was created. "home" meant this shell before there was one
         * Home in the product (F-071); "shared" was Photos. Dropping an alias
         * does not fail visibly - it lands someone on Overview and looks like a
         * bug in the shortcut.
         */
        private val ALIASES: Map<String, OrganizerEntry> = mapOf(
            "home" to Overview,
            "dashboard" to Overview,
            "shared" to Photos,
        )

        /** Resolves an intent extra, or null when it names nothing. */
        fun fromExtra(raw: String?): OrganizerEntry? {
            val key = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.route == key } ?: ALIASES[key]
        }

        /** Every spelling that resolves, for tests and diagnostics. */
        val acceptedSpellings: Set<String> get() = entries.map { it.route }.toSet() + ALIASES.keys
    }
}
