package com.kuro.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
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
import com.kuro.app.domain.models.ThemeMode
import com.kuro.engine.EngineStatus
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroSecondaryButton
import com.kuro.app.ui.components.KuroSettingRow
import com.kuro.app.ui.components.KuroSwitchRow

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel()
) {
    val s by vm.settings.collectAsState()
    val engineStatus by vm.engineStatus.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(text = stringResource(R.string.settings_section_engine), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard(
                title = stringResource(R.string.settings_engine_title),
                subtitle = null
            ) {
                val (label, detail) = when (val st = engineStatus) {
                    is EngineStatus.Ready -> stringResource(R.string.settings_engine_status_ready) to null
                    is EngineStatus.Uninitialized -> stringResource(R.string.settings_engine_status_uninitialized) to null
                    is EngineStatus.Incompatible ->
                        stringResource(R.string.settings_engine_status_incompatible) to
                            "Engine not compatible with Android ${st.androidVersion} on this device"
                    is EngineStatus.Error -> stringResource(R.string.settings_engine_status_error) to st.message
                }
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (detail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(12.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.settings_engine_retry),
                    onClick = vm::retryEngineInit
                )
                if (com.kuro.app.BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(8.dp))
                    KuroSettingRow(
                        title = stringResource(R.string.settings_engine_diagnostics),
                        description = stringResource(R.string.settings_engine_diagnostics_desc),
                        onClick = onOpenDiagnostics
                    )
                }
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_appearance), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard {
                KuroSettingRow(
                    title = stringResource(R.string.settings_theme),
                    description = stringResource(R.string.settings_theme_desc),
                    value = when (s.themeMode) {
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    },
                    onClick = {
                        val next = when (s.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.SYSTEM
                        }
                        vm.setTheme(next)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.settings_dynamic),
                    description = stringResource(R.string.settings_dynamic_desc),
                    checked = s.dynamicColor,
                    onCheckedChange = vm::setDynamic
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    description = stringResource(R.string.settings_reduce_motion_desc),
                    checked = s.reduceMotion,
                    onCheckedChange = vm::setReduceMotion
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_behavior), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard {
                KuroSwitchRow(
                    title = stringResource(R.string.settings_default_launch),
                    description = stringResource(R.string.settings_default_launch_desc),
                    checked = s.defaultToLibrary,
                    onCheckedChange = vm::setDefaultToLibrary
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_about), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard {
                KuroSettingRow(
                    title = stringResource(R.string.settings_version),
                    description = "1.0.0-stage1 (3)"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSettingRow(
                    title = stringResource(R.string.settings_build),
                    description = stringResource(R.string.settings_build_value)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSettingRow(
                    title = stringResource(R.string.settings_legal),
                    description = stringResource(R.string.settings_legal_body)
                )
            }
        }
        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}
