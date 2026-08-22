package com.ciyato.launcher.data

import java.util.Calendar

/**
 * Which categories Home features, and whether it is bedtime.
 *
 * Both decisions lived in LauncherViewModel and read the system clock through
 * property getters, so neither could be tested without waiting for the right
 * hour of the day (F-041). Taking the hour as an argument makes them ordinary
 * functions, and made a shipped bug obvious the moment one was written down.
 */
object TimeAwareLayout {

    /**
     * When bedtime ends.
     *
     * There is no wake-time setting, so this is a fixed early-morning boundary
     * rather than a guess dressed up as a preference.
     */
    const val BEDTIME_END_HOUR = 6

    /** The categories Home features for a given hour. */
    fun featuredCategories(hour: Int, isWeekend: Boolean): List<AppCategory> = when {
        hour < 7 -> listOf(AppCategory.DAILY, AppCategory.UTILITIES, AppCategory.PRODUCTIVITY)
        hour < 12 -> listOf(AppCategory.WORK, AppCategory.PRODUCTIVITY, AppCategory.COMMUNICATION)
        hour < 14 -> listOf(AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.DAILY)
        hour < 18 && !isWeekend -> listOf(AppCategory.WORK, AppCategory.PRODUCTIVITY, AppCategory.FINANCE)
        hour < 18 && isWeekend -> listOf(AppCategory.ENTERTAINMENT, AppCategory.SOCIAL, AppCategory.TRAVEL)
        hour < 22 -> listOf(AppCategory.ENTERTAINMENT, AppCategory.SOCIAL, AppCategory.CREATIVITY)
        else -> listOf(AppCategory.DAILY, AppCategory.ENTERTAINMENT, AppCategory.UTILITIES)
    }

    /**
     * Whether bedtime mode is active at [hour].
     *
     * The bug this replaces: the test was `hour >= startHour`, which is false at
     * 1 a.m. for a bedtime of 22:00. Bedtime therefore switched itself off at
     * midnight — the middle of the night, and precisely the hours it exists to
     * cover. Settings said "apps hidden after this time" while the apps came
     * back an hour or two later.
     *
     * A bedtime window normally crosses midnight, so it is treated as one:
     * active from [startHour] through to [BEDTIME_END_HOUR] the next morning.
     */
    fun isBedtime(hour: Int, startHour: Int, enabled: Boolean): Boolean {
        if (!enabled) return false
        val start = startHour.coerceIn(0, 23)
        // A start before the morning boundary describes a window inside one
        // day, so it does not wrap. The UI only offers 18–23, but the rule
        // should not depend on the slider's range staying that way.
        return if (start <= BEDTIME_END_HOUR) {
            hour in start until BEDTIME_END_HOUR
        } else {
            hour >= start || hour < BEDTIME_END_HOUR
        }
    }

    /** Human description of the window, for settings copy that has to be true. */
    fun bedtimeWindowLabel(startHour: Int): String =
        "%02d:00 to %02d:00".format(startHour.coerceIn(0, 23), BEDTIME_END_HOUR)

    fun isWeekend(dayOfWeek: Int): Boolean =
        dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
}
