package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.CiyatoListCard
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onOpenWallpaper: () -> Unit,
) {
    val homeLayoutMode by viewModel.homeLayoutMode.collectAsState()
    val denseLayout = homeLayoutMode == "dense"
    val gridSize by viewModel.gridSize.collectAsState()
    val font by viewModel.font.collectAsState()
    val fontOption = if (font in setOf("sans", "serif", "mono")) font else "sans"
    val accent = CiyatoGold

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Theme Studio",
                subtitle = "Customize the launcher",
                onBack = onBack,
                subtitleColor = accent
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ThemePreviewCard(
                    denseLayout = denseLayout,
                    accent = accent,
                )
            }

            item { SectionTitle("Layout") }
            item {
                ThemeChoiceRow(
                    icon = Icons.Default.GridView,
                    title = "Home layout",
                    selected = homeLayoutMode,
                    options = listOf("spacious" to "Spacious", "smart" to "Standard", "dense" to "Compact"),
                    onSelect = viewModel::setHomeLayoutMode,
                    accent = accent,
                )
            }
            item {
                ThemeChoiceRow(
                    icon = Icons.Default.GridView,
                    title = "App grid",
                    selected = gridSize,
                    options = listOf("4x4" to "4×4", "5x4" to "5×4", "5x5" to "5×5", "6x5" to "6×5"),
                    onSelect = viewModel::setGridSize,
                    accent = accent,
                )
            }

            item { SectionTitle("Appearance") }
            item {
                ThemeChoiceRow(
                    icon = Icons.Default.TextFields,
                    title = "Typeface",
                    selected = fontOption,
                    options = listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono"),
                    onSelect = viewModel::setFont,
                    accent = accent,
                )
            }
            item {
                CiyatoListCard(
                    title = "Wallpaper Studio",
                    subtitle = "Choose a system wallpaper, private image, or short Ciyato-only video",
                    icon = Icons.Default.Wallpaper,
                    iconColor = accent,
                    onClick = onOpenWallpaper,
                )
            }

            item {
                OutlinedButton(
                    onClick = viewModel::resetLayout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CiyatoSubtleBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CiyatoGold)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset home layout")
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    denseLayout: Boolean,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Live launcher preview", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${if (denseLayout) "Compact" else "Spacious"} home preview",
                    color = CiyatoMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (denseLayout) 220.dp else 270.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(CiyatoBg)
                .border(1.dp, CiyatoBorder, RoundedCornerShape(26.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (denseLayout) 10.dp else 14.dp)) {
                Text("Good morning", color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = if (denseLayout) 14.sp else 18.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (denseLayout) 34.dp else 42.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(CiyatoBgEl2)
                )
                repeat(if (denseLayout) 3 else 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PreviewTile(accent = accent, modifier = Modifier.weight(1f))
                        PreviewTile(accent = accent, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(CiyatoBgEl2)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CiyatoBgEl)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(accent.copy(alpha = 0.18f))
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(54.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CiyatoWhite.copy(alpha = 0.65f)))
            Box(Modifier.width(36.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CiyatoMuted.copy(alpha = 0.55f)))
        }
    }
}

@Composable
private fun ThemeChoiceRow(
    icon: ImageVector,
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = CiyatoSec, modifier = Modifier.size(20.dp))
            Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                val isSelected = selected == value
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accent else CiyatoBgEl2)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        label,
                        color = if (isSelected) CiyatoBg else CiyatoSec,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        color = CiyatoGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun String.labelize(): String =
    split("_").joinToString(" ") { part ->
        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
