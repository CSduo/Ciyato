package com.ciyato.launcher.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * AiCleanupScreen — Suggestion #26
 * AI-powered suggestions for app cleanup, storage, and unused apps.
 */

data class CleanupSuggestion(
    val id: String,
    val title: String,
    val description: String,
    val savingDesc: String,
    val icon: ImageVector,
    val category: CleanupCategory,
    val priority: Int,
    var dismissed: Boolean = false,
)

enum class CleanupCategory {
    UNUSED_APPS,
    STORAGE,
    DUPLICATES,
    PERMISSIONS,
    NOTIFICATIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCleanupScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val apps by viewModel.apps.collectAsState()
    var suggestions by remember { mutableStateOf<List<CleanupSuggestion>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(true) }

    LaunchedEffect(apps) {
        kotlinx.coroutines.delay(1_500L) // Simulated AI analysis
        suggestions = generateSuggestions(apps)
        isAnalyzing = false
    }

    val activeSuggestions = suggestions.filter { !it.dismissed }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("AI Cleanup", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        if (isAnalyzing) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                        0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rot"
                    )
                    Text("✨", fontSize = 48.sp)
                    Text("Analyzing your phone…", color = CiyatoWhite, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("AI is checking for unused apps, storage issues, and optimizations",
                        color = CiyatoMuted, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp))
                    CircularProgressIndicator(color = CiyatoGold, strokeWidth = 3.dp)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (activeSuggestions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("All clear!", color = CiyatoWhite, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your phone is well optimized", color = CiyatoMuted)
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 24.sp)
                        Column {
                            Text("${activeSuggestions.size} suggestions found",
                                color = CiyatoGold, fontWeight = FontWeight.SemiBold)
                            Text("Follow these to reclaim space and battery",
                                color = CiyatoMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            items(activeSuggestions, key = { it.id }) { suggestion ->
                CleanupSuggestionCard(
                    suggestion = suggestion,
                    onDismiss = {
                        suggestions = suggestions.map {
                            if (it.id == suggestion.id) it.copy(dismissed = true) else it
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CleanupSuggestionCard(
    suggestion: CleanupSuggestion,
    onDismiss: () -> Unit,
) {
    val catColor = when (suggestion.category) {
        CleanupCategory.UNUSED_APPS -> Color(0xFF7DB7FF)
        CleanupCategory.STORAGE -> CiyatoGold
        CleanupCategory.DUPLICATES -> Color(0xFFE1306C)
        CleanupCategory.PERMISSIONS -> Color(0xFFFF9500)
        CleanupCategory.NOTIFICATIONS -> Color(0xFF39C66A)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(catColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(suggestion.icon, null, tint = catColor, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(suggestion.title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(suggestion.description, color = CiyatoSec, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Dismiss", tint = CiyatoMuted, modifier = Modifier.size(16.dp))
                }
            }
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(suggestion.savingDesc, color = catColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = {
                        Toast.makeText(context, "Applying cleanup...", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Fix", color = CiyatoGold, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun generateSuggestions(apps: List<com.ciyato.launcher.data.InstalledApp>): List<CleanupSuggestion> {
    val suggestions = mutableListOf<CleanupSuggestion>()

    val largeBatch = apps.filter { it.packageName.contains("game", ignoreCase = true) }.take(3)
    if (largeBatch.isNotEmpty()) {
        suggestions.add(CleanupSuggestion(
            id = "games",
            title = "Large games detected",
            description = "${largeBatch.size} games are taking up significant storage. Consider removing ones you no longer play.",
            savingDesc = "Potentially free 1.2–4 GB",
            icon = Icons.Default.SportsEsports,
            category = CleanupCategory.STORAGE,
            priority = 1,
        ))
    }

    if (apps.size > 30) {
        suggestions.add(CleanupSuggestion(
            id = "unused_apps",
            title = "Unused apps found",
            description = "You have ${apps.size} apps installed. AI estimates ${apps.size / 4} haven't been used in over 30 days.",
            savingDesc = "Free up storage and RAM",
            icon = Icons.Default.AppBlocking,
            category = CleanupCategory.UNUSED_APPS,
            priority = 2,
        ))
    }

    suggestions.add(CleanupSuggestion(
        id = "notifications",
        title = "Notification overload",
        description = "Several apps send frequent notifications that you rarely interact with. Let AI silence them.",
        savingDesc = "Reduce interruptions",
        icon = Icons.Default.NotificationsOff,
        category = CleanupCategory.NOTIFICATIONS,
        priority = 3,
    ))

    suggestions.add(CleanupSuggestion(
        id = "cache",
        title = "App cache can be cleared",
        description = "App caches can safely be cleared to reclaim storage without losing data.",
        savingDesc = "Estimated 300 MB–1.5 GB",
        icon = Icons.Default.CleaningServices,
        category = CleanupCategory.STORAGE,
        priority = 4,
    ))

    return suggestions.sortedBy { it.priority }
}
