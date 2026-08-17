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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ciyato.launcher.data.PhotoDeviceLibrary
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Review screen for photos that LOOK alike.
 *
 * Deliberately not called a duplicate cleaner. Grouping comes from an average
 * hash over the newest [DuplicatePhotoDetector.SCAN_LIMIT] photos, which means
 * "similar", not "byte-identical" — so this is a review step the person drives,
 * not an automatic cleaner (F-103). Three rules follow from that and are load
 * bearing:
 *
 *  - every photo in a group is shown, because the action removes all but one and
 *    the card previously displayed only the first three (F-100);
 *  - the survivor is the person's choice, defaulted to the largest file and
 *    labelled as such rather than as "Best" (F-101);
 *  - coverage is stated wherever a count appears, since a bounded scan cannot
 *    say anything about the photos it never read (F-102).
 */
private fun formatBytesShort(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L * 1024L -> "${bytes / 1024L}KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)}MB"
    else -> String.format(java.util.Locale.US, "%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0))
}

@Composable
fun DuplicatePhotoCleanupScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<DuplicatePhotoDetector.DuplicateGroup>>(emptyList()) }
    // Coverage is kept so every claim on this screen can be qualified by what
    // was actually examined, rather than implying the whole library.
    var scan by remember { mutableStateOf<DuplicatePhotoDetector.DuplicateScan?>(null) }
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
        val result = withContext(Dispatchers.IO) { DuplicatePhotoDetector.findDuplicates(context) }
        scan = result
        groups = result.groups
        isLoading = false
    }

    /**
     * Trashes every photo in [group] except [keep].
     *
     * [keep] is passed in rather than assumed, because the previous version
     * deleted `group.photos.drop(1)` while the card rendered only
     * `group.photos.take(3)`. A group of eight showed three thumbnails and
     * removed seven photos — five of which the person never saw. The execution
     * target is now exactly the set displayed, minus the one they chose to keep.
     */
    suspend fun trashAllExcept(
        group: DuplicatePhotoDetector.DuplicateGroup,
        keep: DuplicatePhotoDetector.PhotoEntry,
    ) {
        val toDelete = group.photos.filter { it.id != keep.id }
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
                    // Trash rather than destroy. A perceptual-hash match is a
                    // very good guess, not a certainty — two genuinely different
                    // photos can land inside the Hamming threshold — so the one
                    // thing this has to support is taking a wrong guess back.
                    val sender = PhotoDeviceLibrary.trashRequest(context, toDelete.map { it.uri })
                    val granted = sender != null &&
                        withContext(Dispatchers.Main) { requestConsent(sender) }
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
            CiyatoTopBar(title = "Duplicate Cleanup", onBack = onBack)
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
                    Icon(Icons.Default.CheckCircle, null, tint = CiyatoGreen, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No look-alikes in the photos checked",
                        color = CiyatoWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Was "No duplicates found!" — a definitive claim about the
                    // whole library from a scan that only hashed the newest 500
                    // (F-102). The coverage is now stated instead of implied.
                    Text(
                        scan?.let { s ->
                            if (s.wasBounded) {
                                "Checked the ${s.scanned} most recent of ${s.libraryTotal} photos. " +
                                    "Older photos were not examined."
                            } else {
                                "Checked all ${s.scanned} photos on this device."
                            }
                        } ?: "",
                        color = CiyatoMuted, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
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
                                Icon(Icons.Default.CheckCircle, null, tint = CiyatoGreen)
                                Spacer(Modifier.width(8.dp))
                                // Deliberately not "Saved NN MB": on Android 11+
                                // these went to the trash, so the space isn't
                                // actually back until it's emptied. Claiming a
                                // saving that hasn't happened yet is the kind of
                                // lie that makes a cleanup tool untrustworthy.
                                Text(
                                    if (PhotoDeviceLibrary.trashSupported) {
                                        "$deletedCount moved to trash · ${savedBytes / 1024 / 1024}MB " +
                                            "frees up when you empty it"
                                    } else {
                                        "Deleted $deletedCount duplicates · Saved ${savedBytes / 1024 / 1024}MB"
                                    },
                                    color = CiyatoGreen, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                item {
                    Column {
                        Text(
                            "${groups.size} look-alike group${if (groups.size != 1) "s" else ""}",
                            color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        // These are matched by an average hash, not by comparing
                        // file contents — so they are similar-looking, not proven
                        // identical, and this screen is a review step rather than
                        // an automatic cleaner (F-103).
                        Text(
                            scan?.let { s ->
                                val coverage = if (s.wasBounded) {
                                    "newest ${s.scanned} of ${s.libraryTotal} photos"
                                } else {
                                    "all ${s.scanned} photos"
                                }
                                "Matched by appearance across the $coverage. Similar is not " +
                                    "identical — check each group before trashing."
                            } ?: "",
                            color = CiyatoMuted, fontSize = 11.sp, lineHeight = 15.sp,
                        )
                    }
                }
                items(groups, key = { g -> g.photos.joinToString(",") { it.id.toString() } }) { group ->
                    // Which photo survives is the person's choice, defaulted to
                    // the largest file. It used to be implicit and unchangeable,
                    // and the button called it "Best" — but the only criterion
                    // available here is byte size, which is not the same thing as
                    // the better photo (F-101). The label now names the real
                    // criterion and the choice is editable.
                    val groupKey = group.photos.joinToString(",") { it.id.toString() }
                    var keepId by remember(groupKey) {
                        mutableStateOf(group.photos.maxByOrNull { it.sizeBytes }?.id ?: group.photos.first().id)
                    }
                    val keep = group.photos.firstOrNull { it.id == keepId } ?: group.photos.first()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "${group.photos.size} photos look alike",
                                color = CiyatoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Tap the one to keep. The rest move to the trash.",
                                color = CiyatoMuted, fontSize = 11.sp,
                            )

                            // EVERY photo in the group is shown, in rows of four.
                            // The old card rendered take(3) while deleting
                            // size - 1, so anything past the third was destroyed
                            // unseen (F-100). What is displayed is now exactly
                            // what the action operates on.
                            group.photos.chunked(4).forEach { rowPhotos ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowPhotos.forEach { photo ->
                                        val isKeeper = photo.id == keepId
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(70.dp),
                                        ) {
                                            Box {
                                                AsyncImage(
                                                    model = photo.uri,
                                                    contentDescription = photo.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(70.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { keepId = photo.id },
                                                )
                                                if (isKeeper) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Keeping this one",
                                                        tint = CiyatoGreen,
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp),
                                                    )
                                                }
                                            }
                                            Text(
                                                if (isKeeper) "Keep" else formatBytesShort(photo.sizeBytes),
                                                color = if (isKeeper) CiyatoGreen else CiyatoMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { groups = groups.filter { it != group } },
                                    border = ButtonDefaults.outlinedButtonBorder,
                                ) { Text("Skip", color = CiyatoMuted, fontSize = 12.sp) }
                                Button(
                                    onClick = { scope.launch { trashAllExcept(group, keep) } },
                                    colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Trash ${group.photos.size - 1}",
                                        color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
