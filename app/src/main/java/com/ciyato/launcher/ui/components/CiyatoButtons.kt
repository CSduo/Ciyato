package com.ciyato.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * Ciyato Primary Button — gold gradient with bold label.
 * The most prominent call-to-action in the design system.
 */
@Composable
fun CiyatoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "btn_scale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .clip(CiyatoShapes.full)
            .background(
                if (enabled) goldGradient else Brush.linearGradient(listOf(CiyatoDisabled, CiyatoDisabled))
            )
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled && !isLoading, onClick = onClick)
            .padding(horizontal = 24.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = CiyatoBg,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, tint = CiyatoBg, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = text,
                    color = if (enabled) CiyatoBg else CiyatoMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * Ciyato Secondary Button — outlined gold border, no fill.
 */
@Composable
fun CiyatoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = CiyatoGold,
    leadingIcon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, tween(120), label = "btn_scale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .clip(CiyatoShapes.full)
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.4f), CiyatoShapes.full)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, tint = accentColor, modifier = Modifier.size(18.dp))
            Text(text, color = if (enabled) accentColor else CiyatoMuted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

/**
 * Ciyato Ghost Button — text only, no background or border.
 */
@Composable
fun CiyatoGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = CiyatoGold,
    leadingIcon: ImageVector? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(CiyatoShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/**
 * Ciyato Icon Button — circle with icon inside.
 */
@Composable
fun CiyatoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    tint: Color = CiyatoSec,
    backgroundColor: Color = CiyatoBgEl,
    borderColor: Color = CiyatoSubtleBorder,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Ciyato AI Action Button — glowing purple circle for AI triggers.
 */
@Composable
fun CiyatoAIButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val pulse by rememberPulse(1f, 1.05f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(pulse)
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        CiyatoPurple.copy(alpha = 0.3f),
                        CiyatoPurple.copy(alpha = 0.1f)
                    )
                )
            )
            .border(1.dp, CiyatoPurple.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription, tint = CiyatoPurple, modifier = Modifier.size(size * 0.45f))
    }
}

/**
 * Ciyato Destructive Button — red warning action.
 */
@Composable
fun CiyatoDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(CiyatoShapes.full)
            .background(CiyatoRed.copy(alpha = 0.12f))
            .border(1.dp, CiyatoRed.copy(alpha = 0.3f), CiyatoShapes.full)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leadingIcon != null) Icon(leadingIcon, null, tint = CiyatoRed, modifier = Modifier.size(18.dp))
            Text(text, color = CiyatoRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/**
 * Toggle Chip — selectable pill chip used for filter rows.
 */
@Composable
fun CiyatoToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = CiyatoGold,
    leadingIcon: ImageVector? = null,
) {
    val bg by animateColorAsState(
        if (selected) selectedColor.copy(alpha = 0.15f) else CiyatoBgEl,
        label = "chip_bg"
    )
    val border by animateColorAsState(
        if (selected) selectedColor.copy(alpha = 0.4f) else CiyatoSubtleBorder,
        label = "chip_border"
    )
    val textColor by animateColorAsState(
        if (selected) selectedColor else CiyatoSec,
        label = "chip_text"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(CiyatoShapes.full)
            .background(bg)
            .border(1.dp, border, CiyatoShapes.full)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, tint = textColor, modifier = Modifier.size(14.dp))
        Text(text, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

/**
 * Small compact action button — used in card trailing areas.
 */
@Composable
fun CiyatoCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = CiyatoGold,
) {
    Text(
        text = text,
        color = if (color == CiyatoGold) CiyatoBg else CiyatoWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier
            .clip(CiyatoShapes.full)
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
