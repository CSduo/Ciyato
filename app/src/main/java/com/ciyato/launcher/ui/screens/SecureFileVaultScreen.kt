package com.ciyato.launcher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ciyato.launcher.data.FileAccess
import com.ciyato.launcher.data.VaultCrypto
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.DisposableEffect

/**
 * SecureFileVaultScreen — Suggestion #68
 * Biometric-gated file vault using AES-256-GCM (Android Keystore) encryption.
 * Files are encrypted on import, and can be decrypted and opened again — the
 * screen previously offered no way out at all, which made "vault" the wrong word
 * for it (F-146).
 * Vault directory lives in app's internal private storage.
 */

@Composable
fun SecureFileVaultScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isUnlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var vaultError by remember { mutableStateOf<String?>(null) }
    var vaultFiles by remember { mutableStateOf<List<String>>(emptyList()) }

    val vaultDir = remember {
        File(context.filesDir, "secure_vault").also { it.mkdirs() }
    }

    // Deleting a vault file destroys the only decryptable copy, so it asks first
    // (F-147). Nothing else in Ciyato deletes user data without confirmation.
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var vaultMessage by remember { mutableStateOf<String?>(null) }

    /**
     * Decrypts a vault file and hands it to another app.
     *
     * The vault had no way to get a file back OUT (F-146): the only per-file
     * action was Delete, while the docs claimed files are "decrypted on open".
     * Encrypting something you can never read again is a shredder, not a vault.
     *
     * Plaintext is written to Ciyato's private cache — not to shared storage —
     * and handed over as a temporary content grant through the same FileProvider
     * path everything else uses, so no raw file:// URI escapes. That cache copy
     * is a real trade-off and is swept on every unlock rather than left lying
     * around indefinitely.
     */
    fun exportAndOpen(name: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val encrypted = File(vaultDir, name).readBytes()
                    val plain = VaultCrypto.decrypt(encrypted)
                    val outDir = File(context.cacheDir, "vault_open").also { it.mkdirs() }
                    val out = File(outDir, name.removeSuffix(".enc"))
                    out.writeBytes(plain)
                    out
                }
            }
            result.fold(
                onSuccess = { file ->
                    val reason = FileAccess.openExternally(context, Uri.fromFile(file))
                    if (reason != null) vaultMessage = reason
                },
                onFailure = { vaultMessage = "That file could not be decrypted." },
            )
        }
    }

    suspend fun refreshVaultFiles() {
        vaultFiles = withContext(Dispatchers.IO) {
            vaultDir.listFiles()
                ?.filterNot { VaultCrypto.isTempArtifact(it.name) }
                ?.map { it.name }
                ?: emptyList()
        }
    }

    // Runs once per unlock: migrates any pre-hardening XOR-encoded files to AES-GCM in place,
    // and re-verifies files already in the new format still decrypt. A file that fails either
    // step is left untouched on disk (see VaultCrypto.storeFile) and reported, never dropped.
    // Also sweeps any orphaned temp file a prior migration could have left behind if the app
    // died between finishing that write and the rename that swaps it in (see VaultCrypto.storeFile)
    // — safe to delete, since the real file it would have replaced was never touched.
    fun onUnlocked() {
        isUnlocked = true
        scope.launch {
            val failed = withContext(Dispatchers.IO) {
                val entries = vaultDir.listFiles() ?: emptyArray()
                val (orphans, files) = entries.partition { VaultCrypto.isTempArtifact(it.name) }
                orphans.forEach { it.delete() }
                // Decrypted copies from a previous session's "open" do not
                // outlive that session.
                File(context.cacheDir, "vault_open").listFiles()?.forEach { it.delete() }
                files.count { file ->
                    runCatching { VaultCrypto.verifyAndMigrate(file, context.packageName) }.isFailure
                }
            }
            vaultError = if (failed > 0) {
                "$failed file${if (failed != 1) "s" else ""} could not be verified and were left unchanged."
            } else null
            refreshVaultFiles()
        }
    }

    fun authenticate() {
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Fail CLOSED here too. This branch used to call onUnlocked() —
            // decrypting every file and enabling the whole UI — whenever the
            // device reported no usable authenticator. The earlier fix below
            // only ever covered the activity-cast path, so the vault still
            // opened itself on any phone with no enrolled biometric and no
            // screen lock. DEVICE_CREDENTIAL is already in the request, so
            // reaching here means there is genuinely nothing to verify against.
            authError = "Set a screen lock or fingerprint on this phone to open the vault."
            return
        }
        // Fail CLOSED. This previously did `isUnlocked = true; loadVaultFiles()`,
        // and because the host was a bare ComponentActivity the cast always
        // failed — so the "secure" vault silently opened itself, every time, with
        // no authentication at all. A vault that can't verify you must stay shut.
        val activity = context.findFragmentActivity() ?: run {
            authError = "Secure prompt unavailable — vault stays locked."
            return
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Secure Vault")
                .setSubtitle("Authenticate to access encrypted files")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ).build()
        )
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
                    val dest = File(vaultDir, "$name.enc")

                    // Size is checked before the file is read, not after.
                    // Encryption here holds the plaintext, the ciphertext and
                    // Cipher's own copy at once, so a large video can cost
                    // several times its size in heap and fail partway through a
                    // security operation (F-017). Refusing up front is the
                    // difference between a clear message and an OOM mid-encrypt.
                    val declaredSize = context.contentResolver
                        .openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    if (declaredSize > 0) {
                        VaultCrypto.rejectionReason(declaredSize)?.let { error(it) }
                    }

                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Couldn't open the selected file")
                    // Re-checked against the bytes actually read: the descriptor
                    // can report UNKNOWN_LENGTH for a stream.
                    VaultCrypto.rejectionReason(bytes.size.toLong())?.let { error(it) }
                    VaultCrypto.storeFile(dest, bytes)
                }
            }
            // Surface the actual reason when there is one worth reading — "too
            // large" is actionable, where a generic failure just looks broken.
            vaultError = result.exceptionOrNull()?.let { e ->
                e.message?.takeIf { it.contains("larger than") || it.contains("empty") }
                    ?: "Couldn't add the file securely — nothing was saved."
            }
            refreshVaultFiles()
        }
    }

    LaunchedEffect(Unit) { authenticate() }

    // Re-lock when the vault leaves the screen.
    //
    // isUnlocked survived for the lifetime of the composable, so a vault opened
    // once stayed open: press Home, hand the phone to someone, and returning
    // through Recents showed the decrypted file list with no prompt (F-018).
    // Authentication is a moment, not a mode.
    //
    // ON_STOP rather than ON_PAUSE deliberately — pause fires for a transient
    // system dialog, including the biometric prompt itself, which would re-lock
    // the screen in the middle of unlocking it.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    isUnlocked = false
                    // The decrypted names are as sensitive as the contents.
                    vaultFiles = emptyList()
                    // Plaintext copies written for "open in another app" do not
                    // outlive the session that asked for them.
                    runCatching {
                        File(context.cacheDir, "vault_open").listFiles()?.forEach { it.delete() }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (!isUnlocked) authenticate()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Secure Vault",
                onBack = onBack,
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { filePicker.launch("*/*") }) {
                            Icon(Icons.Default.Add, "Add file to vault", tint = CiyatoGold)
                        }
                    }
                },
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = isUnlocked,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { unlocked ->
            if (!unlocked) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Lock, null, tint = CiyatoGold, modifier = Modifier.size(64.dp))
                        Text("Vault Locked", color = CiyatoWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("Authenticate to access encrypted files", color = CiyatoMuted)
                        authError?.let { message ->
                            Text(message, color = CiyatoRed, fontSize = 13.sp)
                        }
                        Button(onClick = { authError = null; authenticate() },
                            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
                            Text("Unlock", color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (vaultFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.FolderOpen, null, tint = CiyatoMuted, modifier = Modifier.size(48.dp))
                        Text("Vault is empty", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tap + to add encrypted files", color = CiyatoMuted)
                        vaultError?.let { message ->
                            Text(message, color = CiyatoRed, fontSize = 13.sp)
                        }
                        Button(onClick = { filePicker.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold)) {
                            Icon(Icons.Default.Add, "Add file to vault", tint = Color.Black)
                            Spacer(Modifier.width(6.dp))
                            Text("Add File", color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("${vaultFiles.size} encrypted file${if (vaultFiles.size != 1) "s" else ""}",
                            color = CiyatoMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    vaultError?.let { message ->
                        item {
                            Text(message, color = CiyatoRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    items(vaultFiles) { name ->
                        Card(colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
                            shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(name.removeSuffix(".enc"), color = CiyatoWhite,
                                    fontSize = 13.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { exportAndOpen(name) }) {
                                    Icon(
                                        Icons.Default.FileOpen,
                                        contentDescription = "Open ${name.removeSuffix(".enc")}",
                                        tint = CiyatoSec,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(onClick = { pendingDelete = name }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete ${name.removeSuffix(".enc")}",
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = CiyatoBgEl,
            title = { Text("Delete from vault?", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "\"${name.removeSuffix(".enc")}\" will be permanently deleted. This is the " +
                        "only decryptable copy — Ciyato cannot recover it.",
                    color = CiyatoSec,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = name
                    pendingDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { File(vaultDir, target).delete() }
                        refreshVaultFiles()
                    }
                }) { Text("Delete", color = CiyatoRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep", color = CiyatoSec) }
            },
        )
    }

    vaultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { vaultMessage = null },
            containerColor = CiyatoBgEl,
            title = { Text("Couldn't open", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = { Text(message, color = CiyatoSec, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vaultMessage = null }) { Text("OK", color = CiyatoGold) }
            },
        )
    }

}
