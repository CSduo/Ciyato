package com.ciyato.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
fun CiyatoTheme(
    darkMode: String = "auto",
    font: String = "sans",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val fontFamily = when (font) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val typography = CiyatoTypography.copy(
        displayLarge = CiyatoTypography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = CiyatoTypography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = CiyatoTypography.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = CiyatoTypography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = CiyatoTypography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = CiyatoTypography.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = CiyatoTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = CiyatoTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = CiyatoTypography.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = CiyatoTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = CiyatoTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = CiyatoTypography.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = CiyatoTypography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = CiyatoTypography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = CiyatoTypography.labelSmall.copy(fontFamily = fontFamily),
    )
    MaterialTheme(
        colorScheme = ciyatoColorScheme(darkMode = darkMode, dynamicColor = dynamicColor),
        typography = typography,
        content = content,
    )
}
