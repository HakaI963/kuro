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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.engine.proot.ChrootGates
import com.kuro.app.engine.proot.ProotAcquisition
import com.kuro.app.engine.proot.ProotProvenance
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroSecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        // Step 5c: proot + chroot validation state (all flavors, incl. stub).
        item { ProotDiagnosticsCard() }
        item { ChrootDiagnosticsCard() }
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

/** Step 5c: proot binary validation proof (source, version, ELF, smoke). */
@Composable
private fun ProotDiagnosticsCard() {
    val context = LocalContext.current
    var provenance by remember { mutableStateOf<ProotProvenance?>(null) }
    var validation by remember { mutableStateOf<List<String>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val dir = context.filesDir
        provenance = withContext(Dispatchers.IO) { ProotAcquisition.readProvenance(dir) }
        validation = withContext(Dispatchers.IO) { ProotAcquisition.readValidation(dir) }
        loaded = true
    }
    KuroCard(title = stringResource(R.string.diagnostics_proot_title)) {
        if (!loaded) {
            Text(
                text = stringResource(R.string.state_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@KuroCard
        }
        val prov = provenance
        if (prov == null) {
            Text(
                text = stringResource(R.string.diagnostics_proot_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@KuroCard
        }
        DetailRow(stringResource(R.string.diagnostics_proot_source), prov.source)
        DetailRow(stringResource(R.string.diagnostics_proot_version), prov.versionLine)
        DetailRow(
            stringResource(R.string.diagnostics_proot_sha),
            prov.sha256.take(32) + "…"
        )
        prov.elfProof.forEach { line ->
            DetailRow(stringResource(R.string.diagnostics_proot_elf), line)
        }
        val smoke = validation.firstOrNull { it.startsWith("smoke=") }
            ?.removePrefix("smoke=")
        if (smoke != null) {
            DetailRow(stringResource(R.string.diagnostics_proot_smoke), smoke)
        }
    }
}

/** Step 5c: kuro-chroot gate results (g1 version + g2 mount cycle via su). */
@Composable
private fun ChrootDiagnosticsCard() {
    val context = LocalContext.current
    var gates by remember { mutableStateOf<ChrootGates.Results?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        gates = withContext(Dispatchers.IO) { ChrootGates.readLast(context.filesDir) }
        loaded = true
    }
    KuroCard(title = stringResource(R.string.diagnostics_chroot_title)) {
        if (!loaded) {
            Text(
                text = stringResource(R.string.state_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@KuroCard
        }
        val g = gates
        if (g == null) {
            Text(
                text = stringResource(R.string.diagnostics_chroot_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@KuroCard
        }
        val pass = stringResource(R.string.diagnostics_passed)
        val fail = stringResource(R.string.diagnostics_failed)
        DetailRow(
            stringResource(R.string.diagnostics_chroot_g1),
            (if (g.g1ok) pass else fail) + ": " + g.g1detail
        )
        DetailRow(
            stringResource(R.string.diagnostics_chroot_g2),
            (if (g.g2ok) pass else fail) + ": " + g.g2detail
        )
    }
}
