package com.ciyato.launcher.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

@Composable
fun BatteryStatusWidget(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var batteryTemp by remember { mutableStateOf(0.0f) }

    LaunchedEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevel = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
            
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            batteryTemp = temp / 10.0f
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                CircularProgressIndicator(
                    progress = { batteryLevel / 100f },
                    color = if (isCharging) CiyatoGold else CiyatoBlue,
                    trackColor = CiyatoBgEl2,
                    strokeWidth = 3.dp,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = if (isCharging) Icons.Default.ElectricBolt else Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (isCharging) CiyatoGold else CiyatoBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Battery",
                    color = CiyatoWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isCharging) "Charging" else "Discharging",
                        color = CiyatoSec,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "·  ${batteryTemp}°C",
                        color = CiyatoMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "${batteryLevel}%",
                color = CiyatoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}
