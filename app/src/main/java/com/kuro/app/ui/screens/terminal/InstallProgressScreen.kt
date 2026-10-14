package com.kuro.app.ui.screens.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.engine.proot.InstallFailure
import com.kuro.app.engine.proot.InstallSpec
import com.kuro.app.engine.proot.InstallStage
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.KuroSecondaryButton
import com.kuro.app.ui.components.ProgressPanel
import com.kuro.app.ui.components.ProgressStep

/**
 * Step 5: real staged install progress — Downloading -> Verifying ->
 * Extracting -> Bootstrapping -> Ready. All values measured, cancel cleans
 * the partial tarball, failures show typed retryable states with a copyable
 * detail line. No fake progress anywhere.
 */
@Composable
fun InstallProgressStep(
    spec: InstallSpec,
    installId: String,
    onOpenContainer: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: InstallViewModel = viewModel()
) {
    val stages by vm.stages.collectAsState()
    val stage = stages[installId] ?: InstallStage.Idle
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(stage) {
        if (stage is InstallStage.Ready) {
            vm.completeOnce(context, installId, spec)
        }
        if (stage is InstallStage.Failed || stage is InstallStage.Cancelled) {
            copied = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when (stage) {
            is InstallStage.Idle,
            is InstallStage.Downloading,
            is InstallStage.Verifying,
            is InstallStage.Extracting,
            is InstallStage.Bootstrapping -> {
                InstallActiveCard(stage = stage, spec = spec)
                Spacer(modifier = Modifier.height(16.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.install_cancel),
                    onClick = { vm.cancel(installId) }
                )
            }
            is InstallStage.Ready -> {
                KuroCard(
                    title = stringResource(R.string.install_ready_title),
                    subtitle = stringResource(R.string.install_ready_body)
                ) { }
                Spacer(modifier = Modifier.height(16.dp))
                KuroPrimaryButton(
                    label = stringResource(R.string.install_open_terminal),
                    onClick = { onOpenContainer(stage.containerId) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.install_back),
                    onClick = {
                        vm.forget(installId)
                        onDone()
                    }
                )
            }
            is InstallStage.Failed -> {
                InstallErrorCard(
                    failure = stage.failure,
                    copied = copied,
                    onCopy = {
                        clipboard.setText(AnnotatedString(stage.failure.detail))
                        copied = true
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                KuroPrimaryButton(
                    label = stringResource(R.string.install_retry),
                    onClick = { vm.retry(context, installId) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.install_back),
                    onClick = {
                        vm.forget(installId)
                        onDone()
                    }
                )
            }
            is InstallStage.Cancelled -> {
                KuroCard(title = stringResource(R.string.install_cancel)) { }
                Spacer(modifier = Modifier.height(16.dp))
                KuroPrimaryButton(
                    label = stringResource(R.string.install_retry),
                    onClick = { vm.retry(context, installId) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.install_back),
                    onClick = {
                        vm.forget(installId)
                        onDone()
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun InstallActiveCard(stage: InstallStage, spec: InstallSpec) {
    val distro = DistroCatalog.byId(spec.distroId)
    val downloading = stringResource(R.string.install_downloading)
    val verifying = stringResource(R.string.install_verifying)
    val extracting = stringResource(R.string.install_extracting)
    val bootstrapping = stringResource(R.string.install_bootstrapping)
    when (stage) {
        is InstallStage.Downloading -> {
            val total = stage.totalBytes ?: distro?.tarballBytes?.takeIf { it > 0 }
            val frac = if (total != null && total > 0) {
                (stage.bytes.toFloat() / total).coerceIn(0f, 1f)
            } else null
            val speed = if (stage.bytesPerSec > 0) " • ${formatBytes(stage.bytesPerSec)}/s" else ""
            val eta = stage.etaSec?.let { " • ${it}s left" } ?: ""
            ProgressPanel(
                title = downloading,
                subtitle = "${formatBytes(stage.bytes)}" +
                    (if (total != null) " / ${formatBytes(total)}" else "") + speed + eta,
                progress = frac ?: 0f,
                determinate = frac != null,
                steps = installSteps(downloading, verifying, extracting, bootstrapping, active = 0)
            )
            if (stage.usedFallback) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.install_mirror_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is InstallStage.Verifying -> {
            val frac = if (stage.totalBytes > 0) {
                (stage.bytes.toFloat() / stage.totalBytes).coerceIn(0f, 1f)
            } else null
            ProgressPanel(
                title = verifying,
                subtitle = "${formatBytes(stage.bytes)} / ${formatBytes(stage.totalBytes)}",
                progress = frac ?: 0f,
                determinate = frac != null,
                steps = installSteps(downloading, verifying, extracting, bootstrapping, active = 1)
            )
        }
        is InstallStage.Extracting -> {
            ProgressPanel(
                title = extracting,
                subtitle = "${formatBytes(stage.bytesOut)} • ${stage.files} files",
                progress = 0f,
                determinate = false,
                steps = installSteps(downloading, verifying, extracting, bootstrapping, active = 2)
            )
        }
        else -> {
            ProgressPanel(
                title = bootstrapping,
                subtitle = spec.containerName,
                progress = 0f,
                determinate = false,
                steps = installSteps(downloading, verifying, extracting, bootstrapping, active = 3)
            )
        }
    }
}

private fun installSteps(
    downloading: String,
    verifying: String,
    extracting: String,
    bootstrapping: String,
    active: Int
): List<ProgressStep> {
    val labels = listOf(downloading, verifying, extracting, bootstrapping)
    return labels.mapIndexed { i, label ->
        ProgressStep(label, done = active > i, active = active == i)
    }
}

@Composable
private fun InstallErrorCard(failure: InstallFailure, copied: Boolean, onCopy: () -> Unit) {
    KuroCard(title = failure.title) {
        Text(
            text = failure.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCopy) {
                Text(
                    text = if (copied) stringResource(R.string.install_copied)
                    else stringResource(R.string.install_copy_detail)
                )
            }
        }
    }
}
