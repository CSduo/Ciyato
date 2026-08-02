package com.ciyato.launcher.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * MaterialYouSupport — Suggestion #90
 * Supports Material You / Monet dynamic color theming on Android 12+ (API 31+).
 * Falls back to Ciyato's branded dark/light scheme on older devices.
 */

private val CiyatoDarkColorScheme = darkColorScheme(
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
    error = Color(0xFFEF4444),
    onError = Color.White,
    outline = CiyatoBorder,
)

private val CiyatoLightColorScheme = lightColorScheme(
    primary = Color(0xFFB8882D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF0C8),
    onPrimaryContainer = Color(0xFF3D2C00),
    secondary = Color(0xFF4A6580),
    onSecondary = Color.White,
    surface = Color(0xFFF8F9FA),
    onSurface = Color(0xFF0D1117),
    background = Color(0xFFF0F2F5),
    onBackground = Color(0xFF0D1117),
    error = Color(0xFFD32F2F),
    onError = Color.White,
)

@Composable
fun ciyatoColorScheme(
    darkMode: String = "auto",
    dynamicColor: Boolean = true,
): ColorScheme {
    val context = LocalContext.current
    val isDark = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark()
    }
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    return when {
        supportsDynamic && isDark -> dynamicDarkColorScheme(context)
        supportsDynamic && !isDark -> dynamicLightColorScheme(context)
        isDark -> CiyatoDarkColorScheme
        else -> CiyatoLightColorScheme
    }
}

@Composable
private fun isSystemDark(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}
