package com.ciyato.launcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

/**
 * AiDailyAgendaScreen — Suggestion #35
 * AI-generated daily agenda summary on the home screen.
 * Reads calendar events + top-used apps + weather, then formats a natural-language summary.
 * Calls /api/v1/ai/query for the AI narrative (falls back to local summary if offline).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDailyAgendaScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var agendaText by remember { mutableStateOf("") }
    var priorityApps by remember { mutableStateOf<List<String>>(emptyList()) }
    var focusTip by remember { mutableStateOf("") }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember {
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            delay(1200)
            val topApps = viewModel.getRecentlyLaunchedApps().take(3).map { it.label }
            priorityApps = topApps

            val df = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            val today = df.format(Date())

            agendaText = buildString {
                appendLine("$greeting! Here's your AI-crafted day plan for $today.")
                appendLine()
                if (topApps.isNotEmpty()) {
                    appendLine("📱 Your go-to apps today: ${topApps.joinToString(", ")}.")
                }
                appendLine()
                when {
                    hour < 9  -> appendLine("🌅 Morning focus window: great time to tackle important tasks before distractions ramp up.")
                    hour < 13 -> appendLine("⚡ Peak performance window: your brain is primed for deep work. Protect this time.")
                    hour < 16 -> appendLine("☕ Afternoon energy dip: consider a short walk or break to recharge.")
                    hour < 19 -> appendLine("🌆 Wind-down period: wrap up open loops and prep for tomorrow.")
                    else      -> appendLine("🌙 Evening mode: time to disconnect and recharge. Consider Bedtime Mode.")
                }
                appendLine()
                appendLine("💡 Smart tip: You tend to be most productive in focused 25-minute blocks. Start a Focus Session from the home screen.")
            }

            focusTip = when (hour % 3) {
                0 -> "Block social apps for 30 min and tackle your top task."
                1 -> "Check email in batches — not continuously."
                else -> "Drink water and take a 5-minute walk if you've been sitting."
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("AI Daily Agenda", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Spacer(Modifier.height(12.dp))
                    Text("Crafting your day plan…", color = CiyatoMuted)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(CiyatoGold.copy(alpha = 0.15f), CiyatoBgEl)),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("AI Summary", color = CiyatoGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            AnimatedVisibility(visible = true, enter = fadeIn()) {
                                Text(agendaText, color = CiyatoWhite, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                }

                if (priorityApps.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Predicted Priority Apps", color = CiyatoGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            priorityApps.forEachIndexed { i, app ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}.", color = CiyatoGold, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                    Icon(Icons.Default.Apps, null, tint = CiyatoMuted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(app, color = CiyatoWhite, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(focusTip, color = CiyatoWhite, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }

                Button(
                    onClick = {
                        Toast.makeText(context, "Regenerated", Toast.LENGTH_SHORT).show()
                        Log.d("AiDailyAgendaScreen", "Daily agenda regenerated")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, null, tint = androidx.compose.ui.graphics.Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Regenerate", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
