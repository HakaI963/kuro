package com.kuro.app.ui.screens.clones

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.ui.screens.library.LibraryScreen
import com.kuro.app.ui.screens.library.LibraryViewModel

/**
 * Step 4: the Stage 1 clone manager moved intact into the Clones tab.
 * Behavior is unchanged — this is a thin host over LibraryScreen, so the
 * list, new-clone flow and clone detail all work exactly as before.
 */
@Composable
fun ClonesScreen(
    onOpenClone: (String) -> Unit,
    onNewClone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LibraryViewModel = viewModel(),
    reduceMotion: Boolean = false,
    listState: LazyListState = LazyListState(),
    animateListIn: Boolean = false,
    onListShown: () -> Unit = {}
) {
    LibraryScreen(
        onOpenClone = onOpenClone,
        onNewClone = onNewClone,
        modifier = modifier,
        vm = vm,
        reduceMotion = reduceMotion,
        listState = listState,
        animateListIn = animateListIn,
        onListShown = onListShown
    )
}
