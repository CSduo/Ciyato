package com.ciyato.launcher.ui.components

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ciyato.launcher.data.LauncherWidgetHost
import com.ciyato.launcher.data.WidgetPlacement
import com.ciyato.launcher.data.WidgetPlacementStore
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoMuted

/**
 * One hosted Android widget, rendered from the shared launcher host.
 *
 * Shared by Home and the widget manager on purpose. Two copies of this would be
 * two answers to how tall a widget is and two places to forget
 * `updateAppWidgetSize`, which is the shape of defect the audit kept finding
 * (F-179, F-180).
 *
 * The caller is responsible for wrapping this in `key(appWidgetId)`: the
 * `AndroidView` factory runs once per composition slot, so reusing a slot for a
 * different widget would show the previous widget's view.
 */
@Composable
fun HostedWidget(
    placement: WidgetPlacement,
    info: AppWidgetProviderInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // The provider's own minimum, in dp. minHeight is reported in px.
    val providerMinHeightDp = remember(info.minHeight, density) {
        with(density) { info.minHeight.coerceAtLeast(0).toDp().value.toInt() }
    }
    val heightDp: Dp = remember(placement, providerMinHeightDp) {
        WidgetPlacementStore.resolvedHeightDp(placement, providerMinHeightDp).dp
    }

    // Built through the shared host, which returns null rather than throwing
    // when the providing app has been uninstalled since the widget was placed.
    // Home must not be able to crash because someone removed a weather app.
    val hostView: AppWidgetHostView? = remember(placement.appWidgetId, info) {
        LauncherWidgetHost.createView(context, placement.appWidgetId, info)
    }

    if (hostView == null) {
        Box(
            modifier
                .fillMaxWidth()
                .height(heightDp)
                .background(CiyatoBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "This widget's app is no longer installed",
                color = CiyatoMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        return
    }

    AndroidView(
        factory = { hostView },
        update = { view ->
            // Providers pick a layout from the size they are told they have.
            // Without this call a widget renders for whatever it assumed at
            // bind time and then never learns it was given something else -
            // which is how a 4x2 calendar ends up cropped inside a host that
            // had the room for it all along.
            val widthDp = view.width.takeIf { it > 0 }
                ?.let { with(density) { it.toDp().value.toInt() } }
            if (widthDp != null && widthDp > 0) {
                runCatching {
                    view.updateAppWidgetSize(Bundle.EMPTY, widthDp, heightDp.value.toInt(), widthDp, heightDp.value.toInt())
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(CiyatoBg, RoundedCornerShape(12.dp)),
    )
}
