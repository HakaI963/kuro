@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kuro.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.view.TerminalView

/**
 * Step 9: Kuro terminal button bar (single 56dp row).
 *
 * Three weight-1f sections on #0A0E14: left [CTRL][ALT][ESC], center D-pad
 * single row [UP][LEFT][DOWN][RIGHT], right [TAB][ENT][BKSP]. Keys inject
 * through [TerminalKeyInjector] (the proven nativecode inject path);
 * modifiers reuse the shared lockable [TerminalModifierState]. Buttons are
 * circular #1B2534 / #EAF0F8 (#6366F1 when CTRL/ALT active). IME auto-opens
 * on TerminalView tap, so there is no keyboard-toggle button.
 */
@Composable
fun TerminalButtonBar(
    modState: TerminalModifierState,
    terminalView: () -> TerminalView?,
    modifier: Modifier = Modifier
) {
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF0A0E14))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: CTRL / ALT (toggle + long-press-lock) + ESC.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
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
        }
        // Center: D-pad single row.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KuroBarIconKey(
                icon = Icons.Default.KeyboardArrowUp,
                description = "Arrow up",
                onTap = { press("UP") },
                modifier = Modifier.weight(1f)
            )
            KuroBarIconKey(
                icon = Icons.Default.KeyboardArrowLeft,
                description = "Arrow left",
                onTap = { press("LEFT") },
                modifier = Modifier.weight(1f)
            )
            KuroBarIconKey(
                icon = Icons.Default.KeyboardArrowDown,
                description = "Arrow down",
                onTap = { press("DOWN") },
                modifier = Modifier.weight(1f)
            )
            KuroBarIconKey(
                icon = Icons.Default.KeyboardArrowRight,
                description = "Arrow right",
                onTap = { press("RIGHT") },
                modifier = Modifier.weight(1f)
            )
        }
        // Right: TAB / ENT / BKSP.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KuroBarKey(label = "TAB", onTap = { press("TAB") }, modifier = Modifier.weight(1f))
            KuroBarKey(label = "ENT", onTap = { press("ENTER") }, modifier = Modifier.weight(1f))
            KuroBarKey(label = "BKSP", onTap = { press("BKSP") }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Same-signature wrapper kept so existing callers (TerminalScreen) keep
 * calling `KuroTerminalBar` — delegates to [TerminalButtonBar].
 */
@Composable
fun KuroTerminalBar(
    modState: TerminalModifierState,
    terminalView: () -> TerminalView?,
    modifier: Modifier = Modifier
) {
    TerminalButtonBar(
        modState = modState,
        terminalView = terminalView,
        modifier = modifier
    )
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .heightIn(max = 40.dp)
            .clip(CircleShape)
            .background(
                if (active) Color(0xFF6366F1) else Color(0xFF1B2534)
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Color(0xFFEAF0F8)
        )
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun KuroBarIconKey(
    icon: ImageVector,
    description: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    locked: Boolean = false,
    onLongPress: (() -> Unit)? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .heightIn(max = 40.dp)
            .clip(CircleShape)
            .background(
                if (active) Color(0xFF6366F1) else Color(0xFF1B2534)
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) Color.White else Color(0xFFEAF0F8),
            modifier = Modifier.size(18.dp)
        )
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}
