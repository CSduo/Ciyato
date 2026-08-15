package com.ciyato.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ciyato.launcher.ui.theme.CiyatoBg
import com.ciyato.launcher.ui.theme.CiyatoBorder
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoWhite

/**
 * Long-press menu for a canvas object (Home section or category card): an
 * optional "Reset position" (only offered while it's actually free-positioned)
 * and "Remove", which itself opens [RemoveObjectDialog]. Same near-black
 * floating language as that dialog — never a stock Android popup/DropdownMenu.
 */
@Composable
fun CanvasObjectMenu(
    label: String,
    canReset: Boolean,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(CiyatoBg)
                    .border(1.dp, CiyatoBorder, RoundedCornerShape(22.dp))
                    .clickable(enabled = false, onClick = {})
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    label,
                    color = CiyatoSec,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                if (canReset) {
                    CanvasMenuRow(icon = Icons.Default.RestartAlt, text = "Reset position") {
                        onReset(); onDismiss()
                    }
                }
                CanvasMenuRow(icon = Icons.Default.RemoveCircleOutline, text = "Remove", onClick = onRemove)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CanvasMenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CiyatoWhite, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = CiyatoWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * The launcher's own destructive-confirmation surface for canvas objects —
 * deliberately NOT a stock Material [androidx.compose.material3.AlertDialog].
 * Near-black floating card ([CiyatoBg]), a thin subtle border ([CiyatoBorder]),
 * generous internal spacing, soft typography, a dimmed backdrop, and a
 * restrained scale+fade entrance (skipped entirely when [reduceMotion] is on).
 * The destructive intent is carried by the "Remove" label alone — no red
 * panel, no warning triangle, no stock icon.
 */
@Composable
fun RemoveObjectDialog(
    title: String,
    body: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    reduceMotion: Boolean = false,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = true,
                enter = if (reduceMotion) {
                    fadeIn(tween(1))
                } else {
                    fadeIn(tween(240)) + scaleIn(initialScale = 0.94f, animationSpec = tween(240))
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(CiyatoBg)
                        .border(1.dp, CiyatoBorder, RoundedCornerShape(28.dp))
                        .clickable(enabled = false, onClick = {})
                        .padding(horizontal = 26.dp, vertical = 26.dp),
                ) {
                    Text(title, color = CiyatoWhite, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(body, color = CiyatoSec, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(28.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "Cancel",
                            color = CiyatoSec,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Remove",
                            color = CiyatoWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onConfirm)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
