package com.kuro.app.core.install

/**
 * Host `bootstrap.tar` pins for the GitHub release tag `rootfs`.
 *
 * The app ships `assets/bootstrap.tar` in the APK; this URL is only a
 * repair fallback used when the asset is missing.
 *
 * Filename is stable per applicationId. A SHA change requires a **new**
 * filename (same D9 rule as distro rootfs) so old APKs keep working.
 */
object HostBootstrap {

    const val RELEASE_BASE =
        "https://github.com/abhay-byte/fluxlinux/releases/download/rootfs"

    val KURO = PinnedReleaseArchive(
        fileName = "bootstrap_com.kuro.app.tar",
        sha256 = "5b16c6597d38380c0cab9471d3cf69a0c7a23d9a4191125bd9dd6ddc77277f5c",
        url = "$RELEASE_BASE/bootstrap_com.kuro.app.tar",
        minBytes = 50L * 1024L * 1024L
    )

    fun forApplicationId(applicationId: String): PinnedReleaseArchive = KURO

    /**
     * True when this flavor does **not** package `assets/bootstrap.tar`.
     * Both flavors now ship it (F-Droid builds it from source); the release
     * URL is only a repair fallback when the asset is missing.
     */
    @Suppress("UNUSED_PARAMETER")
    fun downloadsFromRelease(applicationId: String): Boolean = false
}
