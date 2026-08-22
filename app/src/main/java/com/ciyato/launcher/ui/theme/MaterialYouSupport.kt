package com.ciyato.launcher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Material 3 scheme behind Ciyato's fixed dark appearance.
 *
 * This file offered a light scheme and Material You dynamic schemes chosen by a
 * `darkMode` string. None of it was reachable: both Compose roots hard-coded
 * dark with dynamic colour off, so the selector had exactly one outcome. The
 * unreachable branches are gone rather than left to imply a choice the product
 * does not offer — see the contract on [CiyatoTheme].
 *
 * No Ciyato screen reads MaterialTheme.colorScheme; they paint from the fixed
 * palette in Color.kt. This scheme exists for Material's own components.
 */

internal val CiyatoDarkColorScheme = darkColorScheme(
    primary = CiyatoGold,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3D2C00),
    onPrimaryContainer = CiyatoGold,
    secondary = CiyatoSec,
    onSecondary = CiyatoBg,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = CiyatoSec,
    surface = CiyatoBg,
    onSurface = CiyatoWhite,
    surfaceVariant = CiyatoBgEl,
    onSurfaceVariant = CiyatoSec,
    background = CiyatoBg,
    onBackground = CiyatoWhite,
    error = CiyatoRed,
    onError = Color.White,
    outline = CiyatoBorder,
)

