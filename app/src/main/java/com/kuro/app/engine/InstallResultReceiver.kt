package com.kuro.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.util.concurrent.ConcurrentHashMap

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val ok = status == PackageInstaller.STATUS_SUCCESS
        pending.remove(sessionId)?.invoke(ok)
    }

    companion object {
        const val ACTION_RESULT = "com.kuro.app.INSTALL_RESULT"
        const val EXTRA_PACKAGE = "extra_package"
        private val pending = ConcurrentHashMap<Int, (Boolean) -> Unit>()

        fun await(sessionId: Int, cb: (Boolean) -> Unit) {
            pending[sessionId] = cb
        }
    }
}
