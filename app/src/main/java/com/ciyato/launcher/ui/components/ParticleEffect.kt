package com.ciyato.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.ciyato.launcher.ui.theme.CiyatoGold
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ParticleBurst(trigger: Boolean, center: Offset, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(400))
        }
    }

    if (progress.value > 0f && progress.value < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val alpha = 1f - progress.value
            val radius = 100f * progress.value
            val particleSize = 6f * (1f - progress.value)
            
            for (i in 0 until 8) {
                val angle = (i * Math.PI * 2) / 8
                val x = center.x + cos(angle).toFloat() * radius
                val y = center.y + sin(angle).toFloat() * radius
                drawCircle(
                    color = CiyatoGold.copy(alpha = alpha),
                    radius = particleSize,
                    center = Offset(x, y)
                )
            }
        }
    }
}
