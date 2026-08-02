package com.ciyato.launcher.services

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.FocusSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Quick Settings Tile — Focus Mode (#82).
 *
 * Appears in the Android Quick Settings panel as "Focus Mode".
 * Tapping it starts or ends a focus session without opening the launcher UI.
 *
 * Default session: 25 minutes, blocking SOCIAL + ENTERTAINMENT + GAMES.
 *
 * Tile states:
 *  STATE_ACTIVE   → session running (tap to end)
 *  STATE_INACTIVE → no session     (tap to start)
 */
@RequiresApi(Build.VERSION_CODES.N)
class CiyatoFocusTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
    }

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val session = FocusSessionManager.activeSession.value
        val scope = serviceScope ?: return
        if (session != null && session.isActive) {
            FocusSessionManager.endSession()
        } else {
            FocusSessionManager.startSession(
                durationMin       = DEFAULT_FOCUS_MIN,
                blockedCategories = defaultBlockedCategories(),
                scope             = scope,
            )
        }
        // Allow FocusSessionManager to propagate state, then refresh tile.
        handler.postDelayed({ syncTileState() }, 100L)
    }

    override fun onStopListening() {
        super.onStopListening()
        handler.removeCallbacksAndMessages(null)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun syncTileState() {
        val tile = qsTile ?: return
        val isActive = FocusSessionManager.activeSession.value?.isActive == true
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isActive) "Focus ON" else "Focus Mode"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "Tap to end" else "${DEFAULT_FOCUS_MIN} min"
        }
        tile.updateTile()
    }

    private fun defaultBlockedCategories(): List<AppCategory> =
        listOf(AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.GAMES)

    companion object {
        private const val DEFAULT_FOCUS_MIN = 25
    }
}
