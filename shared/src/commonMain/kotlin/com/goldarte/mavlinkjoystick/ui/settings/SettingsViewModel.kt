// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldarte.mavlinkjoystick.data.AppSettings
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val mavlinkManager: MavlinkManager,
) : ViewModel() {
    private val defaultCurveParams = SettingsScreenState.StickCurveSettingsState.CurveParams(
        weight = 1f,
        offset = 0f,
        expo = 0f
    )
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
            ),
            curveSettingsState = SettingsScreenState.StickCurveSettingsState(
                selectedAxis = SettingsScreenState.StickCurveSettingsState.StickAxis.Roll,
                rollParams = defaultCurveParams,
                pitchParams = defaultCurveParams,
                yawParams = defaultCurveParams,
                throttleParams = defaultCurveParams
            ),
            throttleSettingsState = SettingsScreenState.ThrottleSettingsState(
                isCentered = false
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
                        ),
                        curveSettingsState = _state.value.curveSettingsState.copy(
                            rollParams = SettingsScreenState.StickCurveSettingsState.CurveParams(
                                weight = settingsState.rollWeight,
                                offset = settingsState.rollOffset,
                                expo = settingsState.rollExpo
                            ),
                            pitchParams = SettingsScreenState.StickCurveSettingsState.CurveParams(
                                weight = settingsState.pitchWeight,
                                offset = settingsState.pitchOffset,
                                expo = settingsState.pitchExpo
                            ),
                            yawParams = SettingsScreenState.StickCurveSettingsState.CurveParams(
                                weight = settingsState.yawWeight,
                                offset = settingsState.yawOffset,
                                expo = settingsState.yawExpo
                            ),
                            throttleParams = SettingsScreenState.StickCurveSettingsState.CurveParams(
                                weight = settingsState.throttleWeight,
                                offset = settingsState.throttleOffset,
                                expo = settingsState.throttleExpo
                            )
                        ),
                        throttleSettingsState = SettingsScreenState.ThrottleSettingsState(
                            isCentered = settingsState.isThrottleCentered
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
        updateConnectionSettings { it.copy(autoDetect = !it.autoDetect) }
    }

    fun onListenPortChanged(value: String) {
        updateConnectionSettings { it.copy(listenPort = sanitizePortInput(value)) }
    }

    fun onHostChanged(value: String) {
        updateConnectionSettings { it.copy(host = sanitizeIpv4Input(value)) }
    }

    fun onTargetPortChanged(value: String) {
        updateConnectionSettings { it.copy(port = sanitizePortInput(value)) }
    }

    fun onDroneSystemIdChanged(value: String) {
        updateConnectionSettings { it.copy(droneSystemId = sanitizeMavlinkIdInput(value)) }
    }

    fun onDroneComponentIdChanged(value: String) {
        updateConnectionSettings { it.copy(droneComponentId = sanitizeMavlinkIdInput(value)) }
    }

    fun onSaveConnectionSettingsClicked() {
        val connection = _state.value.connectionSettingsState
        val host = parseIpv4(connection.host)
        val port = parsePort(connection.port)
        val listenPort = parsePort(connection.listenPort)
        val systemId = parseMavlinkId(connection.droneSystemId)
        val componentId = parseMavlinkId(connection.droneComponentId)

        val error = when {
            listenPort == null -> "Listen Port must be between 1 and 65535"
            componentId == null -> "Target Component ID must be between 1 and 255"
            // While auto-detection is on these fields are disabled, so the user cannot correct them
            // here. Keep whatever is already stored; the detector overwrites it on the heartbeat.
            !connection.autoDetect && host == null ->
                "Target IP must be a dotted IPv4 address, e.g. 192.168.4.1"
            !connection.autoDetect && port == null -> "Target Port must be between 1 and 65535"
            !connection.autoDetect && systemId == null -> "Target System ID must be between 1 and 255"
            else -> null
        }
        if (error != null) {
            _state.update { state ->
                state.copy(
                    connectionSettingsState = state.connectionSettingsState.copy(validationError = error)
                )
            }
            return
        }

        // Not null: the branch above only lets auto-detection own the target fields.
        val savedListenPort = listenPort ?: return
        val savedComponentId = componentId ?: return
        val stored = appSettings.state.value

        _state.update { state ->
            state.copy(
                connectionSettingsState = state.connectionSettingsState.copy(validationError = null)
            )
        }

        viewModelScope.launch {
            appSettings.setAutoDetect(connection.autoDetect)
            appSettings.setHost(host ?: stored.host)
            appSettings.setPort(port ?: stored.port)
            appSettings.setListenPort(savedListenPort)
            appSettings.setDroneSystemId(systemId ?: stored.droneSystemId)
            appSettings.setDroneComponentId(savedComponentId)
        }
    }

    /** Applies an edit to the connection form and drops a validation message left by a failed save. */
    private fun updateConnectionSettings(
        transform: (SettingsScreenState.ConnectionSettingsState) -> SettingsScreenState.ConnectionSettingsState,
    ) {
        _state.update { state ->
            state.copy(
                connectionSettingsState = transform(state.connectionSettingsState)
                    .copy(validationError = null)
            )
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

    fun onThrottleCenteredChange(isCentered: Boolean) {
        viewModelScope.launch {
            appSettings.setThrottleCentered(isCentered)
        }
    }

    fun onCurveParamsAxisSelected(axis: SettingsScreenState.StickCurveSettingsState.StickAxis) {
        _state.update { it.copy(curveSettingsState = it.curveSettingsState.copy(selectedAxis = axis)) }
    }

    fun onWeightChange(value: Float) {
        _state.update { it.copy(curveSettingsState = it.curveSettingsState.copyWeightForAxis(value)) }
    }

    fun onWeightChangeFinished() {
        viewModelScope.launch { saveWeightForAxis() }
    }

    fun onOffsetChange(value: Float) {
        _state.update { it.copy(curveSettingsState = it.curveSettingsState.copyOffsetForAxis(value)) }
    }

    fun onOffsetChangeFinished() {
        viewModelScope.launch { saveOffsetForAxis() }
    }

    fun onExpoChange(value: Float) {
        _state.update { it.copy(curveSettingsState = it.curveSettingsState.copyExpoForAxis(value)) }
    }

    fun onExpoChangeFinished() {
        viewModelScope.launch { saveExpoForAxis() }
    }

    private fun SettingsScreenState.StickCurveSettingsState.copyWeightForAxis(value: Float): SettingsScreenState.StickCurveSettingsState =
        when (selectedAxis) {
            SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> copy(rollParams = rollParams.copy(weight = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> copy(pitchParams = pitchParams.copy(weight = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> copy(yawParams = yawParams.copy(weight = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> copy(throttleParams = throttleParams.copy(weight = value))
        }

    private suspend fun saveWeightForAxis() {
        when (_state.value.curveSettingsState.selectedAxis) {
            SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> appSettings.setRollWeight(_state.value.curveSettingsState.rollParams.weight)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> appSettings.setPitchWeight(_state.value.curveSettingsState.pitchParams.weight)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> appSettings.setYawWeight(_state.value.curveSettingsState.yawParams.weight)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> appSettings.setThrottleWeight(_state.value.curveSettingsState.throttleParams.weight)
        }
    }

    private fun SettingsScreenState.StickCurveSettingsState.copyOffsetForAxis(value: Float): SettingsScreenState.StickCurveSettingsState =
        when (selectedAxis) {
            SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> copy(rollParams = rollParams.copy(offset = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> copy(pitchParams = pitchParams.copy(offset = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> copy(yawParams = yawParams.copy(offset = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> copy(throttleParams = throttleParams.copy(offset = value))
        }

    private suspend fun saveOffsetForAxis() {
        when (_state.value.curveSettingsState.selectedAxis) {
            SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> appSettings.setRollOffset(_state.value.curveSettingsState.rollParams.offset)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> appSettings.setPitchOffset(_state.value.curveSettingsState.pitchParams.offset)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> appSettings.setYawOffset(_state.value.curveSettingsState.yawParams.offset)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> appSettings.setThrottleOffset(_state.value.curveSettingsState.throttleParams.offset)
        }
    }

    private fun SettingsScreenState.StickCurveSettingsState.copyExpoForAxis(value: Float): SettingsScreenState.StickCurveSettingsState =
        when (selectedAxis) {
            SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> copy(rollParams = rollParams.copy(expo = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> copy(pitchParams = pitchParams.copy(expo = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> copy(yawParams = yawParams.copy(expo = value))
            SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> copy(throttleParams = throttleParams.copy(expo = value))
        }

    private suspend fun saveExpoForAxis() {
        when (_state.value.curveSettingsState.selectedAxis) {
            SettingsScreenState.StickCurveSettingsState.StickAxis.Roll -> appSettings.setRollExpo(_state.value.curveSettingsState.rollParams.expo)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Pitch -> appSettings.setPitchExpo(_state.value.curveSettingsState.pitchParams.expo)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Yaw -> appSettings.setYawExpo(_state.value.curveSettingsState.yawParams.expo)
            SettingsScreenState.StickCurveSettingsState.StickAxis.Throttle -> appSettings.setThrottleExpo(_state.value.curveSettingsState.throttleParams.expo)
        }
    }
}
