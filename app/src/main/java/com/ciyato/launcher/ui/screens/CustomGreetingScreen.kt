package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*
import androidx.compose.ui.draw.clip
import com.ciyato.launcher.viewmodel.LauncherViewModel
import com.ciyato.launcher.viewmodel.customGreeting
import com.ciyato.launcher.viewmodel.setCustomGreeting

/**
 * CustomGreetingScreen — Suggestion #100
 * Allows users to set a custom greeting that appears on the home screen.
 */

private val GREETING_SUGGESTIONS = listOf(
    "Good morning, {name}",
    "Hey {name}! 👋",
    "Welcome back, {name}",
    "Let's go, {name}!",
    "Rise & shine ☀️",
    "Hello, superstar ⭐",
    "You got this! 💪",
    "Make today great",
    "Stay focused 🎯",
    "Good vibes only ✨",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGreetingScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var customText by remember { mutableStateOf(viewModel.customGreeting ?: "") }
    var saved by remember { mutableStateOf(false) }

    fun save(text: String) {
        viewModel.setCustomGreeting(text.takeIf { it.isNotBlank() })
        saved = true
        focusManager.clearFocus()
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Custom Greeting", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { save(customText) }) {
                        Icon(Icons.Default.Check, "Save", tint = CiyatoGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Preview
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Preview", color = CiyatoMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            customText.ifBlank { viewModel.greeting },
                            color = CiyatoSec,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Text input
            item {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it; saved = false },
                    label = { Text("Your greeting", color = CiyatoMuted) },
                    placeholder = { Text("e.g. Hey {name}! 👋", color = CiyatoMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CiyatoWhite,
                        unfocusedTextColor = CiyatoWhite,
                        focusedBorderColor = CiyatoGold,
                        unfocusedBorderColor = CiyatoBorder,
                        cursorColor = CiyatoGold,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save(customText) }),
                    trailingIcon = if (saved) ({
                        Icon(Icons.Default.Check, null, tint = Color(0xFF39C66A))
                    }) else null,
                    supportingText = {
                        Text("Use {name} to insert your name automatically", color = CiyatoMuted, fontSize = 11.sp)
                    }
                )
            }

            // Suggestions
            item {
                Text("Quick picks", color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            items(GREETING_SUGGESTIONS) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (customText == suggestion) CiyatoGold.copy(alpha = 0.12f) else CiyatoBgEl
                        )
                        .border(
                            1.dp,
                            if (customText == suggestion) CiyatoGold else CiyatoBorder,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            customText = suggestion
                            save(suggestion)
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(suggestion, color = CiyatoWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (customText == suggestion) {
                        Icon(Icons.Default.Check, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Reset button
            if (customText.isNotBlank()) {
                item {
                    TextButton(
                        onClick = {
                            customText = ""
                            save("")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to default greeting", color = Color(0xFFEF4444))
                    }
                }
            }
        }
    }
}
