package com.ciyato.launcher.ui.screens

import android.net.Uri
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
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Local URL inspection — warning signs only, never a safety verdict.
 *
 * This looks at the text of a URL. It performs no reputation lookup, contacts no
 * blocklist, and cannot know whether a page is malicious. Two consequences are
 * designed in rather than papered over:
 *
 *  1. **It never says "safe".** The previous version returned `Safe` for
 *     anything that failed to trip a heuristic and the UI rendered
 *     "✅ URL is Safe — No threats detected. Safe to open." A brand-new phishing
 *     domain trips none of these rules, so the reassurance was strongest exactly
 *     where it was most dangerous. The absence of a warning sign is not evidence
 *     of safety, and this type can no longer express that claim.
 *  2. **Every signal is collected**, not just the first. Returning on the first
 *     match hid the fact that a URL had three separate problems.
 */
object SafeBrowsingHelper {

    sealed interface UrlCheck {
        /** Not a parseable URL. */
        data class Invalid(val reason: String) : UrlCheck

        /**
         * Host is an exact match for a widely-known domain. Still not a safety
         * verdict — a compromised or attacker-controlled page can live on any
         * major domain — so this only says "the domain is what it appears to be".
         */
        data class RecognisedDomain(val domain: String) : UrlCheck

        /** One or more warning signs found in the URL text. */
        data class Warnings(val reasons: List<String>) : UrlCheck

        /** Nothing in the URL text stood out. Explicitly NOT "safe". */
        data object NoSignals : UrlCheck
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

    /**
     * True when [host] IS [domain] or a subdomain of it.
     *
     * The bug this replaces was `host.endsWith(domain)`, which matches on raw
     * characters rather than DNS label boundaries — so `evilgoogle.com` "ended
     * with" `google.com` and was declared safe, which is precisely the trick a
     * lookalike domain relies on. Comparing against `".$domain"` forces the
     * match to fall on a label boundary.
     */
    private fun hostMatchesDomain(host: String, domain: String): Boolean {
        val h = host.trimEnd('.').lowercase(Locale.ROOT)
        val d = domain.trimEnd('.').lowercase(Locale.ROOT)
        return h == d || h.endsWith(".$d")
    }

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    suspend fun checkUrl(rawUrl: String): UrlCheck = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: return@withContext UrlCheck.Invalid("That doesn't look like a web address")

        val recognised = KNOWN_SAFE_DOMAINS.firstOrNull { hostMatchesDomain(host, it) }

        // Collected, not short-circuited: a URL with a lookalike host AND a
        // phishing keyword is more alarming than either alone, and the previous
        // version reported only whichever rule happened to run first.
        val reasons = buildList {
            SUSPICIOUS_TLDS.firstOrNull { host.endsWith(it) }?.let {
                add("Uses the $it top-level domain, which is heavily abused")
            }
            PHISHING_PATTERNS.firstOrNull { host.contains(it) || trimmed.contains(it) }?.let {
                add("Contains \"$it\", a pattern common in phishing links")
            }
            if (IPV4.matches(host)) {
                add("Points at a raw IP address instead of a domain name")
            }
            if (host.split('.').size > 4) {
                add("Unusually deep subdomain nesting")
            }
            // Only meaningful when the host is NOT the real domain: a host that
            // merely contains a famous name is the lookalike case the broken
            // endsWith check used to wave through.
            if (recognised == null) {
                KNOWN_SAFE_DOMAINS.firstOrNull { known ->
                    val bare = known.substringBefore('.')
                    host.contains(bare) && !hostMatchesDomain(host, known)
                }?.let { known ->
                    add("Mentions \"${known.substringBefore('.')}\" but is not $known")
                }
            }
        }

        when {
            reasons.isNotEmpty() -> UrlCheck.Warnings(reasons)
            recognised != null -> UrlCheck.RecognisedDomain(recognised)
            else -> UrlCheck.NoSignals
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBrowsingHelperScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SafeBrowsingHelper.UrlCheck?>(null) }
    var isChecking by remember { mutableStateOf(false) }


    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Safe Browsing",
                subtitle = "Reads the address only — no reputation lookup",
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

            // Titles carry no emoji and no safety verdict. Emoji were doing the
            // semantic work here ("✅ URL is Safe"), which a screen reader either
            // skips or reads as "white heavy check mark" — the meaning has to be
            // in the text and the icon, not in a glyph (F-059).
            result?.let { r ->
                val icon = when (r) {
                    is SafeBrowsingHelper.UrlCheck.Warnings -> Icons.Default.Warning
                    is SafeBrowsingHelper.UrlCheck.Invalid -> Icons.Default.GppBad
                    else -> Icons.Default.Shield
                }
                val accent = when (r) {
                    is SafeBrowsingHelper.UrlCheck.Warnings -> CiyatoWarning
                    is SafeBrowsingHelper.UrlCheck.Invalid -> CiyatoRed
                    else -> CiyatoSec
                }
                val title = when (r) {
                    is SafeBrowsingHelper.UrlCheck.Invalid -> "Not a web address"
                    is SafeBrowsingHelper.UrlCheck.Warnings ->
                        if (r.reasons.size == 1) "1 warning sign" else "${r.reasons.size} warning signs"
                    is SafeBrowsingHelper.UrlCheck.RecognisedDomain -> "Domain recognised"
                    SafeBrowsingHelper.UrlCheck.NoSignals -> "Nothing stood out"
                }
                val body = when (r) {
                    is SafeBrowsingHelper.UrlCheck.Invalid -> r.reason
                    is SafeBrowsingHelper.UrlCheck.Warnings -> r.reasons.joinToString("\n") { "• $it" }
                    is SafeBrowsingHelper.UrlCheck.RecognisedDomain ->
                        "This really is ${r.domain}. That doesn't mean the page itself is " +
                            "trustworthy — any site can host a bad page."
                    SafeBrowsingHelper.UrlCheck.NoSignals ->
                        "Ciyato found no warning signs in the address text. It cannot tell you " +
                            "the site is safe — it never contacts a reputation service, and a " +
                            "brand-new scam link looks completely ordinary."
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(icon, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(body, color = CiyatoMuted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
