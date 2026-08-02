package com.ciyato.launcher.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * PrivacyDashboardScreen — Suggestion #88
 * Summary of which apps have what permissions, and quick actions to revoke.
 */

data class AppPermissionSummary(
    val appLabel: String,
    val packageName: String,
    val dangerousPermissions: List<String>,
    val riskLevel: RiskLevel,
)

enum class RiskLevel { LOW, MEDIUM, HIGH }

private val DANGEROUS_PERMISSIONS = mapOf(
    "android.permission.CAMERA" to "Camera",
    "android.permission.RECORD_AUDIO" to "Microphone",
    "android.permission.ACCESS_FINE_LOCATION" to "Precise Location",
    "android.permission.ACCESS_COARSE_LOCATION" to "Approximate Location",
    "android.permission.READ_CONTACTS" to "Contacts",
    "android.permission.READ_CALL_LOG" to "Call Log",
    "android.permission.READ_SMS" to "SMS",
    "android.permission.SEND_SMS" to "Send SMS",
    "android.permission.READ_EXTERNAL_STORAGE" to "Storage",
    "android.permission.WRITE_EXTERNAL_STORAGE" to "Write Storage",
    "android.permission.BODY_SENSORS" to "Body Sensors",
    "android.permission.ACTIVITY_RECOGNITION" to "Activity",
    "android.permission.PROCESS_OUTGOING_CALLS" to "Phone Calls",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val summaries by remember {
        derivedStateOf { buildPrivacySummaries(context) }
    }

    val highRisk = summaries.filter { it.riskLevel == RiskLevel.HIGH }
    val medRisk = summaries.filter { it.riskLevel == RiskLevel.MEDIUM }
    val lowRisk = summaries.filter { it.riskLevel == RiskLevel.LOW }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Privacy Dashboard", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
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
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        RiskStat("${highRisk.size}", "High Risk", Color(0xFFEF4444))
                        RiskStat("${medRisk.size}", "Medium", Color(0xFFFF9500))
                        RiskStat("${lowRisk.size}", "Low Risk", Color(0xFF39C66A))
                        RiskStat("${summaries.size}", "Total Apps", CiyatoSec)
                    }
                }
            }

            // Android 12+ Privacy Dashboard link
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_PRIVACY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("🛡", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Android Privacy Dashboard", color = Color(0xFF7DB7FF),
                                    fontWeight = FontWeight.SemiBold)
                                Text("See recent permission usage", color = CiyatoMuted, fontSize = 12.sp)
                            }
                            Text("→", color = Color(0xFF7DB7FF), fontSize = 18.sp)
                        }
                    }
                }
            }

            if (highRisk.isNotEmpty()) {
                item { SectionLabel("⚠ High Risk Apps", Color(0xFFEF4444)) }
                items(highRisk) { app ->
                    PrivacyAppRow(app = app, context = context)
                }
            }

            if (medRisk.isNotEmpty()) {
                item { SectionLabel("⚡ Medium Risk", Color(0xFFFF9500)) }
                items(medRisk) { app ->
                    PrivacyAppRow(app = app, context = context)
                }
            }

            if (lowRisk.isNotEmpty()) {
                item { SectionLabel("✅ Low Risk", Color(0xFF39C66A)) }
                items(lowRisk) { app ->
                    PrivacyAppRow(app = app, context = context)
                }
            }
        }
    }
}

@Composable
private fun PrivacyAppRow(app: AppPermissionSummary, context: Context) {
    val riskColor = when (app.riskLevel) {
        RiskLevel.HIGH -> Color(0xFFEF4444)
        RiskLevel.MEDIUM -> Color(0xFFFF9500)
        RiskLevel.LOW -> Color(0xFF39C66A)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(riskColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appLabel, color = CiyatoWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    app.dangerousPermissions.take(3).joinToString(", "),
                    color = CiyatoMuted, fontSize = 12.sp, maxLines = 1,
                )
            }
            Text("${app.dangerousPermissions.size}", color = riskColor,
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RiskStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = CiyatoMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.padding(top = 4.dp))
}

private fun buildPrivacySummaries(context: Context): List<AppPermissionSummary> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
        .mapNotNull { appInfo ->
            try {
                val pkgInfo = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                val granted = pkgInfo.requestedPermissions
                    ?.filterIndexed { i, _ ->
                        (pkgInfo.requestedPermissionsFlags?.getOrNull(i) ?: 0) and
                                android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    }
                    ?.mapNotNull { DANGEROUS_PERMISSIONS[it] }
                    ?: emptyList()

                if (granted.isEmpty()) return@mapNotNull null

                val risk = when {
                    granted.size >= 5 -> RiskLevel.HIGH
                    granted.size >= 2 -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }

                AppPermissionSummary(
                    appLabel = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    dangerousPermissions = granted,
                    riskLevel = risk,
                )
            } catch (_: Exception) { null }
        }
        .sortedWith(compareByDescending<AppPermissionSummary> { it.riskLevel.ordinal }
            .thenByDescending { it.dangerousPermissions.size })
}
