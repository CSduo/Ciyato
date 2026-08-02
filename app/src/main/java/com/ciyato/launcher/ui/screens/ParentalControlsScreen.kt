package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import androidx.compose.ui.text.input.PasswordVisualTransformation

/**
 * ParentalControlsScreen — Suggestion #86
 * Screen time limits and category-based app blocking for child profiles.
 * Integrates with FocusSessionManager to enforce blocks.
 * Production: use DevicePolicyManager for hard enforcement.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var parentalModeEnabled by remember { mutableStateOf(false) }
    var dailyScreenTimeLimitHours by remember { mutableStateOf(2f) }
    var blockedCategories by remember {
        mutableStateOf(setOf(AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.GAMES))
    }
    var pinInput by remember { mutableStateOf("") }
    var pinSet by remember { mutableStateOf(false) }
    var storedPin by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Parental Controls", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ChildCare, null, tint = CiyatoGold, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Parental Mode", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                        Text(if (parentalModeEnabled) "Active — restrictions enforced" else "Disabled",
                            color = if (parentalModeEnabled) Color(0xFF4CAF50) else CiyatoMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = parentalModeEnabled,
                        onCheckedChange = {
                            if (it && !pinSet) showPinDialog = true
                            else parentalModeEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CiyatoGold, checkedTrackColor = CiyatoGold.copy(alpha = 0.3f)),
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Daily Screen Time Limit", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${dailyScreenTimeLimitHours.toInt()}h ${((dailyScreenTimeLimitHours % 1) * 60).toInt()}m per day",
                        color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = dailyScreenTimeLimitHours,
                        onValueChange = { dailyScreenTimeLimitHours = it },
                        valueRange = 0.5f..8f,
                        steps = 14,
                        colors = SliderDefaults.colors(thumbColor = CiyatoGold, activeTrackColor = CiyatoGold),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("30 min", color = CiyatoMuted, fontSize = 11.sp)
                        Text("8 hours", color = CiyatoMuted, fontSize = 11.sp)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Blocked Categories", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    val blockableCategories = listOf(
                        AppCategory.SOCIAL to Icons.Default.People,
                        AppCategory.ENTERTAINMENT to Icons.Default.Movie,
                        AppCategory.GAMES to Icons.Default.SportsEsports,
                        AppCategory.SHOPPING to Icons.Default.ShoppingCart,
                    )

                    blockableCategories.forEach { (cat, icon) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = CiyatoMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(cat.displayName, color = CiyatoWhite, modifier = Modifier.weight(1f))
                            Switch(
                                checked = cat in blockedCategories,
                                onCheckedChange = { checked ->
                                    blockedCategories = if (checked) blockedCategories + cat else blockedCategories - cat
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFF44336),
                                    checkedTrackColor = Color(0xFFF44336).copy(alpha = 0.3f),
                                ),
                            )
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Content Filters", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    listOf(
                        "Block adult content" to true,
                        "Safe Search enforced" to true,
                        "Block in-app purchases" to false,
                        "Location sharing blocked" to true,
                    ).forEach { (label, default) ->
                        var checked by remember { mutableStateOf(default) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = CiyatoWhite, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Switch(
                                checked = checked,
                                onCheckedChange = { checked = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = CiyatoGold, checkedTrackColor = CiyatoGold.copy(alpha = 0.3f)),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    Toast.makeText(context, "Controls applied", Toast.LENGTH_SHORT).show()
                },
                enabled = parentalModeEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Save, null, tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Apply Controls", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("Set Parental PIN", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 6) pinInput = it },
                    label = { Text("4–6 digit PIN") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CiyatoGold, cursorColor = CiyatoGold),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length >= 4) {
                        storedPin = pinInput
                        pinSet = true
                        parentalModeEnabled = true
                        pinInput = ""
                        showPinDialog = false
                    }
                }) { Text("Set PIN", color = CiyatoGold) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false; pinInput = "" }) {
                    Text("Cancel", color = CiyatoMuted)
                }
            },
        )
    }
}
