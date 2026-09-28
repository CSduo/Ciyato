package com.ciyato.launcher.ui.screens

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.RemoteException
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.concurrent.TimeUnit
import com.ciyato.launcher.data.PermissionRegistry
import com.ciyato.launcher.ui.components.SpecialAccessGate
import com.ciyato.launcher.ui.components.QueryFailureState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NetworkUsageScreen
 * Shows per-app mobile-data usage over the last 30 days.
 *
 * Deliberately NOT "the current billing cycle" — the query is a rolling 30-day
 * window and Android exposes no billing-cycle boundary to apps, so the doc used
 * to describe something the code never computed (F-155). The on-screen copy
 * already said 30 days; only this comment disagreed.
 */

data class AppNetworkStat(
    val appLabel: String,
    val uid: Int,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes get() = rxBytes + txBytes
}

@Composable
fun NetworkUsageScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    // Null means NetworkStatsManager would not answer. An empty list means it answered
    // and there genuinely was no mobile data. This screen used to say the second thing
    // for both, beside a confident 0 B total.
    var stats by remember { mutableStateOf<List<AppNetworkStat>?>(emptyList()) }
    var isLoading by remember { mutableStateOf(hasPermission) }

    // NetworkStatsManager.querySummary requires the same "Usage access" special
    // app-op as screen-time stats. Granting happens in system Settings, so
    // re-check on resume rather than relying on a runtime permission callback.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            // Off the main thread: querySummary is a binder call and the bucket walk
            // below it is a loop. Run inline in LaunchedEffect it blocked the frame and
            // never yielded, so the loading state could not render.
            stats = withContext(Dispatchers.IO) { getNetworkStats(context) }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    val totalRx = stats?.sumOf { it.rxBytes } ?: 0L
    val totalTx = stats?.sumOf { it.txBytes } ?: 0L

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Data Usage", onBack = onBack)
        }
    ) { padding ->
        if (!hasPermission) {
            SpecialAccessGate(
                capability = PermissionRegistry.usageAccess,
                icon = "📡",
                featureLine = "The mobile-data breakdown is per-app, and Android puts it behind this same switch.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Text("Reading data usage…", color = CiyatoMuted, fontSize = 14.sp)
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
            val current = stats
            if (current == null) {
                // The totals card is deliberately not drawn here. "0 B" beside a failed
                // query is the most confident thing on the screen and the only entirely
                // invented one.
                item {
                    QueryFailureState(
                        title = "Couldn't read your data usage",
                        detail = "Android didn't return network statistics. This does not mean " +
                            "no mobile data was used — Ciyato could not tell either way.",
                    )
                }
                return@LazyColumn
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        NetStat("↓ Download", formatBytes(totalRx), CiyatoGreen)
                        NetStat("↑ Upload", formatBytes(totalTx), CiyatoInfo)
                        NetStat("Total", formatBytes(totalRx + totalTx), CiyatoGold)
                    }
                }
            }

            item {
                Text("By App (30 days)", color = CiyatoWhite, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold)
            }

            if (current.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("No mobile data usage", color = CiyatoMuted, fontWeight = FontWeight.SemiBold)
                            // Now earned: the query succeeded and reported nothing.
                            Text("No app has used mobile data in the last 30 days. Wi-Fi usage isn't counted here.",
                                color = CiyatoMuted, fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                val maxBytes = current.firstOrNull()?.totalBytes?.toFloat() ?: 1f
                itemsIndexed(current.take(20), key = { _, stat -> stat.uid }) { idx, stat ->
                    NetworkStatRow(stat = stat, maxBytes = maxBytes, rank = idx + 1)
                }
            }
        }
    }
}

@Composable
private fun NetworkStatRow(stat: AppNetworkStat, maxBytes: Float, rank: Int) {
    val pct = stat.totalBytes.toFloat() / maxBytes
    val anim = remember { Animatable(0f) }
    LaunchedEffect(pct) { anim.animateTo(pct, tween(700, delayMillis = rank * 30)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stat.appLabel, color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                Text(formatBytes(stat.totalBytes), color = CiyatoGold, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("↓ ${formatBytes(stat.rxBytes)}", color = CiyatoGreen, fontSize = 11.sp)
                Text("↑ ${formatBytes(stat.txBytes)}", color = CiyatoInfo, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFF1E2128))
            ) {
                Box(modifier = Modifier.fillMaxWidth(anim.value).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(CiyatoGold))
            }
        }
    }
}

@Composable
private fun NetStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = CiyatoMuted, fontSize = 11.sp)
    }
}

/** Same "Usage access" special app-op that gates UsageStatsManager also gates NetworkStatsManager#querySummary. */
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

/**
 * Per-app mobile data, or null when NetworkStatsManager would not answer.
 *
 * The empty-list fallback rendered as "No app has used mobile data in the last 30
 * days" beside a confident 0 B total: a specific factual claim about the person's
 * month, produced by a query that failed.
 */
private fun getNetworkStats(context: Context): List<AppNetworkStat>? {
    return try {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val monthAgo = now - TimeUnit.DAYS.toMillis(30)

        val bucket = NetworkStats.Bucket()
        val statsMap = mutableMapOf<Int, Pair<Long, Long>>()

        // use {} rather than a trailing close(). close() sat after the loop, so a throw
        // inside getNextBucket jumped to the outer catch and leaked the session. These
        // are a finite system resource and this screen can be reopened indefinitely.
        nsm.querySummary(ConnectivityManager.TYPE_MOBILE, null, monthAgo, now).use { summary ->
            while (summary.hasNextBucket()) {
                summary.getNextBucket(bucket)
                val uid = bucket.uid
                val (rx, tx) = statsMap.getOrDefault(uid, 0L to 0L)
                statsMap[uid] = (rx + bucket.rxBytes) to (tx + bucket.txBytes)
            }
        }

        statsMap.entries
            .filter { it.value.first + it.value.second > 0 }
            .mapNotNull { (uid, bytes) ->
                val label = try {
                    val packages = pm.getPackagesForUid(uid)
                    val pkg = packages?.firstOrNull() ?: return@mapNotNull null
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { "UID $uid" }
                AppNetworkStat(appLabel = label, uid = uid, rxBytes = bytes.first, txBytes = bytes.second)
            }
            .sortedByDescending { it.totalBytes }
    } catch (_: Exception) { null }
}

internal fun formatBytes(bytes: Long): String {
    return com.ciyato.launcher.data.ByteFormat.format(bytes)
}
