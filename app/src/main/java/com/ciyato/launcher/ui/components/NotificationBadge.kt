package com.ciyato.launcher.ui.components

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

/**
 * NotificationBadge — Suggestion #20
 * Shows notification count badge over an app icon.
 * Requires NotificationListenerService permission.
 */

@Composable
fun BadgedAppIcon(
    app: com.ciyato.launcher.data.InstalledApp,
    size: Dp = 52.dp,
    badgeCount: Int = 0,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
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
            .background(Color(0xFFEF4444))
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

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false
    val cn = ComponentName(context, CiyatoNotificationListener::class.java)
    return flat.contains(cn.flattenToString())
}

/**
 * CiyatoNotificationListener — receives live notification events.
 * Must be registered in AndroidManifest.xml:
 *
 *   <service android:name=".ui.components.CiyatoNotificationListener"
 *       android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
 *       android:exported="true">
 *     <intent-filter>
 *       <action android:name="android.service.notification.NotificationListenerService"/>
 *     </intent-filter>
 *   </service>
 */
class CiyatoNotificationListener : NotificationListenerService() {

    companion object {
        private val _badgeCounts = mutableStateOf<Map<String, Int>>(emptyMap())
        val badgeCounts: State<Map<String, Int>> get() = _badgeCounts
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        rebuildCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        rebuildCounts()
    }

    private fun rebuildCounts() {
        try {
            val counts = activeNotifications
                .groupBy { it.packageName }
                .mapValues { (_, list) -> list.count { !it.isOngoing } }
            _badgeCounts.value = counts
        } catch (_: Exception) {}
    }
}
