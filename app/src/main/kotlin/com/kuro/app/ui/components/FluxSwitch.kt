package com.kuro.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.kuro.app.ui.theme.FluxSwitchCheckedThumb
import com.kuro.app.ui.theme.FluxSwitchCheckedTrack
import com.kuro.app.ui.theme.FluxSwitchUncheckedBorder
import com.kuro.app.ui.theme.FluxSwitchUncheckedThumb
import com.kuro.app.ui.theme.FluxSwitchUncheckedTrack

@Composable
fun FluxSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = if (isDark) {
        SwitchDefaults.colors(
            checkedThumbColor = FluxSwitchCheckedThumb,
            checkedTrackColor = FluxSwitchCheckedTrack,
            checkedBorderColor = FluxSwitchCheckedTrack,
            uncheckedThumbColor = FluxSwitchUncheckedThumb,
            uncheckedTrackColor = FluxSwitchUncheckedTrack,
            uncheckedBorderColor = FluxSwitchUncheckedBorder,
        )
    } else {
        SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.secondary,
            checkedThumbColor = MaterialTheme.colorScheme.onSecondary
        )
    }

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = colors,
        enabled = enabled,
        modifier = modifier
    )
}
