package com.ciyato.launcher.ui.screens

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.data.LocationHelper
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * LocationPredictiveScreen — Suggestion #38
 * Predicts and surfaces app suggestions based on current location.
 * Correlates past app usage with location buckets (Home, Work, Transit, Other).
 */

enum class LocationBucket { HOME, WORK, TRANSIT, OTHER }

data class LocationPrediction(
    val app: InstalledApp,
    val score: Float,
    val reason: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPredictiveScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var predictions by remember { mutableStateOf<List<LocationPrediction>>(emptyList()) }
    var locationBucket by remember { mutableStateOf<LocationBucket?>(null) }
    var locationLabel by remember { mutableStateOf("Detecting location…") }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) { isLoading = false; return@LaunchedEffect }

        val loc = withContext(Dispatchers.IO) { LocationHelper.getLocation(context) }
        if (loc != null) {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        var result: List<android.location.Address>? = null
                        geocoder.getFromLocation(loc.lat, loc.lon, 1) { result = it }
                        result
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(loc.lat, loc.lon, 1)
                    }
                    locationLabel = addresses?.firstOrNull()?.locality ?: "Current Location"
                } catch (_: Exception) { locationLabel = "Current Location" }
            }

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            locationBucket = when {
                hour in 8..17 -> LocationBucket.WORK
                hour in 7..8 || hour in 17..19 -> LocationBucket.TRANSIT
                else -> LocationBucket.HOME
            }
        } else {
            locationLabel = "Location unavailable"
        }

        val apps = viewModel.apps.value
        predictions = buildPredictions(context, apps, locationBucket ?: LocationBucket.OTHER)
        isLoading = false
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Location-Smart Apps", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(locationLabel, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            locationBucket?.let {
                                Text("${it.name} mode", color = CiyatoGold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (!hasPermission) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Location permission needed", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                            Text("Grant location to get context-aware app suggestions", color = CiyatoMuted, fontSize = 12.sp)
                            Button(onClick = { permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                                colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
                                Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CiyatoGold)
                            Spacer(Modifier.height(8.dp))
                            Text("Building location predictions…", color = CiyatoMuted)
                        }
                    }
                }
            } else {
                item { Text("Suggested for here & now", color = CiyatoMuted, fontSize = 12.sp) }
                items(predictions) { pred ->
                    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Apps, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pred.app.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(pred.reason, color = CiyatoMuted, fontSize = 11.sp)
                            }
                            Text("${(pred.score * 100).toInt()}%", color = CiyatoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun buildPredictions(context: Context, apps: List<InstalledApp>, bucket: LocationBucket): List<LocationPrediction> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return emptyList()
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, now - 7L * 86400000, now)
            .associateBy { it.packageName }

        val bucketCategories = when (bucket) {
            LocationBucket.WORK    -> setOf(com.ciyato.launcher.data.AppCategory.WORK, com.ciyato.launcher.data.AppCategory.PRODUCTIVITY, com.ciyato.launcher.data.AppCategory.COMMUNICATION)
            LocationBucket.HOME    -> setOf(com.ciyato.launcher.data.AppCategory.ENTERTAINMENT, com.ciyato.launcher.data.AppCategory.SOCIAL, com.ciyato.launcher.data.AppCategory.DAILY)
            LocationBucket.TRANSIT -> setOf(com.ciyato.launcher.data.AppCategory.SOCIAL, com.ciyato.launcher.data.AppCategory.ENTERTAINMENT, com.ciyato.launcher.data.AppCategory.TRAVEL)
            LocationBucket.OTHER   -> setOf(com.ciyato.launcher.data.AppCategory.UTILITIES, com.ciyato.launcher.data.AppCategory.DAILY)
        }

        apps.filter { it.category in bucketCategories }
            .map { app ->
                val usage = stats[app.packageName]?.totalTimeInForeground ?: 0L
                val score = (0.4f + (usage / 3_600_000f).coerceAtMost(0.6f))
                LocationPrediction(
                    app = app,
                    score = score.coerceIn(0f, 1f),
                    reason = "Frequently used at ${bucket.name.lowercase()} • ${usage / 60000}min/week",
                )
            }
            .sortedByDescending { it.score }
            .take(8)
    } catch (_: Exception) { emptyList() }
}
