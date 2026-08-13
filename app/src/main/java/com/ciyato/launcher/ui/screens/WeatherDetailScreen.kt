package com.ciyato.launcher.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.LocationHelper
import com.ciyato.launcher.data.WeatherRepository
import com.ciyato.launcher.data.WeatherRepository.WeatherState
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.ui.components.Weather2DEffectOverlay
import com.ciyato.launcher.ui.components.resolveSeasonTheme
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * WeatherDetailScreen — Live weather powered by Open-Meteo (no API key).
 * Redesigned with state-aware gradients, animated weather icons, frosted glass hourly cards,
 * animated AQI & UV meters, and crisp modern typography.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    viewModel: LauncherViewModel? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var localState by remember {
        mutableStateOf<WeatherState>(
            if (LocationHelper.hasPermission(context)) WeatherState.Loading
            else WeatherState.NoPermission
        )
    }

    val vmState by (viewModel?.weatherState
        ?: kotlinx.coroutines.flow.MutableStateFlow(localState)).collectAsState()

    val weatherState  = if (viewModel != null) vmState else localState
    val tempUnit      by (viewModel?.tempUnit     ?: kotlinx.coroutines.flow.MutableStateFlow("C")).collectAsState()
    val useFahrenheit = tempUnit == "F"

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (LocationHelper.hasPermission(context)) {
            if (viewModel != null) viewModel.fetchWeather(context)
            else {
                localState = WeatherState.Loading
            }
        }
    }

    LaunchedEffect(Unit) {
        if (LocationHelper.hasPermission(context)) {
            if (viewModel != null) {
                viewModel.fetchWeather(context)
            } else {
                localState = WeatherState.Loading
                val loc = LocationHelper.getLocation(context)
                localState = if (loc != null)
                    WeatherRepository.fetchWeather(loc.lat, loc.lon)
                else WeatherState.NoLocation
            }
        }
    }

    val onRefresh = {
        if (viewModel != null) viewModel.forceRefreshWeather(context)
    }

    // Weather-state aware gradient background (Requirement 2)
    val backgroundGradient = remember(weatherState) {
        if (weatherState is WeatherState.Success) {
            val code = weatherState.weatherCode
            val isDay = weatherState.isDay
            when {
                // Sunny
                code == 0 && isDay -> Brush.verticalGradient(
                    listOf(Color(0xFFFF8C42), Color(0xFFFFC947), Color(0xFF050607))
                )
                // Rainy / Storm
                code in 51..67 || code in 80..86 || code in 95..99 -> Brush.verticalGradient(
                    listOf(Color(0xFF394B6A), Color(0xFF5C9EEA), Color(0xFF050607))
                )
                // Cloudy / Fog
                code in 1..3 || code in 45..48 -> Brush.verticalGradient(
                    listOf(Color(0xFF4A505A), Color(0xFF778090), Color(0xFF050607))
                )
                // Night / Default
                else -> Brush.verticalGradient(listOf(CiyatoBgEl2, CiyatoBg))
            }
        } else {
            Brush.verticalGradient(listOf(CiyatoBgEl2, CiyatoBg))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Weather", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoSec)
                    }
                },
                actions = {
                    if (viewModel != null) {
                        TextButton(onClick = {
                            viewModel.setTempUnit(if (useFahrenheit) "C" else "F")
                        }) {
                            Text(if (useFahrenheit) "°C" else "°F", color = CiyatoGold, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (weatherState is WeatherState.Success || weatherState is WeatherState.Offline) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = CiyatoSec)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {
            AnimatedContent(
                targetState = weatherState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "weather",
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { state ->
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (state) {
                        is WeatherState.NoPermission -> item {
                            WeatherPermissionCard {
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            }
                        }
                        is WeatherState.Loading -> item { WeatherLoadingCard() }
                        is WeatherState.NoLocation -> item {
                            WeatherInfoCard(Icons.Default.LocationOff, "Location unavailable",
                                "Enable location services in Android Settings.", "Retry", onRefresh)
                        }
                        is WeatherState.Offline -> item {
                            WeatherInfoCard(Icons.Default.WifiOff, "You're offline",
                                "Weather requires an internet connection.", "Retry", onRefresh)
                        }
                        is WeatherState.Error -> item {
                            WeatherInfoCard(Icons.Default.CloudOff, "Connection error", state.message, "Retry", onRefresh)
                        }
                        is WeatherState.Success -> {
                            if (state.isStale) item { StaleWeatherBanner(state.cachedAtMillis, onRefresh) }
                            item { WeatherHeroCard(state, useFahrenheit) }
                            item { WeatherHighlightsRow(state, useFahrenheit) }
                            if (state.hourly.isNotEmpty()) {
                                item { HourlyForecastCard(state, useFahrenheit) }
                            }
                            if (state.daily.isNotEmpty()) {
                                item { DailyForecastCard(state, useFahrenheit) }
                            }
                            item { UvSunriseCard(state) }
                            item { WindCompassCard(state) }
                            state.aqi?.let { item { AqiCard(it) } }
                            item { WeatherAttribution() }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// ─── Animated Weather Icon Composable (Requirement 1) ─────────────────────────

@Composable
fun WeatherAnimatedIcon(
    weatherCode: Int,
    isDay: Boolean = true,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val infiniteTransition = rememberInfiniteTransition(label = "weatherIconAnim")

    when {
        // Sunny / Clear Day: Breathing scale pulsing glow
        weatherCode == 0 && isDay -> {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sunPulse"
            )
            val alphaGlow by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sunGlow"
            )
            Icon(
                imageVector = Icons.Filled.WbSunny,
                contentDescription = "Sunny",
                tint = if (tint != Color.Unspecified) tint else CiyatoGoldSoft,
                modifier = modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = alphaGlow
                }
            )
        }

        // Rainy / Storm: Downward moving drop offset animation
        weatherCode in 51..67 || weatherCode in 80..86 || weatherCode in 95..99 -> {
            val dropOffset by infiniteTransition.animateFloat(
                initialValue = -3f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(750, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rainDrop"
            )
            val icon = if (weatherCode in 95..99) Icons.Default.Bolt else Icons.Default.Umbrella
            Icon(
                imageVector = icon,
                contentDescription = "Rainy",
                tint = if (tint != Color.Unspecified) tint else CiyatoBlue,
                modifier = modifier.graphicsLayer {
                    translationY = dropOffset
                }
            )
        }

        // Cloudy / Fog: Gentle horizontal swaying animation
        weatherCode in 1..3 || weatherCode in 45..48 -> {
            val swayOffset by infiniteTransition.animateFloat(
                initialValue = -4f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cloudSway"
            )
            Icon(
                imageVector = if (weatherCode in 45..48) Icons.Default.BlurOn else Icons.Default.Cloud,
                contentDescription = "Cloudy",
                tint = if (tint != Color.Unspecified) tint else CiyatoSec,
                modifier = modifier.graphicsLayer {
                    translationX = swayOffset
                }
            )
        }

        // Clear Night: Soft rotation sway
        weatherCode == 0 && !isDay -> {
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = -6f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "moonSway"
            )
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = "Clear Night",
                tint = if (tint != Color.Unspecified) tint else CiyatoSec,
                modifier = modifier.graphicsLayer {
                    rotationZ = rotationAngle
                }
            )
        }

        // Snow: Slow gentle spin
        weatherCode in 71..77 -> {
            val snowSpin by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(6000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "snowSpin"
            )
            Icon(
                imageVector = Icons.Default.AcUnit,
                contentDescription = "Snow",
                tint = if (tint != Color.Unspecified) tint else Color(0xFFE0F7FA),
                modifier = modifier.graphicsLayer {
                    rotationZ = snowSpin
                }
            )
        }

        // Fallback static helper icon
        else -> {
            Icon(
                imageVector = weatherIcon(weatherCode, isDay),
                contentDescription = null,
                tint = if (tint != Color.Unspecified) tint else CiyatoSec,
                modifier = modifier
            )
        }
    }
}

// ─── Permission card ──────────────────────────────────────────────────────────

@Composable
private fun WeatherPermissionCard(onEnable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(CiyatoGold.copy(alpha = 0.08f))
            .border(1.dp, CiyatoGold.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(CiyatoGold.copy(0.18f))) {
                Icon(Icons.Default.LocationOn, null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
            }
            Column {
                Text("Enable Local Weather", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Precise location available", color = CiyatoGold, fontSize = 12.sp)
            }
        }
        Text("Ciyato uses Open-Meteo to show local conditions. Android lets you choose precise or approximate foreground access; your choice is respected.",
            color = CiyatoSec, fontSize = 13.sp, lineHeight = 20.sp)
        Button(onClick = onEnable, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enable Local Weather", color = CiyatoBg, fontWeight = FontWeight.Bold)
        }
        Text("Foreground location only · No background tracking · Open-Meteo",
            color = CiyatoMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WeatherLoadingCard() {
    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = CiyatoGold, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
            Text("Fetching local weather…", color = CiyatoSec, fontSize = 14.sp)
        }
    }
}

// ─── Stale-cache banner ───────────────────────────────────────────────────────
// Shown only when the weather on screen is a saved snapshot served because the
// live fetch just failed — never rendered silently as if it were fresh.

@Composable
private fun StaleWeatherBanner(cachedAtMillis: Long?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CiyatoAmber.copy(alpha = 0.12f))
            .border(1.dp, CiyatoAmber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.CloudOff, null, tint = CiyatoAmber, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text("Showing saved weather", color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "From ${formatRelativeAge(cachedAtMillis)} — couldn't reach the weather service",
                color = CiyatoMuted, fontSize = 11.sp,
            )
        }
        TextButton(onClick = onRefresh) {
            Text("Retry", color = CiyatoAmber, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

private fun formatRelativeAge(millis: Long?): String {
    if (millis == null) return "earlier"
    val ageMs = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    val minutes = ageMs / 60_000
    return when {
        minutes < 1  -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}

// ─── Hero card (Requirement 3) ────────────────────────────────────────────────

@Composable
private fun WeatherHeroCard(state: WeatherState.Success, useFahrenheit: Boolean) {
    val theme = resolveSeasonTheme(state.weatherCode, state.tempC)
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
        Weather2DEffectOverlay(theme = theme, modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(CiyatoBgEl.copy(0.85f), CiyatoBgEl2.copy(0.92f))))
                .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(28.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.LocationOn, null, tint = CiyatoGold, modifier = Modifier.size(16.dp))
            Text(state.locationName, color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            WeatherAnimatedIcon(
                weatherCode = state.weatherCode,
                isDay = state.isDay,
                modifier = Modifier.size(60.dp)
            )
            Column {
                val displayTemp = if (useFahrenheit) "${WeatherRepository.cToF(state.tempC)}°" else "${state.tempC}°"
                // Large Temperature Display (Requirement 3)
                Text(
                    text = displayTemp,
                    color = CiyatoWhite,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = (-2).sp,
                    lineHeight = 76.sp,
                    modifier = Modifier.graphicsLayer { alpha = 0.95f }
                )
                Text(state.condition, color = CiyatoSec, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }

        HorizontalDivider(color = CiyatoSubtleBorder)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val fl  = if (useFahrenheit) "${WeatherRepository.cToF(state.feelsLikeC)}°" else "${state.feelsLikeC}°"
            val hi  = if (useFahrenheit) "${WeatherRepository.cToF(state.highC)}°" else "${state.highC}°"
            val lo  = if (useFahrenheit) "${WeatherRepository.cToF(state.lowC)}°" else "${state.lowC}°"
            StatChip("Feels like", fl)
            StatChip("High", hi, Color(0xFFFF6B6B))
            StatChip("Low", lo, CiyatoBlue)
        }
    }
}
}

@Composable
private fun StatChip(label: String, value: String, color: Color = CiyatoWhite) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = CiyatoMuted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Highlights row (wind + humidity) ────────────────────────────────────────

@Composable
private fun WeatherHighlightsRow(state: WeatherState.Success, useFahrenheit: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricTile("Wind", "${state.windKmh.toInt()} km/h", Icons.Default.Air, Modifier.weight(1f))
        MetricTile("Humidity", "${state.humidity}%", Icons.Default.WaterDrop, Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(CiyatoGold.copy(0.12f))) {
            Icon(icon, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(label, color = CiyatoMuted, fontSize = 11.sp)
            Text(value, color = CiyatoWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Hourly forecast strip (Requirement 4 & 7) ───────────────────────────────

@Composable
private fun HourlyForecastCard(state: WeatherState.Success, useFahrenheit: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Schedule, null, tint = CiyatoGold, modifier = Modifier.size(16.dp))
            Text("Next 24 Hours", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        HorizontalDivider(color = CiyatoSubtleBorder)
        // Key added for hourly items (Requirement 7)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.hourly, key = { it.timeLabel }) { entry ->
                HourlyTile(entry, useFahrenheit)
            }
        }
    }
}

// Frosted Glass Card for Hourly Tile (Requirement 4)
@Composable
private fun HourlyTile(entry: WeatherRepository.HourlyEntry, useFahrenheit: Boolean) {
    val temp = if (useFahrenheit) WeatherRepository.cToF(entry.tempC) else entry.tempC
    val iconTint = if (entry.weatherCode == 0 && entry.isDay) CiyatoGoldSoft else CiyatoSec
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(entry.timeLabel, color = CiyatoMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        WeatherAnimatedIcon(
            weatherCode = entry.weatherCode,
            isDay = entry.isDay,
            modifier = Modifier.size(24.dp),
            tint = iconTint
        )
        Text("$temp°", color = CiyatoWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (entry.rainPct > 0) {
            Text("${entry.rainPct}%", color = CiyatoBlue, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── 7-day forecast (Requirement 5 & 7) ──────────────────────────────────────

@Composable
private fun DailyForecastCard(state: WeatherState.Success, useFahrenheit: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.DateRange, null, tint = CiyatoGold, modifier = Modifier.size(16.dp))
            Text("7-Day Forecast", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        HorizontalDivider(color = CiyatoSubtleBorder)
        state.daily.forEachIndexed { i, entry ->
            // Key added for daily list items (Requirement 7)
            key(entry.dayLabel) {
                val hi    = if (useFahrenheit) WeatherRepository.cToF(entry.highC) else entry.highC
                val lo    = if (useFahrenheit) WeatherRepository.cToF(entry.lowC) else entry.lowC
                val label = if (i == 0) "Today" else entry.dayLabel

                // Weather color accent matching logic (Requirement 5)
                val accentColor = when {
                    entry.weatherCode == 0 -> Color(0xFFFFC947)
                    entry.weatherCode in 51..67 || entry.weatherCode in 80..86 || entry.weatherCode in 95..99 -> Color(0xFF5C9EEA)
                    entry.weatherCode in 1..3 || entry.weatherCode in 45..48 -> Color(0xFF778090)
                    entry.weatherCode in 71..77 -> Color(0xFFE0F7FA)
                    else -> CiyatoGold
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Subtle left-side colored accent bar matching weather color (Requirement 5)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Text(
                        label,
                        color = if (i == 0) CiyatoGold else CiyatoWhite,
                        fontSize = 13.sp,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(48.dp)
                    )
                    WeatherAnimatedIcon(
                        weatherCode = entry.weatherCode,
                        isDay = true,
                        modifier = Modifier.size(18.dp)
                    )
                    if (entry.rainPct > 0) {
                        Text("${entry.rainPct}%", color = CiyatoBlue, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                    } else {
                        Spacer(Modifier.width(32.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("$lo°", color = CiyatoBlue, fontSize = 13.sp)
                    Text(" – ", color = CiyatoMuted, fontSize = 13.sp)
                    Text("$hi°", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (i < state.daily.size - 1) HorizontalDivider(color = CiyatoSubtleBorder.copy(alpha = 0.5f))
            }
        }
    }
}

// ─── UV + Sunrise/Sunset (Requirement 6) ─────────────────────────────────────

@Composable
private fun UvSunriseCard(state: WeatherState.Success) {
    val uvColor = when {
        state.uvIndex < 3  -> Color(0xFF39C66A)
        state.uvIndex < 6  -> Color(0xFFF5C542)
        state.uvIndex < 8  -> Color(0xFFFF8C42)
        state.uvIndex < 11 -> CiyatoRed
        else               -> Color(0xFF9C27B0)
    }
    val uvLabel = when {
        state.uvIndex < 3  -> "Low"
        state.uvIndex < 6  -> "Moderate"
        state.uvIndex < 8  -> "High"
        state.uvIndex < 11 -> "Very High"
        else               -> "Extreme"
    }

    // Animated UV progress bar (Requirement 6)
    val targetUvPct = (state.uvIndex.toFloat() / 12f).coerceIn(0f, 1f)
    val animatedUvPct by animateFloatAsState(
        targetValue = targetUvPct,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "uvProgress"
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // UV Index
        Column(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(CiyatoBgEl)
                .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.WbSunny, null, tint = uvColor, modifier = Modifier.size(16.dp))
                Text("UV Index", color = CiyatoMuted, fontSize = 11.sp)
            }
            Text("%.1f".format(state.uvIndex), color = uvColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(uvLabel, color = uvColor.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            
            LinearProgressIndicator(
                progress = { animatedUvPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = uvColor,
                trackColor = CiyatoBgEl2
            )
        }
        // Sunrise / Sunset
        Column(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(CiyatoBgEl)
                .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Sun", color = CiyatoMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WbSunny, null, tint = CiyatoGoldSoft, modifier = Modifier.size(14.dp))
                Text(state.sunrise, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DarkMode, null, tint = CiyatoSec, modifier = Modifier.size(14.dp))
                Text(state.sunset, color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Wind compass card ───────────────────────────────────────────────────────

@Composable
private fun WindCompassCard(state: WeatherState.Success) {
    val direction = WeatherRepository.windDirection(state.windDirectionDeg)
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Wind Details", color = CiyatoMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Speed", color = CiyatoMuted, fontSize = 10.sp)
                Text("${state.windKmh.toInt()} km/h", color = CiyatoWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Direction", color = CiyatoMuted, fontSize = 10.sp)
                Text(direction, color = CiyatoGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${state.windDirectionDeg}°", color = CiyatoMuted, fontSize = 10.sp)
            }
        }
    }
}

// ─── AQI card (Requirement 6) ─────────────────────────────────────────────────

@Composable
private fun AqiCard(aqi: WeatherRepository.AqiData) {
    val color   = Color(WeatherRepository.aqiColor(aqi.aqiEu))
    val label   = WeatherRepository.aqiLabel(aqi.aqiEu)
    val targetPct = (aqi.aqiEu / 100f).coerceIn(0f, 1f)

    // Animated AQI progress bar (Requirement 6)
    val animatedPct by animateFloatAsState(
        targetValue = targetPct,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "aqiProgress"
    )

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Air, null, tint = color, modifier = Modifier.size(16.dp))
            Text("Air Quality (European AQI)", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("$label — ${aqi.aqiEu}", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text("PM2.5: ${"%.1f".format(aqi.pm25)} µg/m³", color = CiyatoMuted, fontSize = 11.sp)
                Text("PM10 : ${"%.1f".format(aqi.pm10)} µg/m³", color = CiyatoMuted, fontSize = 11.sp)
            }
        }
        LinearProgressIndicator(
            progress = { animatedPct },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = CiyatoBgEl2
        )
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when {
    code == 0  && isDay  -> Icons.Outlined.WbSunny
    code == 0  && !isDay -> Icons.Default.DarkMode
    code in 1..2         -> Icons.Default.Cloud
    code == 3            -> Icons.Default.Cloud
    code in 45..48       -> Icons.Default.BlurOn
    code in 51..67       -> Icons.Default.Grain
    code in 71..77       -> Icons.Default.AcUnit
    code in 80..86       -> Icons.Default.Umbrella
    code in 95..99       -> Icons.Default.Bolt
    else                 -> Icons.Default.Cloud
}

@Composable
private fun WeatherInfoCard(
    icon: ImageVector, title: String, body: String,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CiyatoBgEl)
        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = CiyatoMuted, modifier = Modifier.size(36.dp))
        Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(body, color = CiyatoSec, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CiyatoGold),
                border = androidx.compose.foundation.BorderStroke(1.dp, CiyatoGold.copy(0.4f)),
                modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text(actionLabel, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun WeatherAttribution() {
    Text("Weather by Open-Meteo.com · Geocoding by Nominatim / OpenStreetMap · Air quality by Open-Meteo",
        color = CiyatoMuted, fontSize = 10.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}
