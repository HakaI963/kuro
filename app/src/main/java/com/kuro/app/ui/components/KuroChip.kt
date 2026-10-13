package com.kuro.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KuroChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (onClick != null) {
        AssistChip(
            onClick = onClick,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            modifier = modifier,
            shape = MaterialTheme.shapes.small
        )
    } else {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun KuroFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.padding(end = 8.dp),
        shape = MaterialTheme.shapes.small
    )
}
