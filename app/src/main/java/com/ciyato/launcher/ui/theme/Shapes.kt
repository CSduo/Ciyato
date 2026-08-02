package com.ciyato.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ciyato Shape System.
 * All corner radii must be sourced from here — never hardcode dp values in composables.
 */
object CiyatoShapes {
    /** 6dp — Chips, badges, small indicators */
    val extraSmall   = RoundedCornerShape(6.dp)
    /** 8dp — Inputs, toggles, small buttons */
    val small        = RoundedCornerShape(8.dp)
    /** 14dp — Standard cards */
    val medium       = RoundedCornerShape(14.dp)
    /** 20dp — Prominent cards, drawers */
    val large        = RoundedCornerShape(20.dp)
    /** 28dp — Hero cards, major panels */
    val extraLarge   = RoundedCornerShape(28.dp)
    /** 28dp — Full panel containers */
    val massive      = RoundedCornerShape(28.dp)
    /** 999dp — Pills, circular badges */
    val full         = RoundedCornerShape(999.dp)
    /** 22dp — App icons specifically */
    val appIcon      = RoundedCornerShape(22.dp)
    /** Circle — Avatars, action buttons */
    val circle       = CircleShape
    /** Bottom sheet — top corners only */
    val bottomSheet  = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Top card — bottom corners only */
    val topCard      = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
}

// ─── Glass Morph Card ─────────────────────────────────────────────────────────

/**
 * Premium glassmorphism container.
 * Renders a translucent card with gradient background, subtle border, and content.
 *
 * @param glassAlpha Opacity of the glass tint (0.06–0.20 recommended)
 * @param borderAlpha Opacity of the border highlight
 * @param cornerRadius Corner radius from CiyatoShapes
 */
@Composable
fun GlassMorphCard(
    modifier: Modifier = Modifier,
    glassAlpha: Float = 0.08f,
    borderAlpha: Float = 0.12f,
    cornerRadius: RoundedCornerShape = CiyatoShapes.large,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(cornerRadius)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glassAlpha),
                        Color.White.copy(alpha = glassAlpha * 0.5f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.White.copy(alpha = borderAlpha * 0.3f)
                    )
                ),
                shape = cornerRadius
            ),
        content = content
    )
}

/**
 * Standard elevated dark card — the most common card in Ciyato.
 */
@Composable
fun ElevatedDarkCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CiyatoShapes.large,
    borderColor: Color = CiyatoSubtleBorder,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(CiyatoBgEl)
            .border(1.dp, borderColor, shape),
        content = content
    )
}

/**
 * Accent-bordered card — used for highlighted/featured items.
 */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    accentColor: Color = CiyatoGold,
    shape: RoundedCornerShape = CiyatoShapes.large,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.08f),
                        CiyatoBgEl
                    )
                )
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.25f),
                shape = shape
            ),
        content = content
    )
}

/**
 * Pill-shaped outline badge — for labels, status, and filter chips.
 */
@Composable
fun PillBadge(
    modifier: Modifier = Modifier,
    color: Color = CiyatoGold,
    filled: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(CiyatoShapes.full)
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.25f), CiyatoShapes.full),
        content = content
    )
}
