package com.ciyato.launcher.ui.screens

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import kotlinx.coroutines.withContext

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

    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) { DuplicatePhotoDetector.findDuplicates(context) }
        isLoading = false
    }

    fun deleteKeepingBest(group: DuplicatePhotoDetector.DuplicateGroup) {
        val toDelete = group.photos.drop(1) // keep first (largest or most recent)
        toDelete.forEach { photo ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.contentResolver.delete(photo.uri, null, null)
                } else {
                    context.contentResolver.delete(photo.uri, null, null)
                }
                savedBytes += photo.sizeBytes
                deletedCount++
            } catch (_: Exception) {}
        }
        groups = groups.filter { it != group }
    }

    Scaffold(
        containerColor = CiyatoBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Cleanup", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CiyatoWhite)
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
                                    onClick = { deleteKeepingBest(group) },
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
