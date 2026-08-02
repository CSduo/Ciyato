package com.ciyato.launcher.ui.screens

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * SmartReplyScreen — Suggestion #43
 * Smart reply suggestions for notification actions.
 * Provides context-aware quick-reply options based on message content.
 * (Production: use ML Kit Smart Reply API for neural suggestions.)
 */

data class NotificationWithReplies(
    val packageName: String,
    val appLabel: String,
    val messageText: String,
    val sender: String,
    val suggestedReplies: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartReplyScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val sampleNotifications = remember {
        listOf(
            NotificationWithReplies(
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
                messageText = "Are you free for coffee tomorrow?",
                sender = "Alex",
                suggestedReplies = generateSmartReplies("Are you free for coffee tomorrow?"),
            ),
            NotificationWithReplies(
                packageName = "com.google.android.apps.messaging",
                appLabel = "Messages",
                messageText = "Can you send me the document?",
                sender = "Work",
                suggestedReplies = generateSmartReplies("Can you send me the document?"),
            ),
            NotificationWithReplies(
                packageName = "com.slack",
                appLabel = "Slack",
                messageText = "Meeting at 3pm today?",
                sender = "#general",
                suggestedReplies = generateSmartReplies("Meeting at 3pm today?"),
            ),
        )
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Smart Replies", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "AI-suggested replies for recent messages",
                    color = CiyatoMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(sampleNotifications, key = { it.packageName + "_" + it.sender }) { notif ->
                SmartReplyCard(notif)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = CiyatoMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Smart replies are generated on-device. No message content leaves your phone.",
                            color = CiyatoMuted, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartReplyCard(notif: NotificationWithReplies) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = CiyatoGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(notif.appLabel, color = CiyatoGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(notif.sender, color = CiyatoMuted, fontSize = 11.sp)
            }
            Text("\"${notif.messageText}\"", color = CiyatoWhite, fontSize = 13.sp)
            Text("Suggested replies:", color = CiyatoMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                notif.suggestedReplies.take(3).forEach { reply ->
                    SuggestionChip(
                        onClick = {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Smart Reply", reply)
                            clipboardManager?.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied: \"$reply\"", Toast.LENGTH_SHORT).show()
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(notif.packageName)
                            if (launchIntent != null) {
                                runCatching { context.startActivity(launchIntent) }
                            }
                        },
                        label = { Text(reply, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = CiyatoGold.copy(alpha = 0.1f),
                            labelColor = CiyatoGold,
                        ),
                    )
                }
            }
        }
    }
}

private fun generateSmartReplies(message: String): List<String> {
    val lower = message.lowercase()
    return when {
        lower.contains("coffee") || lower.contains("lunch") || lower.contains("dinner") ->
            listOf("Sure!", "I'm free!", "Can't make it, sorry")
        lower.contains("meeting") || lower.contains("call") ->
            listOf("Yes, works for me", "Can we reschedule?", "I'll be there")
        lower.contains("document") || lower.contains("file") || lower.contains("send") ->
            listOf("On it!", "Will send shortly", "Can you resend the details?")
        lower.contains("?") ->
            listOf("Yes", "No", "Let me check")
        else ->
            listOf("Got it!", "Thanks!", "Will do")
    }
}
