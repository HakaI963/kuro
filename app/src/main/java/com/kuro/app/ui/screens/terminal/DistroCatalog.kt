package com.kuro.app.ui.screens.terminal

/**
 * Step 5 catalog: the ONLY place distro facts live — display info AND the
 * real rootfs tarballs the installer downloads. Versions/sizes below were
 * verified 2026-10-14 against the upstream sources (os-release inside each
 * tarball, release asset byte counts; Alpine primary re-checked via HEAD
 * 2026-10-14: Content-Length 3439136 matches).
 *
 * Primary source: termux/proot-distro release rootfs tarballs (aarch64,
 * proot-ready). Kali is not published by proot-distro: fallback is the
 * official Kali NetHunter minimal arm64 rootfs (pinned dated release with
 * upstream SHA256SUMS). Refresh policy: bump versions + URLs when upstream
 * rotates; a rotated Kali checksum surfaces as an honest checksum error
 * (never silent).
 *
 * Step 5d: every entry MAY carry an official-vendor fallback URL used ONLY
 * when the primary fails twice with HTTP 4xx/5xx (see DownloadPolicy +
 * ContainerProvisioner). Fallback sizes were HEAD-verified 2026-10-14;
 * fallbackBytes = 0 means "unknown, resolve via HEAD at runtime".
 * Debian has no fallback: no stable official direct rootfs tarball exists
 * (Debian ships installer/cloud images, not rootfs tarballs; the
 * debuerreotype project keeps only metadata in git, images go to Docker
 * Hub which needs registry auth). Debian is primary-only by design.
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
    val sha256: String? = null,
    val fallbackUrl: String? = null,
    val fallbackBytes: Long = 0L,
    val fallbackSha256: String? = null
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
            sha256 = null, // proot-distro publishes no checksums; size-verified
            // Official Fedora Container Base (Minimal) aarch64 OCI image.
            // download.fedoraproject.org 302-redirects to a mirror; the
            // extractor materializes the OCI layer (see RootfsInstaller).
            // HEAD-verified 2026-10-14: 50769636 B. No published whole-file
            // checksum → size-verified only.
            fallbackUrl = "https://download.fedoraproject.org/pub/fedora/linux/releases/" +
                "43/Container/aarch64/images/" +
                "Fedora-Container-Base-Generic-Minimal-43-1.6.aarch64.oci.tar.xz",
            fallbackBytes = 50769636L,
            fallbackSha256 = null
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
            sha256 = "d6403a5da175df325611d23af4b92330856059c45454eced7f4cdf3ca6df2e4e",
            // Same official file via the rolling `current` symlink: helps if
            // the dated path ever 404s. HEAD-verified same 137313840 B.
            fallbackUrl = "https://kali.download/nethunter-images/current/rootfs/" +
                "kali-nethunter-rootfs-minimal-arm64.tar.xz",
            fallbackBytes = 137313840L,
            fallbackSha256 = "d6403a5da175df325611d23af4b92330856059c45454eced7f4cdf3ca6df2e4e"
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
            sha256 = null, // proot-distro publishes no checksums; size-verified
            // Official ubuntu-base minimal rootfs (gzip tarball — the
            // extractor sniffs gzip vs xz). HEAD-verified 2026-10-14:
            // 29936675 B; SHA256SUMS-pinned (rotates with point releases).
            fallbackUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/" +
                "release/ubuntu-base-24.04.5-base-arm64.tar.gz",
            fallbackBytes = 29936675L,
            fallbackSha256 = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"
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
            // No fallbackUrl by design (see class doc): no stable official
            // direct rootfs tarball exists for Debian trixie arm64.
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
            sha256 = null, // proot-distro publishes no checksums; size-verified
            // Official Alpine minirootfs (gzip tarball). HEAD-verified
            // 2026-10-14: 3969729 B; .sha256-pinned (rotates with respins).
            fallbackUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/" +
                "aarch64/alpine-minirootfs-3.22.6-aarch64.tar.gz",
            fallbackBytes = 3969729L,
            fallbackSha256 = "821565fa8f3953eefd12497b166b4b50add2f7c57fb312e75862f5867e06fefe"
        )
    )

    fun byId(id: String): DistroEntry? = entries.firstOrNull { it.id == id }

    fun displayNameOf(id: String): String = byId(id)?.name ?: id
}

/** Step 5d: per-URL expectations for the active download source. */
fun DistroEntry.expectedBytesFor(url: String): Long =
    if (fallbackUrl != null && url == fallbackUrl) fallbackBytes else tarballBytes

/** Step 5d: per-URL checksum for the active download source (null = size-only). */
fun DistroEntry.sha256For(url: String): String? =
    if (fallbackUrl != null && url == fallbackUrl) fallbackSha256 else sha256
