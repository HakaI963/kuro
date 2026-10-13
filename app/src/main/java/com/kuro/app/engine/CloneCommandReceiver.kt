package com.kuro.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CloneCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val parsed = CrossProfileBridge.parse(intent) ?: return
        val (command, packageName) = parsed
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val engine = WorkProfileCloneEngine(context.applicationContext)
                when (command) {
                    CrossProfileBridge.Command.OPEN -> engine.open(packageName)
                    CrossProfileBridge.Command.FREEZE -> engine.freeze(packageName)
                    CrossProfileBridge.Command.UNFREEZE -> engine.unfreeze(packageName)
                    CrossProfileBridge.Command.HIDE -> engine.hide(packageName)
                    CrossProfileBridge.Command.UNHIDE -> engine.unhide(packageName)
                    CrossProfileBridge.Command.REMOVE -> engine.remove(packageName)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
