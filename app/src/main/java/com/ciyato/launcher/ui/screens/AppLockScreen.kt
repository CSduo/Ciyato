package com.ciyato.launcher.ui.screens

import android.content.Context
import android.content.ContextWrapper
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
import com.ciyato.launcher.viewmodel.LauncherViewModel
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
 * App Lock — authentication before Ciyato opens a locked app.
 *
 * [AppLockGate] existed with no preference behind it and no caller at all
 * (F-021, F-148). It is wired now, and deliberately in one place: the ViewModel
 * holds back the launch and publishes the pending app, [AppLockHost] renders the
 * gate at both Compose roots, and every launch surface in the app is covered
 * without any of them knowing about locking.
 *
 * The scope is stated wherever a user meets this feature, never implied away: it
 * gates launches that start in Ciyato. Recents, notifications, system search and
 * other launchers do not pass through Ciyato and will not ask. No launcher can
 * change that.
 */

enum class AuthState { IDLE, AUTHENTICATING, SUCCESS, FAILED }

/**
 * Renders the gate whenever a locked app is waiting on authentication.
 *
 * Placed once at each Compose root rather than at each launch site, so a launch
 * surface added later is gated by default instead of by remembering to.
 */
@Composable
fun AppLockHost(viewModel: LauncherViewModel) {
    val pending by viewModel.pendingLockedApp.collectAsState()
    pending?.let { app ->
        AppLockGate(
            app = app,
            onAuthenticated = viewModel::confirmPendingLock,
            onDismiss = viewModel::cancelPendingLock,
        )
    }
}

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
            Text(
                "Unlock to open it from Ciyato",
                color = CiyatoMuted, fontSize = 14.sp,
            )

            if (failMessage != null) {
                Text(failMessage!!, color = CiyatoRed, fontSize = 14.sp)
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
                // No biometric and no screen lock enrolled. Refusing outright
                // would strand the app behind a gate that can never be passed,
                // so say why and let the launch through — the lock is a Ciyato
                // convenience, not a security boundary, and pretending otherwise
                // here would only lock someone out of their own phone.
                Text(
                    "This device has no fingerprint, face unlock or screen lock set up, " +
                        "so Ciyato cannot ask for one. Set a screen lock in Android " +
                        "Settings to make this work.",
                    color = CiyatoMuted, fontSize = 13.sp, lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Button(
                    onClick = onAuthenticated,
                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open anyway", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }

            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CiyatoSec)
            }
        }
    }
}

/**
 * Compose's LocalContext is frequently a ContextWrapper around the Activity
 * rather than the Activity itself, so a bare `context as? FragmentActivity`
 * cast is unreliable. Unwrap the base-context chain to find the real host.
 */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

fun triggerBiometric(
    context: Context,
    appLabel: String,
    onSuccess: () -> Unit,
    onFailed: () -> Unit,
    onError: (String) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(context)
    // Never fail silently: a swallowed return here looks identical to "the user
    // ignored the prompt", which previously made app-lock a no-op with no clue why.
    val activity = context.findFragmentActivity() ?: run {
        onError("Secure prompt unavailable on this screen.")
        return
    }

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
