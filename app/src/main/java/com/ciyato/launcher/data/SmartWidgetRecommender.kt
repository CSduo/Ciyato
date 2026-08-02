package com.ciyato.launcher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.BatteryManager
import java.util.Calendar

/**
 * SmartWidgetRecommender — Suggestion #151
 *
 * Analyses time of day, battery level, location motion, and recent app usage
 * to suggest the most contextually relevant widgets for the home screen.
 *
 * Usage:
 *   val recs = SmartWidgetRecommender.recommend(context)
 *   val top3 = SmartWidgetRecommender.topRecommendations(context, 3)
 */
object SmartWidgetRecommender {

    enum class WidgetType(val label: String) {
        WEATHER("Weather"),
        CLOCK("Clock"),
        BATTERY("Battery"),
        MEDIA("Now Playing"),
        CALENDAR("Agenda"),
        STEPS("Steps"),
        STOCK("Markets"),
        NEWS("Headlines"),
        STICKY_NOTE("Sticky Note"),
        FOCUS_TIMER("Focus Timer"),
        WORLD_CLOCK("World Clock"),
        AFFIRMATION("Daily Quote"),
        COUNTDOWN("Countdown"),
    }

    data class WidgetRecommendation(
        val type: WidgetType,
        val reason: String,
        val score: Float,
        val priority: Int,
    )

    private data class Score(val value: Float, val reason: String)

    /**
     * Returns all widget recommendations sorted by descending score.
     * Must be called off the main thread (reads UsageStats + LocationManager).
     */
    fun recommend(context: Context): List<WidgetRecommendation> {
        val cal        = Calendar.getInstance()
        val hour       = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek  = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend  = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val batteryPct = getBatteryPercent(context)
        val charging   = isCharging(context)
        val moving     = isUserMoving(context)
        val recent     = getRecentApps(context)

        val scores = mutableMapOf<WidgetType, Score>()

        scores[WidgetType.CLOCK] = when {
            hour in 5..9   -> Score(0.95f, "Morning routine — clock keeps you on time")
            hour in 21..23 -> Score(0.85f, "Winding down — see the time before bed")
            else           -> Score(0.75f, "Always helpful to see the time at a glance")
        }

        scores[WidgetType.WEATHER] = when {
            hour in 6..10  -> Score(0.92f, "Check the forecast before heading out")
            hour in 17..19 -> Score(0.85f, "Evening forecast for tomorrow's planning")
            else           -> Score(0.55f, "Live weather at a glance")
        }

        scores[WidgetType.BATTERY] = when {
            batteryPct <= 20 && !charging -> Score(0.98f, "Battery critically low — monitor it now")
            charging                      -> Score(0.88f, "Currently charging — track the progress")
            batteryPct <= 40              -> Score(0.72f, "Battery is getting low")
            else                          -> Score(0.40f, "Battery status overview")
        }

        val musicPkgs = setOf(
            "com.spotify.music", "com.apple.android.music", "com.google.android.music",
            "com.amazon.music", "com.pandora.android", "com.soundcloud.android",
            "com.google.android.apps.podcasts", "au.com.shiftyjelly.pocketcasts",
        )
        scores[WidgetType.MEDIA] = when {
            recent.any { it in musicPkgs } -> Score(0.91f, "Recently used a music app")
            hour in 7..9                   -> Score(0.65f, "Morning commute — quick media controls")
            hour in 17..20                 -> Score(0.60f, "Commute home — quick media controls")
            else                           -> Score(0.35f, "Media playback controls")
        }

        scores[WidgetType.CALENDAR] = when {
            !isWeekend && hour in 7..10  -> Score(0.93f, "Weekday morning — check your schedule")
            !isWeekend && hour in 13..14 -> Score(0.78f, "Post-lunch — afternoon meetings overview")
            isWeekend                    -> Score(0.42f, "Weekend agenda at a glance")
            else                         -> Score(0.55f, "Today's calendar events")
        }

        scores[WidgetType.STEPS] = when {
            moving && hour in 8..20 -> Score(0.87f, "You're on the move — track your steps")
            hour in 19..22          -> Score(0.70f, "Evening step count recap")
            else                    -> Score(0.40f, "Daily step tracking")
        }

        val financePkgs = setOf(
            "com.robinhood.android", "com.coinbase.android", "com.binance.dev",
            "com.etrade.android", "com.fidelity.android", "net.etrade.mobile",
        )
        val usedFinance = recent.any { it in financePkgs }
        val marketOpen  = !isWeekend && hour in 9..16
        scores[WidgetType.STOCK] = when {
            usedFinance && marketOpen -> Score(0.88f, "Finance app open + markets are live")
            usedFinance               -> Score(0.65f, "Finance app used recently")
            marketOpen                -> Score(0.52f, "Stock markets are open")
            else                      -> Score(0.22f, "Market overview")
        }

        scores[WidgetType.NEWS] = when {
            hour in 6..9   -> Score(0.84f, "Morning news briefing")
            hour in 12..13 -> Score(0.76f, "Lunchtime headline catch-up")
            else           -> Score(0.38f, "Latest headlines")
        }

        scores[WidgetType.STICKY_NOTE] = when {
            hour in 20..23 -> Score(0.72f, "Evening — jot down tomorrow's tasks")
            hour in 9..11  -> Score(0.60f, "Morning — capture ideas quickly")
            else           -> Score(0.35f, "Quick notes always at hand")
        }

        scores[WidgetType.FOCUS_TIMER] = when {
            !isWeekend && hour in 9..17 -> Score(0.80f, "Work hours — start a focus session")
            !isWeekend && hour in 7..9  -> Score(0.60f, "Prep a morning focus block")
            else                        -> Score(0.30f, "Pomodoro focus timer")
        }

        scores[WidgetType.WORLD_CLOCK] = when {
            moving        -> Score(0.74f, "Travelling — track multiple time zones")
            hour in 8..10 -> Score(0.55f, "Check international team hours")
            else          -> Score(0.28f, "Multiple time zone view")
        }

        scores[WidgetType.AFFIRMATION] = when {
            hour in 5..9 -> Score(0.78f, "Start your day with a motivational quote")
            else         -> Score(0.30f, "Daily inspiration")
        }

        scores[WidgetType.COUNTDOWN] = Score(0.32f, "Track upcoming events")

        return scores.entries
            .sortedByDescending { it.value.value }
            .mapIndexed { index, entry ->
                WidgetRecommendation(
                    type     = entry.key,
                    reason   = entry.value.reason,
                    score    = entry.value.value,
                    priority = index + 1,
                )
            }
    }

    /** Returns the top N widget recommendations for the current context. */
    fun topRecommendations(context: Context, count: Int = 3): List<WidgetRecommendation> =
        recommend(context).take(count)

    private fun getBatteryPercent(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return 100
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) 100 else level * 100 / scale
    }

    private fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun isUserMoving(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: return false
            val ageMs = System.currentTimeMillis() - loc.time
            ageMs < 5 * 60 * 1_000L && loc.speed > 0.5f
        } catch (_: SecurityException) {
            false
        }
    }

    private fun getRecentApps(context: Context): Set<String> {
        return try {
            val usm   = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end   = System.currentTimeMillis()
            val start = end - 2 * 60 * 60 * 1_000L
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .filter { it.totalTimeInForeground > 5_000L }
                .map { it.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
