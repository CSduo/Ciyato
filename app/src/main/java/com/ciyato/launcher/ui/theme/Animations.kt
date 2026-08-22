package com.ciyato.launcher.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

/**
 * Ciyato Motion System.
 * All animation specs used across the app must reference constants defined here.
 * Never hardcode tween/spring values in composables — always use these.
 */
object CiyatoMotion {

    // ─── Spring Specs ─────────────────────────────────────────────────────────
    /** Standard spring: fluid, responsive */
    val spring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium
    )

    /** Enter spring: slightly bouncy entrance */
    val enterSpring = spring<Float>(
        dampingRatio = 0.75f,
        stiffness    = 300f
    )

    /** Tight spring: no bounce, quick settle */
    val tightSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness    = Spring.StiffnessHigh
    )

    /** Gentle spring: slow, floaty */
    val gentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness    = Spring.StiffnessLow
    )

    /** Offset spring for spatial transitions */
    val offsetSpring = spring<IntOffset>(
        dampingRatio = 0.8f,
        stiffness    = 350f
    )

    // ─── Tween Specs ──────────────────────────────────────────────────────────
    /** Quick transition: 150ms */
    val quick = tween<Float>(150, easing = FastOutLinearInEasing)

    /** Standard transition: 250ms */
    val standard = tween<Float>(250, easing = FastOutSlowInEasing)

    /** Emphasis: 350ms */
    val emphasis = tween<Float>(350, easing = FastOutSlowInEasing)

    /** Slow: 500ms for major screen transitions */
    val slow = tween<Float>(500, easing = LinearOutSlowInEasing)

    /** Exit: 200ms fast-out */
    val exit = tween<Float>(200, easing = FastOutLinearInEasing)

    // ─── Duration Constants ───────────────────────────────────────────────────
    const val QUICK_MS      = 150
    const val STANDARD_MS   = 250
    const val EMPHASIS_MS   = 350
    const val SLOW_MS       = 500
    const val EXIT_MS       = 200
    const val STAGGER_MS    = 40   // per-item stagger for list animations

    // ─── AnimatedVisibility Specs ─────────────────────────────────────────────
    val fadeIn: EnterTransition   = fadeIn(tween(STANDARD_MS, easing = FastOutSlowInEasing))
    val fadeOut: ExitTransition   = fadeOut(tween(EXIT_MS, easing = FastOutLinearInEasing))

    val slideInRight: EnterTransition = slideInHorizontally(
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing)
    ) { it } + fadeIn(tween(STANDARD_MS))

    val slideOutLeft: ExitTransition = slideOutHorizontally(
        animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing)
    ) { -it } + fadeOut(tween(EXIT_MS))

    val slideInLeft: EnterTransition = slideInHorizontally(
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing)
    ) { -it } + fadeIn(tween(STANDARD_MS))

    val slideOutRight: ExitTransition = slideOutHorizontally(
        animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing)
    ) { it } + fadeOut(tween(EXIT_MS))

    val slideInUp: EnterTransition = slideInVertically(
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing)
    ) { it / 2 } + fadeIn(tween(STANDARD_MS))

    val slideInDown: EnterTransition = slideInVertically(
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing)
    ) { -it / 2 } + fadeIn(tween(STANDARD_MS))

    val slideOutDown: ExitTransition = slideOutVertically(
        animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing)
    ) { it / 2 } + fadeOut(tween(EXIT_MS))

    val scaleIn: EnterTransition = scaleIn(
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing),
        initialScale = 0.85f
    ) + fadeIn(tween(STANDARD_MS))

    val scaleOut: ExitTransition = scaleOut(
        animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing),
        targetScale = 0.85f
    ) + fadeOut(tween(EXIT_MS))

    val expandIn: EnterTransition = expandVertically(
        animationSpec = tween(EMPHASIS_MS, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Top
    ) + fadeIn(tween(EMPHASIS_MS))

    val collapseOut: ExitTransition = shrinkVertically(
        animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing),
        shrinkTowards = Alignment.Top
    ) + fadeOut(tween(EXIT_MS))

    /** Bottom sheet emergence */
    val sheetEnter: EnterTransition = slideInVertically(
        animationSpec = tween(EMPHASIS_MS, easing = FastOutSlowInEasing)
    ) { it } + fadeIn(tween(EMPHASIS_MS))

    val sheetExit: ExitTransition = slideOutVertically(
        animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing)
    ) { it } + fadeOut(tween(EXIT_MS))

    // ─── Infinite Animation Specs ─────────────────────────────────────────────
    /** Pulse: gentle scale up/down for notifications, AI indicators */
    fun pulseSpec() = infiniteRepeatable<Float>(
        animation = tween(900, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )

    /** Breathing: slow in/out for ambient mode */
    fun breathingSpec() = infiniteRepeatable<Float>(
        animation = tween(2000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )

    /** Shimmer: fast left-to-right for skeleton loading */
    fun shimmerSpec() = infiniteRepeatable<Float>(
        animation = tween(1200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    )

    /** Orbit: continuous rotation for agent loading indicator */
    fun orbitSpec() = infiniteRepeatable<Float>(
        animation = tween(1500, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    )

    /** Scanning: back-and-forth for AI scanner animation */
    fun scanSpec() = infiniteRepeatable<Float>(
        animation = tween(1800, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )

    // ─── Navigation Page Transitions ──────────────────────────────────────────
    /** Standard push right: for forward navigation */
    val navEnterTransition: EnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing)
    ) + fadeIn(tween(STANDARD_MS))

    val navExitTransition: ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing)
    ) + fadeOut(tween(STANDARD_MS))

    val navPopEnterTransition: EnterTransition = slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = tween(STANDARD_MS, easing = FastOutSlowInEasing)
    ) + fadeIn(tween(STANDARD_MS))

    val navPopExitTransition: ExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(EXIT_MS, easing = FastOutLinearInEasing)
    ) + fadeOut(tween(EXIT_MS))

    // ─── Screen Transition Defaults ───────────────────────────────────────────
    val CiyatoScreenEnter: EnterTransition = fadeIn(animationSpec = spring()) + slideInVertically(
        animationSpec = spring(),
        initialOffsetY = { it / 8 }
    )

    val CiyatoScreenExit: ExitTransition = fadeOut() + slideOutVertically(
        targetOffsetY = { -it / 12 }
    )
}

