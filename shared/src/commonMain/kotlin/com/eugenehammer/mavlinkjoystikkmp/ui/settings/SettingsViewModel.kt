package com.eugenehammer.mavlinkjoystikkmp.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsScreenState(
            selectedTab = SettingsScreenState.SettingsTab.Connection,
            connectionSettingsState = SettingsScreenState.ConnectionSettingsState(
                autoDetect = true,
                host = "",
                port = "",
                listenPort = "",
                droneSystemId = "1",
                droneComponentId = "1"
            ),
            stickSizeState = SettingsScreenState.StickSizeState(
                leftFactor = 0.65f,
                rightFactor = 0.65f
            ),
            stickAppearanceState = SettingsScreenState.StickAppearanceState(
                showCircularArea = true,
                showSquareArea = true,
                showCircleBoundaries = true,
                knobColor = Color.Cyan
            )
        )
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            appSettings.state.collectLatest { settingsState ->
                _state.update {
                    it.copy(
                        connectionSettingsState = SettingsScreenState.ConnectionSettingsState(
                            autoDetect = settingsState.autoDetect,
                            host = settingsState.host,
                            port = settingsState.port.toString(),
                            listenPort = settingsState.listenPort.toString(),
                            droneSystemId = settingsState.droneSystemId.toString(),
                            droneComponentId = settingsState.droneComponentId.toString()
                        ),
                        stickSizeState = SettingsScreenState.StickSizeState(
                            leftFactor = settingsState.leftStickSizeFactor,
                            rightFactor = settingsState.rightStickSizeFactor
                        ),
                        stickAppearanceState = SettingsScreenState.StickAppearanceState(
                            showCircularArea = settingsState.showCircularArea,
                            showSquareArea = settingsState.showSquareArea,
                            showCircleBoundaries = settingsState.showCircleBoundaries,
                            knobColor = Color(settingsState.knobColor)
                        )
                    )
                }
            }
        }
    }

    fun onTabSelected(tab: SettingsScreenState.SettingsTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onAutodetectCheckboxClicked() {
        _state.update { it.copy(connectionSettingsState = it.connectionSettingsState.copy(autoDetect = !_state.value.connectionSettingsState.autoDetect)) }
    }

    fun onListenPortChanged(value: String) {
        _state.update { it.copy(connectionSettingsState = it.connectionSettingsState.copy(listenPort = value)) }
    }

    fun onHostChanged(value: String) {
        _state.update { state -> state.copy(connectionSettingsState = state.connectionSettingsState.copy(host = value.filter { it.isDigit() })) }
    }

    fun onTargetPortChanged(value: String) {
        _state.update { state -> state.copy(connectionSettingsState = state.connectionSettingsState.copy(port = value.filter { it.isDigit() })) }
    }

    fun onDroneSystemIdChanged(value: String) {
        _state.update { state ->
            state.copy(
                connectionSettingsState = state.connectionSettingsState.copy(droneSystemId = value.filter { it.isDigit() })
            )
        }
    }

    fun onDroneComponentIdChanged(value: String) {
        _state.update { state ->
            state.copy(
                connectionSettingsState = state.connectionSettingsState.copy(droneComponentId = value.filter { it.isDigit() })
            )
        }
    }

    fun onSaveConnectionSettingsClicked() {
        viewModelScope.launch {
            with(_state.value.connectionSettingsState) {
                appSettings.setAutoDetect(autoDetect)
                appSettings.setHost(host)
                appSettings.setPort(port.toInt())
                appSettings.setListenPort(listenPort.toInt())
                appSettings.setDroneSystemId(droneSystemId.toInt())
                appSettings.setDroneComponentId(droneComponentId.toInt())
            }
        }
    }

    fun onLeftStickFactorChanged(value: Float) {
        _state.update { it.copy(stickSizeState = it.stickSizeState.copy(leftFactor = value)) }
    }

    fun onLeftStickFactorDragEnded() {
        viewModelScope.launch {
            appSettings.setLeftStickSizeFactor(_state.value.stickSizeState.leftFactor)
        }
    }

    fun onRightStickFactorChanged(value: Float) {
        _state.update { it.copy(stickSizeState = it.stickSizeState.copy(rightFactor = value)) }
    }

    fun onRightStickFactorDragEnded() {
        viewModelScope.launch {
            appSettings.setRightStickSizeFactor(_state.value.stickSizeState.rightFactor)
        }
    }

    fun onShowCircularAreaChange() {
        viewModelScope.launch {
            appSettings.setShowCircularArea(!_state.value.stickAppearanceState.showCircularArea)
        }
    }

    fun onShowSquareAreaChange() {
        viewModelScope.launch {
            appSettings.setShowSquareArea(!_state.value.stickAppearanceState.showSquareArea)
        }
    }

    fun onShowCircleBoundariesChange() {
        viewModelScope.launch {
            appSettings.setShowCircleBoundaries(!_state.value.stickAppearanceState.showCircleBoundaries)
        }
    }

    fun onKnobColorChange(color: Color) {
        viewModelScope.launch {
            appSettings.setKnobColor(color.toArgb())
        }
    }
}