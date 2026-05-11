package com.eugenehammer.mavlinkjoystikkmp.ui.flight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import com.eugenehammer.mavlinkjoystikkmp.utils.CurveUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FlightViewModel(
    private val mavlinkManager: MavlinkManager,
    private val appSettings: AppSettings,
) : ViewModel() {
    private val leftJoystickInitialState = FlightScreenState.JoystickState(
        x = 0f,
        y = 0f,
        isDragging = false,
        isThrottleMode = true
    )
    private val _uiState = MutableStateFlow(
        FlightScreenState(
            armed = false,
            connected = false,
            batteryVoltage = "--.-V",
            flightMode = "---",
            autopilotName = "---",
            connectionStatus = "NO LINK",
            rollDeg = 0f,
            pitchDeg = 0f,
            yawHeading = 0f,
            leftJoystickState = leftJoystickInitialState,
            rightJoystickState = leftJoystickInitialState.copy(isThrottleMode = false)
        ),
    )
    val uiState: StateFlow<FlightScreenState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FlightScreenEvent>()
    val events = _events.asSharedFlow()

    init {
        subscribeOnSettings()
        observeMavlink()
        mavlinkManager.start()
    }

    private fun subscribeOnSettings() {
        viewModelScope.launch {
            appSettings.state.collectLatest { newSettings ->
                mavlinkManager.stop()
                mavlinkManager.targetHost = newSettings.host
                mavlinkManager.targetPort = newSettings.port
                mavlinkManager.listenPort = newSettings.listenPort
                mavlinkManager.droneSystemId = newSettings.droneSystemId
                mavlinkManager.droneComponentId = newSettings.droneComponentId
                mavlinkManager.autoDetect = newSettings.autoDetect
                mavlinkManager.start()
            }
        }
        viewModelScope.launch { appSettings.load() }
    }

    private fun observeMavlink() {
        mavlinkManager.onStateChanged = { armed, connected ->
            _uiState.update {
                it.copy(armed = armed, connected = connected)
            }
        }

        mavlinkManager.onAttitudeReceived = { rollDeg, pitchDeg, yawDeg ->
            _uiState.update {
                it.copy(
                    rollDeg = rollDeg,
                    pitchDeg = pitchDeg,
                    yawHeading = ((yawDeg % 360f) + 360f) % 360f
                )
            }
        }

        mavlinkManager.onBatteryVoltageReceived = { voltage ->
            _uiState.update { it.copy(batteryVoltage = "${voltage}V") }
        }

        mavlinkManager.onFlightModeReceived = { mode ->
            _uiState.update { it.copy(flightMode = mode) }
        }

        mavlinkManager.onAutopilotNameReceived = { name ->
            _uiState.update { it.copy(autopilotName = name) }
        }
    }

    fun onLeftStickChanged(x: Float, y: Float) {
        _uiState.update { it.copy(leftJoystickState = it.leftJoystickState.copy(x = x, y = y)) }
        pushChannels()
    }

    fun onLeftStickReleased() {
        _uiState.update { it.copy(leftJoystickState = it.leftJoystickState.copy(isDragging = false)) }
    }

    fun onRightStickChanged(x: Float, y: Float) {
        _uiState.update { it.copy(rightJoystickState = it.rightJoystickState.copy(x = x, y = y)) }
        pushChannels()
    }

    fun onRightStickReleased() {
        _uiState.update { it.copy(rightJoystickState = it.rightJoystickState.copy(isDragging = false)) }
    }

    fun onSettingsButtonClicked() {
        viewModelScope.launch { _events.emit(FlightScreenEvent.GoToSettings) }
    }

    fun onArmClick() {
        _uiState.update { it.copy(leftJoystickState = leftJoystickInitialState) }
        viewModelScope.launch {
            if (uiState.value.armed) {
                mavlinkManager.sendArmCommand(false)
            } else {
                mavlinkManager.sendArmCommand(true)
            }
        }
    }

    fun onArmLongClick() {
        _uiState.update { it.copy(leftJoystickState = leftJoystickInitialState) }
        viewModelScope.launch {
            mavlinkManager.sendArmCommand(false)
        }
    }

    private fun pushChannels() {
        with(appSettings.state.value) {
            val roll = CurveUtils.applyCurve(
                value = _uiState.value.rightJoystickState.x,
                weight = rollWeight,
                offset = rollOffset,
                expo = rollExpo,
            )

            val pitch = CurveUtils.applyCurve(
                value = _uiState.value.rightJoystickState.y,
                weight = pitchWeight,
                offset = pitchOffset,
                expo = pitchExpo,
            )

            val yaw = CurveUtils.applyCurve(
                value = _uiState.value.leftJoystickState.x,
                weight = yawWeight,
                offset = yawOffset,
                expo = yawExpo,
            )

            val throttle = CurveUtils.applyCurve(
                value = _uiState.value.leftJoystickState.y,
                weight = throttleWeight,
                offset = throttleOffset,
                expo = throttleExpo,
            )

            viewModelScope.launch {
                mavlinkManager.setChannels(
                    roll = roll,
                    pitch = pitch,
                    throttle = throttle,
                    yaw = yaw,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            mavlinkManager.stop()
        }
    }
}