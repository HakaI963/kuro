package com.kuro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivarna.fluxlinux.R
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.ui.theme.KuroPalette

@Composable
fun StatusBadge(status: CloneStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        CloneStatus.ACTIVE -> stringResource(R.string.status_active) to KuroPalette.Success
        CloneStatus.ERROR -> stringResource(R.string.status_error) to KuroPalette.Error
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
