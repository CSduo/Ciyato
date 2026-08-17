package com.ciyato.launcher.data

/**
 * Focus session logic, derived entirely from a persisted end instant.
 *
 * This used to be a stateful singleton: the session lived in a `MutableStateFlow`
 * inside an `object`, and a 1-second ticker was launched in whatever
 * `CoroutineScope` the *caller* happened to pass in. Three defects followed from
 * that one decision, and all three disappear here rather than being patched:
 *
 *  - **The Quick Settings tile could freeze a session forever** (F-120). The tile
 *    passed its own service scope, which Android cancels when the tile service is
 *    destroyed — moments after the panel closes. The ticker was the only thing
 *    that ever cleared an expired session, so the session stayed "active"
 *    indefinitely, blocking apps with no visible way to stop it.
 *  - **A session did not survive process death** (F-121). Launchers are killed
 *    routinely; a 60-minute session silently evaporated and the setup screen
 *    reappeared as though nothing had been running.
 *  - **A separate persisted `focus_mode_active` boolean outlived the session it
 *    described** (F-175), because nothing read it back or reset it.
 *
 * The fix is to stop storing "a session is running" at all and instead store
 * *when it ends*. Whether focus is on becomes a question about the clock, which
 * is answerable at any moment, after any kind of death, without a timer. A ticker
 * is still needed to animate a countdown — but that is a display concern and
 * belongs to the screen showing it, not to the source of truth.
 */
object FocusSessionManager {

    data class FocusSession(
        /** Absolute wall-clock instant the session ends. */
        val endsAt: Long,
        val durationMs: Long,
        val blockedCategories: List<AppCategory>,
    ) {
        val isActive: Boolean get() = System.currentTimeMillis() < endsAt
        val remainingMs: Long get() = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val remainingMin: Int get() = (remainingMs / 60_000).toInt()
        val remainingSec: Int get() = ((remainingMs % 60_000) / 1_000).toInt()
        val progressFraction: Float
            get() = if (durationMs <= 0L) 1f
            else (1f - remainingMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Rebuilds the session from persisted values, or null when none is running.
     *
     * Returns null for an end time in the past, so an expired session needs no
     * cleanup pass to stop taking effect — it simply stops being a session.
     */
    fun sessionOf(endsAt: Long, durationMin: Int, blockedCatsCsv: String): FocusSession? {
        if (endsAt <= 0L || System.currentTimeMillis() >= endsAt) return null
        return FocusSession(
            endsAt = endsAt,
            durationMs = durationMin.coerceAtLeast(1) * 60_000L,
            blockedCategories = decodeCategories(blockedCatsCsv),
        )
    }

    /** Unknown or renamed category names are dropped rather than crashing. */
    fun decodeCategories(csv: String): List<AppCategory> =
        csv.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { name -> runCatching { AppCategory.valueOf(name) }.getOrNull() }

    fun encodeCategories(categories: List<AppCategory>): String =
        categories.joinToString(",") { it.name }

    /**
     * Whether [category] is currently held back, computed from the clock.
     *
     * Scoped honestly: this only affects launches that go through Ciyato. It is
     * not OS-level enforcement, and copy must not imply that it is (F-119).
     */
    fun isBlocked(session: FocusSession?, category: AppCategory): Boolean =
        session != null && session.isActive && category in session.blockedCategories
}
