package com.eugenehammer.mavlinkjoystikkmp.ui.flight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkConfig
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkEvent
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import com.eugenehammer.mavlinkjoystikkmp.utils.CurveUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FlightViewModel(
    private val mavlinkClient: MavlinkManager,
    private val appSettings: AppSettings,
) : ViewModel() {
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
            yawHeading = 0f
        ),
    )
    val uiState: StateFlow<FlightScreenState> = _uiState.asStateFlow()

    // ─────────────────────────────────────────────
    // Raw stick values
    // ─────────────────────────────────────────────

    private var throttleRaw = 0f
    private var yawRaw = 0f
    private var pitchRaw = 0f
    private var rollRaw = 0f


    init {
        subscribeOnSettings()
        observeMavlink()
        viewModelScope.launch { mavlinkClient.start() }
    }

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    fun onLeftStickChanged(
        x: Float,
        y: Float,
    ) {
        yawRaw = x
        throttleRaw = y

        pushChannels()
    }

    fun onRightStickChanged(
        x: Float,
        y: Float,
    ) {
        rollRaw = x
        pitchRaw = y

        pushChannels()
    }

    fun onArmClick() {
        viewModelScope.launch {
            if (uiState.value.armed) {
                mavlinkClient.disarm()
            } else {
                mavlinkClient.arm()
            }
        }
    }

    fun onArmLongClick() {
        viewModelScope.launch {
            mavlinkClient.disarm()
        }
    }

    // ─────────────────────────────────────────────
    // MAVLink observing
    // ─────────────────────────────────────────────

    private fun observeMavlink() {
        viewModelScope.launch {
            mavlinkClient.state.collect { mavState ->
                _uiState.update {
                    it.copy(
                        armed = mavState.armed,
                        connected = mavState.connected,
                        rollDeg = mavState.rollDeg,
                        pitchDeg = mavState.pitchDeg,
                        yawHeading = ((mavState.yawDeg % 360f) + 360f) % 360f,
                        batteryVoltage = mavState.batteryVoltage?.let { voltage -> "${voltage}V" } ?: "--.-V",
                        flightMode = mavState.flightMode,
                        autopilotName = mavState.autopilotName,
                        connectionStatus = if (mavState.connected) "${mavState.targetHost}:${mavState.targetPort}" else "NO LINK",
                    )
                }
            }
        }

        viewModelScope.launch {
            mavlinkClient.events.collect { event ->
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

    private fun pushChannels() {
        with(appSettings.state.value) {
            val roll = CurveUtils.applyCurve(
                x = rollRaw,
                weight = rollWeight,
                offset = rollOffset,
                expo = rollExpo,
            )

            val pitch = CurveUtils.applyCurve(
                x = pitchRaw,
                weight = pitchWeight,
                offset = pitchOffset,
                expo = pitchExpo,
            )

            val yaw = CurveUtils.applyCurve(
                x = yawRaw,
                weight = yawWeight,
                offset = yawOffset,
                expo = yawExpo,
            )

            val throttle = CurveUtils.applyCurve(
                x = throttleRaw,
                weight = throttleWeight,
                offset = throttleOffset,
                expo = throttleExpo,
            )

            viewModelScope.launch {
                mavlinkClient.setChannels(
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

                mavlinkClient.stop()
                mavlinkClient.updateConfig(newConfig)
                mavlinkClient.start()
            }
        }
        viewModelScope.launch { appSettings.load() }
    }

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            mavlinkClient.stop()
        }
    }
}