package com.eugenehammer.mavlinkjoystikkmp.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MavlinkConsoleState(
    val log: String = "",
    val input: String = ""
)

class MavlinkConsoleViewModel(
    private val mavlinkManager: MavlinkManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MavlinkConsoleState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            mavlinkManager.consoleFlow.collectLatest { log ->
                _state.update { it.copy(log = log) }
            }
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun sendConsoleMessage() {
        val text = _state.value.input.trim()

        if (text.isEmpty()) return

        mavlinkManager.sendSerialControl(text)
        _state.update { it.copy(input = "") }
    }
}
