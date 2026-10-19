package com.goldarte.mavlinkjoystick.ui.flight

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldarte.mavlinkjoystick.data.AppSettings
import com.goldarte.mavlinkjoystick.mavlink.MavlinkEvent
import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import com.goldarte.mavlinkjoystick.utils.CurveUtils
import com.goldarte.mavlinkjoystick.utils.format
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation used on app start and whenever the link is lost: no telemetry is known yet, so
 * every reading is a placeholder. Joystick fields are filled in per instance.
 */
private val APP_START_STATE = FlightScreenState(
    armed = false,
    batteryVoltage = "--.-V",
    flightMode = "---",
    autopilotName = "---",
    connectionStatus = null,
    rollDeg = 0f,
    pitchDeg = 0f,
    yawHeading = 0f,
    leftJoystickState = FlightScreenState.JoystickState(
        valueX = 0f,
        valueY = 0f,
        isThrottleMode = true,
        isThrottleCentered = false,
        showCircularArea = true,
        showSquareArea = true,
        showCircleBoundaries = true,
        stickSizeFactor = 0.65f,
        knobColor = Color(0xFFFF5C8D),
    ),
    rightJoystickState = FlightScreenState.JoystickState(
        valueX = 0f,
        valueY = 0f,
        isThrottleMode = false,
        isThrottleCentered = false,
        showCircularArea = true,
        showSquareArea = true,
        showCircleBoundaries = true,
        stickSizeFactor = 0.65f,
        knobColor = Color(0xFFFF5C8D),
    ),
)

