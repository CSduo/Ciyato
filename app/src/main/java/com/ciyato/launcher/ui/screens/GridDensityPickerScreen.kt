package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * GridDensityPickerScreen — Suggestion #14
 * Lets users choose their home screen grid density with a live preview.
 */

data class GridDensityOption(
    val label: String,
    val columns: Int,
    val rows: Int,
    val isDense: Boolean,
    val emoji: String,
    val description: String,
)

private val DENSITY_OPTIONS = listOf(
    GridDensityOption("Spacious", 2, 3, false, "🌿", "2 columns — large icons, relaxed spacing"),
    GridDensityOption("Comfortable", 3, 4, false, "⚖️", "3 columns — balanced layout (default)"),
    GridDensityOption("Compact", 4, 5, true, "📱", "4 columns — more apps visible at once"),
    GridDensityOption("Dense", 5, 6, true, "⚡", "5 columns — maximum information density"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridDensityPickerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val denseLayout by viewModel.denseLayout.collectAsState()
    var selectedIdx by remember {
        mutableIntStateOf(if (denseLayout) 2 else 1)
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Grid Density", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
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
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live preview
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Preview", color = CiyatoMuted, fontSize = 13.sp)
                        val opt = DENSITY_OPTIONS[selectedIdx]
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(opt.columns),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(160.dp),
                            userScrollEnabled = false,
                        ) {
                            items(opt.columns * opt.rows) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(if (opt.isDense) 10.dp else 14.dp))
                                        .background(Color(0xFF1E2128)),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Choose Density", color = CiyatoWhite, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
            }

            items(DENSITY_OPTIONS.size) { i ->
                val opt = DENSITY_OPTIONS[i]
                val isSelected = selectedIdx == i

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) CiyatoGold.copy(alpha = 0.08f) else CiyatoBgEl
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isSelected) CiyatoGold else CiyatoBorder,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable {
                            selectedIdx = i
                            viewModel.setDenseLayout(opt.isDense)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(opt.emoji, fontSize = 28.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(opt.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("${opt.columns}×${opt.rows} grid", color = CiyatoGold, fontSize = 13.sp)
                            Text(opt.description, color = CiyatoMuted, fontSize = 12.sp)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
