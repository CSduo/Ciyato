package com.ciyato.launcher.ui.screens

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ciyato.launcher.data.LauncherSettingsRepository
import com.ciyato.launcher.data.PlacedWidgetStore
import com.ciyato.launcher.ui.components.CiyatoTopBar
import com.ciyato.launcher.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * WidgetHostScreen — Suggestion #15
 * Allows users to pick and place Android app widgets on the Ciyato home screen
 * using AppWidgetHost + AppWidgetManager APIs.
 *
 * Placed widget IDs are persisted through [LauncherSettingsRepository] (see
 * [PlacedWidgetStore]) so they survive leaving this screen — the host's
 * widget-ID allocation already outlives the screen, so keeping only an
 * in-memory list of what's placed both loses widgets on navigation and leaks
 * the IDs the host allocated for them. On load, every saved ID is reconciled
 * against [AppWidgetManager]: an ID whose provider no longer exists (e.g. the
 * providing app was uninstalled) is deallocated via
 * [AppWidgetHost.deleteAppWidgetId] and dropped, rather than rendered as a
 * broken tile.
 */

private const val WIDGET_HOST_ID = 1001

data class PlacedWidget(
    val appWidgetId: Int,
    val label: String,
    val providerInfo: AppWidgetProviderInfo,
)

@Composable
fun WidgetHostScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { LauncherSettingsRepository(context) }
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    val widgetHost = remember { AppWidgetHost(context, WIDGET_HOST_ID).also { it.startListening() } }

    var placedWidgets by remember { mutableStateOf<List<PlacedWidget>>(emptyList()) }
    var availableProviders by remember { mutableStateOf<List<AppWidgetProviderInfo>>(emptyList()) }
    var showPickerDialog by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    fun persistIds(widgets: List<PlacedWidget>) {
        scope.launch { settingsRepo.setPlacedWidgetIds(PlacedWidgetStore.serialize(widgets.map { it.appWidgetId })) }
    }

    LaunchedEffect(Unit) {
        availableProviders = widgetManager.installedProviders

        val savedIds = PlacedWidgetStore.parse(settingsRepo.placedWidgetIds.first())
        val valid = mutableListOf<PlacedWidget>()
        val staleIds = mutableListOf<Int>()
        savedIds.forEach { id ->
            val info = widgetManager.getAppWidgetInfo(id)
            if (info != null) {
                valid += PlacedWidget(appWidgetId = id, label = info.loadLabel(context.packageManager), providerInfo = info)
            } else {
                staleIds += id
            }
        }
        if (staleIds.isNotEmpty()) {
            // The provider is gone (app uninstalled, or binding never completed) —
            // deallocate the ID rather than leave it dangling on the host, and drop
            // it from what we persist so it isn't rendered as a broken tile.
            staleIds.forEach { widgetHost.deleteAppWidgetId(it) }
            settingsRepo.setPlacedWidgetIds(PlacedWidgetStore.serialize(valid.map { it.appWidgetId }))
        }
        placedWidgets = valid
        isLoaded = true
    }

    DisposableEffect(Unit) {
        onDispose { widgetHost.stopListening() }
    }

    val bindWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (appWidgetId != -1) {
            val info = widgetManager.getAppWidgetInfo(appWidgetId)
            if (info != null) {
                val updated = placedWidgets + PlacedWidget(
                    appWidgetId = appWidgetId,
                    label = info.loadLabel(context.packageManager),
                    providerInfo = info,
                )
                placedWidgets = updated
                persistIds(updated)
            } else {
                // Binding was declined/cancelled — the ID was already allocated by
                // pickWidget() below, so free it instead of leaking it.
                widgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
    }

    fun pickWidget(provider: AppWidgetProviderInfo) {
        val appWidgetId = widgetHost.allocateAppWidgetId()
        val granted = widgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
        if (!granted) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            bindWidgetLauncher.launch(intent)
        } else {
            val updated = placedWidgets + PlacedWidget(
                appWidgetId = appWidgetId,
                label = provider.loadLabel(context.packageManager),
                providerInfo = provider,
            )
            placedWidgets = updated
            persistIds(updated)
        }
        showPickerDialog = false
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
        }
    ) { padding ->
        if (!isLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CiyatoGold)
            }
        } else if (placedWidgets.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Widgets, null, tint = CiyatoMuted, modifier = Modifier.size(56.dp))
                    Text("No widgets placed", color = CiyatoWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap + to pick a widget from installed apps", color = CiyatoMuted, fontSize = 13.sp)
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
                items(placedWidgets, key = { it.appWidgetId }) { widget ->
                    WidgetCard(
                        context = context,
                        widget = widget,
                        host = widgetHost,
                        onRemove = {
                            val updated = placedWidgets.filter { it.appWidgetId != widget.appWidgetId }
                            placedWidgets = updated
                            widgetHost.deleteAppWidgetId(widget.appWidgetId)
                            persistIds(updated)
                        },
                    )
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
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(availableProviders, key = { it.provider.className + "_" + it.provider.packageName }) { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickWidget(provider) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Widgets, null, tint = CiyatoGold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(provider.loadLabel(context.packageManager), color = CiyatoWhite, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickerDialog = false }) {
                    Text("Cancel", color = CiyatoGold)
                }
            }
        )
    }
}

@Composable
private fun WidgetCard(
    context: Context,
    widget: PlacedWidget,
    host: AppWidgetHost,
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
                    widget.label,
                    color = CiyatoWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) {
                    Text("Remove", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            AndroidView(
                factory = {
                    host.createView(context, widget.appWidgetId, widget.providerInfo) as AppWidgetHostView
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(CiyatoBg, RoundedCornerShape(12.dp)),
            )
        }
    }
}
