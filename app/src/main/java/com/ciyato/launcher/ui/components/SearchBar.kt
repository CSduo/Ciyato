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
 * Reusable Ciyato search bar. Used by the App Drawer and AI Search.
 *
 * The defaults were built for a light drawer that does not exist: a warm cream
 * fill with near-black text. AI Search takes the defaults, so it shipped a cream
 * pill sitting inside the near-black screen — visible proof of the appearance
 * inconsistency behind F-036, not a theory about it. The App Drawer passed dark
 * overrides for every one of them, which is what a wrong default looks like when
 * only one caller has noticed.
 *
 * Defaults are the dark palette now. The parameters stay: a search bar over a
 * photo or a wallpaper still needs to adjust.
 */
@Composable
fun CiyatoSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search apps...",
    backgroundColor: Color = CiyatoBgEl2,
    borderColor: Color = CiyatoBorder,
    iconTint: Color = CiyatoMuted,
    textColor: Color = CiyatoWhite,
    placeholderColor: Color = CiyatoMuted,
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
