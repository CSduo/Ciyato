package com.ciyato.launcher.ui.screens

import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
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
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.ui.components.AppIconView
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * GuestModeScreen — Suggestion #87
 * Restricted launcher profile that shows only approved apps.
 * Exits only after the device owner authenticates (biometric or device
 * PIN/pattern/password via BiometricPrompt — the same gate AppLockScreen
 * uses), so a guest can't just tap through the dialog to leave.
 * No access to hidden apps, files, settings, or personal data.
 */

private val GUEST_ALLOWED_CATEGORIES = setOf(
    AppCategory.UTILITIES,
    AppCategory.PRODUCTIVITY,
    AppCategory.CREATIVITY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestModeScreen(
    viewModel: LauncherViewModel,
    onExitGuestMode: () -> Unit,
) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsState()
    val iconShape by viewModel.iconShape.collectAsState()

    val guestApps = remember(apps) {
        apps.filter { it.category in GUEST_ALLOWED_CATEGORIES }.take(12)
    }

    var showExitDialog by remember { mutableStateOf(false) }
    var exitFailMessage by remember { mutableStateOf<String?>(null) }

    // Device has no biometric AND no PIN/pattern/password set up at all — there is
    // nothing to authenticate against, so gating exit would trap the guest with no
    // way out. Every other device authenticates before Guest Mode can be exited.
    val canAuthenticate = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(containerColor = CiyatoBg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            Box(
                Modifier
                    .background(CiyatoGold.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonOutline, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guest Mode", color = CiyatoGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Limited access — approved apps only", color = CiyatoMuted, fontSize = 12.sp)

            Spacer(Modifier.height(24.dp))

            if (guestApps.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Apps, null, tint = CiyatoMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No approved apps in guest profile", color = CiyatoMuted)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(guestApps) { app ->
                        AppIconView(
                            app = app,
                            iconShape = iconShape,
                            onClick = { viewModel.launchApp(app) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = { showExitDialog = true },
                modifier = Modifier.fillMaxWidth(),
                border = ButtonDefaults.outlinedButtonBorder,
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = CiyatoMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exit Guest Mode", color = CiyatoMuted)
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false; exitFailMessage = null },
            containerColor = CiyatoBgEl,
            title = { Text("Exit Guest Mode", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (canAuthenticate) {
                            "Confirm it's you to leave guest mode. Use your fingerprint, face, or device PIN."
                        } else {
                            "This device has no screen lock set up, so guest mode can't be secured. " +
                                "Add a screen lock in Android Settings to protect this."
                        },
                        color = CiyatoMuted,
                        fontSize = 13.sp,
                    )
                    exitFailMessage?.let { message ->
                        Text(message, color = Color(0xFFEF4444), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exitFailMessage = null
                    if (!canAuthenticate) {
                        // Nothing to verify against — refusing here would lock the
                        // guest in with no escape, so allow the exit.
                        showExitDialog = false
                        onExitGuestMode()
                        return@TextButton
                    }
                    triggerBiometric(
                        context = context,
                        appLabel = "Guest Mode",
                        onSuccess = {
                            showExitDialog = false
                            exitFailMessage = null
                            onExitGuestMode()
                        },
                        onFailed = { exitFailMessage = "Not recognised. Try again." },
                        onError = { message -> exitFailMessage = message },
                    )
                }) { Text("Exit", color = CiyatoGold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false; exitFailMessage = null }) {
                    Text("Cancel", color = CiyatoMuted)
                }
            },
        )
    }
}
