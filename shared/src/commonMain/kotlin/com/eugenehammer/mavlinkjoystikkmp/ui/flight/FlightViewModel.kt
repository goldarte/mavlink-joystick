package com.eugenehammer.mavlinkjoystikkmp.ui.flight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkConfig
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkEvent
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import com.eugenehammer.mavlinkjoystikkmp.utils.CurveUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FlightViewModel(
    private val mavlinkClient: MavlinkManager,
//    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val settings = AppSettings()

    private val _uiState =
        MutableStateFlow(
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

    val uiState: StateFlow<FlightScreenState> =
        _uiState.asStateFlow()

    // ─────────────────────────────────────────────
    // Raw stick values
    // ─────────────────────────────────────────────

    private var throttleRaw = 0f
    private var yawRaw = 0f
    private var pitchRaw = 0f
    private var rollRaw = 0f

    // ─────────────────────────────────────────────
    // Curves
    // ─────────────────────────────────────────────

    private var rollWeight = 1.0f
    private var rollOffset = 0.0f
    private var rollExpo = 0.0f

    private var pitchWeight = 1.0f
    private var pitchOffset = 0.0f
    private var pitchExpo = 0.0f

    private var yawWeight = 1.0f
    private var yawOffset = 0.0f
    private var yawExpo = 0.0f

    private var thrWeight = 1.0f
    private var thrOffset = 0.0f
    private var thrExpo = 0.0f

    init {

        loadSettings()

        observeMavlink()

        viewModelScope.launch {

            mavlinkClient.start()
        }
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

    fun reloadSettingsIfNeeded() {

        val oldConfig =
            currentConfig()

        loadSettings()

        val newConfig =
            currentConfig()

        if (oldConfig != newConfig) {

            viewModelScope.launch {

                mavlinkClient.stop()

                mavlinkClient.updateConfig(
                    newConfig,
                )

                mavlinkClient.start()
            }
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

                        yawHeading =
                            (
                                    (
                                            mavState.yawDeg % 360f
                                            ) + 360f
                                    ) % 360f,

                        batteryVoltage =
                            mavState.batteryVoltage
                                ?.let { voltage ->

//                                    String.format(
//                                        Locale.US,
//                                        "%.1fV",
                                        "${voltage}V"
//                                    )
                                }
                                ?: "--.-V",

                        flightMode =
                            mavState.flightMode,

                        autopilotName =
                            mavState.autopilotName,

                        connectionStatus =
                            if (mavState.connected) {

                                "${mavState.targetHost}:${mavState.targetPort}"

                            } else {

                                "NO LINK"
                            },
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

    // ─────────────────────────────────────────────
    // Channels
    // ─────────────────────────────────────────────

    private fun pushChannels() {

        val roll =
            CurveUtils.applyCurve(
                x = rollRaw,
                weight = rollWeight,
                offset = rollOffset,
                expo = rollExpo,
            )

        val pitch =
            CurveUtils.applyCurve(
                x = pitchRaw,
                weight = pitchWeight,
                offset = pitchOffset,
                expo = pitchExpo,
            )

        val yaw =
            CurveUtils.applyCurve(
                x = yawRaw,
                weight = yawWeight,
                offset = yawOffset,
                expo = yawExpo,
            )

        val throttle =
            CurveUtils.applyCurve(
                x = throttleRaw,
                weight = thrWeight,
                offset = thrOffset,
                expo = thrExpo,
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

    // ─────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────

    private fun handleStatusText(
        event: MavlinkEvent.StatusText,
    ) {

        // TODO
    }

    private fun handleSerialData(
        event: MavlinkEvent.SerialData,
    ) {

        // TODO
    }

    // ─────────────────────────────────────────────
    // Settings
    // ─────────────────────────────────────────────

    private fun loadSettings() {

//        val settings =
//            settingsRepository.getSettings()

        // ───── Curves: roll ─────

        rollWeight =
            settings.rollWeight

        rollOffset =
            settings.rollOffset

        rollExpo =
            settings.rollExpo

        // ───── Curves: pitch ─────

        pitchWeight =
            settings.pitchWeight

        pitchOffset =
            settings.pitchOffset

        pitchExpo =
            settings.pitchExpo

        // ───── Curves: yaw ─────

        yawWeight =
            settings.yawWeight

        yawOffset =
            settings.yawOffset

        yawExpo =
            settings.yawExpo

        // ───── Curves: throttle ─────

        thrWeight =
            settings.throttleWeight

        thrOffset =
            settings.throttleOffset

        thrExpo =
            settings.throttleExpo

        viewModelScope.launch {

            mavlinkClient.updateConfig(
                currentConfig(),
            )
        }
    }

    private fun currentConfig(): MavlinkConfig {

//        val settings =
//            settingsRepository.getSettings()

        return MavlinkConfig(
            targetHost =
                settings.host,

            targetPort =
                settings.port,

            listenPort =
                settings.listenPort,

            droneSystemId =
                settings.droneSystemId,

            droneComponentId =
                settings.droneComponentId,

            autoDetect =
                settings.autoDetect,
        )
    }

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    override fun onCleared() {

        super.onCleared()

        viewModelScope.launch {

            mavlinkClient.stop()
        }
    }
}

data class AppSettings(

    // ─────────────────────────────────────────────
    // Connection
    // ─────────────────────────────────────────────

    val host: String = "255.255.255.255",

    val port: Int = 14550,

    val listenPort: Int = 14550,

    val droneSystemId: Int = 1,

    val droneComponentId: Int = 1,

    val autoDetect: Boolean = true,

    // ─────────────────────────────────────────────
    // Joystick visuals
    // ─────────────────────────────────────────────

    val leftStickSizeFactor: Float = 0.65f,

    val rightStickSizeFactor: Float = 0.65f,

    val showCircularArea: Boolean = true,

    val showSquareArea: Boolean = true,

    val showCircleBoundaries: Boolean = false,

    val knobColor: Long = 0xFFF44336,

    // ─────────────────────────────────────────────
    // Roll curve
    // ─────────────────────────────────────────────

    val rollWeight: Float = 1.0f,

    val rollOffset: Float = 0.0f,

    val rollExpo: Float = 0.0f,

    // ─────────────────────────────────────────────
    // Pitch curve
    // ─────────────────────────────────────────────

    val pitchWeight: Float = 1.0f,

    val pitchOffset: Float = 0.0f,

    val pitchExpo: Float = 0.0f,

    // ─────────────────────────────────────────────
    // Yaw curve
    // ─────────────────────────────────────────────

    val yawWeight: Float = 1.0f,

    val yawOffset: Float = 0.0f,

    val yawExpo: Float = 0.0f,

    // ─────────────────────────────────────────────
    // Throttle curve
    // ─────────────────────────────────────────────

    val throttleWeight: Float = 1.0f,

    val throttleOffset: Float = 0.0f,

    val throttleExpo: Float = 0.0f,
)