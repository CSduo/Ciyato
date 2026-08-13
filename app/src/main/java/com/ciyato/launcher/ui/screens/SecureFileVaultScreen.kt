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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.ciyato.launcher.data.VaultCrypto
import com.ciyato.launcher.ui.theme.*
import com.ciyato.launcher.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SecureFileVaultScreen — Suggestion #68
 * Biometric-gated file vault using AES-256-GCM (Android Keystore) encryption.
 * Files are encrypted on import and decrypted on open via [VaultCrypto].
 * Vault directory lives in app's internal private storage.
 */

@OptIn(ExperimentalMaterial3Api::class)
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
            onUnlocked()
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
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Couldn't open the selected file")
                    VaultCrypto.storeFile(dest, bytes)
                }
            }
            vaultError = result.exceptionOrNull()?.let { "Couldn't add the file securely — nothing was saved." }
            refreshVaultFiles()
        }
    }

    LaunchedEffect(Unit) { authenticate() }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            TopAppBar(
                title = { Text("Secure Vault", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiyatoWhite)
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { filePicker.launch("*/*") }) {
                            Icon(Icons.Default.Add, "Add file to vault", tint = CiyatoGold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CiyatoBg),
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
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { File(vaultDir, name).delete() }
                                        refreshVaultFiles()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
