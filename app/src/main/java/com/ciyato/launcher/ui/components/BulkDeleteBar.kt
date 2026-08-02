package com.ciyato.launcher.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * BulkDeleteBar — Suggestion #66
 * Action bar shown when items are selected in Files / Screenshots / Photos.
 * Includes item count, select-all, and delete with undo support.
 */

@Composable
fun BulkDeleteBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(CiyatoBgEl)
                .border(1.dp, CiyatoBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClearSelection, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Clear selection", tint = CiyatoMuted)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$selectedCount selected",
                    color = CiyatoWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                if (selectedCount < totalCount) {
                    Text(
                        "of $totalCount items",
                        color = CiyatoMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            if (selectedCount < totalCount) {
                TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = CiyatoGold,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("All", color = CiyatoGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun rememberBulkDeleteState(itemKeys: List<String>): BulkDeleteState {
    return remember { BulkDeleteState(itemKeys) }
}

class BulkDeleteState(private val allKeys: List<String>) {
    private val _selected = mutableStateListOf<String>()
    val selected: Set<String> get() = _selected.toSet()
    val selectedCount get() = _selected.size
    val isAllSelected get() = _selected.size == allKeys.size

    fun toggle(key: String) {
        if (key in _selected) _selected.remove(key) else _selected.add(key)
    }

    fun selectAll() { _selected.addAll(allKeys) }
    fun clearAll() { _selected.clear() }
    fun isSelected(key: String) = key in _selected
}
