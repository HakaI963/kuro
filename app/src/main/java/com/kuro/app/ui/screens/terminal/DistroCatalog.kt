package com.kuro.app.ui.screens.terminal

/**
 * Step 5 catalog: the ONLY place distro facts live — display info AND the
 * real rootfs tarballs the installer downloads. Versions/sizes below were
 * verified 2026-10-14 against the upstream sources (os-release inside each
 * tarball, release asset byte counts).
 *
 * Primary source: termux/proot-distro release rootfs tarballs (aarch64,
 * proot-ready). Kali is not published by proot-distro: fallback is the
 * official Kali NetHunter minimal arm64 rootfs (pinned dated release with
 * upstream SHA256SUMS). Refresh policy: bump versions + URLs when upstream
 * rotates; a rotated Kali checksum surfaces as an honest checksum error
 * (never silent).
 */
data class DistroEntry(
    val id: String,
    val name: String,
    val version: String,
    val approxDownloadSize: String,
    val arch: String = "aarch64",
    val sourceUrl: String,
    val tarballUrl: String,
    val tarballBytes: Long,
    val sha256: String? = null
)

object DistroCatalog {
    val entries: List<DistroEntry> = listOf(
        DistroEntry(
            id = "fedora",
            name = "Fedora",
            version = "43",
            approxDownloadSize = "~37 MB rootfs",
            sourceUrl = "https://download.fedoraproject.org",
            tarballUrl = "https://github.com/termux/proot-distro/releases/download/" +
                "v4.31.0/fedora-aarch64-pd-v4.31.0.tar.xz",
            tarballBytes = 38525452L,
            sha256 = null // proot-distro publishes no checksums; size-verified
        ),
        DistroEntry(
            id = "kali",
            name = "Kali Linux",
            version = "2026.2",
            approxDownloadSize = "~131 MB rootfs",
            sourceUrl = "https://www.kali.org/get-kali/",
            tarballUrl = "https://kali.download/nethunter-images/kali-2026.2/rootfs/" +
                "kali-nethunter-rootfs-minimal-arm64.tar.xz",
            tarballBytes = 137313840L,
            sha256 = "d6403a5da175df325611d23af4b92330856059c45454eced7f4cdf3ca6df2e4e"
        ),
        DistroEntry(
            id = "ubuntu",
            name = "Ubuntu",
            version = "24.04 LTS",
            approxDownloadSize = "~62 MB rootfs",
            sourceUrl = "https://releases.ubuntu.com",
            tarballUrl = "https://github.com/termux/proot-distro/releases/download/" +
                "v4.18.0/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz",
            tarballBytes = 64133552L,
            sha256 = null // proot-distro publishes no checksums; size-verified
        ),
        DistroEntry(
            id = "debian",
            name = "Debian",
            version = "13 (trixie)",
            approxDownloadSize = "~34 MB rootfs",
            sourceUrl = "https://www.debian.org/distrib/",
            tarballUrl = "https://github.com/termux/proot-distro/releases/download/" +
                "v4.29.0/debian-trixie-aarch64-pd-v4.29.0.tar.xz",
            tarballBytes = 35409704L,
            sha256 = null // proot-distro publishes no checksums; size-verified
        ),
        DistroEntry(
            id = "alpine",
            name = "Alpine",
            version = "3.22",
            approxDownloadSize = "~3.4 MB rootfs",
            sourceUrl = "https://alpinelinux.org/downloads/",
            tarballUrl = "https://github.com/termux/proot-distro/releases/download/" +
                "v4.30.1/alpine-aarch64-pd-v4.30.1.tar.xz",
            tarballBytes = 3439136L,
            sha256 = null // proot-distro publishes no checksums; size-verified
        )
    )

    fun byId(id: String): DistroEntry? = entries.firstOrNull { it.id == id }

    fun displayNameOf(id: String): String = byId(id)?.name ?: id
}
