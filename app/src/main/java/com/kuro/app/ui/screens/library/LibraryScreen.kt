package com.kuro.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.ui.components.CloneCard
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroBottomSheet
import com.kuro.app.ui.components.KuroFilterChip
import com.kuro.app.ui.components.KuroListItem
import com.kuro.app.ui.components.KuroSearchBar
import com.kuro.app.ui.components.LoadingSkeleton
import com.kuro.app.ui.components.SectionHeader

@Composable
fun LibraryScreen(
    onOpenClone: (String) -> Unit,
    onNewClone: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LibraryViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val sheetFor by vm.showSheetFor.collectAsState()
    val sources by vm.sources.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            KuroSearchBar(value = state.query, onValueChange = vm::onQuery, hint = stringResource(R.string.library_search_hint))
        }
        item {
            LazyRow(modifier = Modifier.fillMaxWidth()) {
                item {
                    KuroFilterChip(
                        label = stringResource(R.string.library_filter_all),
                        selected = state.filter == LibraryFilter.ALL,
                        onClick = { vm.onFilter(LibraryFilter.ALL) }
                    )
                }
                item {
                    KuroFilterChip(
                        label = stringResource(R.string.library_filter_running),
                        selected = state.filter == LibraryFilter.RUNNING,
                        onClick = { vm.onFilter(LibraryFilter.RUNNING) }
                    )
                }
                item {
                    KuroFilterChip(
                        label = stringResource(R.string.library_filter_frozen),
                        selected = state.filter == LibraryFilter.FROZEN,
                        onClick = { vm.onFilter(LibraryFilter.FROZEN) }
                    )
                }
                item {
                    KuroFilterChip(
                        label = stringResource(R.string.library_filter_private),
                        selected = state.filter == LibraryFilter.PRIVATE,
                        onClick = { vm.onFilter(LibraryFilter.PRIVATE) }
                    )
                }
            }
        }
        if (state.isLoading) {
            item { LoadingSkeleton(lines = 4) }
        } else {
            item { SectionHeader(title = stringResource(R.string.library_section_clones)) }
            if (state.filtered.isEmpty()) {
                item {
                    EmptyStateView(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_body),
                        actionLabel = stringResource(R.string.library_empty_action),
                        onAction = onNewClone,
                        icon = Icons.Filled.Inventory2
                    )
                }
            } else {
                items(state.filtered, key = { it.id }) { clone ->
                    CloneCard(
                        clone = clone,
                        onClick = { onOpenClone(clone.id) },
                        onLongClick = { vm.openSheet(clone.id) }
                    )
                }
            }
            item {
                SectionHeader(title = stringResource(R.string.library_section_sources))
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(sources.take(4), key = { it.id }) { app ->
                KuroListItem(
                    title = app.name,
                    subtitle = app.packageName,
                    leadingLetter = app.iconLetter,
                    leadingColor = Color(app.accentArgb)
                )
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
                KuroListItem(title = stringResource(R.string.library_action_freeze), subtitle = target.status.name, onClick = {
                    vm.toggleFreeze(target.id)
                })
                KuroListItem(title = stringResource(R.string.library_action_delete), subtitle = target.name, onClick = {
                    vm.delete(target.id)
                })
            }
        }
    }
}
