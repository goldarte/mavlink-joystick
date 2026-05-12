package com.eugenehammer.mavlinkjoystikkmp.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsScreenState(
            selectedTab = SettingsScreenState.SettingsTab.Connection
        )
    )
    val state = _state.asStateFlow()

    fun onTabSelected(tab: SettingsScreenState.SettingsTab) {
        _state.update { it.copy(selectedTab = tab) }
    }
}