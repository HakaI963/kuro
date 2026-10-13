package com.kuro.app.engine

import android.content.Context
import android.content.Intent

/**
 * Minimal strongly-typed cross-profile bridge.
 *
 * Parent (personal) UI never calls DevicePolicyManager as profile owner directly.
 * When Kuro is profile owner inside the managed profile, commands are forwarded
 * to the profile instance via an explicit intent handled there. Until provisioning
 * completes, callers receive [EngineResult.Unavailable] instead of crashing.
 */
object CrossProfileBridge {

    const val ACTION_CLONE_COMMAND = "com.kuro.app.action.CLONE_COMMAND"
    const val EXTRA_COMMAND = "extra_command"
    const val EXTRA_PACKAGE = "extra_package"

    enum class Command { OPEN, FREEZE, UNFREEZE, HIDE, UNHIDE, REMOVE }

    fun buildCommandIntent(context: Context, command: Command, packageName: String): Intent {
        return Intent(ACTION_CLONE_COMMAND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_COMMAND, command.name)
            putExtra(EXTRA_PACKAGE, packageName)
        }
    }

    fun parse(intent: Intent?): Pair<Command, String>? {
        if (intent == null) return null
        if (intent.action != ACTION_CLONE_COMMAND) return null
        val cmd = runCatching { Command.valueOf(intent.getStringExtra(EXTRA_COMMAND) ?: "") }.getOrNull() ?: return null
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
        return cmd to pkg
    }
}
