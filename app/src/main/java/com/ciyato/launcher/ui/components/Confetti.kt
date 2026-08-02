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
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

@Composable
fun ConfettiBurst(trigger: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(1500))
        }
    }

    if (progress.value > 0f && progress.value < 1f) {
        val particles = remember {
            List(50) {
                ConfettiParticle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speedX = Random.nextFloat() * 2f - 1f,
                    speedY = Random.nextFloat() * 3f + 1f,
                    color = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta).random()
                )
            }
        }
        
        Canvas(modifier = modifier.fillMaxSize()) {
            particles.forEach { particle ->
                val currentY = particle.y * size.height + (particle.speedY * size.height * progress.value)
                val currentX = particle.x * size.width + (particle.speedX * size.width * progress.value)
                drawCircle(
                    color = particle.color.copy(alpha = 1f - progress.value),
                    radius = 8f,
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val speedX: Float,
    val speedY: Float,
    val color: Color
)
