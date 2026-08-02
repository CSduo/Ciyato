package com.ciyato.launcher.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import kotlinx.coroutines.delay

/**
 * VpnStatusIndicator — Suggestion #81
 * Shows a VPN shield badge on the home screen when a VPN is active.
 */

@Composable
fun VpnStatusIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isVpnActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isVpnActive = isVpnConnected(context)
            delay(5_000L)
        }
    }

    if (!isVpnActive) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A2E3A))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = "VPN active" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF39C66A))
        )
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            tint = Color(0xFF39C66A),
            modifier = Modifier.size(13.dp),
        )
        Text("VPN", color = Color(0xFF39C66A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun isVpnConnected(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networks = cm.allNetworks
            networks.any { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } else {
            @Suppress("DEPRECATION")
            cm.allNetworkInfo.any { it.type == ConnectivityManager.TYPE_VPN && it.isConnected }
        }
    } catch (_: Exception) { false }
}
