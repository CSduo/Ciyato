package com.ciyato.launcher.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.NetworkClient
import androidx.compose.ui.text.input.ImeAction
import com.ciyato.launcher.ui.components.CiyatoPasswordField
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * DataBreachCheckerScreen — Suggestion #85
 * Checks if a password has appeared in known data breaches
 * using the HaveIBeenPwned k-anonymity API (only first 5 chars of SHA-1 sent).
 *
 * Two separate protections, both required for the privacy claim on screen:
 *  - k-anonymity: only a 5-character hash prefix is sent, so the service sees a
 *    bucket of roughly 800 hashes and never the password or its full hash.
 *  - response padding: requested via the Add-Padding header, so the response
 *    size does not reveal which prefix was queried to an on-path observer.
 */

sealed class BreachResult {
    data class Found(val count: Int) : BreachResult()
    object NotFound : BreachResult()
}

/** Distinguishes "we have an answer" from "the check itself failed" — never collapsed into one. */
private sealed class CheckOutcome {
    data class Answered(val result: BreachResult) : CheckOutcome()
    data class Failed(val reason: String) : CheckOutcome()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBreachCheckerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BreachResult?>(null) }
    var error by remember { mutableStateOf("") }

    suspend fun checkPassword(pw: String): CheckOutcome = withContext(Dispatchers.IO) {
        val sha1 = sha1(pw).uppercase()
        val prefix = sha1.take(5)
        val suffix = sha1.drop(5)
        try {
            // NetworkClient handles the timeout + retry-on-transient-failure
            // policy and always closes the connection, even on error.
            val body = NetworkClient.fetchText(
                "https://api.pwnedpasswords.com/range/$prefix",
                headers = mapOf(
                    "User-Agent" to "Ciyato-Launcher",
                    // Without this, every prefix returns a differently-sized
                    // response, so anyone who can see the encrypted traffic can
                    // infer which prefix was asked for from the byte count alone.
                    // k-anonymity protects the hash; padding protects the query.
                    // The screen claims the check is privacy-safe — this is part
                    // of what makes that claim true.
                    "Add-Padding" to "true",
                ),
            )
            val breachResult = parseBreachResponse(body, suffix)
            CheckOutcome.Answered(breachResult)
        } catch (_: UnknownHostException) {
            CheckOutcome.Failed("You're offline. Check your connection and try again.")
        } catch (e: NetworkClient.HttpStatusException) {
            CheckOutcome.Failed("The breach-check service returned an error (HTTP ${e.code}). Try again shortly.")
        } catch (_: Exception) {
            CheckOutcome.Failed("Could not reach the breach-check service. Try again.")
        }
    }

    // One definition of "start the check", shared by the button and the
    // keyboard's Go key, so the two can never drift apart.
    val canCheck = password.isNotBlank() && !isChecking
    fun runCheck() {
        isChecking = true
        result = null
        error = ""
        scope.launch {
            when (val outcome = checkPassword(password)) {
                is CheckOutcome.Answered -> result = outcome.result
                is CheckOutcome.Failed   -> error = outcome.reason
            }
            isChecking = false
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Breach Checker",
                subtitle = "Privacy-safe k-anonymity check",
                onBack = onBack,
            )
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
            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Privacy-safe check", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Only the first 5 characters of a SHA-1 hash are sent. Your password never leaves your device.",
                        color = CiyatoMuted, fontSize = 12.sp,
                    )
                }
            }

            // The design system's own password field, rather than a second
            // hand-rolled one. It was unused and — until this change — did not
            // mask its input at all; putting the real screen on it means that
            // cannot quietly break again.
            CiyatoPasswordField(
                value = password,
                onValueChange = { password = it; result = null; error = "" },
                label = "Password to check",
                imeAction = ImeAction.Go,
                onImeAction = { if (canCheck) runCheck() },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = ::runCheck,
                enabled = canCheck,
                colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isChecking) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…", color = Color.Black)
                } else {
                    Icon(Icons.Default.Search, null, tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Check Password", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }

            val currentResult = result
            if (currentResult != null) {
                val bg = if (currentResult is BreachResult.Found) Color(0xFFF44336).copy(alpha = 0.15f) else CiyatoGreen.copy(alpha = 0.15f)
                val icon = if (currentResult is BreachResult.Found) Icons.Default.Warning else Icons.Default.CheckCircle
                val title = if (currentResult is BreachResult.Found) "⚠️ Password Compromised" else "✅ Password Safe"
                val msg = if (currentResult is BreachResult.Found) {
                    "This password appeared in ${currentResult.count.toIntFormatted()} known breaches. Change it immediately."
                } else {
                    "Not found in any known breach database."
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (currentResult is BreachResult.Found) Color(0xFFF44336) else CiyatoGreen
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                            Text(msg, color = CiyatoMuted, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }
        }
    }
}

/**
 * Finds [suffix] in a pwnedpasswords range response.
 *
 * Extracted from the composable so it can be tested: with padding enabled the
 * response deliberately contains synthetic hashes, and telling those apart from
 * real hits is now a correctness requirement rather than a detail. A padded
 * entry carries an occurrence count of zero; counting one as a hit would tell
 * someone their password was breached "0 times" — alarming and wrong.
 *
 * Matching is case-insensitive because the API's casing is not contractual, and
 * malformed lines are skipped rather than being allowed to abort the scan.
 */
internal fun parseBreachResponse(body: String, suffix: String): BreachResult =
    body.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith(suffix, ignoreCase = true)) return@mapNotNull null
            trimmed.substringAfter(':', "").trim().toIntOrNull()
        }
        .firstOrNull { it > 0 }
        ?.let { BreachResult.Found(it) }
        ?: BreachResult.NotFound

private fun sha1(text: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun Int.toIntFormatted(): String = "%,d".format(this)
