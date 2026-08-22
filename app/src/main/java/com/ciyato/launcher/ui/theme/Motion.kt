package com.ciyato.launcher.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ciyato's motion policy.
 *
 * Reduce Motion was a Home-only setting in practice. Nine files ran infinite
 * animations and two consulted the preference — the two that had been fixed
 * individually, each by threading a `reduceMotion: Boolean` parameter down to
 * the composable that needed it. That is why the other seven never got it: the
 * mechanism required every intermediate composable to know and forward a flag
 * it had no other use for, so the default was always "keep animating" (F-167).
 *
 * A CompositionLocal inverts that. The preference is provided once at each
 * Compose root and any decorative animation can read it without a parameter,
 * so honouring it is the easy path rather than the diligent one.
 *
 * The distinction that matters is decorative versus essential. A shimmer, a
 * pulse, drifting rain — those exist to look alive and are what Reduce Motion
 * is for; they also run a frame every 16 ms forever, which costs battery on a
 * screen nobody is watching. A determinate progress bar communicates state and
 * stays.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * A value that animates back and forth forever — or holds still.
 *
 * Returns [restingValue] (defaulting to [initialValue]) when Reduce Motion is
 * on, and critically does not start an infinite transition at all in that case,
 * so there is no animation left running invisibly.
 */
@Composable
fun decorativePulse(
    targetValue: Float,
    durationMillis: Int,
    initialValue: Float = 1f,
    easing: Easing = FastOutSlowInEasing,
    repeatMode: RepeatMode = RepeatMode.Reverse,
    restingValue: Float = initialValue,
    label: String = "decorative_pulse",
): Float {
    if (LocalReduceMotion.current) return restingValue
    val transition = rememberInfiniteTransition(label = label)
    val value by transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = easing), repeatMode),
        label = label,
    )
    return value
}

/**
 * A 0..1 value that sweeps in one direction forever — shimmer highlights,
 * falling particles, orbiting glows. Holds at [restingValue] when Reduce
 * Motion is on.
 */
@Composable
fun decorativeSweep(
    durationMillis: Int,
    initialValue: Float = 0f,
    targetValue: Float = 1f,
    easing: Easing = androidx.compose.animation.core.LinearEasing,
    restingValue: Float = initialValue,
    label: String = "decorative_sweep",
): Float = decorativePulse(
    targetValue = targetValue,
    durationMillis = durationMillis,
    initialValue = initialValue,
    easing = easing,
    repeatMode = RepeatMode.Restart,
    restingValue = restingValue,
    label = label,
)

/** True when decorative motion should be suppressed. */
val reduceMotionEnabled: Boolean
    @Composable get() = LocalReduceMotion.current
