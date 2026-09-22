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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Shield
import android.os.Build
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator

/**
 * What each installed app has declared it can access.
 *
 * Deliberately NOT a risk score. This screen used to sort apps into red HIGH
 * RISK / amber MEDIUM / green LOW buckets by counting declared permissions
 * (F-158), and a count is not a risk: a messaging app declaring CAMERA is doing
 * its job, a flashlight app declaring it is worth a look, and nothing in a
 * manifest distinguishes the two. Red said "this app is dangerous" on evidence
 * that did not support it, and green said "this app is safe" on none at all -
 * which is the worse of the two, because it is reassurance.
 *
 * So the categories are descriptive and the colours are not a verdict:
 *   Sensitive     - location, contacts, camera, microphone, SMS, call logs
 *   Connectivity  - network, wifi, bluetooth
 *   Other         - everything else
 *
 * A declared permission is also not a granted one, and neither is evidence of
 * use. Android's own Privacy dashboard shows what was actually accessed and
 * when, so the banner links there; this screen answers the different question
 * of what an app could ask for. Tapping an app opens its system App info page,
 * where the person can actually change something.
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

    // Built off the main thread.
    //
    // This was a remember{} block, and a remember block runs DURING composition
    // on the main thread. It called getPackageInfo once per installed app -
    // a synchronous binder round trip each time - so opening this screen on a
    // phone with 150 apps meant 150 IPC calls inside the first frame (F-157).
    // The cost scales with how many apps someone has installed, which is exactly
    // the population most likely to open a permission audit.
    var auditedApps by remember { mutableStateOf<List<AuditedApp>>(emptyList()) }
    var isAuditing by remember { mutableStateOf(true) }
    LaunchedEffect(apps) {
        isAuditing = true
        auditedApps = withContext(Dispatchers.IO) {
            apps.filter { !it.isSystemApp }
                .map { app -> AuditedApp(app, getAppPermissions(context, app.packageName)) }
                .sortedByDescending { it.sensitivityOrder }
        }
        isAuditing = false
    }

    val filtered = remember(auditedApps, filterLevel) {
        when (filterLevel) {
            "Sensitive"    -> auditedApps.filter { it.category == PermissionCategory.SENSITIVE }
            "Connectivity" -> auditedApps.filter { it.category == PermissionCategory.CONNECTIVITY }
            "Other"        -> auditedApps.filter { it.category == PermissionCategory.OTHER }
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
        if (isAuditing) {
            // An empty audit and an unfinished one look identical otherwise, and
            // "0 apps request sensitive permissions" is a reassuring thing to
            // show someone while the work has not started.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Spacer(Modifier.height(12.dp))
                    Text("Reading declared permissions...", color = CiyatoMuted, fontSize = 13.sp)
                }
            }
            return@Scaffold
        }
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
                // Salvaged from PrivacyDashboardScreen, which duplicated this
                // screen's job (grouping apps by declared sensitive permissions)
                // while being unreachable from anywhere. This link was the one
                // thing it had that this screen did not, and it is the more
                // useful half: Android's own dashboard reports permissions
                // actually *used* recently, where this screen can only read what
                // is *declared* in a manifest.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CiyatoBgEl)
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_PRIVACY_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = CiyatoBlue,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Android Privacy Dashboard",
                                    color = CiyatoWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    "Which permissions apps actually used recently — this screen " +
                                        "can only show what they declare.",
                                    color = CiyatoMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }

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

/**
 * What kind of thing a declared permission reaches - not how dangerous it is.
 * The names are the whole point: see the file KDoc for why this is no longer
 * HIGH/MEDIUM/LOW.
 */
private enum class PermissionCategory { SENSITIVE, CONNECTIVITY, OTHER }

private data class AuditedApp(
    val app: InstalledApp,
    val permissions: List<String>,
) {
    val sensitive: List<String> get() = permissions.filter { isSensitive(it) }
    val connectivity:  List<String> get() = permissions.filter { isConnectivity(it) }

    /**
     * Sort order only - most-sensitive-first, so the apps worth a look are at
     * the top. It is NOT shown, NOT compared between apps, and NOT a score.
     * It was called riskScore, and a number named that eventually gets
     * rendered.
     */
    val sensitivityOrder: Int
        get() = sensitive.size * 10 + connectivity.size * 3 +
            (permissions.size - sensitive.size - connectivity.size)

    val category: PermissionCategory get() = when {
        sensitive.isNotEmpty() -> PermissionCategory.SENSITIVE
        connectivity.isNotEmpty()  -> PermissionCategory.CONNECTIVITY
        else                  -> PermissionCategory.OTHER
    }
}

