package com.ciyato.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ciyato.launcher.ui.theme.*

/**
 * Ciyato Alert Dialog — styled dark dialog with primary/secondary actions.
 */
@Composable
fun CiyatoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    onConfirm: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconColor: Color = CiyatoGold,
    isDestructive: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .clip(CiyatoShapes.extraLarge)
                .background(CiyatoBgEl)
                .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.extraLarge)
                .padding(CiyatoSpacing.sp24),
            verticalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp16)
        ) {
            if (icon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f))
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
                }
            }
            Text(title, style = headingM, color = CiyatoWhite)
            Text(message, style = bodyM, color = CiyatoSec, lineHeight = 22.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp12, Alignment.End)
            ) {
                CiyatoGhostButton(text = dismissLabel, onClick = onDismiss)
                if (onConfirm != null) {
                    if (isDestructive) {
                        CiyatoDestructiveButton(text = confirmLabel, onClick = { onConfirm(); onDismiss() })
                    } else {
                        CiyatoButton(text = confirmLabel, onClick = { onConfirm(); onDismiss() })
                    }
                }
            }
        }
    }
}

/**
 * Ciyato Tips Banner — dismissible contextual tip card.
 */
@Composable
fun CiyatoTipBanner(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.TipsAndUpdates,
    accentColor: Color = CiyatoGold,
    actionLabel: String = "",
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.large)
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.20f), CiyatoShapes.large)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = bodyM, color = CiyatoWhite, lineHeight = 18.sp)
            if (actionLabel.isNotBlank()) {
                Text(
                    actionLabel,
                    style = labelL,
                    color = accentColor,
                    modifier = Modifier
                        .clickable(onClick = onAction)
                        .padding(top = 4.dp)
                )
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Dismiss", tint = CiyatoMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Ciyato Permission Card — styled permission request UI.
 */
@Composable
fun CiyatoPermissionCard(
    title: String,
    description: String,
    buttonLabel: String,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Shield,
    accentColor: Color = CiyatoGold,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.extraLarge)
            .background(accentColor.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.20f), CiyatoShapes.extraLarge)
            .padding(CiyatoSpacing.sp20),
        verticalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp12)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp12)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CiyatoShapes.medium)
                    .background(accentColor.copy(alpha = 0.12f))
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, style = headingS, color = CiyatoWhite)
                Text("Permission required", style = labelM, color = accentColor)
            }
        }
        Text(description, style = bodyM, color = CiyatoSec, lineHeight = 20.sp)
        CiyatoButton(
            text = buttonLabel,
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Uses Android system permissions", style = labelM, color = CiyatoMuted)
    }
}

/**
 * Loading placeholder shimmer card.
 */
@Composable
fun CiyatoShimmerCard(
    modifier: Modifier = Modifier,
    height: Int = 80,
) {
    val shimmer by rememberShimmer()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CiyatoShapes.large)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        CiyatoShimmer1,
                        CiyatoShimmer2,
                        CiyatoShimmer1,
                    ),
                    startX = shimmer * 1000f,
                    endX = shimmer * 1000f + 400f
                )
            )
    )
}
