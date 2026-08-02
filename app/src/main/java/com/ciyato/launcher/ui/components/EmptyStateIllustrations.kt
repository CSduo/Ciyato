package com.ciyato.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec

@Composable
fun EmptyAppsState(message: String, modifier: Modifier = Modifier) {
    EmptyStateBase(
        message = message,
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = CiyatoGold, modifier = Modifier.size(48.dp)) },
        modifier = modifier
    )
}

@Composable
fun EmptyFilesState(message: String, modifier: Modifier = Modifier) {
    EmptyStateBase(
        message = message,
        icon = { Icon(Icons.Default.Folder, null, tint = CiyatoGold, modifier = Modifier.size(48.dp)) },
        modifier = modifier
    )
}

@Composable
fun EmptySearchState(query: String, modifier: Modifier = Modifier) {
    EmptyStateBase(
        message = "No results found for \"$query\"",
        icon = { Icon(Icons.Default.Search, null, tint = CiyatoGold, modifier = Modifier.size(48.dp)) },
        modifier = modifier
    )
}

@Composable
fun EmptyPhotosState(modifier: Modifier = Modifier) {
    EmptyStateBase(
        message = "No photos available",
        icon = { Icon(Icons.Default.PhotoCamera, null, tint = CiyatoGold, modifier = Modifier.size(48.dp)) },
        modifier = modifier
    )
}

@Composable
private fun EmptyStateBase(message: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, size.height / 2)
                    quadraticBezierTo(size.width / 4, 0f, size.width / 2, size.height / 2)
                    quadraticBezierTo(size.width * 3 / 4, size.height, size.width, size.height / 2)
                }
                drawPath(path, color = CiyatoSec.copy(alpha = 0.2f), style = Stroke(width = 2.dp.toPx()))
            }
            icon()
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, color = CiyatoMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
