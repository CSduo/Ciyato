package com.ciyato.launcher.ui.screens

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * ContextualSuggestionsScreen — Suggestion #30
 * Shows app suggestions based on time-of-day usage patterns.
 */

data class AppSuggestion(
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val confidence: Float,
    val timeSlot: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualSuggestionsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsState()
    var suggestions by remember { mutableStateOf<List<AppSuggestion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasUsageStatsPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(apps, hasPermission) {
        suggestions = if (hasPermission) {
            buildContextualSuggestions(context, apps.map { it.packageName to it.label })
        } else {
            emptyList()
        }
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Smart Suggestions",
                subtitle = "Based on your usage patterns",
                onBack = onBack,
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CiyatoGold)
            }
            return@Scaffold
        }

        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("📊", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Usage Access Required", color = CiyatoWhite, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ciyato needs Usage Access permission to learn your app patterns and suggest the right app at the right time.",
                    color = CiyatoMuted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                ) {
                    Text("Grant Permission", color = Color.Black)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E2A1E))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Based on your patterns", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                        Text("Ciyato learns from your habits to surface the right apps at the right time.",
                            color = CiyatoMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }

            if (suggestions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📈", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Not enough data yet", color = CiyatoWhite, fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text("Use your phone for a few days — Ciyato will start learning your patterns.",
                                color = CiyatoMuted, fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                val grouped = suggestions.groupBy { it.timeSlot }
                grouped.forEach { (slot, slotSuggestions) ->
                    item {
                        Text(slot, color = CiyatoGold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    items(slotSuggestions, key = { it.packageName }) { suggestion ->
                        SuggestionCard(suggestion = suggestion, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: AppSuggestion, viewModel: LauncherViewModel) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Was a hand-rolled launch intent, which skipped both Focus
                // blocking and App Lock — a suggestion card could open an app
                // the rest of Ciyato refuses to open.
                if (!viewModel.launchPackage(suggestion.packageName)) {
                    android.widget.Toast.makeText(
                        context,
                        "${suggestion.appLabel} isn't installed any more",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2128)),
                contentAlignment = Alignment.Center,
            ) {
                Text(suggestion.appLabel.take(1), color = CiyatoGold, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(suggestion.appLabel, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(suggestion.reason, color = CiyatoMuted, fontSize = 12.sp)
            }
            Text("${(suggestion.confidence * 100).toInt()}%", color = CiyatoSec, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun buildContextualSuggestions(
    context: Context,
    apps: List<Pair<String, String>>,
): List<AppSuggestion> {
    val suggestions = mutableListOf<AppSuggestion>()
    try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, weekAgo, now)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeSlot = when (hour) {
            in 5..8 -> "Morning Routine ☀️"
            in 9..11 -> "Work Hours 💼"
            in 12..13 -> "Lunch Break 🍽"
            in 14..17 -> "Afternoon 🌤"
            in 18..20 -> "Evening 🌆"
            else -> "Late Night 🌙"
        }

        val topApps = stats.sortedByDescending { it.totalTimeInForeground }.take(5)
        val appMap = apps.toMap()
        topApps.forEach { stat ->
            val label = appMap[stat.packageName] ?: stat.packageName.split(".").last()
            val usageHours = stat.totalTimeInForeground / 3_600_000f
            if (usageHours > 0.1f) {
                suggestions.add(AppSuggestion(
                    packageName = stat.packageName,
                    appLabel = label,
                    reason = "Used ${String.format("%.1f", usageHours)}h this week — often at this time",
                    confidence = (usageHours / 2f).coerceIn(0.4f, 0.95f),
                    timeSlot = timeSlot,
                ))
            }
        }
    } catch (_: Exception) {}
    return suggestions
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
