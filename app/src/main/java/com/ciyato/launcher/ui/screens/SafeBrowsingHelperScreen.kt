package com.ciyato.launcher.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * SafeBrowsingHelperScreen — Suggestion #83
 * Checks URLs against a safe-browsing heuristic list before launching.
 * Production: integrate Google SafeBrowsing API v4 with an API key.
 * Current: checks against known malicious TLD patterns and phishing indicators.
 */

object SafeBrowsingHelper {

    sealed class SafetyResult {
        object Safe : SafetyResult()
        data class Suspicious(val reason: String) : SafetyResult()
        data class Unsafe(val reason: String) : SafetyResult()
    }

    private val SUSPICIOUS_TLDS = setOf(".xyz", ".tk", ".ml", ".ga", ".cf", ".gq", ".pw", ".cc")
    private val PHISHING_PATTERNS = listOf(
        "login-", "secure-", "verify-", "account-", "update-",
        "confirm-", "webscr", "paypal-", "apple-", "google-login",
    )
    private val KNOWN_SAFE_DOMAINS = setOf(
        "google.com", "youtube.com", "facebook.com", "twitter.com",
        "instagram.com", "linkedin.com", "github.com", "stackoverflow.com",
        "reddit.com", "wikipedia.org", "amazon.com", "apple.com",
    )

    suspend fun checkUrl(rawUrl: String): SafetyResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(rawUrl.trim())
            val host = uri.host?.lowercase() ?: return@withContext SafetyResult.Unsafe("Invalid URL")

            // Whitelist check
            if (KNOWN_SAFE_DOMAINS.any { host.endsWith(it) }) return@withContext SafetyResult.Safe

            // TLD check
            val suspiciousTld = SUSPICIOUS_TLDS.firstOrNull { host.endsWith(it) }
            if (suspiciousTld != null) return@withContext SafetyResult.Suspicious("Suspicious TLD: $suspiciousTld")

            // Phishing pattern check
            val phishingPattern = PHISHING_PATTERNS.firstOrNull { host.contains(it) || rawUrl.contains(it) }
            if (phishingPattern != null) return@withContext SafetyResult.Suspicious("Phishing indicator: '$phishingPattern'")

            // IP address check (direct IP URLs are suspicious)
            if (host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                return@withContext SafetyResult.Suspicious("Direct IP URL — potential redirect or phishing")
            }

            // Excessive subdomains
            if (host.split(".").size > 4) {
                return@withContext SafetyResult.Suspicious("Excessive subdomain depth — common in phishing")
            }

            SafetyResult.Safe
        } catch (_: Exception) { SafetyResult.Unsafe("Could not parse URL") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBrowsingScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SafeBrowsingHelper.SafetyResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }


    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Safe Browsing", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
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
            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Check any URL before opening it", color = CiyatoMuted, fontSize = 13.sp)
                }
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it; result = null },
                label = { Text("URL to check") },
                placeholder = { Text("https://example.com") },
                leadingIcon = { Icon(Icons.Default.Link, null, tint = CiyatoMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CiyatoGold, focusedLabelColor = CiyatoGold, cursorColor = CiyatoGold),
            )

            Button(
                onClick = {
                    isChecking = true
                    result = null
                    scope.launch {
                        result = SafeBrowsingHelper.checkUrl(urlInput)
                        isChecking = false
                    }
                },
                enabled = urlInput.isNotBlank() && !isChecking,
                colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isChecking) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…", color = Color.Black)
                } else {
                    Icon(Icons.Default.Shield, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check URL", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }

            result?.let { r ->
                val (bg, icon, title, msg, color) = when (r) {
                    is SafeBrowsingHelper.SafetyResult.Safe ->
                        listOf(Color(0xFF4CAF50).copy(alpha = 0.15f), Icons.Default.CheckCircle,
                            "✅ URL is Safe", "No threats detected. Safe to open.", Color(0xFF4CAF50))
                    is SafeBrowsingHelper.SafetyResult.Suspicious ->
                        listOf(Color(0xFFFF9800).copy(alpha = 0.15f), Icons.Default.Warning,
                            "⚠️ Suspicious URL", r.reason, Color(0xFFFF9800))
                    is SafeBrowsingHelper.SafetyResult.Unsafe ->
                        listOf(Color(0xFFF44336).copy(alpha = 0.15f), Icons.Default.GppBad,
                            "🚫 Unsafe URL", r.reason, Color(0xFFF44336))
                }
                Card(colors = CardDefaults.cardColors(containerColor = bg as Color), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(icon as androidx.compose.ui.graphics.vector.ImageVector, null, tint = color as Color)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title as String, color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                            Text(msg as String, color = CiyatoMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
