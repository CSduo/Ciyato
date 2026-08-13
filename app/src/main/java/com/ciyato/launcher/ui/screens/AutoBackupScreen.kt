package com.ciyato.launcher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.ciyato.launcher.data.LauncherSettingsRepository
import com.ciyato.launcher.data.PhotoBackupWorker
import com.ciyato.launcher.data.hasPhotoBackupPermission
import com.ciyato.launcher.data.runPhotoBackup
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AutoBackupScreen — Suggestion #67
 *
 * Real, on-device photo backup: SAF folder picker with persisted URI
 * permission, MediaStore query, and a real file copy via ContentResolver
 * streams. Only photos (MediaStore.Images.Media) are backed up — the UI says
 * "Photo Backup" rather than "files" so it never claims more than it does.
 *
 * Backup can run on demand ("Back Up Now") or on a genuine daily schedule via
 * WorkManager ([PhotoBackupWorker]) once the "Automatic Backup" switch is on —
 * that switch is what makes "Auto" true rather than aspirational.
 */

@Composable
fun AutoBackupScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { LauncherSettingsRepository(context) }

    val folderUriRaw by settingsRepo.photoBackupFolderUri.collectAsState(initial = "")
    val autoEnabled by settingsRepo.photoBackupAutoEnabled.collectAsState(initial = false)
    val lastRunAt by settingsRepo.photoBackupLastRunAt.collectAsState(initial = 0L)
    val lastCount by settingsRepo.photoBackupLastCount.collectAsState(initial = 0)
    val folderUri = remember(folderUriRaw) { folderUriRaw.takeIf { it.isNotBlank() }?.let { Uri.parse(it) } }

    var isBackingUp by remember { mutableStateOf(false) }
    var backupProgress by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }

    // Same permission-gate pattern as StorageCleanupScreen: check on entry,
    // re-check via a system-permission launcher, and again on every resume so
    // granting from system Settings updates the screen without a restart.
    var hasPermission by remember { mutableStateOf(hasPhotoBackupPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermission = hasPhotoBackupPermission(context) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = hasPhotoBackupPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the WorkManager schedule consistent with the persisted setting —
    // this is what runs the backup even when nobody opens this screen.
    LaunchedEffect(autoEnabled, folderUriRaw) {
        if (autoEnabled && folderUriRaw.isNotBlank()) PhotoBackupWorker.schedule(context)
        else PhotoBackupWorker.cancel(context)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { settingsRepo.setPhotoBackupFolderUri(uri.toString()) }
        }
    }

    fun startBackup() {
        val folder = folderUri ?: return
        isBackingUp = true
        backupProgress = 0f
        statusMessage = ""

        scope.launch {
            val sinceSeconds = lastRunAt / 1000L
            val result = withContext(Dispatchers.IO) {
                runPhotoBackup(context, folder, sinceSeconds) { done, total ->
                    backupProgress = if (total > 0) done.toFloat() / total else 1f
                }
            }
            isBackingUp = false
            if (result.error != null) {
                statusMessage = "Backup failed: ${result.error}"
            } else {
                settingsRepo.setPhotoBackupLastRun(result.completedAtMs, result.copiedCount)
                statusMessage = if (result.copiedCount > 0) {
                    "Backup complete! ${result.copiedCount} photo${if (result.copiedCount == 1) "" else "s"} saved."
                } else {
                    "Backup complete — no new photos since the last backup."
                }
            }
        }
    }

    val lastBackupLabel = remember(lastRunAt) {
        if (lastRunAt <= 0L) null
        else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastRunAt))
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Photo Backup", subtitle = "Automatic or on demand", onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!hasPermission) {
                BackupPermissionCard(
                    onGrant = {
                        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                            android.Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(arrayOf(perm))
                    },
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup Destination", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        folderUri?.lastPathSegment ?: "No folder selected",
                        color = if (folderUri != null) CiyatoWhite else CiyatoMuted,
                        fontSize = 13.sp,
                    )
                    Button(
                        onClick = { folderPicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = CiyatoBgEl),
                        border = ButtonDefaults.outlinedButtonBorder,
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Choose Folder", color = CiyatoGold, fontSize = 13.sp)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic Backup", color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Backs up new photos about once a day, without opening Ciyato",
                            color = CiyatoMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = autoEnabled,
                        enabled = folderUri != null && hasPermission,
                        onCheckedChange = { enabled -> scope.launch { settingsRepo.setPhotoBackupAutoEnabled(enabled) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = CiyatoWhite, checkedTrackColor = CiyatoGold),
                    )
                }
            }

            if (lastBackupLabel != null) {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Last backup: $lastBackupLabel", color = CiyatoWhite, fontSize = 13.sp)
                            Text("$lastCount photo${if (lastCount == 1) "" else "s"} copied that run", color = CiyatoMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isBackingUp) {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Backing up…", color = CiyatoWhite, fontSize = 13.sp)
                        LinearProgressIndicator(
                            progress = { backupProgress },
                            color = CiyatoGold,
                            trackColor = CiyatoBg,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(backupProgress * 100).toInt()}% complete", color = CiyatoMuted, fontSize = 12.sp)
                    }
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = CiyatoWhite, fontSize = 13.sp)
            }

            Button(
                onClick = { startBackup() },
                enabled = folderUri != null && hasPermission && !isBackingUp,
                colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Backup, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back Up Now", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BackupPermissionCard(onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.PhotoLibrary, null, tint = CiyatoGold, modifier = Modifier.size(26.dp))
            Text("Photo access needed", color = CiyatoWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Ciyato needs photo access to find and back up images from your device.",
                color = CiyatoMuted,
                fontSize = 13.sp,
            )
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
                Text("Grant Access", color = CiyatoBg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
