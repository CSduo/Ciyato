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
