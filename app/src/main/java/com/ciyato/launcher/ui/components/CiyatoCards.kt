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
