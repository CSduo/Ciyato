package com.ciyato.launcher.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.*

/**
 * AppLockScreen — Suggestion #78
 * Biometric / PIN lock for individual apps.
 * Call authenticate() before launching a locked app.
 */

enum class AuthState { IDLE, AUTHENTICATING, SUCCESS, FAILED }

@Composable
fun AppLockGate(
    app: InstalledApp,
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var authState by remember { mutableStateOf(AuthState.IDLE) }
    var failMessage by remember { mutableStateOf<String?>(null) }

    val biometricAvailable = remember {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(Unit) {
        if (biometricAvailable) {
            triggerBiometric(
                context = context,
                appLabel = app.label,
                onSuccess = {
                    authState = AuthState.SUCCESS
                    onAuthenticated()
                },
                onFailed = {
                    authState = AuthState.FAILED
                    failMessage = "Authentication failed. Try again."
                },
                onError = { msg ->
                    authState = AuthState.FAILED
                    failMessage = msg
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CiyatoBg.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(Color(0xFF1E2128))
                    .border(2.dp, CiyatoGold.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = CiyatoGold, modifier = Modifier.size(32.dp))
            }

            Text("${app.label} is locked", color = CiyatoWhite, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold)
            Text("Authenticate to open this app", color = CiyatoMuted, fontSize = 14.sp)

            if (failMessage != null) {
                Text(failMessage!!, color = Color(0xFFEF4444), fontSize = 14.sp)
            }

            if (biometricAvailable) {
                Button(
                    onClick = {
                        failMessage = null
                        triggerBiometric(context, app.label, onSuccess = {
                            authState = AuthState.SUCCESS
                            onAuthenticated()
                        }, onFailed = {
                            failMessage = "Authentication failed. Try again."
                        }, onError = { failMessage = it })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Fingerprint, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Authenticate", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("Biometric authentication not available on this device.",
                    color = CiyatoMuted, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CiyatoSec)
            }
        }
    }
}

private fun triggerBiometric(
    context: Context,
    appLabel: String,
    onSuccess: () -> Unit,
    onFailed: () -> Unit,
    onError: (String) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(context)
    val activity = context as? FragmentActivity ?: return

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationFailed() {
            onFailed()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                onError(errString.toString())
            }
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock $appLabel")
        .setSubtitle("Use your biometric to open this app")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    prompt.authenticate(info)
}
