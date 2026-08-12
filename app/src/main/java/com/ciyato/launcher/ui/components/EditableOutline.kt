package com.ciyato.launcher.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The single visual signal, launcher-wide, for "this can be long-pressed and
 * moved": a static (non-animated) white outline. One rule applied uniformly —
 * every movable element (apps, folders, category cards, the Home header,
 * search bar, Recent row, weather card, Today card, dock) gets exactly this
 * treatment in edit mode, so the user learns one visual language instead of a
 * different hint per component.
 */
fun Modifier.editableOutline(isEditMode: Boolean, shape: Shape = RoundedCornerShape(22.dp)): Modifier =
    if (isEditMode) this.border(1.5.dp, Color.White.copy(alpha = 0.85f), shape) else this
