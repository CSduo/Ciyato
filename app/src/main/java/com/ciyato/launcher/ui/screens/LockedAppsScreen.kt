package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.AppIconView
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBgEl
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSubtleBorder
import com.ciyato.launcher.ui.theme.CiyatoWhite
import com.ciyato.launcher.viewmodel.LauncherViewModel

/**
 * The apps Ciyato asks to unlock, and where to stop asking.
 *
 * Locking happens per app from its long-press menu, which is the right place to
 * do it and the wrong place to review it: without this screen, someone who locks
 * an app and forgets which has no way to find out except long-pressing their way
 * through the drawer — and no way at all to reach an app they also hid.
 *
 * The scope note is repeated here rather than left in the enable dialog. It is
 * the one thing about this feature that is easy to misremember, and the cost of
 * misremembering it is believing an app is protected when it is not.
 */
@Composable
fun LockedAppsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val lockedCsv by viewModel.lockedApps.collectAsState()
    val allApps by viewModel.allApps.collectAsState()
    val lockedPackages = viewModel.parsePackageCsv(lockedCsv)
    // Resolved against the installed list so an uninstalled package does not
    // linger as an unexplained row, and sorted by name so the list is stable.
    val locked = allApps.filter { it.packageName in lockedPackages }.sortedBy { it.label.lowercase() }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "App Lock",
                subtitle = if (locked.isEmpty()) "No apps require unlocking" else "${locked.size} locked",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(CiyatoBgEl)
                        .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "What this covers",
                        color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                    Text(
                        "Ciyato asks for your fingerprint, face or screen lock before opening " +
                            "these apps from Home, the drawer, search, suggestions or anywhere " +
                            "else in Ciyato.",
                        color = CiyatoMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                    Text(
                        "It does not lock them everywhere. Recents, notifications, system search " +
                            "and other launchers do not pass through Ciyato and will not ask. No " +
                            "launcher can change that — for a real lock, use the app's own setting.",
                        color = CiyatoMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            }

            if (locked.isEmpty()) {
                item {
                    Text(
                        "Long-press any app and choose \"Require unlock\" to add it here.",
                        color = CiyatoMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(locked, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CiyatoBgEl)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconView(app = app, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = CiyatoMuted, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Unlock required in Ciyato", color = CiyatoMuted, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { viewModel.setAppLocked(app.packageName, false) }) {
                        Text("Remove", color = CiyatoGold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
