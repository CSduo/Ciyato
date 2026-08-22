package com.ciyato.launcher.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlin.math.sin

/**
 * A paced breathing exercise. Nothing more, deliberately.
 *
 * This was "Stress-Free Mode", which displayed a CALM/MILD/STRESSED verdict
 * inferred from the clock and a recent-app count. That inference is gone: it
 * measured nothing, and an app asserting an emotional state it cannot observe is
 * worse than one staying quiet — particularly for anyone inclined to believe it.
 * What remains is the part that was always honest
 * (high notification count, rapid app switching, late-night usage) and activates
 * a calmer, distraction-reduced layout with breathing exercises.
 */

@Composable
fun StressFreeModeScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    var breathingActive by remember { mutableStateOf(false) }
    val breathPhase = remember { Animatable(0f) }

    LaunchedEffect(breathingActive) {
        if (breathingActive) {
            breathPhase.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                )
            )
        } else {
            breathPhase.snapTo(0f)
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(title = "Breathing", subtitle = "A paced exercise, on request", onBack = onBack)
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
            // The "Stress Indicator" that stood here has been removed, not
            // softened (F-131). It reported CALM / MILD / STRESSED from two
            // signals: the hour of day (23:00–06:00 was rendered as STRESSED in
            // red) and whether more than eight apps had been opened recently.
            // Neither measures stress. Being awake late means you are awake
            // late, and a phone cannot tell the difference between a person
            // under pressure and one browsing recipes. Showing a red emotional
            // verdict on that basis is a wellbeing claim from nothing, and the
            // people most likely to believe it are the ones it could affect.
            //
            // The breathing exercise below is real, so it stays — as something
            // offered, not as treatment for a diagnosis the app invented.
            Card(
                colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Take a moment", color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "A paced breathing exercise, whenever you want one. Ciyato doesn't " +
                            "monitor how you're feeling and can't tell — this is here if it's useful.",
                        color = CiyatoMuted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Breathing Exercise", color = CiyatoGold, fontWeight = FontWeight.SemiBold)
                    val radius = 60f + breathPhase.value * 40f
                    Canvas(Modifier.size(160.dp)) {
                        drawCircle(
                            color = CiyatoGold.copy(alpha = 0.2f + breathPhase.value * 0.3f),
                            radius = (center.x * 0.6f) + breathPhase.value * center.x * 0.3f,
                            style = Fill,
                        )
                        drawCircle(color = CiyatoGold.copy(alpha = 0.8f), radius = 20f)
                    }
                    Text(
                        if (breathingActive)
                            if (breathPhase.value < 0.5f) "Inhale…" else "Exhale…"
                        else "Tap to begin",
                        color = CiyatoWhite,
                        fontWeight = FontWeight.Medium,
                    )
                    Button(
                        onClick = { breathingActive = !breathingActive },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (breathingActive) CiyatoGold else CiyatoBg,
                        ),
                    ) {
                        Text(
                            if (breathingActive) "Stop" else "Start Breathing",
                            color = if (breathingActive) Color.Black else CiyatoGold,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick Calm Actions", color = CiyatoGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    val context = LocalContext.current
                    listOf(
                        Icons.Default.DoNotDisturb to "Enable Do Not Disturb",
                        Icons.Default.NightlightRound to "Activate Bedtime Mode",
                        Icons.Default.TimerOff to "Pause Focus Timer",
                        Icons.Default.WifiOff to "Go Offline for 30 min",
                    ).forEach { (icon, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (label) {
                                        "Enable Do Not Disturb" -> {
                                            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                                            if (nm?.isNotificationPolicyAccessGranted == false) {
                                                // Can't flip DND without this permission — send the user to grant it
                                                // instead of claiming success we didn't achieve.
                                                Toast.makeText(context, "Grant Do Not Disturb access to continue", Toast.LENGTH_SHORT).show()
                                                context.startActivity(
                                                    android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            } else {
                                                nm?.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                                                Toast.makeText(context, "Do Not Disturb enabled", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        "Activate Bedtime Mode" -> {
                                            viewModel.setBedtimeMode(true)
                                            Toast.makeText(context, "Bedtime Mode activated", Toast.LENGTH_SHORT).show()
                                        }
                                        "Pause Focus Timer" -> {
                                            viewModel.endFocusSession()
                                            Toast.makeText(context, "Focus session ended", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {
                                            // Apps can't disable Wi-Fi/mobile data directly on modern Android —
                                            // hand off to the real system settings instead of faking it.
                                            Toast.makeText(context, "Flip Airplane Mode to go offline", Toast.LENGTH_SHORT).show()
                                            context.startActivity(
                                                android.content.Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(icon, null, tint = CiyatoMuted, modifier = Modifier.size(18.dp))
                            Text(label, color = CiyatoWhite, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