// ─── Screen Transition Top-Level Tokens ───────────────────────────────────────
val CiyatoScreenEnter: EnterTransition = CiyatoMotion.CiyatoScreenEnter
val CiyatoScreenExit: ExitTransition = CiyatoMotion.CiyatoScreenExit

// ─── Reusable Animated Values ─────────────────────────────────────────────────

/**
 * The shared infinite-animation helpers, all of which now consult Reduce Motion.
 *
 * These back roughly seven call sites across buttons, navigation, iconography,
 * loading skeletons and FocusSessionScreen. Guarding them here rather than at
 * each caller is the point: a preference that has to be remembered at every use
 * site is a preference that gets forgotten, which is how seven of nine animated
 * files came to ignore it (F-167).
 *
 * Each rests at the value that looks deliberate rather than broken — a pulse at
 * its base scale, a glow at full brightness, a shimmer mid-sweep.
 */

/**
 * Returns a continuously pulsing Float between [minScale] and [maxScale].
 * Usage: val scale by rememberPulse()
 */
@Composable
fun rememberPulse(minScale: Float = 1f, maxScale: Float = 1.06f): State<Float> {
    if (LocalReduceMotion.current) return remember(minScale) { mutableStateOf(minScale) }
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = minScale,
        targetValue  = maxScale,
        animationSpec = CiyatoMotion.pulseSpec(),
        label = "pulse_scale"
    )
}

/**
 * Returns a continuously breathing Float for ambient glow animations.
 */
@Composable
fun rememberBreathing(min: Float = 0.6f, max: Float = 1f): State<Float> {
    // Rests at max, not min: this drives an ambient glow's alpha, and settling
    // at the dim end would leave the element looking disabled rather than still.
    if (LocalReduceMotion.current) return remember(max) { mutableStateOf(max) }
    val transition = rememberInfiniteTransition(label = "breathing")
    return transition.animateFloat(
        initialValue = min,
        targetValue  = max,
        animationSpec = CiyatoMotion.breathingSpec(),
        label = "breathing_alpha"
    )
}

/**
 * Returns a 0f→1f shimmer progress for skeleton loading effects.
 */
@Composable
fun rememberShimmer(): State<Float> {
    if (LocalReduceMotion.current) return remember { mutableStateOf(0.5f) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = CiyatoMotion.shimmerSpec(),
        label = "shimmer_offset"
    )
}

/**
 * Returns a 0°→360° rotation for orbit/spinner indicators.
 */
@Composable
fun rememberOrbit(): State<Float> {
    if (LocalReduceMotion.current) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "orbit")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = CiyatoMotion.orbitSpec(),
        label = "orbit_angle"
    )
}
