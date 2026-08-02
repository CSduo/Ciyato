package com.ciyato.launcher.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Focus session manager — Suggestion #75.
 * Tracks active focus sessions: start time, duration, blocked categories.
 * Integrates with LauncherViewModel to hide blocked categories during focus.
 */
object FocusSessionManager {

    data class FocusSession(
        val startedAt:       Long,
        val durationMs:      Long,
        val blockedCategories: List<AppCategory>,
    ) {
        val endsAt: Long   get() = startedAt + durationMs
        val isActive: Boolean get() = System.currentTimeMillis() < endsAt
        val remainingMs: Long get() = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val remainingMin: Int get() = (remainingMs / 60_000).toInt()
        val remainingSec: Int get() = ((remainingMs % 60_000) / 1_000).toInt()
        val progressFraction: Float get() = 1f - (remainingMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    private val _activeSession = MutableStateFlow<FocusSession?>(null)
    val activeSession: StateFlow<FocusSession?> = _activeSession.asStateFlow()

    private var timerJob: Job? = null

    fun startSession(
        durationMin: Int,
        blockedCategories: List<AppCategory>,
        scope: CoroutineScope,
    ) {
        timerJob?.cancel()
        _activeSession.value = FocusSession(
            startedAt          = System.currentTimeMillis(),
            durationMs         = durationMin * 60_000L,
            blockedCategories  = blockedCategories,
        )
        timerJob = scope.launch {
            while (_activeSession.value?.isActive == true) {
                delay(1_000L)
                // Trigger recomposition by re-emitting same object
                _activeSession.value = _activeSession.value?.copy()
            }
            _activeSession.value = null
        }
    }

    fun endSession() {
        timerJob?.cancel()
        _activeSession.value = null
    }

    fun isBlocked(category: AppCategory): Boolean =
        _activeSession.value?.let { it.isActive && category in it.blockedCategories } ?: false
}
