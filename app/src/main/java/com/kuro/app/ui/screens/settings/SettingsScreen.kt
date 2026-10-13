package com.kuro.app.ui.screens.settings

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
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroSettingRow
import com.kuro.app.ui.components.KuroSwitchRow

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel()
) {
    val s by vm.settings.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.settings_auto_freeze),
                    description = stringResource(R.string.settings_auto_freeze_desc),
                    checked = s.autoFreezeIdle,
                    onCheckedChange = vm::setAutoFreeze
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.settings_hidden_visibility),
                    description = stringResource(R.string.settings_hidden_visibility_desc),
                    checked = s.showHiddenClones,
                    onCheckedChange = vm::setShowHidden
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_security), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard {
                KuroSwitchRow(
                    title = stringResource(R.string.settings_app_lock),
                    description = stringResource(R.string.settings_app_lock_desc),
                    checked = s.appLockEnabled,
                    onCheckedChange = vm::setAppLock
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.settings_biometric),
                    description = stringResource(R.string.settings_biometric_desc),
                    checked = s.biometricUnlock,
                    onCheckedChange = vm::setBiometric
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_about), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard {
                KuroSettingRow(
                    title = stringResource(R.string.settings_version),
                    description = "1.0.0-step1 (1)"
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
