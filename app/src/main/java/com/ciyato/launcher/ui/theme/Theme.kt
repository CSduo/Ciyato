package com.ciyato.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * Ciyato's one appearance.
 *
 * This took `darkMode` and `dynamicColor` parameters, and both Compose roots
 * passed "dark" and false — the only two call sites in the app. Everything else
 * behind them (a light scheme, Material You dynamic schemes, a system-follows
 * branch) was unreachable, while the preference feeding it stayed writable, so a
 * user could change an appearance setting, watch it persist, and reasonably
 * conclude the app was broken (F-036).
 *
 * The product is deliberately dark: near-black, graphite surfaces, silver
 * accent, and every screen paints from that fixed palette rather than from
 * MaterialTheme's scheme. Rather than keep machinery that implies a choice
 * nobody can make, the contract is stated here. Full light coverage would mean
 * theming ~150 palette references across every screen — a real project, not a
 * flag, and it is recorded as such rather than half-wired.
 */
@Composable
fun CiyatoTheme(
    font: String = "sans",
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
        // Material 3's own components (dialogs, sliders, text fields, ripples)
        // read this even though no Ciyato screen does.
        colorScheme = CiyatoDarkColorScheme,
        typography = typography,
        content = content,
    )
}
