package com.kuro.app.ui.screens.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.domain.models.ProvisioningState
import com.kuro.app.domain.models.ThemeMode
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.KuroSecondaryButton
import com.kuro.app.ui.components.KuroSettingRow
import com.kuro.app.ui.components.KuroSwitchRow

@Composable
fun SettingsScreen(
    onOpenExplainer: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel()
) {
    val s by vm.settings.collectAsState()
    val provisioning by vm.provisioning.collectAsState()
    val context = LocalContext.current

    val provisionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vm.onProvisionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        vm.refreshProvisioning(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(text = stringResource(R.string.settings_section_engine), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard(
                title = stringResource(R.string.settings_engine_title),
                subtitle = when (provisioning) {
                    is ProvisioningState.Active -> stringResource(R.string.settings_engine_active_desc)
                    is ProvisioningState.NotSetup -> stringResource(R.string.settings_engine_not_setup_desc)
                    is ProvisioningState.ProfileExistsNotOwner ->
                        "A work profile already exists but is owned by another app. Only one work profile is allowed per device."
                    is ProvisioningState.Blocked -> "Setup blocked. See details in the explainer."
                    is ProvisioningState.Checking -> stringResource(R.string.state_loading)
                }
            ) {
                Text(
                    text = when (provisioning) {
                        is ProvisioningState.Active -> stringResource(R.string.settings_engine_active)
                        is ProvisioningState.NotSetup -> stringResource(R.string.settings_engine_not_setup)
                        else -> stringResource(R.string.settings_engine_blocked)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (provisioning !is ProvisioningState.Active) {
                    KuroPrimaryButton(
                        label = stringResource(R.string.settings_engine_setup),
                        onClick = {
                            val intent = vm.provisionIntent(context)
                            if (intent != null) {
                                try {
                                    provisionLauncher.launch(intent)
                                } catch (_: Exception) {
                                    vm.refreshProvisioning(context)
                                }
                            } else {
                                vm.refreshProvisioning(context)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                KuroSecondaryButton(
                    label = stringResource(R.string.settings_engine_learn),
                    onClick = onOpenExplainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.settings_engine_refresh),
                    onClick = { vm.refreshProvisioning(context) }
                )
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
                    description = "1.0.0-step2 (2)"
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

@Composable
fun EngineExplainerScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Text(text = stringResource(R.string.engine_explainer_title), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            KuroCard {
                Text(text = stringResource(R.string.engine_explainer_body1), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            KuroCard {
                Text(text = stringResource(R.string.engine_explainer_body2), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Text(text = stringResource(R.string.engine_explainer_limits_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            KuroCard {
                Text(text = stringResource(R.string.engine_explainer_limits_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
