package com.ciyato.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.*

 /**
  * Ciyato standard text input field.
 * Silver focus ring, dark background, clean label.
 */
@Composable
fun CiyatoInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: () -> Unit = {},
    isError: Boolean = false,
    errorMessage: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    maxLines: Int = 1,
    enabled: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        when {
            isError  -> CiyatoRed
            isFocused -> CiyatoGold
            else     -> CiyatoSubtleBorder
        },
        animationSpec = tween(200),
        label = "input_border"
    )
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = labelL,
                color = if (isFocused) CiyatoGold else CiyatoSec,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = CiyatoMuted, fontSize = 14.sp) },
            leadingIcon = if (leadingIcon != null) ({
                Icon(leadingIcon, null, tint = if (isFocused) CiyatoGold else CiyatoMuted, modifier = Modifier.size(20.dp))
            }) else null,
            trailingIcon = if (trailingIcon != null) ({
                Icon(
                    trailingIcon, null,
                    tint = CiyatoMuted,
                    modifier = Modifier.size(20.dp).clickable(onClick = onTrailingIconClick)
                )
            }) else null,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            singleLine = singleLine,
            maxLines = maxLines,
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = CiyatoBgEl,
                unfocusedContainerColor = CiyatoBgEl,
                focusedBorderColor      = CiyatoGold,
                unfocusedBorderColor    = CiyatoSubtleBorder,
                errorBorderColor        = CiyatoRed,
                focusedTextColor        = CiyatoWhite,
                unfocusedTextColor      = CiyatoWhite,
                cursorColor             = CiyatoGold,
                focusedLabelColor       = CiyatoGold,
                unfocusedLabelColor     = CiyatoMuted,
            ),
            shape = CiyatoShapes.medium
        )
        if (isError && errorMessage.isNotBlank()) {
            Text(errorMessage, color = CiyatoRed, style = labelM, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

/**
 * Ciyato Search Field — specialized for search interactions.
 */
@Composable
fun CiyatoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onClear: () -> Unit = {},
    onSearch: (String) -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = CiyatoMuted, fontSize = 15.sp) },
        leadingIcon = {
            Icon(Icons.Default.Search, "Search", tint = CiyatoMuted, modifier = Modifier.size(20.dp))
        },
        trailingIcon = if (value.isNotBlank()) ({
            Icon(
                Icons.Default.Close, "Clear",
                tint = CiyatoMuted,
                modifier = Modifier.size(20.dp).clickable(onClick = onClear)
            )
        }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = CiyatoBgEl,
            unfocusedContainerColor = CiyatoBgEl,
            focusedBorderColor      = CiyatoGold,
            unfocusedBorderColor    = CiyatoSubtleBorder,
            focusedTextColor        = CiyatoWhite,
            unfocusedTextColor      = CiyatoWhite,
            cursorColor             = CiyatoGold,
        ),
        shape = CiyatoShapes.full
    )
}

 /**
 * Ciyato Switch — silver-accented toggle.
 */
@Composable
fun CiyatoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CiyatoGold,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor       = CiyatoBg,
            checkedTrackColor       = accentColor,
            checkedBorderColor      = accentColor,
            uncheckedThumbColor     = CiyatoMuted,
            uncheckedTrackColor     = CiyatoBgEl2,
            uncheckedBorderColor    = CiyatoSubtleBorder,
            disabledCheckedThumbColor   = CiyatoDisabled,
            disabledUncheckedThumbColor = CiyatoDisabled,
        )
    )
}

/**
 * Ciyato Setting Row with Switch — the standard settings list item.
 */
@Composable
fun CiyatoSettingSwitch(
    title: String,
    subtitle: String = "",
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CiyatoGold,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CiyatoShapes.large)
            .background(CiyatoBgEl)
            .border(1.dp, CiyatoSubtleBorder, CiyatoShapes.large)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(CiyatoSpacing.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiyatoSpacing.iconText)
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CiyatoShapes.medium)
                    .background(accentColor.copy(alpha = 0.12f))
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = headingS, color = CiyatoWhite)
            if (subtitle.isNotBlank()) Text(subtitle, style = bodyM, color = CiyatoMuted, modifier = Modifier.padding(top = 2.dp))
        }
        CiyatoSwitch(checked = checked, onCheckedChange = onCheckedChange, accentColor = accentColor, enabled = enabled)
    }
}

 /**
 * Ciyato Slider — silver-tinted range input.
 */
@Composable
fun CiyatoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    label: String = "",
    valueLabel: String = "",
    accentColor: Color = CiyatoGold,
) {
    Column(modifier = modifier) {
        if (label.isNotBlank() || valueLabel.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (label.isNotBlank()) Text(label, style = labelL, color = CiyatoSec)
                if (valueLabel.isNotBlank()) Text(valueLabel, style = labelL, color = accentColor)
            }
            Spacer(Modifier.height(6.dp))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor            = accentColor,
                activeTrackColor      = accentColor,
                inactiveTrackColor    = CiyatoBgEl2,
                activeTickColor       = accentColor.copy(alpha = 0.5f),
                inactiveTickColor     = CiyatoMuted.copy(alpha = 0.3f),
            )
        )
    }
}

/**
 * Ciyato Password Field — secure text input with reveal toggle.
 */
@Composable
fun CiyatoPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Password",
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var showPassword by remember { mutableStateOf(false) }
    CiyatoInputField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        leadingIcon = Icons.Default.Lock,
        trailingIcon = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
        onTrailingIconClick = { showPassword = !showPassword },
        isError = isError,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onImeAction = onImeAction,
    )
}
