package com.ciyato.launcher.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ─── Ciyato Dark Premium Palette ─────────────────────────────────────────────
val CiyatoBg          = Color(0xFF06080A)   // black reference background
val CiyatoBgEl        = Color(0xFF101316)   // elevated glass cards
val CiyatoBgEl2       = Color(0xFF191D21)   // second-level graphite
val CiyatoBgEl3       = Color(0xFF242A30)   // third-level graphite
val CiyatoActiveTab   = Color(0xFF252A2F)   // active tab state background
val CiyatoCardHover   = Color(0xFF20262B)   // card pressed/hover state

// Glass surfaces
val CiyatoGlassCard   = Color(0x0FFFFFFF)   // rgba(255,255,255,0.06)
val CiyatoGlassStr    = Color(0x17FFFFFF)   // rgba(255,255,255,0.09)
val CiyatoGlassMed    = Color(0x1FFFFFFF)   // rgba(255,255,255,0.12)
val CiyatoGlassHeavy  = Color(0x2BFFFFFF)   // rgba(255,255,255,0.17)
val CiyatoGlassOverlay= Color(0x08FFFFFF)   // very subtle glass overlay

// Borders
val CiyatoBorder      = Color(0x1AFFFFFF)   // rgba(255,255,255,0.10)
val CiyatoSubtleBorder= Color(0x22FFFFFF)   // rgba(255,255,255,0.133)
val CiyatoStrongBorder= Color(0x26FFFFFF)   // rgba(255,255,255,0.15)
val CiyatoDivider     = Color(0x08FFFFFF)   // list dividers

// Text
val CiyatoWhite       = Color(0xFFF7F7F5)   // primary text
val CiyatoSec         = Color(0xFFC6C8C8)   // secondary text
val CiyatoMuted       = Color(0xFF8B9093)   // muted / metadata
val CiyatoDisabled    = Color(0xFF4B5054)   // disabled state

// Accents
val CiyatoGold        = Color(0xFFE8E8E4)   // silver accent (kept name for compatibility)
val CiyatoGoldSoft    = Color(0xFFFFFFFF)   // white highlight
val CiyatoGoldDark    = Color(0xFF7C807F)   // graphite gradient end
val CiyatoGoldDim     = Color(0x33E8E8E4)   // silver at 20% opacity
val CiyatoBlue        = Color(0xFFB9C7D6)   // cool silver-blue accent
val CiyatoBlueDark    = Color(0xFF7F91A2)   // deeper silver-blue
val CiyatoGreen       = Color(0xFF39C66A)   // green success
val CiyatoGreenDark   = Color(0xFF27A355)   // deep green
val CiyatoRed         = Color(0xFFEF5350)   // red warning/danger
val CiyatoRedDark     = Color(0xFFD32F2F)   // deep red
val CiyatoPurple      = Color(0xFF9C6AFF)   // AI/Magic accent
val CiyatoAmber       = Color(0xFFD0C5AF)   // restrained warm neutral
val CiyatoCyan        = Color(0xFF51C7A5)   // teal / finance
val CiyatoRose        = Color(0xFFFF6B8C)   // social / entertainment
val CiyatoPeach       = Color(0xFFFF7A45)   // education / college
val CiyatoNeonBlue    = Color(0xFF00C6FF)   // neon highlight
val CiyatoNeonGold    = Color(0xFFFFFFFF)   // white highlight
val CiyatoAIGlow      = Color(0xFF9C6AFF)   // AI action glow color

// Weather Accent Colors
val CiyatoSunny       = Color(0xFFFFC947)  // warm amber golden
val CiyatoSunnyGlow   = Color(0xFFFFE08A)  // soft golden glow
val CiyatoRainy       = Color(0xFF5C9EEA)  // rich steel blue
val CiyatoStormy      = Color(0xFF394B6A)  // dark navy
val CiyatoSnowy       = Color(0xFFD4EFFF)  // icy pale blue
val CiyatoWindy       = Color(0xFF7EC8C8)  // teal-mint
val CiyatoCloudy      = Color(0xFFB0BAC9)  // soft slate gray
val CiyatoNight       = Color(0xFF1B2340)  // deep midnight navy
val CiyatoSunrise     = Color(0xFFFF8C42)  // warm orange sunrise
val CiyatoFoggy       = Color(0xFFCED5DC)  // soft fog

