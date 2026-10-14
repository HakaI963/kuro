package com.kuro.app.ui.screens.terminal

import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kuro.app.engine.proot.ContainerEngineLog
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

/**
 * Step 5: embeds the vendored Termux TerminalView.
 * Attach/detach semantics: attaching an already-running session re-adopts its
 * live emulator (updateSize path); disposing this View on back navigation
 * only drops the renderer — the pty session keeps running headless.
 */
@Composable
fun KuroTerminalView(
    session: TerminalSession,
    filesDir: File,
    onAttached: () -> Unit,
    modifier: Modifier = Modifier
) {
    val holder = remember { ViewHolder() }
    val client = remember {
        KuroTerminalViewClient(
            filesDir = filesDir,
            viewOf = { holder.view }
        )
    }

    AndroidView(
        factory = { ctx ->
            TerminalView(ctx, null).also { view ->
                view.setTerminalViewClient(client)
                view.setTextSize(13)
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                if (view.attachSession(session)) onAttached()
                holder.view = view
            }
        },
        update = { view ->
            holder.view = view
            if (view.attachSession(session)) onAttached()
        },
        modifier = modifier
    )

    DisposableEffect(session) {
        onDispose {
            // Detach only: the session (pty + process) keeps running.
            holder.view = null
        }
    }
}

private class ViewHolder {
    var view: TerminalView? = null
}

private class KuroTerminalViewClient(
    private val filesDir: File,
    private val viewOf: () -> TerminalView?
) : TerminalViewClient {

    override fun onScale(scale: Float): Float {
        // Pinch zoom deferred (Step 9 polish): keep type size fixed.
        return 1f
    }

    override fun onSingleTapUp(e: MotionEvent) {
        // Real behavior: tap focuses the view and shows the soft keyboard.
        val view = viewOf() ?: return
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, 0)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        var toWrite = codePoint
        if (ctrlDown) {
            // Mirror Termux behavior: Ctrl+letter -> control character.
            if (toWrite in 'a'.code..'z'.code) toWrite -= 'a'.code - 1
            else if (toWrite in 'A'.code..'Z'.code) toWrite -= 'A'.code - 1
        }
        session.writeCodePoint(false, toWrite)
        return true
    }

    override fun onEmulatorSet() {
        viewOf()?.onScreenUpdated()
    }

    override fun logError(tag: String, message: String) {
        ContainerEngineLog.append(filesDir, "termview", "E/$tag: $message")
    }

    override fun logWarn(tag: String, message: String) {
        ContainerEngineLog.append(filesDir, "termview", "W/$tag: $message")
    }

    override fun logInfo(tag: String, message: String) = Unit
    override fun logDebug(tag: String, message: String) = Unit
    override fun logVerbose(tag: String, message: String) = Unit

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        ContainerEngineLog.append(filesDir, "termview", "E/$tag: $message ${e.message}")
    }

    override fun logStackTrace(tag: String, e: Exception) {
        ContainerEngineLog.append(filesDir, "termview", "E/$tag: ${e.message}")
    }
}