class FlightViewModel(
    private val mavlinkManager: MavlinkManager,
    private val appSettings: AppSettings,
) : ViewModel() {
    private val leftJoystickInitialState = FlightScreenState.JoystickState(
        valueX = 0f,
        valueY = 0f,
        isThrottleMode = true,
        isThrottleCentered = false,
        showCircularArea = true,
        showSquareArea = true,
        showCircleBoundaries = true,
        stickSizeFactor = 0.65f,
        knobColor = Color(0xFFFF5C8D),
    )
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<FlightScreenState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FlightScreenEvent>()
    val events = _events.asSharedFlow()

    // Keep the arming reset at an actual zero, even with configured channel offsets,
    // until the user moves the left stick again.
    private var leftStickResetForArming = false

    /**
     * State shown on app start, also used when the link times out so a dropped connection cannot
     * leave stale telemetry on screen. The joystick visuals are re-applied from settings as soon
     * as they are collected.
     */
    private fun initialState() = APP_START_STATE.copy(
        leftJoystickState = leftJoystickInitialState,
        rightJoystickState = leftJoystickInitialState.copy(isThrottleMode = false),
    )

    /**
     * Restores the app-start presentation once the drone has been gone for good, without touching
     * stick values or the configured joystick appearance. The manager drops its arm flag at the
     * same moment, so the button comes back disarmed in step with the link state.
     */
    private fun resetConnectionDisplay() {
        _uiState.update { state ->
            state.copy(
                armed = APP_START_STATE.armed,
                batteryVoltage = APP_START_STATE.batteryVoltage,
                flightMode = APP_START_STATE.flightMode,
                autopilotName = APP_START_STATE.autopilotName,
                connectionStatus = APP_START_STATE.connectionStatus,
                rollDeg = APP_START_STATE.rollDeg,
                pitchDeg = APP_START_STATE.pitchDeg,
                yawHeading = APP_START_STATE.yawHeading,
            )
        }
    }

    init {
        subscribeOnSettings()
        observeMavlink()
    }

    private fun subscribeOnSettings() {
        viewModelScope.launch {
            appSettings.state.collectLatest { newSettings ->
                _uiState.update {
                    it.copy(
                        leftJoystickState = it.leftJoystickState.copy(
                            isThrottleMode = newSettings.isLeftJoystickInThrottleMode,
                            isThrottleCentered = newSettings.isThrottleCentered,
                            showCircularArea = newSettings.showCircularArea,
                            showSquareArea = newSettings.showSquareArea,
                            showCircleBoundaries = newSettings.showCircleBoundaries,
                            stickSizeFactor = newSettings.leftStickSizeFactor,
                            knobColor = Color(newSettings.knobColor)
                        ),
                        rightJoystickState = it.rightJoystickState.copy(
                            isThrottleMode = newSettings.isRightJoystickInThrottleMode,
                            isThrottleCentered = newSettings.isThrottleCentered,
                            showCircularArea = newSettings.showCircularArea,
                            showSquareArea = newSettings.showSquareArea,
                            showCircleBoundaries = newSettings.showCircleBoundaries,
                            stickSizeFactor = newSettings.rightStickSizeFactor,
                            knobColor = Color(newSettings.knobColor)
                        )
                    )
                }
                // The stick now produces -1..1 instead of 0..1, so the transport has to encode the
                // throttle channel accordingly. This does not touch the network settings below.
                mavlinkManager.isThrottleCentered = newSettings.isThrottleCentered
                // Discovery updates the manager before saving settings. Persisting that
                // address (or changing joystick appearance) must not tear down the link.
                val connectionChanged = mavlinkManager.targetHost != newSettings.host ||
                    mavlinkManager.targetPort != newSettings.port ||
                    mavlinkManager.listenPort != newSettings.listenPort ||
                    mavlinkManager.droneSystemId != newSettings.droneSystemId ||
                    mavlinkManager.droneComponentId != newSettings.droneComponentId ||
                    mavlinkManager.autoDetect != newSettings.autoDetect
                if (connectionChanged) {
                    mavlinkManager.stop()
                    mavlinkManager.targetHost = newSettings.host
                    mavlinkManager.targetPort = newSettings.port
                    mavlinkManager.listenPort = newSettings.listenPort
                    mavlinkManager.droneSystemId = newSettings.droneSystemId
                    mavlinkManager.droneComponentId = newSettings.droneComponentId
                    mavlinkManager.autoDetect = newSettings.autoDetect
                }
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
                        _uiState.update { it.copy(batteryVoltage = "${event.voltage.format(1)}V") }
                    }

                    is MavlinkEvent.FlightModeReceived -> {
                        _uiState.update { it.copy(flightMode = event.mode) }
                    }

                    is MavlinkEvent.AutopilotNameReceived -> {
                        _uiState.update { it.copy(autopilotName = event.name) }
                    }

                    is MavlinkEvent.StatusTextReceived -> Unit

                    // The link is only briefly gone; keep the readings so a reconnect is seamless.
                    is MavlinkEvent.ConnectionLost -> Unit

                    // The drone has been gone long enough that the readings cannot be trusted.
                    is MavlinkEvent.ScreenReset -> resetConnectionDisplay()
                }
            }
        }
    }

    fun onLeftStickChanged(x: Float, y: Float) {
        if (x != 0f || y != 0f) leftStickResetForArming = false
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
                leftStickResetForArming = true
                _uiState.update {
                    it.copy(leftJoystickState = it.leftJoystickState.copy(valueX = 0f, valueY = 0f))
                }
                pushChannels()
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

            val yaw = if (leftStickResetForArming) 0f else CurveUtils.applyCurve(
                value = _uiState.value.leftJoystickState.valueX,
                weight = yawWeight,
                offset = yawOffset,
                expo = yawExpo,
            )

            val throttle = if (leftStickResetForArming) 0f else CurveUtils.applyCurve(
                value = _uiState.value.leftJoystickState.valueY,
                weight = throttleWeight,
                offset = throttleOffset,
                expo = throttleExpo,
            )

            mavlinkManager.setChannels(
                roll = roll,
                pitch = pitch,
                throttle = throttle,
                yaw = yaw,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()

        mavlinkManager.stop()
    }
}
