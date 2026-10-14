package com.ivarna.fluxlinux.core.data

import android.content.Context

/**
 * Step 6: per-distro UI config chosen in the install wizard — container
 * display name, Kuro Bridge toggle, interface mode. Persisted in
 * SharedPreferences; the `kuro` CLI itself lands later, but the toggle and
 * config exist now and are honored by the UI that reads them.
 */
object ContainerUiConfig {
    private const val PREFS = "kuro_container_cfg"

    /** Interface modes offered in the wizard. */
    const val TUI = "TUI"
    const val XFCE4 = "XFCE4"
    const val KDE = "KDE"
    val MODES = listOf(TUI, XFCE4, KDE)

    data class Config(
        val containerName: String,
        val bridgeEnabled: Boolean,
        val interfaceMode: String
    )

    fun defaultName(distroId: String): String = distroId.substringBefore("_chroot")

    fun placeholder(distroId: String): String = "e.g. ${defaultName(distroId)}-mobile"

    fun load(context: Context, distroId: String): Config {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            containerName = prefs.getString(key(distroId, "name"), null)
                ?.takeIf { it.isNotBlank() } ?: defaultName(distroId),
            bridgeEnabled = prefs.getBoolean(key(distroId, "bridge"), true),
            interfaceMode = prefs.getString(key(distroId, "iface"), TUI)
                ?.takeIf { it in MODES } ?: TUI
        )
    }

    fun save(context: Context, distroId: String, config: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(distroId, "name"), config.containerName)
            .putBoolean(key(distroId, "bridge"), config.bridgeEnabled)
            .putString(key(distroId, "iface"), config.interfaceMode)
            .apply()
    }

    private fun key(distroId: String, field: String) = "$distroId.$field"
}
