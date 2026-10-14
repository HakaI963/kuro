package com.kuro.app.engine.proot

/**
 * Step 5: every container-stage failure surfaces as one of these typed states.
 * Each carries a short title plus a copyable detail line for the UI, and is
 * also appended to filesDir/kuro-engine.log. Never a crash, never fake state.
 */
sealed interface InstallFailure {
    val title: String
    val detail: String

    data class Network(override val detail: String) : InstallFailure {
        override val title: String = "Download failed"
    }

    data class Checksum(override val detail: String) : InstallFailure {
        override val title: String = "Checksum mismatch"
    }

    data class Extract(override val detail: String) : InstallFailure {
        override val title: String = "Extract failed"
    }

    data class Exec(override val detail: String) : InstallFailure {
        override val title: String = "Could not start"
    }

    data class Permission(override val detail: String) : InstallFailure {
        override val title: String = "Permission missing"
    }

    data class Storage(override val detail: String) : InstallFailure {
        override val title: String = "Not enough space"
    }
}
