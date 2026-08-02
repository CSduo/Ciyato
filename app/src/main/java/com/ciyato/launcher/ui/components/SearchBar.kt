package com.ciyato.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

/**
 * Reusable Ciyato search bar.
 * Used in: App Drawer (light mode), AI Search.
 * Accepts custom bg/border/text colors so it works on both dark and light surfaces.
 */
@Composable
fun CiyatoSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search apps...",
    backgroundColor: Color = Color(0xFFECE9E3),   // warm cream for light drawer
    borderColor: Color = Color(0x18000000),         // very subtle dark border on light
    iconTint: Color = CiyatoLightSec,
    textColor: Color = CiyatoLightText,
    placeholderColor: Color = CiyatoLightSec,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        cursorBrush = SolidColor(CiyatoGold),
        textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 14.sp),
        modifier = modifier,
        decorationBox = { innerField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = iconTint, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, color = placeholderColor, fontSize = 14.sp)
                    }
                    innerField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear",
                            tint = iconTint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
    )
}
