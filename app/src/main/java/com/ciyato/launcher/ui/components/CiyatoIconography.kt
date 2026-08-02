package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/** Standard icon sizes */
object IconSize {
    val Micro  = 12.dp
    val Small  = 16.dp
    val Medium = 20.dp
    val Large  = 24.dp
    val XL     = 32.dp
    val XXL    = 48.dp
}

/**
 * Category Icon Badge — icon in a rounded colored square.
 * Used in category cards, file type indicators, app drawer.
 */
@Composable
fun CategoryIconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = IconSize.Medium,
    shape: androidx.compose.foundation.shape.RoundedCornerShape = CiyatoShapes.medium,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
    }
}

/**
 * Ciyato Logo Mark — the C✦ brand icon with gold accent.
 */
@Composable
fun CiyatoLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    backgroundColor: Color = Color(0xFF0B0F12),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CiyatoShapes.extraSmall)
            .background(backgroundColor)
            .border(1.dp, CiyatoGold.copy(alpha = 0.3f), CiyatoShapes.extraSmall)
    ) {
        Text(
            text = "C✦",
            color = CiyatoGold,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Notification count badge on icon.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun IconWithBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = CiyatoSec,
    iconSize: Dp = IconSize.Large,
    badgeCount: Int = 0,
) {
    Box(modifier = modifier) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
        if (badgeCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(CiyatoRed)
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    color = CiyatoWhite,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * AI Glow Icon — purple glowing icon for AI features.
 */
@Composable
fun AIGlowIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = IconSize.Large,
) {
    val breathing by rememberBreathing(0.5f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        CiyatoPurple.copy(alpha = breathing * 0.25f),
                        CiyatoPurple.copy(alpha = 0.05f)
                    )
                )
            )
    ) {
        Icon(icon, null, tint = CiyatoPurple.copy(alpha = 0.8f + breathing * 0.2f), modifier = Modifier.size(iconSize))
    }
}

/**
 * Status Dot — small colored circle indicating status.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulsing: Boolean = false,
) {
    val scale by if (pulsing) rememberPulse(0.9f, 1.2f) else androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    Box(
        modifier = modifier
            .size(size * scale)
            .clip(CircleShape)
            .background(color)
    )
}
