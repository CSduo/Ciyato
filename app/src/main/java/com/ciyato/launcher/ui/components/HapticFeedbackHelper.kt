package com.ciyato.launcher.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback helpers — Suggestion #1.
 *
 * Provides two levels of haptic feedback:
 *  - Light: `LongPress` — for card taps, selection changes
 *  - Medium: `GestureEnd` — for confirmations, toggles
 *  - Heavy: `REJECT` — for blocked actions (Focus Mode, errors)
 *
 * Always respects the system's accessibility haptic settings.
 * The ViewModel's `hapticFeedback` preference gates usage.
 */

@Composable
fun rememberCiyatoHaptic(enabled: Boolean = true): CiyatoHaptic {
    val systemHaptic = LocalHapticFeedback.current
    val view         = LocalView.current
    return remember(enabled) { CiyatoHaptic(systemHaptic, view, enabled) }
}

class CiyatoHaptic(
    private val haptic: HapticFeedback,
    private val view: View,
    private val enabled: Boolean,
) {
    /** Light tap — app launches, card taps. */
    fun tap() {
        if (!enabled) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Medium confirmation — toggle switches, selections. */
    fun confirm() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** Heavy rejection — Focus Mode block, errors. */
    fun reject() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Long-press context menu open. */
    fun longPress() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Keyboard key press (for search). */
    fun keyPress() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
