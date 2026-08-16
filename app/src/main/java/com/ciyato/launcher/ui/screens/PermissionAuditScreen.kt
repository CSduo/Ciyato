package com.ciyato.launcher.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.components.RealAppIcon
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * PermissionAuditScreen — Suggestion #139.
 *
 * Lists all installed apps with their declared permissions, flagging:
 *   🔴 HIGH RISK: Location, Contacts, Camera, Microphone, SMS, Call Logs, Storage, Biometric
 *   🟡 MEDIUM:    Network access, Wifi, Bluetooth
 *   🟢 LOW:       Vibrate, Receive boot, etc.
 *
 * Tapping an app opens its Android system App Info page for granular control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionAuditScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsState()

    var filterLevel: String by remember { mutableStateOf("All") }
    val filters = listOf("All", "Sensitive", "Connectivity", "Other")

    val auditedApps = remember(apps) {
        apps.filter { !it.isSystemApp }.map { app ->
            val perms = getAppPermissions(context, app.packageName)
            AuditedApp(app, perms)
        }.sortedByDescending { it.riskScore }
    }

    val filtered = remember(auditedApps, filterLevel) {
        when (filterLevel) {
            "Sensitive"    -> auditedApps.filter { it.riskLevel == PermissionRiskLevel.HIGH }
            "Connectivity" -> auditedApps.filter { it.riskLevel == PermissionRiskLevel.MEDIUM }
            "Other"        -> auditedApps.filter { it.riskLevel == PermissionRiskLevel.LOW }
            else        -> auditedApps
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Permission Review",
                subtitle = "Declared app permissions",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Summary banner
            PermissionSummaryBanner(auditedApps)

            // Filter tabs
            CiyatoTabRow(
                tabs = filters,
                selectedIndex = filters.indexOf(filterLevel).coerceAtLeast(0),
                onTabSelected = { idx -> filterLevel = filters[idx] }
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.app.packageName }) { audited ->
                    AuditAppCard(
                        audited = audited,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", audited.app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Could not open app settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Data model ────────────────────────────────────────────────────────────────

private enum class PermissionRiskLevel { HIGH, MEDIUM, LOW }

private data class AuditedApp(
    val app: InstalledApp,
    val permissions: List<String>,
) {
    val highRisk: List<String> get() = permissions.filter { isHighRisk(it) }
    val medRisk:  List<String> get() = permissions.filter { isMedRisk(it) }

    val riskScore: Int get() = highRisk.size * 10 + medRisk.size * 3 + (permissions.size - highRisk.size - medRisk.size)

    val riskLevel: PermissionRiskLevel get() = when {
        highRisk.isNotEmpty() -> PermissionRiskLevel.HIGH
        medRisk.isNotEmpty()  -> PermissionRiskLevel.MEDIUM
        else                  -> PermissionRiskLevel.LOW
    }
}

private val HIGH_RISK_PERMS = setOf(
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_CALENDAR",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_CALL_LOG",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.USE_BIOMETRIC",
    "android.permission.USE_FINGERPRINT",
    "android.permission.READ_PHONE_STATE",
    "android.permission.PROCESS_OUTGOING_CALLS",
)

private val MED_RISK_PERMS = setOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.NFC",
    "android.permission.ACTIVITY_RECOGNITION",
)

private fun isHighRisk(perm: String) = HIGH_RISK_PERMS.any { perm.equals(it, ignoreCase = true) }
private fun isMedRisk(perm: String)  = MED_RISK_PERMS.any  { perm.equals(it, ignoreCase = true) }

private fun getAppPermissions(context: android.content.Context, pkg: String): List<String> =
    try {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.toList() ?: emptyList()
    } catch (_: Exception) { emptyList() }

// ── UI components ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionSummaryBanner(audited: List<AuditedApp>) {
    val highCount = audited.count { it.riskLevel == PermissionRiskLevel.HIGH }
    val medCount  = audited.count { it.riskLevel == PermissionRiskLevel.MEDIUM }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Shows permissions declared by each app. Android controls whether access is currently granted.",
            color = CiyatoMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryPill("${audited.size} reviewed", CiyatoSec, Modifier.weight(1f))
            SummaryPill("$highCount sensitive", CiyatoRed, Modifier.weight(1f))
            SummaryPill("$medCount connectivity", Color(0xFFF5C542), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AuditAppCard(audited: AuditedApp, onClick: () -> Unit) {
    val riskColor = when (audited.riskLevel) {
        PermissionRiskLevel.HIGH   -> CiyatoRed
        PermissionRiskLevel.MEDIUM -> Color(0xFFF5C542)
        PermissionRiskLevel.LOW    -> CiyatoGreen
    }
    val riskLabel = when (audited.riskLevel) {
        PermissionRiskLevel.HIGH   -> "Sensitive"
        PermissionRiskLevel.MEDIUM -> "Connectivity"
        PermissionRiskLevel.LOW    -> "Other"
    }

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl)
            .border(1.dp, if (audited.riskLevel == PermissionRiskLevel.HIGH) riskColor.copy(0.3f) else CiyatoSubtleBorder, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RealAppIcon(drawable = audited.app.icon, size = 36.dp, scale = audited.app.iconScale, rotation = audited.app.iconRotation, accentHex = audited.app.iconAccent)
            Column(modifier = Modifier.weight(1f)) {
                Text(audited.app.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(audited.app.packageName.take(36), color = CiyatoMuted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(riskColor.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(riskLabel, color = riskColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("${audited.permissions.size} perms", color = CiyatoMuted, fontSize = 10.sp)
            }
        }

        if (expanded) {
            HorizontalDivider(color = CiyatoSubtleBorder)
            if (audited.highRisk.isNotEmpty()) {
                Text("Sensitive declared permissions:", color = CiyatoRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                audited.highRisk.forEach { perm ->
                    Text("• ${perm.substringAfterLast(".")}", color = CiyatoSec, fontSize = 11.sp)
                }
            }
            if (audited.medRisk.isNotEmpty()) {
                Text("Connectivity declared permissions:", color = Color(0xFFF5C542), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                audited.medRisk.forEach { perm ->
                    Text("• ${perm.substringAfterLast(".")}", color = CiyatoMuted, fontSize = 11.sp)
                }
            }
            TextButton(
                onClick = {
                    expanded = true
                    onClick()
                }
            ) {
                Text("Open App Settings →", color = CiyatoBlue, fontSize = 12.sp)
            }
        }
    }
}
