package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ChipShape = RoundedCornerShape(13.dp)

/**
 * An icon as a lit object: gradient fill, a highlight across its top half, and a
 * shadow in its own colour so it sits on the surface rather than being printed into it.
 *
 * Shared by the Organizer's categories and Cleanup's, so the two screens speak one
 * visual language. Shadow colour is honoured from API 28; on 26 and 27 it falls back to
 * a neutral shadow, which still reads correctly.
 */
@Composable
fun GlyphChip(icon: ImageVector, light: Color, deep: Color, side: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(side)
            .shadow(elevation = 8.dp, shape = ChipShape, ambientColor = deep, spotColor = deep)
            .clip(ChipShape)
            .background(Brush.linearGradient(listOf(light, deep)))
            .drawWithContent {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        endY = size.height * 0.55f,
                    ),
                )
                drawContent()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(side * 0.5f))
    }
}
