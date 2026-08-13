package com.ciyato.launcher.ui.screens

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ciyato.launcher.data.DuplicatePhotoDetector
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * DuplicatePhotoCleanupScreen — Suggestion #64
 * Finds duplicate photos using perceptual hashing and presents a
 * one-tap cleanup UI to delete duplicates (keeping the best quality copy).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatePhotoCleanupScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<DuplicatePhotoDetector.DuplicateGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var deletedCount by remember { mutableStateOf(0) }
    var savedBytes by remember { mutableStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Android 10+ requires explicit user consent (via a system dialog) before an app can
    // delete MediaStore entries it did not create itself. Without this, contentResolver.delete()
    // throws (or is silently swallowed) and nothing is actually removed from the device.
    var pendingConsentResume by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pendingConsentResume?.invoke(result.resultCode == Activity.RESULT_OK)
        pendingConsentResume = null
    }

    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) { DuplicatePhotoDetector.findDuplicates(context) }
        isLoading = false
    }

    suspend fun deleteKeepingBest(group: DuplicatePhotoDetector.DuplicateGroup) {
        val toDelete = group.photos.drop(1) // keep first (largest or most recent)
        val requestConsent: suspend (IntentSender) -> Boolean = { intentSender ->
            suspendCancellableCoroutine { cont ->
                pendingConsentResume = { granted -> cont.resume(granted) }
                deleteConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        }
        val actuallyDeleted = withContext(Dispatchers.IO) {
            when {
                toDelete.isEmpty() -> emptyList()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, toDelete.map { it.uri })
                    val granted = withContext(Dispatchers.Main) { requestConsent(pendingIntent.intentSender) }
                    if (granted) toDelete else emptyList()
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    toDelete.filter { photo ->
                        try {
                            context.contentResolver.delete(photo.uri, null, null) > 0
                        } catch (security: RecoverableSecurityException) {
                            val granted = withContext(Dispatchers.Main) {
                                requestConsent(security.userAction.actionIntent.intentSender)
                            }
                            granted && runCatching { context.contentResolver.delete(photo.uri, null, null) > 0 }.getOrDefault(false)
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
                else -> {
                    toDelete.filter { photo ->
                        try {
                            context.contentResolver.delete(photo.uri, null, null) > 0
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            }
        }
        deletedCount += actuallyDeleted.size
        savedBytes += actuallyDeleted.sumOf { it.sizeBytes }
        // Only drop the group once every duplicate in it was actually removed — a denied or
        // partial deletion must stay visible so the user can retry, instead of the group
        // silently vanishing while the "duplicate" files are still sitting on the device.
        if (actuallyDeleted.size == toDelete.size) {
            groups = groups.filter { it != group }
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Cleanup", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CiyatoGold)
                    Spacer(Modifier.height(8.dp))
                    Text("Scanning for duplicates…", color = CiyatoMuted)
                    Text("This may take a moment", color = CiyatoMuted, fontSize = 12.sp)
                }
            }
            groups.isEmpty() && deletedCount == 0 -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No duplicates found!", color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (deletedCount > 0) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                            shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                                Spacer(Modifier.width(8.dp))
                                Text("Deleted $deletedCount duplicates · Saved ${savedBytes / 1024 / 1024}MB",
                                    color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                item {
                    Text("${groups.size} duplicate group${if (groups.size != 1) "s" else ""} found",
                        color = CiyatoMuted, fontSize = 12.sp)
                }
                items(groups, key = { it.hashCode() }) { group ->
                    Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                        shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${group.photos.size} similar photos", color = CiyatoGold,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.photos.take(3).forEach { photo ->
                                    AsyncImage(
                                        model = photo.uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { groups = groups.filter { it != group } },
                                    border = ButtonDefaults.outlinedButtonBorder,
                                ) { Text("Skip", color = CiyatoMuted, fontSize = 12.sp) }
                                Button(
                                    onClick = { scope.launch { deleteKeepingBest(group) } },
                                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Keep Best, Delete ${group.photos.size - 1}",
                                        color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
