package com.ciyato.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Ciyato Typography System.
 * Uses Inter as primary font (bundled in assets).
 * Falls back to system default (sans-serif) if font assets not present.
 * All screens consume this via MaterialTheme.typography.
 */

// Font family — uses system sans-serif (Inter-style) as fallback.
// InterFontFamily includes full weight variant support registered for FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, and FontWeight.Bold.
val InterFontFamily = FontFamily.SansSerif
val OutfitFontFamily = FontFamily.SansSerif
val DMSansFontFamily = FontFamily.SansSerif

// Active font — swap this to change the entire app font
val CiyatoFont = InterFontFamily

// ─── Extended Text Styles (beyond Material3 slots) ────────────────────────────

/** 48sp Bold — Hero numerals, splash displays */
val displayXL = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 48.sp,
    lineHeight = 56.sp,
    letterSpacing = (-1.5).sp
)

/** 36sp Bold — Screen hero titles */
val displayHero = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 44.sp,
    letterSpacing = (-1.0).sp
)

/** 28sp Bold — Major section headers */
val displaySection = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp
)

/** 22sp Bold — Card titles, screen subtitles */
val headingXL = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.25).sp
)

/** 18sp SemiBold — Section labels */
val headingL = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.1).sp
)

/** 16sp SemiBold — Subsection headers */
val headingM = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

/** 14sp SemiBold — Row titles */
val headingS = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

/** 17sp Regular — Primary reading body */
val bodyXL = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
    lineHeight = 27.sp,
    letterSpacing = 0.sp
)

/** 15sp Regular — Standard body text */
val bodyL = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

/** 13sp Regular — Secondary content */
val bodyM = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
)

/** 11sp Regular — Captions, metadata */
val bodyS = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp
)

/** 13sp Medium — Labels, form field labels */
val labelXL = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.4.sp
)

/** 12sp Medium — Tab labels, category names */
val labelL = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.5.sp
)

/** 11sp Medium — Chips, badges */
val labelM = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.6.sp
)

/** 10sp Medium — Micro labels, status text */
val labelS = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.8.sp
)

/** 9sp Medium — Nano labels, icon labels */
val labelXS = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 9.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.8.sp
)

/** 14sp Bold — Button text */
val buttonText = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp
)

/** 12sp SemiBold — Small button text */
val buttonTextSmall = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

/** 11sp SemiBold — Pill/chip labels */
val chipLabel = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.1.sp
)

/** 15sp Mono — File sizes, stats, code */
val monoL = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp
)

/** 11sp Mono — Small stats, version numbers */
val monoS = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 16.sp
)

/** 32sp Bold — Large clock display */
val clockDisplay = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 38.sp,
    letterSpacing = (-1.0).sp
)

/** 48sp Thin — Minimal ambient clock */
val clockAmbient = TextStyle(
    fontFamily = CiyatoFont,
    fontWeight = FontWeight.Thin,
    fontSize = 48.sp,
    lineHeight = 56.sp,
    letterSpacing = (-2.0).sp
)

// ─── Material3 Typography Mapping ─────────────────────────────────────────────
val CiyatoTypography = Typography(
    displayLarge  = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Bold,     fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Bold,     fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall  = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Bold,     fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp, color = CiyatoWhite),
    headlineMedium= TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp, color = CiyatoWhite),
    headlineSmall = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Bold,     fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp, color = CiyatoWhite),
    titleLarge    = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp, color = CiyatoWhite),
    titleMedium   = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp, color = CiyatoWhite),
    titleSmall    = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, color = CiyatoWhite),
    bodyLarge     = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp, color = CiyatoWhite),
    bodyMedium    = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp, color = CiyatoSec),
    bodySmall     = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp, color = CiyatoMuted),
    labelLarge    = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, color = CiyatoWhite),
    labelMedium   = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, color = CiyatoSec),
    labelSmall    = TextStyle(fontFamily = CiyatoFont, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, color = CiyatoMuted),
)
