package com.ciyato.launcher.services

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.data.FocusSessionManager
import com.ciyato.launcher.data.LauncherSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        val scope = serviceScope ?: return
        // Reads and writes the SAME persisted state the launcher UI uses, and
        // honours the person's configured duration instead of a hard-coded 25
        // minutes with hard-coded categories (F-176). Because the session is an
        // absolute end instant in DataStore, this service no longer owns its
        // lifetime — previously the ticker ran in this scope, which Android
        // cancels when the tile is destroyed, so a session started here never
        // ended (F-120).
        scope.launch {
            val settings = LauncherSettingsRepository(applicationContext)
            val current = FocusSessionManager.sessionOf(
                endsAt = settings.focusEndsAt.first(),
                durationMin = settings.focusDurationMin.first(),
                blockedCatsCsv = settings.focusBlockedCats.first(),
            )
            if (current != null && current.isActive) {
                settings.setFocusEndsAt(0L)
            } else {
                val minutes = settings.focusDurationMin.first().coerceIn(1, 120)
                settings.setFocusEndsAt(System.currentTimeMillis() + minutes * 60_000L)
            }
            syncTileState()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        handler.removeCallbacksAndMessages(null)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Reflects persisted state, so the tile agrees with the launcher UI. */
    private fun syncTileState() {
        val scope = serviceScope ?: return
        scope.launch {
            val tile = qsTile ?: return@launch
            val settings = LauncherSettingsRepository(applicationContext)
            val configuredMin = settings.focusDurationMin.first()
            val session = FocusSessionManager.sessionOf(
                endsAt = settings.focusEndsAt.first(),
                durationMin = configuredMin,
                blockedCatsCsv = settings.focusBlockedCats.first(),
            )
            val active = session != null && session.isActive
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (active) "Focus on" else "Focus"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = when {
                    active && session!!.remainingMin > 0 -> "${session.remainingMin} min left"
                    active -> "Ending"
                    else -> "$configuredMin min"
                }
            }
            tile.updateTile()
        }
    }
}
