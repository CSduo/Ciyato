package com.ciyato.launcher.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TrackerScannerScreen — Suggestion #82
 * Scans installed apps for known tracker/ad-library signatures.
 * Uses a curated list of common tracker package prefixes (Exodus Privacy-inspired).
 * Production: fetch updated tracker list from Exodus Privacy API.
 */

data class AppTrackerResult(
    val packageName: String,
    val appLabel: String,
    val trackers: List<String>,
    val riskLevel: RiskLevel,
)



private val KNOWN_TRACKER_SIGNATURES = mapOf(
    "com.google.android.gms.ads"      to "Google Ads",
    "com.facebook.ads"                to "Facebook Audience Network",
    "com.unity3d.ads"                 to "Unity Ads",
    "com.applovin"                    to "AppLovin",
    "com.chartboost"                  to "ChartBoost",
    "com.mopub"                       to "MoPub",
    "io.branch.referral"              to "Branch.io",
    "com.mixpanel.android"            to "Mixpanel",
    "com.amplitude.api"               to "Amplitude",
    "com.flurry.android"              to "Flurry",
    "com.crashlytics"                 to "Crashlytics",
    "com.adjust.sdk"                  to "Adjust",
    "com.appsflyer"                   to "AppsFlyer",
    "io.intercom.android"             to "Intercom",
    "com.onesignal"                   to "OneSignal",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScannerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<AppTrackerResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        results = withContext(Dispatchers.IO) { scanForTrackers(context) }
        isLoading = false
    }

    val highRisk = results.count { it.riskLevel == RiskLevel.HIGH }
    val medRisk  = results.count { it.riskLevel == RiskLevel.MEDIUM }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Tracker Scanner", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
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
                    Spacer(Modifier.height(8.dp))
                    Text("Scanning installed apps…", color = CiyatoMuted)
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                        shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            TrackerStat("Apps Scanned", results.size.toString(), CiyatoWhite)
                            TrackerStat("High Risk", highRisk.toString(), Color(0xFFF44336))
                            TrackerStat("Medium Risk", medRisk.toString(), Color(0xFFFF9800))
                        }
                    }
                }

                val sorted = results.filter { it.trackers.isNotEmpty() }
                    .sortedByDescending { it.trackers.size }

                if (sorted.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Shield, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No known trackers detected", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    items(sorted, key = { it.packageName }) { result ->
                        TrackerResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = CiyatoMuted, fontSize = 11.sp)
    }
}

@Composable
private fun TrackerResultCard(result: AppTrackerResult) {
    val riskColor = when (result.riskLevel) {
        RiskLevel.HIGH   -> Color(0xFFF44336)
        RiskLevel.MEDIUM -> Color(0xFFFF9800)
        RiskLevel.LOW    -> Color(0xFF4CAF50)
    }
    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrackChanges, null, tint = riskColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(result.appLabel, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                Surface(color = riskColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                    Text(result.riskLevel.name, color = riskColor, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            result.trackers.forEach { tracker ->
                Text("• $tracker", color = CiyatoMuted, fontSize = 12.sp)
            }
        }
    }
}

private fun scanForTrackers(context: Context): List<AppTrackerResult> {
    val pm = context.packageManager
    val installedPackages = try {
        pm.getInstalledPackages(PackageManager.GET_META_DATA)
    } catch (_: Exception) { return emptyList() }

    return installedPackages.mapNotNull { pkg ->
        val packageName = pkg.packageName
        val detectedTrackers = KNOWN_TRACKER_SIGNATURES.entries
            .filter { (signature, _) -> packageName.startsWith(signature.split(".").take(3).joinToString(".")) }
            .map { it.value }

        if (detectedTrackers.isEmpty()) return@mapNotNull null

        val label = try { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
                    catch (_: Exception) { packageName }

        AppTrackerResult(
            packageName = packageName,
            appLabel = label,
            trackers = detectedTrackers,
            riskLevel = when (detectedTrackers.size) {
                0    -> RiskLevel.LOW
                1, 2 -> RiskLevel.MEDIUM
                else -> RiskLevel.HIGH
            },
        )
    }
}
