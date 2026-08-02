package com.ciyato.launcher.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ciyato.launcher.data.InstalledApp

/**
 * DragDropAppGrid — Suggestion #17
 * Drag-and-drop app rearrangement using Compose's detectDragGesturesAfterLongPress.
 * Long-press an icon to lift it, then drag to reorder.
 */

@Composable
fun DragDropAppGrid(
    apps: List<InstalledApp>,
    columns: Int = 4,
    onReorder: (List<InstalledApp>) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (app: InstalledApp, isDragging: Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    var items by remember(apps) { mutableStateOf(apps) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val itemSize = 80.dp
    val itemSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { itemSize.toPx() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items, key = { _, app -> app.packageName }) { index, app ->
            val isDragging = draggedIndex == index

            Box(
                modifier = Modifier
                    .size(itemSize)
                    .zIndex(if (isDragging) 1f else 0f)
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = Offset.Zero
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                dragOffset += delta

                                val currentIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                val colCount = columns
                                val rowDelta = (dragOffset.y / itemSizePx).toInt()
                                val colDelta = (dragOffset.x / itemSizePx).toInt()
                                val targetIdx = (currentIdx + colDelta + rowDelta * colCount)
                                    .coerceIn(0, items.lastIndex)

                                if (targetIdx != currentIdx) {
                                    val mutable = items.toMutableList()
                                    mutable.add(targetIdx, mutable.removeAt(currentIdx))
                                    items = mutable
                                    draggedIndex = targetIdx
                                    dragOffset = Offset.Zero
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                draggedIndex = null
                                dragOffset = Offset.Zero
                                onReorder(items)
                            },
                            onDragCancel = {
                                draggedIndex = null
                                dragOffset = Offset.Zero
                            },
                        )
                    },
            ) {
                itemContent(app, isDragging)
            }
        }
    }
}
