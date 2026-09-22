package com.ciyato.launcher.ui.screens

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.LauncherSettingsRepository
import com.ciyato.launcher.data.LauncherWidgetHost
import com.ciyato.launcher.data.WidgetPlacement
import com.ciyato.launcher.data.WidgetPlacementStore
import com.ciyato.launcher.data.rememberLauncherWidgetHost
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.components.HostedWidget
import com.ciyato.launcher.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Add, configure, resize and remove the widgets that live on Home.
 *
 * This screen used to BE the feature: it allocated widget IDs, hosted the views
 * inside its own list, and owned the AppWidgetHost lifecycle. Settings promised
 * placement on the home screen and delivered a gallery you had to open in order
 * to see (F-179), while the host stopped listening the moment you left it, so
 * widgets updated only while the management screen was in front of you (F-138).
 *
 * Now the widgets are Home canvas objects — see [WidgetPlacement] — and this is
 * a management surface over that list. It is deliberately secondary: every
 * action here changes what Home shows.
 */
@Composable
fun WidgetHostScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { LauncherSettingsRepository(context) }
    val widgetManager = remember { AppWidgetManager.getInstance(context) }

    // The process-wide host, retained while this screen is composed and released
    // without disturbing Home's own retain (F-138).
    val widgetHost = rememberLauncherWidgetHost()

    var placements by remember { mutableStateOf<List<WidgetPlacement>>(emptyList()) }
    var availableProviders by remember { mutableStateOf<List<AppWidgetProviderInfo>>(emptyList()) }
    var showPickerDialog by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    fun persist(updated: List<WidgetPlacement>) {
        placements = updated
        scope.launch { settingsRepo.setPlacedWidgetIds(WidgetPlacementStore.serialize(updated)) }
    }

    LaunchedEffect(Unit) {
        availableProviders = runCatching { widgetManager.installedProviders }.getOrDefault(emptyList())

        val saved = WidgetPlacementStore.parse(settingsRepo.placedWidgetIds.first())
        val valid = saved.filter { widgetManager.getAppWidgetInfo(it.appWidgetId) != null }
        val stale = saved.filter { placement -> valid.none { it.appWidgetId == placement.appWidgetId } }
        if (stale.isNotEmpty()) {
            // The provider is gone (app uninstalled, or binding never completed):
            // give the ID back rather than leave it allocated forever, and drop it
            // from storage so Home does not render a dead tile for it.
            stale.forEach { LauncherWidgetHost.deleteAppWidgetId(context, it.appWidgetId) }
            settingsRepo.setPlacedWidgetIds(WidgetPlacementStore.serialize(valid))
        }
        placements = valid
        isLoaded = true
    }

    // The ID allocated for an in-flight bind, so it can be reclaimed if the flow
    // does not complete. Without this, cancelling the system bind dialog leaked
    // the ID permanently: result.data is null on cancel, so appWidgetId came back
    // as -1 and the cleanup branch was never reached (F-139).
    var pendingWidgetId by remember { mutableStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }

    DisposableEffect(Unit) {
        onDispose {
            // An allocation still in flight when the screen dies would otherwise
            // be orphaned with no owner and no way to reclaim it. The host itself
            // is NOT stopped here: Home may still be showing widgets.
            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                LauncherWidgetHost.deleteAppWidgetId(context, pendingWidgetId)
            }
        }
    }

    fun releasePending() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            LauncherWidgetHost.deleteAppWidgetId(context, pendingWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    fun addBound(appWidgetId: Int) {
        persist(placements + WidgetPlacement(appWidgetId = appWidgetId))
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    // Some providers ship a configuration activity and are not usable until it
    // has run: a clock with no timezone chosen, a folder widget with no folder.
    // That step was never launched, so those widgets bound and then rendered
    // empty or default forever (F-136, F-180).
    val configureWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = pendingWidgetId
        val stillBound = id != AppWidgetManager.INVALID_APPWIDGET_ID &&
            widgetManager.getAppWidgetInfo(id) != null
        if (result.resultCode == android.app.Activity.RESULT_OK && stillBound) {
            addBound(id)
        } else {
            // Configuration cancelled: an unconfigured widget is not useful, so
            // the binding is undone rather than left half-made on Home.
            releasePending()
        }
        showPickerDialog = false
    }

    fun finishBinding(appWidgetId: Int, provider: AppWidgetProviderInfo) {
        if (provider.configure != null) {
            pendingWidgetId = appWidgetId
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = provider.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val launched = runCatching { configureWidgetLauncher.launch(intent) }.isSuccess
            if (!launched) {
                // Provider declares a config activity that cannot be started.
                // Keep the widget rather than losing it; it may still render.
                addBound(appWidgetId)
                showPickerDialog = false
            }
        } else {
            addBound(appWidgetId)
            showPickerDialog = false
        }
    }

    val bindWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = pendingWidgetId
        val provider = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetManager.getAppWidgetInfo(id)
        } else null
        if (result.resultCode == android.app.Activity.RESULT_OK && provider != null) {
            finishBinding(id, provider)
        } else {
            // Covers the real cancel case: no data, no permission, or a provider
            // that vanished. Either way the allocated ID goes back.
            releasePending()
            showPickerDialog = false
        }
    }

    fun pickWidget(provider: AppWidgetProviderInfo) {
        val appWidgetId = widgetHost.allocateAppWidgetId()
        pendingWidgetId = appWidgetId
        val granted = runCatching {
            widgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
        }.getOrDefault(false)
        if (!granted) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            bindWidgetLauncher.launch(intent)
        } else {
            finishBinding(appWidgetId, provider)
        }
    }

    Scaffold(
        containerColor = CiyatoBg,
        topBar = {
            CiyatoTopBar(
                title = "Widgets",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showPickerDialog = true }) {
                        Icon(Icons.Default.Add, "Add widget", tint = CiyatoGold)
                    }
                },
            )
        },
    ) { padding ->
        if (!isLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CiyatoGold)
            }
        } else if (placements.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(Icons.Default.Widgets, null, tint = CiyatoMuted, modifier = Modifier.size(56.dp))
                    Text("No widgets on Home", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Widgets you add appear on your home screen, where you can drag them anywhere.",
                        color = CiyatoMuted,
                        fontSize = 13.sp,
                    )
                    Button(
                        onClick = { showPickerDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
                    ) {
                        Icon(Icons.Default.Add, "Add widget", tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Widget", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(padding),
            ) {
                item {
                    Text(
                        "These are on your home screen. Long-press one there to move or remove it.",
                        color = CiyatoMuted,
                        fontSize = 12.sp,
                    )
                }
                items(placements, key = { it.appWidgetId }) { placement ->
                    val info = remember(placement.appWidgetId) {
                        widgetManager.getAppWidgetInfo(placement.appWidgetId)
                    }
                    if (info != null) {
                        val providerMinHeightDp = remember(info.minHeight) {
                            with(density) { info.minHeight.coerceAtLeast(0).toDp().value.toInt() }
                        }
                        WidgetCard(
                            placement = placement,
                            info = info,
                            label = remember(info) { info.loadLabel(context.packageManager) },
                            onResize = { steps ->
                                persist(
                                    placements.map {
                                        if (it.appWidgetId == placement.appWidgetId) {
                                            WidgetPlacementStore.resized(it, providerMinHeightDp, steps)
                                        } else {
                                            it
                                        }
                                    },
                                )
                            },
                            onRemove = {
                                persist(placements.filter { it.appWidgetId != placement.appWidgetId })
                                LauncherWidgetHost.deleteAppWidgetId(context, placement.appWidgetId)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPickerDialog = false },
            containerColor = CiyatoBgEl,
            title = { Text("Choose Widget", color = CiyatoWhite, fontWeight = FontWeight.SemiBold) },
            text = {
                if (availableProviders.isEmpty()) {
                    Text("No installed app offers a widget.", color = CiyatoMuted, fontSize = 13.sp)
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(
                            availableProviders,
                            key = { it.provider.className + "_" + it.provider.packageName },
                        ) { provider ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pickWidget(provider) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Widgets, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    provider.loadLabel(context.packageManager),
                                    color = CiyatoWhite,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickerDialog = false }) {
                    Text("Cancel", color = CiyatoGold)
                }
            },
        )
    }
}

@Composable
private fun WidgetCard(
    placement: WidgetPlacement,
    info: AppWidgetProviderInfo,
    label: CharSequence,
    onResize: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CiyatoBgEl),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label.toString(),
                    color = CiyatoWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Height is stored per widget, so a resize survives a restart and
                // Home renders the size chosen here (F-180).
                IconButton(
                    onClick = { onResize(-1) },
                    modifier = Modifier.semantics { contentDescription = "Make $label shorter" },
                ) {
                    Icon(Icons.Default.Remove, null, tint = CiyatoSec, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onResize(1) },
                    modifier = Modifier.semantics { contentDescription = "Make $label taller" },
                ) {
                    Icon(Icons.Default.Add, null, tint = CiyatoSec, modifier = Modifier.size(18.dp))
                }
                TextButton(onClick = onRemove) {
                    Text("Remove", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            key(placement.appWidgetId) {
                HostedWidget(placement = placement, info = info)
            }
        }
    }
}
