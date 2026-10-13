package com.kuro.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.ui.components.CloneCard
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroBottomSheet
import com.kuro.app.ui.components.KuroListItem
import com.kuro.app.ui.components.KuroSearchBar
import com.kuro.app.ui.components.RealAppIcon
import com.kuro.app.ui.components.SectionHeader
import com.kuro.app.ui.components.firstLoadFade

@Composable
fun LibraryScreen(
    onOpenClone: (String) -> Unit,
    onNewClone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LibraryViewModel = viewModel(),
    reduceMotion: Boolean = false,
    listState: LazyListState = LazyListState(),
    animateListIn: Boolean = false,
    onListShown: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val sheetFor by vm.showSheetFor.collectAsState()
    val sources by vm.sources.collectAsState(initial = emptyList())
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            snackbar.showSnackbar(state.message!!)
            vm.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { _ ->
        LaunchedEffect(animateListIn) {
            if (!animateListIn) onListShown()
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .firstLoadFade(animate = animateListIn, reduceMotion = reduceMotion, onShown = onListShown)
                .padding(horizontal = 20.dp),
            // Step 3c: between sections 24dp; list rows 12dp inside sections.
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // All tab headers align at the same y (16dp below app bar).
                Spacer(modifier = Modifier.height(16.dp))
                KuroSearchBar(value = state.query, onValueChange = vm::onQuery, hint = stringResource(R.string.library_search_hint))
            }
            // Clones section: title -> content 12dp.
            item {
                SectionHeader(title = stringResource(R.string.library_section_clones))
                Spacer(modifier = Modifier.height(12.dp))
                if (state.filtered.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_body),
                        actionLabel = stringResource(R.string.library_empty_action),
                        onAction = onNewClone,
                        icon = Icons.Filled.Inventory2
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.filtered.forEach { clone ->
                            CloneCard(
                                clone = clone,
                                onClick = { onOpenClone(clone.id) },
                                onLongClick = { vm.openSheet(clone.id) },
                                reduceMotion = reduceMotion
                            )
                        }
                    }
                }
            }
            // Sources section.
            item {
                SectionHeader(title = stringResource(R.string.library_section_sources))
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    sources.take(8).forEach { app ->
                        RowWithIcon(
                            title = app.label,
                            subtitle = app.packageName,
                            packageName = app.packageName,
                            fallbackLetter = app.iconLetter
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(90.dp)) }
        }
    }

    val target = state.filtered.firstOrNull { it.id == sheetFor } ?: state.clones.firstOrNull { it.id == sheetFor }
    if (target != null) {
        KuroBottomSheet(onDismiss = vm::closeSheet) {
            Text(text = stringResource(R.string.library_sheet_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                KuroListItem(title = stringResource(R.string.library_action_open), subtitle = target.name, onClick = {
                    vm.closeSheet(); onOpenClone(target.id)
                })
                KuroListItem(title = stringResource(R.string.library_action_details), subtitle = target.sourceAppName, onClick = {
                    vm.closeSheet(); onOpenClone(target.id)
                })
                KuroListItem(title = stringResource(R.string.library_action_delete), subtitle = target.name, onClick = {
                    vm.delete(target.id)
                })
            }
        }
    }
}

@Composable
private fun RowWithIcon(title: String, subtitle: String, packageName: String, fallbackLetter: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RealAppIcon(packageName = packageName, fallbackLetter = fallbackLetter)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
