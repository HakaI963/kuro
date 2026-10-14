package com.kuro.app.ui.screens

import androidx.compose.runtime.Composable
import com.kuro.app.core.chroot.ChrootInfoStore

/**
 * Legacy wrapper for ChrootSettingsScreen.
 * Delegates to parameterized [ChrootStorageDetailScreen].
 */
@Composable
fun ChrootSettingsScreen(
    onBack: () -> Unit,
    onNavigateToInstall: (() -> Unit)? = null
) {
    ChrootStorageDetailScreen(
        distroId = ChrootInfoStore.DEFAULT_DEBIAN_ID,
        onBack = onBack,
        onNavigateToInstall = onNavigateToInstall
    )
}
