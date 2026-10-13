package com.kuro.engine.blackbox

import android.content.Context
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

object BlackBoxBootstrap {
    @Volatile var attachAttempted: Boolean = false
        private set
    @Volatile var attachError: String? = null
        private set

    fun attach(base: Context) {
        attachAttempted = true
        try {
            BlackBoxCore.get().closeCodeInit()
        } catch (_: Exception) {
        }
        try {
            BlackBoxCore.get().doAttachBaseContext(
                base,
                object : ClientConfiguration() {
                    override fun getHostPackageName(): String = base.packageName
                }
            )
        } catch (e: Throwable) {
            attachError = e.message ?: e.toString()
        }
    }

    fun create() {
        try {
            BlackBoxCore.get().doCreate()
        } catch (e: Throwable) {
            val msg = e.message ?: e.toString()
            attachError = if (attachError == null) msg else "${attachError}; $msg"
        }
    }
}
