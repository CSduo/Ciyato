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
import androidx.compose.runtime.DisposableEffect

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
    /**
     * Opens Photos. Required, not defaulted: a no-op default is how "show
     * photos" came to be advertised and do nothing (F-140).
     */
    onOpenPhotos: () -> Unit,
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

    // Decorative "listening" pulse. The state is also carried by the button's
    // label and colour, so suppressing the motion loses nothing (F-167).
    val pulseScale = remember { Animatable(1f) }
    val reduceMotion = com.ciyato.launcher.ui.theme.LocalReduceMotion.current
    LaunchedEffect(isListening, reduceMotion) {
        if (isListening && !reduceMotion) {
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
                // Matched against the INSTALLED apps, not searchResults.
                //
                // searchResults holds whatever the person last typed into the
                // search screen. So "open Gmail" worked only if Gmail happened
                // to be sitting in a stale result set, and otherwise fell
                // through to "Searching for 'gmail'…" — which looks like the
                // voice command is unsupported rather than mis-wired (F-141).
                //
                // Exact label first, then prefix, then substring: "open maps"
                // should reach Maps rather than whichever installed app merely
                // contains "maps" somewhere in its name.
                val installed = viewModel.apps.value
                val app = installed.firstOrNull { it.label.lowercase(Locale.getDefault()) == appName }
                    ?: installed.firstOrNull { it.label.lowercase(Locale.getDefault()).startsWith(appName) }
                    ?: installed.firstOrNull { it.label.lowercase(Locale.getDefault()).contains(appName) }
                if (app != null) { viewModel.launchApp(app); "Launching ${app.label}…" }
                else { onOpenSearch(appName); "No installed app matches '$appName' — searching…" }
            }
            // Advertised in the help text and absent from this handler, so
            // saying it produced "Command not recognized" while the screen
            // listed it as an example (F-140). Implemented rather than removed:
            // Photos is a real destination and this is the obvious phrasing.
            lower == "show photos" || lower == "open photos" ||
                lower.startsWith("show photos") || lower.startsWith("open my photos") -> {
                onOpenPhotos(); "Opening Photos…"
            }
            lower.contains("focus mode") || lower.contains("focus session") -> {
                viewModel.startFocusSession(); "Focus session started!"
            }
            // Ciyato renders one appearance. These used to persist a
            // light/dark preference and announce success, but both Compose roots
            // render dark unconditionally and nothing reads the preference — so
            // "Light mode enabled." was simply false, and the stored value made
            // the app look broken rather than opinionated (F-142, F-177).
            lower.contains("dark mode on") -> "Ciyato is always dark — you're already in it."
            lower.contains("dark mode off") || lower.contains("light mode") ->
                "Ciyato doesn't have a light mode. Its dark look is fixed by design."
            lower.contains("search ") -> {
                val q = lower.substringAfter("search ").trim()
                onOpenSearch(q); "Searching for '$q'…"
            }
            else -> "Command not recognized: \"$text\". Try 'open Gmail' or 'open my work apps'."
        }
    }

    /**
     * The recogniser currently listening, if any.
     *
     * It was destroyed only from onResults and onError. Leave the screen while
     * it is listening and neither fires: the recogniser leaked and the
     * microphone stayed open on a screen the person had already left, with the
     * only indication being the system mic indicator (F-143). There was also no
     * way to stop it deliberately.
     */
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListening() {
        activeRecognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        activeRecognizer = null
        isListening = false
    }

    // Releasing the microphone is not optional cleanup, so it is tied to the
    // composable's lifetime rather than to a callback that may never arrive.
    DisposableEffect(Unit) { onDispose { stopListening() } }

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
        activeRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = texts?.firstOrNull() ?: ""
                transcript = best
                if (best.isNotBlank()) handleCommand(best)
                isListening = false
                activeRecognizer = null
                recognizer.destroy()
            }
            override fun onError(error: Int) {
                errorText = "Could not hear you (error $error). Try again."
                isListening = false
                activeRecognizer = null
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
