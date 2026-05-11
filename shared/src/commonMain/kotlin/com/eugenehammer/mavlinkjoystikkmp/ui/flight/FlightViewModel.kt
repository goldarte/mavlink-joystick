package com.eugenehammer.mavlinkjoystikkmp.ui.flight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkConfig
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkEvent
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
        viewModelScope.launch { mavlinkManager.start() }
    }

    private fun subscribeOnSettings() {
        viewModelScope.launch {
            appSettings.state.collectLatest { newSettings ->
                val newConfig = MavlinkConfig(
                    targetHost = newSettings.host,
                    targetPort = newSettings.port,
                    listenPort = newSettings.listenPort,
                    droneSystemId = newSettings.droneSystemId,
                    droneComponentId = newSettings.droneComponentId,
                    autoDetect = newSettings.autoDetect,
                )

                mavlinkManager.stop()
                mavlinkManager.updateConfig(newConfig)
                mavlinkManager.start()
            }
        }
        viewModelScope.launch { appSettings.load() }
    }

    private fun observeMavlink() {
        viewModelScope.launch {
            mavlinkManager.state.collect { mavState ->
                _uiState.update {
                    it.copy(
                        armed = mavState.armed,
                        connected = mavState.connected,
                        rollDeg = mavState.rollDeg,
                        pitchDeg = mavState.pitchDeg,
                        yawHeading = ((mavState.yawDeg % 360f) + 360f) % 360f,
                        batteryVoltage = mavState.batteryVoltage?.let { voltage -> "${voltage}V" }
                            ?: "--.-V",
                        flightMode = mavState.flightMode,
                        autopilotName = mavState.autopilotName,
                        connectionStatus = if (mavState.connected) "${mavState.targetHost}:${mavState.targetPort}" else "NO LINK",
                    )
                }
            }
        }

        viewModelScope.launch {
            mavlinkManager.events.collect { event ->
                when (event) {
                    is MavlinkEvent.StatusText -> {
                        handleStatusText(event)
                    }

                    is MavlinkEvent.SerialData -> {
                        handleSerialData(event)
                    }
                }
            }
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
                mavlinkManager.disarm()
            } else {
                mavlinkManager.arm()
            }
        }
    }

    fun onArmLongClick() {
        _uiState.update { it.copy(leftJoystickState = leftJoystickInitialState) }
        viewModelScope.launch {
            mavlinkManager.disarm()
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

    private fun handleStatusText(event: MavlinkEvent.StatusText) {
        // TODO
    }

    private fun handleSerialData(event: MavlinkEvent.SerialData) {
        // TODO
    }

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            mavlinkManager.stop()
        }
    }
}