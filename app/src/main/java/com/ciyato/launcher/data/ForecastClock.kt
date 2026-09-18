package com.ciyato.launcher.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Picking "now" out of an hourly forecast.
 *
 * Ciyato asks Open-Meteo for `timezone=auto`, so every timestamp it returns is
 * local time *at the forecast location*. The code then matched those against
 * `Calendar.getInstance()` and a `SimpleDateFormat` in the device's default zone
 * (F-032). Those agree only while the phone and the weather are in the same
 * zone, which is exactly the case nobody needs to check.
 *
 * Ask for London's weather from Mumbai and the match fails on both the hour and,
 * across midnight, the date. The old code then fell back to `?: 0` - index zero,
 * which is **midnight of the first forecast day** - so the "current conditions"
 * card showed the middle of last night, confidently and with no indication
 * anything had gone wrong.
 *
 * The response carries `utc_offset_seconds` precisely so this can be done
 * correctly. Remote local time is derived from it, never inferred from the
 * device.
 */
object ForecastClock {

    /** Open-Meteo hourly stamps look like `2026-09-18T14:00`. */
    private val HOUR_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")

    /**
     * Index of the hourly entry covering the current hour at the forecast
     * location, or null when the forecast does not cover now at all.
     *
     * @param utcOffsetSeconds the response's `utc_offset_seconds` - the offset
     *   at the forecast location, which is not the device's.
     */
    fun currentHourIndex(
        times: List<String>,
        nowUtcMillis: Long,
        utcOffsetSeconds: Int,
    ): Int? {
        if (times.isEmpty()) return null
        val key = Instant.ofEpochMilli(nowUtcMillis)
            .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds.coerceIn(-64_800, 64_800)))
            .toLocalDateTime()
            .truncatedTo(ChronoUnit.HOURS)
            .format(HOUR_KEY)
        return times.indexOfFirst { it.startsWith(key) }.takeIf { it >= 0 }
    }

    /**
     * Where the hourly strip should start.
     *
     * Falls forward to the next entry still ahead of now rather than to index
     * zero. A forecast that begins later today is a normal response; showing its
     * first hour is right, and showing last midnight because an exact match
     * failed is the bug this replaces.
     *
     * Returns 0 only when every entry is in the past, where there is nothing
     * better to show and the caller is expected to treat the data as stale.
     */
    fun hourlyStartIndex(
        times: List<String>,
        nowUtcMillis: Long,
        utcOffsetSeconds: Int,
    ): Int {
        currentHourIndex(times, nowUtcMillis, utcOffsetSeconds)?.let { return it }
        if (times.isEmpty()) return 0
        val nowKey = Instant.ofEpochMilli(nowUtcMillis)
            .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds.coerceIn(-64_800, 64_800)))
            .toLocalDateTime()
            .truncatedTo(ChronoUnit.HOURS)
            .format(HOUR_KEY)
        // Stamps sort lexicographically because the format is fixed-width.
        val ahead = times.indexOfFirst { it >= nowKey }
        return if (ahead >= 0) ahead else 0
    }

    /**
     * True when the forecast no longer covers the present hour at its own
     * location - the signal that a snapshot is stale rather than simply early.
     */
    fun isStale(times: List<String>, nowUtcMillis: Long, utcOffsetSeconds: Int): Boolean {
        if (times.isEmpty()) return true
        return currentHourIndex(times, nowUtcMillis, utcOffsetSeconds) == null &&
            hourlyStartIndex(times, nowUtcMillis, utcOffsetSeconds) == 0 &&
            times.last() < Instant.ofEpochMilli(nowUtcMillis)
                .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds.coerceIn(-64_800, 64_800)))
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.HOURS)
                .format(HOUR_KEY)
    }
}
