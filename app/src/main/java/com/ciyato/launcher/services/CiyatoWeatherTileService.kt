package com.ciyato.launcher.services

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick Settings Tile — Weather Refresh (#82).
 *
 * Displays "Weather" in the Android Quick Settings panel.
 * Tapping the tile signals a weather refresh request; the actual data fetch
 * is handled by WeatherRepository + LauncherViewModel when the launcher
 * next comes to the foreground (or via a WorkManager job if configured).
 *
 * This tile intentionally does NOT hold a direct reference to the ViewModel
 * or any Coroutine scope — TileService callbacks are called on the main thread
 * and the service lifecycle is independent of the Activity.
 *
 * Tile states:
 *  STATE_ACTIVE      → weather data available
 *  STATE_UNAVAILABLE → refreshing in progress (brief, auto-resets)
 */
@RequiresApi(Build.VERSION_CODES.N)
class CiyatoWeatherTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        setTileActive("Weather")
    }

    override fun onClick() {
        super.onClick()
        // Show "refreshing" state briefly, then restore active.
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.label = "Refreshing…"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Please wait"
        }
        tile.updateTile()

        // Persist a flag so the launcher knows to refresh on next resume.
        flagRefreshRequested()

        // Reset tile after a brief UI delay.
        handler.postDelayed({ setTileActive("Weather") }, 2_000L)
    }

    override fun onStopListening() {
        super.onStopListening()
        handler.removeCallbacksAndMessages(null)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setTileActive(label: String) {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Tap to refresh"
        }
        tile.updateTile()
    }

    /**
     * Writes a lightweight SharedPreferences flag so the launcher's
     * WeatherViewModel knows to trigger a fresh fetch on next resume.
     * Uses a dedicated prefs file to avoid DataStore conflicts.
     */
    private fun flagRefreshRequested() {
        runCatching {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(KEY_REFRESH_REQUESTED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    companion object {
        const val PREFS_NAME              = "ciyato_tile_prefs"
        const val KEY_REFRESH_REQUESTED_AT = "weather_refresh_requested_at"
    }
}
