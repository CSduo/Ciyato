package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.InstalledApp
import com.ciyato.launcher.ui.theme.*

/**
 * PASS 1-2-3 — Duplicate Smart Shortcuts strip.
 *
 * The whole card is now clickable via onStripTap → opens DuplicateShortcutsScreen.
 * Individual app icons still launch the app via onAppTap.
 * Visual design preserved from reference.
 */
@Composable
fun DuplicateShortcutStrip(
    apps: List<InstalledApp>,
    onAppTap: (InstalledApp) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    onStripTap: () -> Unit = onManage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoGold.copy(alpha = 0.08f))
            .border(1.dp, CiyatoGold.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Duplicate smart shortcuts — tap to manage" }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // Header row: sparkle icon + titles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Gold sparkle badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CiyatoGold.copy(alpha = 0.18f))
                    .clickable(onClick = onStripTap),
            ) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = CiyatoGold,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(modifier = Modifier.weight(1f).clickable(onClick = onStripTap)) {
                Text(
                    "Duplicate smart shortcuts",
                    color = CiyatoWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "One app, multiple places. Always in context.",
                    color = CiyatoSec,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }

            // Close button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CiyatoBgEl2.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = CiyatoMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Icon strip with "+" badge on each app
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(apps.take(6)) { app ->
                Box(contentAlignment = Alignment.TopEnd) {
                    RealAppIcon(
                        drawable = app.icon,
                        size = 48.dp,
                        modifier = Modifier.clickable(onClick = { onAppTap(app) }),
                    )
                    // Small gold "+" badge top-right
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(16.dp)
                            .offset(x = 2.dp, y = (-2).dp)
                            .clip(CircleShape)
                            .background(CiyatoGold)
                            .border(1.5.dp, CiyatoBg, CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = CiyatoBg,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }

            // "+" add slot at the end
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CiyatoBgEl2)
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onStripTap),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Manage shortcuts",
                        tint = CiyatoSec, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // "Manage" link
        Text(
            "Manage shortcuts",
            color = CiyatoGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.End),
        )
    }
}
