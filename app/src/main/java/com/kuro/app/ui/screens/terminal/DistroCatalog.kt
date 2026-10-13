package com.kuro.app.ui.screens.terminal

/**
 * Step 4 distro catalog: the ONLY place distro facts live.
 * Source URLs are real project download pages; exact per-version image URLs
 * are finalized in Step 5 when the container engine downloads them.
 */
data class DistroEntry(
    val id: String,
    val name: String,
    val version: String,
    val approxDownloadSize: String,
    val arch: String = "aarch64",
    val sourceUrl: String
)

object DistroCatalog {
    val entries: List<DistroEntry> = listOf(
        DistroEntry(
            id = "fedora",
            name = "Fedora",
            version = "42",
            approxDownloadSize = "~180 MB rootfs",
            sourceUrl = "https://download.fedoraproject.org"
        ),
        DistroEntry(
            id = "kali",
            name = "Kali Linux",
            version = "2025.2",
            approxDownloadSize = "~220 MB rootfs",
            sourceUrl = "https://www.kali.org/get-kali/"
        ),
        DistroEntry(
            id = "ubuntu",
            name = "Ubuntu",
            version = "24.04 LTS",
            approxDownloadSize = "~150 MB rootfs",
            sourceUrl = "https://releases.ubuntu.com"
        ),
        DistroEntry(
            id = "debian",
            name = "Debian",
            version = "13 (trixie)",
            approxDownloadSize = "~140 MB rootfs",
            sourceUrl = "https://www.debian.org/distrib/"
        ),
        DistroEntry(
            id = "alpine",
            name = "Alpine",
            version = "3.21",
            approxDownloadSize = "~12 MB rootfs",
            sourceUrl = "https://alpinelinux.org/downloads/"
        )
    )

    fun byId(id: String): DistroEntry? = entries.firstOrNull { it.id == id }
}
