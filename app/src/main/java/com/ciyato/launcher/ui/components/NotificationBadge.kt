package com.ciyato.launcher.ui.components

import com.ciyato.launcher.services.CiyatoNotificationListenerService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoRed

/**
 * NotificationBadge — Suggestion #20
 * Shows notification count badge over an app icon.
 * Requires NotificationListenerService permission.
 */

@Composable
fun BadgedAppIcon(
    app: com.ciyato.launcher.data.InstalledApp,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    badgeCount: Int = 0,
    onClick: () -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopEnd) {
        AppIconView(app = app, size = size, onClick = onClick)
        if (badgeCount > 0) {
            NotificationBadge(count = badgeCount)
        }
    }
}

@Composable
fun NotificationBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val displayCount = if (count > 99) "99+" else count.toString()
    val badgeWidth = if (count > 9) 20.dp else 16.dp

    Box(
        modifier = modifier
            .widthIn(min = badgeWidth)
            .height(16.dp)
            .clip(CircleShape)
            .background(CiyatoRed)
            .border(1.5.dp, CiyatoBg, CircleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            displayCount,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 9.sp,
        )
    }
}

/**
 * Whether the person has granted notification access to Ciyato's listener.
 *
 * Points at [CiyatoNotificationListenerService], the one that is actually
 * declared in the manifest. It used to name a second, duplicate listener class
 * that lived in this file — neither was declared, so this returned false
 * forever and badge counts stayed empty no matter what the user did.
 */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false
    val cn = ComponentName(context, CiyatoNotificationListenerService::class.java)
    return flat.contains(cn.flattenToString())
}
