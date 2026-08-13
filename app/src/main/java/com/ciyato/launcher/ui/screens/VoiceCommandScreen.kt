package com.ciyato.launcher.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.AppCategory
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import java.util.Locale

/**
 * VoiceCommandScreen — Suggestion #39
 * Voice command integration using Android's SpeechRecognizer.
 * Recognized intents: "open [app]", "open my [category] apps", "search [query]",
 * "focus mode", "show photos", "dark mode on/off".
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCommandScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onOpenCategory: (AppCategory) -> Unit = {},
    onOpenSearch: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        errorText = if (granted) "" else "Microphone permission denied. Voice commands need audio access to hear you."
    }

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(isListening) {
        if (isListening) {
            pulseScale.animateTo(1.3f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
        } else {
            pulseScale.snapTo(1f)
        }
    }

    fun handleCommand(text: String) {
        val lower = text.lowercase(Locale.getDefault())
        resultText = when {
            lower.startsWith("open my") || lower.startsWith("show my") -> {
                val cat = AppCategory.entries.firstOrNull { lower.contains(it.displayName.lowercase()) }
                if (cat != null) { onOpenCategory(cat); "Opening ${cat.displayName} apps…" }
                else "Category not recognized. Try 'open my work apps'."
            }
            lower.startsWith("open ") -> {
                val appName = lower.removePrefix("open ").trim()
                val app = viewModel.searchResults.value.firstOrNull { it.label.lowercase().contains(appName) }
                if (app != null) { viewModel.launchApp(app); "Launching ${app.label}…" }
                else { onOpenSearch(appName); "Searching for '$appName'…" }
            }
            lower.contains("focus mode") || lower.contains("focus session") -> {
                viewModel.startFocusSession(); "Focus session started!"
            }
            lower.contains("dark mode on")  -> { viewModel.setDarkMode("dark");  "Dark mode enabled." }
            lower.contains("dark mode off") -> { viewModel.setDarkMode("light"); "Light mode enabled." }
            lower.contains("search ") -> {
                val q = lower.substringAfter("search ").trim()
                onOpenSearch(q); "Searching for '$q'…"
            }
            else -> "Command not recognized: \"$text\". Try 'open Gmail' or 'open my work apps'."
        }
    }

    fun startListening() {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            errorText = "Speech recognition not available on this device."
            return
        }
        transcript = ""
        resultText = ""
        errorText = ""
        isListening = true

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = texts?.firstOrNull() ?: ""
                transcript = best
                if (best.isNotBlank()) handleCommand(best)
                isListening = false
                recognizer.destroy()
            }
            override fun onError(error: Int) {
                errorText = "Could not hear you (error $error). Try again."
                isListening = false
                recognizer.destroy()
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.startListening(intent)
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Voice Commands",
                subtitle = "Speak, Ciyato listens",
                onBack = onBack,
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(140.dp)
                        .scale(pulseScale.value)
                        .background(
                            if (isListening) CiyatoGold.copy(alpha = 0.2f) else CiyatoBgEl,
                            CircleShape,
                        )
                )
                IconButton(
                    onClick = { if (!isListening) startListening() },
                    modifier = Modifier
                        .size(80.dp)
                        .background(if (isListening) CiyatoGold else CiyatoBgEl, CircleShape),
                ) {
                    Icon(
                        if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        null,
                        tint = if (isListening) Color.Black else CiyatoGold,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Text(
                if (isListening) "Listening…" else "Tap to speak",
                color = if (isListening) CiyatoGold else CiyatoMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            if (transcript.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("You said:", color = CiyatoMuted, fontSize = 11.sp)
                        Text("\"$transcript\"", color = CiyatoWhite, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (resultText.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                    shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = CiyatoGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(resultText, color = CiyatoWhite, fontSize = 13.sp)
                    }
                }
            }

            if (errorText.isNotBlank()) {
                Text(errorText, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Try: \"open Gmail\", \"open my work apps\",\n\"focus mode\", \"dark mode on\"",
                color = CiyatoMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
