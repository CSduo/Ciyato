package com.ciyato.launcher.ui.screens

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
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
import com.ciyato.launcher.data.UsageAverages
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * AiChangelogScreen — Suggestion #45
 * AI-generated summary of "what changed on your phone today":
 * new installs, significant usage changes, updates.
 */

data class PhoneChangeEntry(
    val emoji: String,
    val title: String,
    val description: String,
    val changeType: ChangeType,
)

enum class ChangeType { NEW_INSTALL, USAGE_UP, USAGE_DOWN, UPDATE, UNINSTALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChangelogScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<PhoneChangeEntry>>(emptyList()) }
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

    LaunchedEffect(hasPermission) {
        // A 600 ms sleep used to sit here to make a local UsageStats query feel
        // like "analysis" (F-125). Inventing latency to imply computation is the
        // same class of dishonesty as inventing the numbers.
        entries = if (hasPermission) buildChangelog(context) else emptyList()
        isLoading = false
    }

    val today = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()) }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Today's Summary",
                subtitle = today,
                onBack = onBack,
            )
        }
    ) { padding ->
        if (!isLoading && !hasPermission) {
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
                    "Ciyato needs Usage Access permission to summarize what changed on your phone today.",
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
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A1A)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Phone Digest", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(today, color = CiyatoMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CiyatoGold, strokeWidth = 3.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("Analyzing your day…", color = CiyatoMuted)
                        }
                    }
                }
                return@LazyColumn
            }

            if (entries.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("😌", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Quiet day", color = CiyatoWhite, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("No significant changes on your phone today.", color = CiyatoMuted)
                        }
                    }
                }
            } else {
                items(entries, key = { it.title }) { entry ->
                    ChangelogCard(entry)
                }
            }
        }
    }
}

@Composable
private fun ChangelogCard(entry: PhoneChangeEntry) {
    val accentColor = when (entry.changeType) {
        ChangeType.NEW_INSTALL -> CiyatoGreen
        ChangeType.USAGE_UP -> CiyatoInfo
        ChangeType.USAGE_DOWN -> CiyatoMuted
        ChangeType.UPDATE -> CiyatoGold
        ChangeType.UNINSTALL -> CiyatoRed
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Text(entry.emoji, fontSize = 20.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(entry.description, color = CiyatoSec, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

private fun buildChangelog(context: Context): List<PhoneChangeEntry> {
    val entries = mutableListOf<PhoneChangeEntry>()
    val pm = context.packageManager

    try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // The local calendar day, not a rolling 24 hours (F-126). "Today" that
        // silently includes yesterday afternoon is not what the word means, and
        // the number moves depending on what time you happen to look.
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Prior seven full days, ending where today begins, so today's own
        // partial usage is not folded into the average it is compared against.
        val weekAgo = todayStart - TimeUnit.DAYS.toMillis(7)

        val todayStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, todayStart, now)
        val weekStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, weekAgo, todayStart)

        // The baseline, and the number of days it is actually built from.
        //
        // Two separate bugs lived in this expression (F-124). associate{} kept
        // one arbitrary bucket per package and discarded the rest, so a "weekly
        // average" was a single day divided by seven. Summing fixed that and
        // left the divisor assumed: `/ 7f` on a phone holding three days of
        // history understates every average by 2.3x, and today then reads as a
        // surge against it. UsageAverages measures the divisor instead, and is
        // unit-tested because this arithmetic decides what the user is told.
        val baseline = UsageAverages.baseline(
            weekStats.map {
                UsageAverages.Bucket(
                    packageName = it.packageName,
                    foregroundMs = it.totalTimeInForeground,
                    timestampMs = it.firstTimeStamp,
                )
            },
        )
        val weekAvg = baseline.averageMsPerDay

        // Today can also span several buckets, so it is summed the same way.
        val todayTotals = HashMap<String, Long>()
        todayStats.forEach { stat ->
            todayTotals[stat.packageName] =
                (todayTotals[stat.packageName] ?: 0L) + stat.totalTimeInForeground
        }

        todayTotals.forEach { (packageName, todayMs) ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { return@forEach }

            // Below three observed days there is no baseline worth comparing
            // against, so nothing "unusual" is claimed. Silence beats a
            // confident percentage computed from one day of history.
            if (!baseline.isComparable) return@forEach

            val avg = weekAvg[packageName] ?: 0f
            val ratio = UsageAverages.ratio(todayMs, avg)
            val period = baseline.periodLabel()

            when {
                avg < 60_000 && todayMs > 300_000 -> entries.add(PhoneChangeEntry(
                    emoji = "🆕",
                    title = "First time using $label",
                    description = "First time you've opened $label in the last " +
                        "${baseline.daysCovered} days — ${todayMs / 60_000} min today.",
                    changeType = ChangeType.NEW_INSTALL,
                ))
                ratio != null && ratio > 2.5f && todayMs > 600_000 -> entries.add(PhoneChangeEntry(
                    emoji = "📈",
                    title = "$label usage surged",
                    description = "${(ratio * 100 - 100).toInt()}% more than your $period.",
                    changeType = ChangeType.USAGE_UP,
                ))
                ratio != null && ratio < 0.2f && avg > 300_000 -> entries.add(PhoneChangeEntry(
                    emoji = "📉",
                    title = "Less $label today",
                    description = "Well below your $period. Taking a break?",
                    changeType = ChangeType.USAGE_DOWN,
                ))
            }
        }

        try {
            val allApps = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            allApps.filter { it.firstInstallTime >= todayStart }.take(3).forEach { pkg ->
                // applicationInfo is nullable as of the API 36 SDK. Skipping the
                // entry is right: without it there is no label to show, and
                // asserting non-null here would crash on exactly the edge case
                // the platform introduced the nullability to describe.
                val appInfo = pkg.applicationInfo ?: return@forEach
                val label = pm.getApplicationLabel(appInfo).toString()
                entries.add(PhoneChangeEntry(
                    emoji = "📦",
                    title = "New install: $label",
                    description = "Installed today. ${pkg.packageName}",
                    changeType = ChangeType.NEW_INSTALL,
                ))
            }
        } catch (_: Exception) {}

    } catch (_: Exception) {}

    return entries.take(10)
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
