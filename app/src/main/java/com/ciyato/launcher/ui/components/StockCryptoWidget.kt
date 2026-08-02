package com.ciyato.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

@Composable
fun StockCryptoWidget(
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            StockItem("BTC", "$59,420", "+2.4%", listOf(40f, 45f, 42f, 48f, 52f, 50f, 60f)),
            StockItem("ETH", "$3,120", "+1.8%", listOf(30f, 28f, 32f, 35f, 34f, 38f, 42f))
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = CiyatoBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.width(60.dp)) {
                        Text(item.symbol, color = CiyatoWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(item.change, color = CiyatoBlue, fontSize = 10.sp)
                    }

                    // Mini sparkline chart drawn on Canvas
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                    ) {
                        val path = Path()
                        val points = item.history
                        if (points.isNotEmpty()) {
                            val dx = size.width / (points.size - 1)
                            val minY = points.minOrNull() ?: 0f
                            val maxY = points.maxOrNull() ?: 100f
                            val dy = if (maxY - minY == 0f) 1f else size.height / (maxY - minY)

                            points.forEachIndexed { idx, valY ->
                                val x = idx * dx
                                val y = size.height - (valY - minY) * dy
                                if (idx == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                        }
                        drawPath(
                            path = path,
                            color = CiyatoBlue,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    Text(
                        text = item.price,
                        color = CiyatoWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private data class StockItem(
    val symbol: String,
    val price: String,
    val change: String,
    val history: List<Float>
)
