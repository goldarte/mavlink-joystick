package com.goldarte.mavlinkjoystick.ui.flight

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldarte.mavlinkjoystick.data.AppSettings
import com.goldarte.mavlinkjoystick.mavlink.MavlinkEvent
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import com.goldarte.mavlinkjoystick.utils.CurveUtils
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
        viewModelScope.launch {
            mavlinkManager.connectionState.collect { connectionState ->
                _uiState.update {
                    it.copy(
                        armed = connectionState.armed,
                        connectionStatus = "${mavlinkManager.targetHost}:${mavlinkManager.targetPort}"
                            .takeIf { connectionState.connected }
                    )
                }
            }
        }

        viewModelScope.launch {
            mavlinkManager.events.collect { event ->
                when (event) {
                    is MavlinkEvent.AttitudeReceived -> {
                        _uiState.update {
                            it.copy(
                                rollDeg = event.roll,
                                pitchDeg = event.pitch,
                                yawHeading = ((event.yaw % 360f) + 360f) % 360f
                            )
                        }
                    }

                    is MavlinkEvent.BatteryVoltageReceived -> {
                        _uiState.update { it.copy(batteryVoltage = "${event.voltage}V") }
                    }

                    is MavlinkEvent.FlightModeReceived -> {
                        _uiState.update { it.copy(flightMode = event.mode) }
                    }

                    is MavlinkEvent.AutopilotNameReceived -> {
                        _uiState.update { it.copy(autopilotName = event.name) }
                    }

                    is MavlinkEvent.StatusTextReceived -> Unit
                }
            }
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

    fun onMenuButtonClicked() {
        viewModelScope.launch { _events.emit(FlightScreenEvent.GoToMenu) }
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
