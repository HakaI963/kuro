package com.kuro.app.ui.screens.diagnostics

data class SpaceRow(
    val userId: Int,
    val packages: List<String>
)

data class DiagnosticsData(
    val implClass: String,
    val commit: String,
    val nativeLoaded: Boolean,
    val spaces: List<SpaceRow>,
    val spaceDir: String,
    val spaceBytes: Long,
    val lastError: String?
)

data class DiagnosticsUiState(
    val loading: Boolean = true,
    val stub: Boolean = false,
    val data: DiagnosticsData? = null
)