// Full-opacity semantics for the state containers below. These existed only as
// 10%-alpha container fills, so every screen that needed the solid colour for
// text or an icon re-typed the hex by hand and they drifted apart.
val CiyatoWarning     = Color(0xFFFFB347)   // caution / medium risk
val CiyatoInfo        = Color(0xFF7DB7FF)   // informational / links

// State containers (filled backgrounds for state cards)
val CiyatoErrorContainer   = Color(0x1AEF5350)
val CiyatoSuccessContainer = Color(0x1A39C66A)
val CiyatoWarningContainer = Color(0x1AFFB347)
val CiyatoInfoContainer    = Color(0x1A7DB7FF)
val CiyatoAIContainer      = Color(0x1A9C6AFF)

// Shimmer (skeleton loading)
val CiyatoShimmer1    = Color(0xFF1A2028)
val CiyatoShimmer2    = Color(0xFF22303D)

// Category Accent Colors
val CatWork          = Color(0xFF96A8BA)   // Work = silver blue
val CatSocial        = Color(0xFFA8A8A8)   // Social = silver
val CatFinance       = Color(0xFF9FB8AF)   // Finance = muted sage
val CatEntertainment = Color(0xFFA7A0B8)   // Entertainment = muted lavender
val CatCreativity    = Color(0xFFB7AA9D)   // Creativity = warm gray
val CatUtilities     = Color(0xFF8E9498)   // Utilities = graphite
val CatHealth        = Color(0xFF9FB8A4)   // Health = muted green
val CatTravel        = Color(0xFFA8B4BD)   // Travel = pale steel
val CatEducation     = Color(0xFFB8B1A4)   // Education = stone
val CatDaily         = Color(0xFFC8C8C4)   // Daily = bright silver
val CatGaming        = Color(0xFFA8A8A8)   // Gaming = silver
val CatShopping      = Color(0xFFA0B0AA)   // Shopping = muted teal

// ─── Gradient Brushes ─────────────────────────────────────────────────────────
val goldGradient = Brush.linearGradient(
    colors = listOf(CiyatoGoldSoft, CiyatoGoldDark)
)

val goldGradientVertical = Brush.verticalGradient(
    colors = listOf(CiyatoGoldSoft, CiyatoGoldDark)
)

val blueGlowGradient = Brush.radialGradient(
    colors = listOf(CiyatoBlue.copy(alpha = 0.3f), Color.Transparent)
)

val purpleAIGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF9C6AFF), Color(0xFF6A3FBF))
)

val darkCardGradient = Brush.verticalGradient(
    colors = listOf(CiyatoBgEl2, CiyatoBg)
)

val backgroundScrimGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, CiyatoBg.copy(alpha = 0.92f))
)

val glassSurfaceGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x17FFFFFF),
        Color(0x08FFFFFF)
    )
)

val homeScreenOverlayGradient = Brush.verticalGradient(
    colors = listOf(
        CiyatoBg.copy(alpha = 0.7f),
        Color.Transparent,
        CiyatoBg.copy(alpha = 0.85f)
    ),
    startY = 0f,
    endY = Float.POSITIVE_INFINITY
)

val aiAgentGradient = Brush.sweepGradient(
    colors = listOf(
        CiyatoPurple,
        CiyatoBlue,
        CiyatoGreen,
        CiyatoPurple
    )
)

val goldShimmerGradient = Brush.linearGradient(
    colors = listOf(
        CiyatoGold.copy(alpha = 0f),
        CiyatoGold.copy(alpha = 0.6f),
        CiyatoGold.copy(alpha = 0f)
    )
)
