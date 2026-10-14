package com.kuro.app.core.root

/**
 * On-device chroot SSOT paths + helper identity.
 * Shared by [com.kuro.app.core.terminal.ChrootCommandBuilder] and
 * [RootShell] without an import cycle (RootShell = su only, never imports
 * ChrootCommandBuilder — plan §2.5).
 */
object ChrootPaths {
    /** Device-side Kuro root for chroot containers, mounts and session scripts. */
    const val KURO_ROOT = "/data/local/kuro"

    /** Chroot containers live at `$CHROOT_BASE/<distro-id>/rootfs`. */
    const val CHROOT_BASE = "/data/local/kuro/chroot"

    /** Guest mount staging. */
    const val MOUNTS_DIR = "/data/local/kuro/mounts"

    /** Staged root helper scripts (uninstallers, task scripts). */
    const val SESSIONS_DIR = "/data/local/kuro/sessions"

    /** Idempotent shell snippet: create the Kuro on-device tree (run as root). */
    const val ENSURE_DIRS_CMD =
        "mkdir -p /data/local/kuro /data/local/kuro/chroot /data/local/kuro/mounts /data/local/kuro/sessions"

    /** Debian 13 chroot rootfs (historical default). */
    const val DEBIAN_CHROOT_PATH = "/data/local/kuro/chroot/debian13_chroot/rootfs"

    /** Alpine 3.24 chroot rootfs (coexists with Debian). */
    const val ALPINE_CHROOT_PATH = "/data/local/kuro/chroot/alpine_chroot/rootfs"

    const val FEDORA_CHROOT_PATH = "/data/local/kuro/chroot/fedora_chroot/rootfs"
    const val VOID_CHROOT_PATH = "/data/local/kuro/chroot/void_chroot/rootfs"
    const val OPENSUSE_CHROOT_PATH = "/data/local/kuro/chroot/opensuse_chroot/rootfs"

    const val DEEPIN_CHROOT_PATH = "/data/local/kuro/chroot/deepin_chroot/rootfs"
    const val CHIMERA_CHROOT_PATH = "/data/local/kuro/chroot/chimera_chroot/rootfs"
    const val MANJARO_CHROOT_PATH = "/data/local/kuro/chroot/manjaro_chroot/rootfs"

    const val UBUNTU_CHROOT_PATH = "/data/local/kuro/chroot/ubuntu_chroot/rootfs"
    const val KALI_CHROOT_PATH = "/data/local/kuro/chroot/kali_chroot/rootfs"
    const val PARROT_CHROOT_PATH = "/data/local/kuro/chroot/parrot_chroot/rootfs"
    const val ARCH_CHROOT_PATH = "/data/local/kuro/chroot/archlinux_chroot/rootfs"

    /**
     * Default chroot path for unscoped APIs (settings / legacy).
     * Prefer [DEBIAN_CHROOT_PATH] / [ALPINE_CHROOT_PATH] or profile.chrootPath.
     */
    const val CHROOT_PATH = DEBIAN_CHROOT_PATH

    /** On-device SSOT helper (assets/scripts/chroot/fluxlinux_chroot.sh). */
    const val CHROOT_HELPER = "/data/local/kuro/fluxlinux_chroot.sh"
    const val CHROOT_HELPER_ASSET = "scripts/chroot/fluxlinux_chroot.sh"
    /** Must match first `# fluxlinux-chroot vN` line in the asset. */
    const val CHROOT_HELPER_VERSION = "fluxlinux-chroot v2.9"

    /** Session executable — must be a system binary (SELinux blocks exec of app-data scripts). */
    const val SESSION_EXEC = "/system/bin/sh"

    fun pathForDistro(distroId: String): String = when (distroId) {
        "alpine_chroot" -> ALPINE_CHROOT_PATH
        "debian13_chroot", "debian_chroot" -> DEBIAN_CHROOT_PATH
        "fedora_chroot" -> FEDORA_CHROOT_PATH
        "void_chroot" -> VOID_CHROOT_PATH
        "opensuse_chroot" -> OPENSUSE_CHROOT_PATH
        "deepin_chroot" -> DEEPIN_CHROOT_PATH
        "chimera_chroot" -> CHIMERA_CHROOT_PATH
        "manjaro_chroot" -> MANJARO_CHROOT_PATH
        "ubuntu_chroot" -> UBUNTU_CHROOT_PATH
        "kali_chroot" -> KALI_CHROOT_PATH
        "parrot_chroot" -> PARROT_CHROOT_PATH
        "archlinux_chroot" -> ARCH_CHROOT_PATH
        else -> CHROOT_PATH
    }
}
