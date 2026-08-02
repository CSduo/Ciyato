package com.ciyato.launcher.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.ui.components.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIOptimizerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit
) {
    val aiManager = viewModel.aiOptimizer
    val agents by aiManager.agents.collectAsState()
    val isOptimizing by aiManager.isOptimizing.collectAsState()
    val logs by aiManager.optimizationLog.collectAsState()
    val freedBytes by aiManager.freedBytes.collectAsState()

    val storageInfo = remember(isOptimizing) { aiManager.getStorageInfo() }
    val usedStorage = storageInfo.first
    val totalStorage = storageInfo.second
    val storagePercentage = (usedStorage.toFloat() / totalStorage.toFloat() * 100).toInt()

    // Breathing pulse for optimization scanner
    val scalePulse by rememberPulse(1f, 1.08f)

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "AI Agent Optimizer",
                subtitle = "Local autonomous system management",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // ── 1. Storage & Memory Status Cards ──────────────────────────────────
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Storage Usage Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(CiyatoBgEl)
                            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Storage, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Internal Storage", color = CiyatoSec, fontSize = 12.sp)
                        Text("$storagePercentage% Used", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${formatSize(usedStorage)} / ${formatSize(totalStorage)}",
                            color = CiyatoMuted,
                            fontSize = 11.sp
                        )
                    }

                    // Memory (RAM) Health Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(CiyatoBgEl)
                            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Memory, null, tint = CiyatoBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("System Heap RAM", color = CiyatoSec, fontSize = 12.sp)
                        Text(
                            text = if (isOptimizing) "Cleaning…" else "58% Load",
                            color = CiyatoWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Thermal Index: Normal", color = CiyatoMuted, fontSize = 11.sp)
                    }
                }
            }

            // ── 2. Optimization Scanner Action ────────────────────────────────────
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(CiyatoBgEl, CiyatoBgEl2)))
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(24.dp))
                        .padding(vertical = 32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Scanner button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .scale(if (isOptimizing) scalePulse else 1f)
                                .size(86.dp)
                                .clip(CircleShape)
                                .background(CiyatoGold.copy(alpha = if (isOptimizing) 0.22f else 0.12f))
                                .border(2.dp, CiyatoGold, CircleShape)
                                .clickable(enabled = !isOptimizing) { viewModel.optimizeSystem() }
                        ) {
                            Icon(
                                imageVector = if (isOptimizing) Icons.Default.HourglassEmpty else Icons.Default.AutoMode,
                                contentDescription = "Optimize Now",
                                tint = CiyatoGold,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isOptimizing) "Agents executing optimizations…" else "System Diagnostics",
                                color = CiyatoWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (freedBytes > 0) "Last clean released ${formatSize(freedBytes)}" else "Tap the scanner to trigger local optimization agents.",
                                color = CiyatoMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── 3. Active AI Agents ───────────────────────────────────────────────
            item {
                Text(
                    "Active Optimization Agents",
                    color = CiyatoWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(agents, key = { it.name }) { agent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CiyatoBgEl)
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CiyatoGold.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = when (agent.name) {
                                "App Organizer Agent" -> Icons.Default.GridView
                                "File Optimizer Agent" -> Icons.Default.FolderOpen
                                else -> Icons.Default.Memory
                            },
                            contentDescription = null,
                            tint = CiyatoGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(agent.name, color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CiyatoBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(agent.status, color = CiyatoBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(agent.description, color = CiyatoSec, fontSize = 12.sp)
                        Text("Thought: ${agent.lastAction}", color = CiyatoMuted, fontSize = 11.sp)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CiyatoBgEl2.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lightbulb, null, tint = CiyatoGold, modifier = Modifier.size(14.dp))
                                Text(agent.suggestion, color = CiyatoSec, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ── 4. Live Agent Execution Logs ──────────────────────────────────────
            if (logs.isNotEmpty() || isOptimizing) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CiyatoBgEl2.copy(alpha = 0.35f))
                            .border(1.dp, CiyatoBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Agent Execution Log", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        logs.forEach { log ->
                            Text(log, color = CiyatoMuted, fontSize = 11.sp)
                        }
                        if (isOptimizing) {
                            CircularProgressIndicator(
                                color = CiyatoGold,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp).align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes Bytes"
    }
}
