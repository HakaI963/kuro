package com.kuro.app.ui.terminal

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.view.TerminalView

/**
 * Step 7: Kuro terminal button bar (replaces the old multi-row toolbar).
 *
 * Single 56dp row on `surface`: left modifiers [CTRL][ALT][ESC][TAB],
 * center D-pad (Up above Left/Down/Right), right [ENT][BKSP][keyboard].
 * Keys inject through [TerminalKeyInjector] (the proven nativecode inject
 * path); modifiers reuse the shared lockable [TerminalModifierState].
 * Buttons use tertiaryContainer / onTertiaryContainer.
 */
@Composable
fun KuroTerminalBar(
    modState: TerminalModifierState,
    terminalView: () -> TerminalView?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun press(key: String) {
        TerminalKeyInjector.injectKey(
            terminalView(),
            key,
            modState.ctrlActive,
            modState.altActive,
            modState.shiftActive
        )
        modState.consumeModifiers()
    }

    fun toggleKeyboard() {
        val tv = terminalView()
        if (tv == null) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (tv.isFocused) {
            hideIme(tv)
        } else {
            tv.requestFocus()
            imm?.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: modifiers + ESC/TAB.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KuroBarKey(
                label = "CTRL",
                active = modState.ctrlActive,
                locked = modState.ctrlLocked,
                onTap = { modState.toggleCtrl() },
                onLongPress = { modState.lockCtrl() },
                modifier = Modifier.weight(1f)
            )
            KuroBarKey(
                label = "ALT",
                active = modState.altActive,
                locked = modState.altLocked,
                onTap = { modState.toggleAlt() },
                onLongPress = { modState.lockAlt() },
                modifier = Modifier.weight(1f)
            )
            KuroBarKey(label = "ESC", onTap = { press("ESC") }, modifier = Modifier.weight(1f))
            KuroBarKey(label = "TAB", onTap = { press("TAB") }, modifier = Modifier.weight(1f))
        }
        // Center: D-pad.
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            KuroBarIconKey(
                icon = Icons.Default.KeyboardArrowUp,
                description = "Arrow up",
                onTap = { press("UP") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KuroBarIconKey(
                    icon = Icons.Default.KeyboardArrowLeft,
                    description = "Arrow left",
                    onTap = { press("LEFT") }
                )
                KuroBarIconKey(
                    icon = Icons.Default.KeyboardArrowDown,
                    description = "Arrow down",
                    onTap = { press("DOWN") }
                )
                KuroBarIconKey(
                    icon = Icons.Default.KeyboardArrowRight,
                    description = "Arrow right",
                    onTap = { press("RIGHT") }
                )
            }
        }
        // Right: enter / backspace / keyboard.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KuroBarKey(label = "ENT", onTap = { press("ENTER") }, modifier = Modifier.weight(1f))
            KuroBarKey(label = "BKSP", onTap = { press("BKSP") }, modifier = Modifier.weight(1f))
            KuroBarIconKey(
                icon = Icons.Default.Keyboard,
                description = "Toggle keyboard",
                onTap = { toggleKeyboard() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KuroBarKey(
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    locked: Boolean = false,
    onLongPress: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) colors.tertiary else colors.tertiaryContainer
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) colors.onTertiary else colors.onTertiaryContainer
        )
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .background(colors.onTertiary, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
private fun KuroBarIconKey(
    icon: ImageVector,
    description: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.tertiaryContainer)
            .combinedClickable(onClick = onTap)
            .padding(horizontal = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.onTertiaryContainer,
            modifier = Modifier.size(16.dp)
        )
    }
}
