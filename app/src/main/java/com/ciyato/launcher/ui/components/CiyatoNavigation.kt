package com.ciyato.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * Ciyato Standard Top App Bar.
 * Used by all full screens (Files, Settings, Theme, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiyatoTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    subtitleColor: Color = CiyatoGold,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(title, style = headingM, color = CiyatoWhite)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = labelL, color = subtitleColor)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CiyatoSec,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg)
    )
}

/**
 * Ciyato Bottom Navigation Bar.
 * Supports 2–5 tabs with icon + label.
 * Active tab shows gold-filled circle behind icon.
 */
data class CiyatoNavItem(
    val icon: ImageVector,
    val label: String,
    val badgeCount: Int = 0,
)

@Composable
fun CiyatoBottomNavBar(
    items: List<CiyatoNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Edge-to-edge activities draw behind the system bar; keep the pill
            // clear of gesture/3-button navigation and off the screen edges.
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            .clip(CiyatoShapes.full)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.full)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { idx, item ->
            CiyatoNavButton(
                item = item,
                selected = idx == selectedIndex,
                onClick = { onItemSelected(idx) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CiyatoNavButton(
    item: CiyatoNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(200),
        label = "nav_scale"
    )
    val iconTint by animateColorAsState(
        if (selected) CiyatoBg else CiyatoSec,
        label = "nav_tint"
    )
    val bgColor by animateColorAsState(
        if (selected) CiyatoGold else Color.Transparent,
        label = "nav_bg"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CiyatoShapes.full)
                .background(bgColor)
        ) {
            if (item.badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = CiyatoRed) {
                            Text(if (item.badgeCount > 99) "99+" else "${item.badgeCount}", style = labelXS)
                        }
                    }
                ) {
                    Icon(item.icon, item.label, tint = iconTint, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(item.icon, item.label, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = item.label,
            style = labelXS,
            color = if (selected) CiyatoGold else CiyatoMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Ciyato Tab Row — horizontal scrollable tab bar.
 * Used in FilesScreen (Organizer | Collections | Timeline) and similar.
 */
@Composable
fun CiyatoTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CiyatoGold,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp8)
    ) {
        tabs.forEachIndexed { idx, tab ->
            val selected = idx == selectedIndex
            Text(
                text = tab,
                style = chipLabel,
                color = if (selected) CiyatoBg else CiyatoSec,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(CiyatoShapes.full)
                    .background(if (selected) accentColor else CiyatoBgEl)
                    .border(1.dp, if (selected) accentColor else CiyatoSubtleBorder, CiyatoShapes.full)
                    .clickable { onTabSelected(idx) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

/**
 * Ciyato Breadcrumb — path indicator for nested navigation.
 */
@Composable
fun CiyatoBreadcrumb(
    path: List<String>,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiyatoSpacing.sp4)
    ) {
        path.forEachIndexed { idx, segment ->
            Text(
                text = segment,
                style = labelL,
                color = if (idx == path.lastIndex) CiyatoGold else CiyatoMuted,
                fontWeight = if (idx == path.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { onSegmentClick(idx) }
            )
            if (idx < path.lastIndex) {
                Icon(Icons.Default.ChevronRight, null, tint = CiyatoMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Ciyato Step Indicator — onboarding progress dots.
 */
@Composable
fun CiyatoStepIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = CiyatoGold,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { idx ->
            val active = idx == currentStep
            val width by animateFloatAsState(if (active) 24f else 8f, tween(250), label = "dot_w")
            val dotColor by animateColorAsState(
                if (active) activeColor else CiyatoMuted.copy(alpha = 0.4f),
                label = "dot_color"
            )
            Box(
                modifier = Modifier
                    .width(width.dp)
                    .height(8.dp)
                    .clip(CiyatoShapes.full)
                    .background(dotColor)
            )
        }
    }
}

/**
 * Ciyato Floating Action Button — gold circle FAB with icon.
 */
@Composable
fun CiyatoFAB(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = CiyatoGold,
) {
    val pulse by rememberPulse(1f, 1.04f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(pulse)
            .size(56.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription, tint = CiyatoBg, modifier = Modifier.size(24.dp))
    }
}

