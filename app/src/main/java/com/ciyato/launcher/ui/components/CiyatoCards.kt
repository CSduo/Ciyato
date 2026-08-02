package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * KPI Card — single metric with trend indicator.
 * Most commonly used in dashboards and stats screens.
 */
@Composable
fun CiyatoKPICard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    accentColor: Color = CiyatoGold,
    icon: ImageVector? = null,
    trendUp: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(CiyatoShapes.large)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(CiyatoSpacing.cardPad),
        verticalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp8)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = labelL, color = CiyatoSec)
            if (icon != null) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
        }
        Text(value, style = headingXL, color = CiyatoWhite)
        if (subtitle.isNotBlank() || trendUp != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (trendUp != null) {
                    Icon(
                        if (trendUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        null,
                        tint = if (trendUp) CiyatoGreen else CiyatoRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (subtitle.isNotBlank()) Text(subtitle, style = bodyS, color = CiyatoMuted)
            }
        }
    }
}

/**
 * Stat Row — 3 KPI values side-by-side.
 */
@Composable
fun CiyatoStatRow(
    stats: List<Triple<String, String, Color>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.large)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large)
            .padding(CiyatoSpacing.cardPad),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stats.forEachIndexed { idx, (label, value, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(label, style = labelM, color = CiyatoMuted)
            }
            if (idx < stats.size - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(CiyatoDivider)
                )
            }
        }
    }
}

/**
 * Info Row Card — icon + title + subtitle + optional trailing.
 * Used in settings, detail screens.
 */
@Composable
fun CiyatoListCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    icon: ImageVector? = null,
    iconColor: Color = CiyatoGold,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.large)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(CiyatoSpacing.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiyatoSpacing.iconText)
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CiyatoShapes.medium)
                    .background(iconColor.copy(alpha = 0.12f))
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = headingS, color = CiyatoWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = bodyM, color = CiyatoMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing?.invoke()
    }
}

/**
 * Section Header — label on left + optional action on right.
 */
@Composable
fun CiyatoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "",
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = headingM, color = CiyatoWhite)
        if (actionLabel.isNotBlank()) {
            Text(
                actionLabel,
                style = labelL,
                color = CiyatoGold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

/**
 * Empty State — illustration + title + subtitle + optional action.
 */
@Composable
fun CiyatoEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    actionLabel: String = "",
    onAction: () -> Unit = {},
    iconColor: Color = CiyatoGold,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp12)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.10f))
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(36.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = headingM, color = CiyatoWhite)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = bodyM, color = CiyatoMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (actionLabel.isNotBlank()) {
            CiyatoSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * Error State — red icon + message + retry.
 */
@Composable
fun CiyatoErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp12)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CiyatoErrorContainer)
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = CiyatoRed, modifier = Modifier.size(30.dp))
        }
        Text(message, style = bodyM, color = CiyatoSec)
        if (onRetry != null) {
            CiyatoGhostButton(text = "Retry", onClick = onRetry, leadingIcon = Icons.Default.Refresh)
        }
    }
}

/**
 * Badge — small colored pill with text.
 */
@Composable
fun CiyatoBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CiyatoGold,
    filled: Boolean = false,
) {
    val bg = if (filled) color else color.copy(alpha = 0.15f)
    val textColor = if (filled) CiyatoBg else color
    Text(
        text = text,
        color = textColor,
        style = labelM,
        modifier = modifier
            .clip(CiyatoShapes.full)
            .background(bg)
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.3f), CiyatoShapes.full)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * Progress Card — bar progress with label and percentage.
 */
@Composable
fun CiyatoProgressCard(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    accentColor: Color = CiyatoGold,
    progressLabel: String = "${(progress * 100).toInt()}%",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.large)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large)
            .padding(CiyatoSpacing.cardPad),
        verticalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp10)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = headingS, color = CiyatoWhite)
            Text(progressLabel, style = labelL, color = accentColor)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CiyatoShapes.full),
            color = accentColor,
            trackColor = CiyatoBgEl2
        )
        if (subtitle.isNotBlank()) Text(subtitle, style = bodyS, color = CiyatoMuted)
    }
}
