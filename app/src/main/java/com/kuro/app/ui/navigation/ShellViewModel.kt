package com.kuro.app.ui.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShellViewModel : ViewModel() {
    private val _currentTab = MutableStateFlow(BottomDestination.Home)
    val currentTab: StateFlow<BottomDestination> = _currentTab.asStateFlow()

    private val seenTabs = mutableSetOf<BottomDestination>()

    private val listStates = mutableMapOf<BottomDestination, LazyListState>()

    fun select(tab: BottomDestination) {
        _currentTab.value = tab
    }

    fun listStateFor(tab: BottomDestination): LazyListState =
        listStates.getOrPut(tab) { LazyListState() }

    fun shouldAnimateList(tab: BottomDestination): Boolean =
        !seenTabs.contains(tab)

    fun markListSeen(tab: BottomDestination) {
        seenTabs.add(tab)
    }
}
