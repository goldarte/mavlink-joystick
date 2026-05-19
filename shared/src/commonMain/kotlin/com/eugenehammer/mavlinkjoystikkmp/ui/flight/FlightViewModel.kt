package com.eugenehammer.mavlinkjoystikkmp.ui.flight

import androidx.compose.ui.graphics.Color
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
        valueX = 0f,
        valueY = 0f,
        isThrottleMode = true,
        showCircularArea = true,
        showSquareArea = true,
        showCircleBoundaries = true,
        stickSizeFactor = 0.65f,
        knobColor = Color(0xFFFF5C8D),
    )
    private val _uiState = MutableStateFlow(
        FlightScreenState(
            armed = false,
            batteryVoltage = "--.-V",
            flightMode = "---",
            autopilotName = "---",
            connectionStatus = null,
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
                _uiState.update {
                    it.copy(
                        leftJoystickState = it.leftJoystickState.copy(
                            isThrottleMode = newSettings.isLeftJoystickInThrottleMode,
                            showCircularArea = newSettings.showCircularArea,
                            showSquareArea = newSettings.showSquareArea,
                            showCircleBoundaries = newSettings.showCircleBoundaries,
                            stickSizeFactor = newSettings.leftStickSizeFactor,
                            knobColor = Color(newSettings.knobColor)
                        ),
                        rightJoystickState = it.rightJoystickState.copy(
                            isThrottleMode = newSettings.isRightJoystickInThrottleMode,
                            showCircularArea = newSettings.showCircularArea,
                            showSquareArea = newSettings.showSquareArea,
                            showCircleBoundaries = newSettings.showCircleBoundaries,
                            stickSizeFactor = newSettings.rightStickSizeFactor,
                            knobColor = Color(newSettings.knobColor)
                        )
                    )
                }
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
                it.copy(
                    armed = armed,
                    connectionStatus = "${mavlinkManager.targetHost}:${mavlinkManager.targetPort}".takeIf { connected }
                )
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
        _uiState.update {
            it.copy(
                leftJoystickState = it.leftJoystickState.copy(
                    valueX = x,
                    valueY = y
                )
            )
        }
        pushChannels()
    }

    fun onRightStickChanged(x: Float, y: Float) {
        _uiState.update {
            it.copy(
                rightJoystickState = it.rightJoystickState.copy(
                    valueX = x,
                    valueY = y
                )
            )
        }
        pushChannels()
    }

    fun onSettingsButtonClicked() {
        viewModelScope.launch { _events.emit(FlightScreenEvent.GoToSettings) }
    }

    fun onArmClick() {
        viewModelScope.launch {
            if (uiState.value.armed) {
                mavlinkManager.sendArmCommand(false)
            } else {
                mavlinkManager.sendArmCommand(true)
            }
        }
    }

    fun onArmLongClick() {
        viewModelScope.launch {
            mavlinkManager.sendArmCommand(false)
        }
    }

    private fun pushChannels() {
        with(appSettings.state.value) {
            val roll = CurveUtils.applyCurve(
                value = _uiState.value.rightJoystickState.valueX,
                weight = rollWeight,
                offset = rollOffset,
                expo = rollExpo,
            )

            val pitch = CurveUtils.applyCurve(
                value = _uiState.value.rightJoystickState.valueY,
                weight = pitchWeight,
                offset = pitchOffset,
                expo = pitchExpo,
            )

            val yaw = CurveUtils.applyCurve(
                value = _uiState.value.leftJoystickState.valueX,
                weight = yawWeight,
                offset = yawOffset,
                expo = yawExpo,
            )

            val throttle = CurveUtils.applyCurve(
                value = _uiState.value.leftJoystickState.valueY,
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