private val SENSITIVE_PERMS = setOf(
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

private val CONNECTIVITY_PERMS = setOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.NFC",
    "android.permission.ACTIVITY_RECOGNITION",
)

private fun isSensitive(perm: String) = SENSITIVE_PERMS.any { perm.equals(it, ignoreCase = true) }
private fun isConnectivity(perm: String)  = CONNECTIVITY_PERMS.any  { perm.equals(it, ignoreCase = true) }

private fun getAppPermissions(context: android.content.Context, pkg: String): List<String> =
    try {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.toList() ?: emptyList()
    } catch (_: Exception) { emptyList() }

// ── UI components ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionSummaryBanner(audited: List<AuditedApp>) {
    val context = LocalContext.current
    val highCount = audited.count { it.category == PermissionCategory.SENSITIVE }
    val medCount  = audited.count { it.category == PermissionCategory.CONNECTIVITY }

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
            SummaryPill("$highCount sensitive", CiyatoGold, Modifier.weight(1f))
            SummaryPill("$medCount connectivity", CiyatoSec, Modifier.weight(1f))
        }
        // What an app CAN ask for is a different question from what it has
        // actually done, and this screen only answers the first. Android keeps
        // the second - which app used the camera, when - and it is the more
        // useful one, so it is offered here rather than approximated (F-158).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    // ACTION_PRIVACY_SETTINGS is where the dashboard lives on
                    // Android 12+; older releases and some OEM builds do not
                    // have it, so a missing screen falls back rather than
                    // throwing ActivityNotFoundException at the person.
                    val opened = runCatching {
                        context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS))
                    }.isSuccess
                    if (!opened) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Shield, null, tint = CiyatoSec, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "See what apps actually used, and when, in Android's Privacy dashboard",
                color = CiyatoSec,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
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
    // Attention, not alarm.
    //
    // This was red / amber / green. Red on an app that declares CAMERA accuses
    // it of something the manifest cannot support, and green on one that does
    // not is reassurance nobody earned (F-158). Gold marks the rows worth
    // reading first; everything else is ordinary chrome.
    val categoryColor = when (audited.category) {
        PermissionCategory.SENSITIVE -> CiyatoGold
        PermissionCategory.CONNECTIVITY -> CiyatoSec
        PermissionCategory.OTHER -> CiyatoMuted
    }
    val categoryLabel = when (audited.category) {
        PermissionCategory.SENSITIVE   -> "Sensitive"
        PermissionCategory.CONNECTIVITY -> "Connectivity"
        PermissionCategory.OTHER    -> "Other"
    }

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiyatoBgEl)
            .border(1.dp, if (audited.category == PermissionCategory.SENSITIVE) categoryColor.copy(0.3f) else CiyatoSubtleBorder, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RealAppIcon(drawable = audited.app.icon, size = 36.dp, scale = audited.app.iconScale, rotation = audited.app.iconRotation, accentHex = audited.app.iconAccent)
            Column(modifier = Modifier.weight(1f)) {
                Text(audited.app.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(audited.app.packageName.take(36), color = CiyatoMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(categoryColor.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(categoryLabel, color = categoryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("${audited.permissions.size} perms", color = CiyatoMuted, fontSize = 11.sp)
            }
        }

        if (expanded) {
            HorizontalDivider(color = CiyatoSubtleBorder)
            if (audited.sensitive.isNotEmpty()) {
                Text("Sensitive declared permissions:", color = CiyatoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                audited.sensitive.forEach { perm ->
                    Text("• ${perm.substringAfterLast(".")}", color = CiyatoSec, fontSize = 11.sp)
                }
            }
            if (audited.connectivity.isNotEmpty()) {
                Text("Connectivity declared permissions:", color = CiyatoSec, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                audited.connectivity.forEach { perm ->
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
