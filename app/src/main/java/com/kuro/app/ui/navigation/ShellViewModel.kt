package com.kuro.app.ui.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShellViewModel : ViewModel() {
    private val _currentTab = MutableStateFlow(BottomDestination.System)
    val currentTab: StateFlow<BottomDestination> = _currentTab.asStateFlow()

    // Step 3c: navigation direction for enter-only tab slide (+1 forward, -1 back).
    // Used by ShellScreen to slide new content from the navigation direction.
    private val _direction = MutableStateFlow(1)
    val direction: StateFlow<Int> = _direction.asStateFlow()

    private val seenTabs = mutableSetOf<BottomDestination>()

    private val listStates = mutableMapOf<BottomDestination, LazyListState>()

    fun select(tab: BottomDestination) {
        if (tab != _currentTab.value) {
            _direction.value = if (tab.ordinal > _currentTab.value.ordinal) 1 else -1
            _currentTab.value = tab
        }
    }

    fun listStateFor(tab: BottomDestination): LazyListState =
        listStates.getOrPut(tab) { LazyListState() }

    fun shouldAnimateList(tab: BottomDestination): Boolean =
        !seenTabs.contains(tab)

    fun markListSeen(tab: BottomDestination) {
        seenTabs.add(tab)
    }
}
