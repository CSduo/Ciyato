package com.ciyato.launcher.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.components.AppIconView
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import com.ciyato.launcher.viewmodel.isHidden

/**
 * HiddenVaultScreen — Suggestion #18
 * Biometric-gated screen showing apps the user has hidden.
 * Requires passing BiometricPrompt before revealing any app names.
 */

@Composable
fun HiddenVaultScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val hiddenAppsStr by viewModel.hiddenApps.collectAsState()
    val allApps by viewModel.allApps.collectAsState()

    var isUnlocked by remember { mutableStateOf(false) }
    var isBiometricUnavailable by remember { mutableStateOf(false) }

    val hiddenApps = remember(hiddenAppsStr, allApps) {
        val hiddenPkgs = hiddenAppsStr.split(",").map { it.trim() }.toSet()
        allApps.filter { it.packageName in hiddenPkgs }
    }

    fun authenticate() {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Fail CLOSED. `isUnlocked = true` here revealed the hidden-apps
            // list to anyone holding the phone whenever no authenticator was
            // enrolled — which, with USE_BIOMETRIC stripped from the manifest,
            // was every device. Hidden apps that unhide themselves are not
            // hidden; the lock has to hold even when it cannot ask.
            isBiometricUnavailable = true
            return
        }
        // Fail CLOSED — this used to set `isUnlocked = true`, and since the host
        // was a bare ComponentActivity the cast always failed, so the hidden-apps
        // vault revealed itself without any authentication at all.
        val activity = context.findFragmentActivity() ?: run {
            isBiometricUnavailable = true
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isUnlocked = true
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hidden Vault")
            .setSubtitle("Verify your identity to view hidden apps")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ).build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) { authenticate() }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Hidden Vault", onBack = onBack)
        }
    ) { padding ->
        AnimatedContent(
            targetState = isUnlocked,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { unlocked ->
            if (!unlocked) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Lock, null, tint = CiyatoGold, modifier = Modifier.size(64.dp))
                        Text("Vault Locked", color = CiyatoWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("Authenticate to view your hidden apps", color = CiyatoMuted)
                        Button(onClick = { authenticate() },
                            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
                            Text("Unlock", color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (hiddenApps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VisibilityOff, null, tint = CiyatoMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No hidden apps", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Long-press any app → Hide to add it here", color = CiyatoMuted)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(hiddenApps, key = { it.packageName }) { app ->
                        AppIconView(
                            app = app,
                            iconShape = viewModel.iconShape.collectAsState().value,
                            onClick = { viewModel.launchApp(app) },
                        )
                    }
                }
            }
        }
    }
}
