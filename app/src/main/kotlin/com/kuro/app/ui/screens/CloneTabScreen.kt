package com.kuro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kuro.app.ui.screens.addclone.AddCloneScreen
import com.kuro.app.ui.screens.details.CloneDetailsScreen
import com.kuro.app.ui.screens.library.LibraryScreen

/**
 * Step 6: Kuro Clones tab — the working BlackBox clone stack (list, new
 * clone from installed apps or APK via SAF, detail with launch/delete),
 * hosted with local list/add/detail navigation. Opaque root.
 */
@Composable
fun CloneTabScreen(modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<CloneRoute>(CloneRoute.List) }
    when (val r = route) {
        is CloneRoute.List -> LibraryScreen(
            onOpenClone = { route = CloneRoute.Detail(it) },
            onNewClone = { route = CloneRoute.Add },
            modifier = modifier.background(MaterialTheme.colorScheme.background)
        )
        is CloneRoute.Add -> AddCloneScreen(
            onDone = { route = CloneRoute.List },
            onOpenClone = { route = CloneRoute.Detail(it) },
            modifier = modifier.background(MaterialTheme.colorScheme.background)
        )
        is CloneRoute.Detail -> CloneDetailsScreen(
            cloneId = r.cloneId,
            onBack = { route = CloneRoute.List },
            onDeleted = { route = CloneRoute.List },
            modifier = modifier.background(MaterialTheme.colorScheme.background)
        )
    }
}

private sealed interface CloneRoute {
    data object List : CloneRoute
    data object Add : CloneRoute
    data class Detail(val cloneId: String) : CloneRoute
}
