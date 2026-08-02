package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * WhatsNewSheet — Suggestion #106
 * Bottom sheet changelog shown on first launch after an app update.
 * Version check is done via BuildConfig.VERSION_CODE stored in SharedPreferences.
 */

data class ChangelogItem(
    val emoji: String,
    val title: String,
    val description: String,
    val isHighlight: Boolean = false,
)

private val WHATS_NEW_ITEMS = listOf(
    ChangelogItem("✨", "AI Cleanup Suggestions", "Ciyato now detects unused apps and recommends what to uninstall.", isHighlight = true),
    ChangelogItem("🗓", "Calendar Integration", "Today's events appear on your home screen in real time."),
    ChangelogItem("🔒", "App Lock", "Lock any app with biometrics — perfect for private apps."),
    ChangelogItem("📊", "Screen Time Dashboard", "See per-app screen time right inside Ciyato."),
    ChangelogItem("🎨", "Material You Support", "Launcher now adapts to your Android 12+ wallpaper colors."),
    ChangelogItem("🛡", "Privacy Dashboard", "Review which apps have access to camera, mic, and location."),
    ChangelogItem("↩️", "Undo for Hide/Delete", "Made a mistake? Tap Undo on any destructive action."),
    ChangelogItem("📡", "Data Usage Tracker", "See which apps are consuming the most mobile data."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CiyatoBgEl,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("What's New ✨", color = CiyatoWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Version 1.2.0 — Jun 2026", color = CiyatoMuted, fontSize = 13.sp)
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(WHATS_NEW_ITEMS) { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isHighlight) Color(0xFF1E293B) else CiyatoBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(item.emoji, fontSize = 22.sp, modifier = Modifier.padding(top = 2.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp)
                                Text(item.description, color = CiyatoSec, fontSize = 13.sp,
                                    lineHeight = 18.sp)
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Got it! 🎉", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

fun shouldShowWhatsNew(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("ciyato_version", android.content.Context.MODE_PRIVATE)
    val lastSeen = prefs.getInt("last_seen_version_code", 0)
    val current = try {
        context.packageManager.getPackageInfo(context.packageName, 0).let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                it.longVersionCode.toInt()
            else
                @Suppress("DEPRECATION") it.versionCode
        }
    } catch (_: Exception) { 0 }
    return current > lastSeen
}

fun markWhatsNewSeen(context: android.content.Context) {
    val prefs = context.getSharedPreferences("ciyato_version", android.content.Context.MODE_PRIVATE)
    val current = try {
        context.packageManager.getPackageInfo(context.packageName, 0).let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                it.longVersionCode.toInt()
            else
                @Suppress("DEPRECATION") it.versionCode
        }
    } catch (_: Exception) { 0 }
    prefs.edit().putInt("last_seen_version_code", current).apply()
}
