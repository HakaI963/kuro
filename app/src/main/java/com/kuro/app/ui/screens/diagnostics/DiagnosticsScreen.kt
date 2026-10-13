package com.kuro.app.ui.screens.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroSecondaryButton

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    vm: DiagnosticsViewModel = viewModel()
) {
    val state by vm.ui.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        if (state.loading) {
            item {
                Text(
                    text = stringResource(R.string.state_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state.stub || state.data == null) {
            item {
                KuroCard(
                    title = stringResource(R.string.diagnostics_stub_title),
                    subtitle = stringResource(R.string.diagnostics_stub_body)
                ) { }
            }
        } else {
            val d = state.data!!
            item {
                KuroCard(title = stringResource(R.string.diagnostics_impl_title)) {
                    DetailRow(stringResource(R.string.diagnostics_impl_class), d.implClass)
                    DetailRow(stringResource(R.string.diagnostics_commit), d.commit.take(12))
                    DetailRow(
                        stringResource(R.string.diagnostics_native),
                        if (d.nativeLoaded) stringResource(R.string.diagnostics_yes)
                        else stringResource(R.string.diagnostics_no)
                    )
                }
            }
            item {
                KuroCard(
                    title = stringResource(R.string.diagnostics_spaces_title),
                    subtitle = stringResource(R.string.diagnostics_spaces_subtitle)
                ) {
                    if (d.spaces.isEmpty()) {
                        Text(
                            text = stringResource(R.string.diagnostics_spaces_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        d.spaces.forEach { space ->
                            Text(
                                text = "user ${space.userId}: " +
                                    if (space.packages.isEmpty()) "—"
                                    else space.packages.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            item {
                KuroCard(title = stringResource(R.string.diagnostics_storage_title)) {
                    DetailRow(stringResource(R.string.diagnostics_storage_path), d.spaceDir)
                    DetailRow(stringResource(R.string.diagnostics_storage_size), formatBytes(d.spaceBytes))
                }
            }
            item {
                KuroCard(title = stringResource(R.string.diagnostics_error_title)) {
                    Text(
                        text = d.lastError
                            ?: stringResource(R.string.diagnostics_error_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                KuroSecondaryButton(
                    label = stringResource(R.string.diagnostics_refresh),
                    onClick = vm::refresh
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
}
