package com.ciyato.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ciyato Shape System.
 * All corner radii must be sourced from here — never hardcode dp values in composables.
 */
object CiyatoShapes {
    /** 6dp — Chips, badges, small indicators */
    val extraSmall   = RoundedCornerShape(6.dp)
    /** 8dp — Inputs, toggles, small buttons */
    val small        = RoundedCornerShape(8.dp)
    /** 14dp — Standard cards */
    val medium       = RoundedCornerShape(14.dp)
    /** 20dp — Prominent cards, drawers */
    val large        = RoundedCornerShape(20.dp)
    /** 28dp — Hero cards, major panels */
    val extraLarge   = RoundedCornerShape(28.dp)
    /** 28dp — Full panel containers */
    val massive      = RoundedCornerShape(28.dp)
    /** 999dp — Pills, circular badges */
    val full         = RoundedCornerShape(999.dp)
    /** 22dp — App icons specifically */
    val appIcon      = RoundedCornerShape(22.dp)
    /** Circle — Avatars, action buttons */
    val circle       = CircleShape
    /** Bottom sheet — top corners only */
    val bottomSheet  = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Top card — bottom corners only */
    val topCard      = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
}

// ─── Glass Morph Card ─────────────────────────────────────────────────────────
