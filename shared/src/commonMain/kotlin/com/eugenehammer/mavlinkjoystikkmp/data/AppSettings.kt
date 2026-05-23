package com.eugenehammer.mavlinkjoystikkmp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private val INITIAL_SETTINGS_STATE = SettingsState(
    // Connection
    host = "255.255.255.255",
    port = 14550,
    listenPort = 14550,
    droneSystemId = 1,
    droneComponentId = 1,
    autoDetect = true,

    // Joystick visuals
    leftStickSizeFactor = 0.65f,
    rightStickSizeFactor = 0.65f,
    isLeftJoystickInThrottleMode = true,
    isRightJoystickInThrottleMode = false,
    showCircularArea = true,
    showSquareArea = true,
    showCircleBoundaries = false,
    knobColor = -769226,

    // Roll curve
    rollWeight = 1.0f,
    rollOffset = 0.0f,
    rollExpo = 0.0f,

    // Pitch curve
    pitchWeight = 1.0f,
    pitchOffset = 0.0f,
    pitchExpo = 0.0f,

    // Yaw curve
    yawWeight = 1.0f,
    yawOffset = 0.0f,
    yawExpo = 0.0f,

    // Throttle curve
    throttleWeight = 1.0f,
    throttleOffset = 0.0f,
    throttleExpo = 0.0f
)

class AppSettings(private val dataStore: DataStore<Preferences>) {
    private val _state = MutableStateFlow(INITIAL_SETTINGS_STATE)
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    // ─────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────

    suspend fun load() {
        dataStore.data.collect { prefs ->
            _state.update {
                SettingsState(
                    host = prefs[AppSettingsKeys.HOST] ?: it.host,
                    port = prefs[AppSettingsKeys.PORT] ?: it.port,
                    listenPort = prefs[AppSettingsKeys.LISTEN_PORT] ?: it.listenPort,
                    droneSystemId = prefs[AppSettingsKeys.DRONE_SYSTEM_ID] ?: it.droneSystemId,
                    droneComponentId = prefs[AppSettingsKeys.DRONE_COMPONENT_ID]
                        ?: it.droneComponentId,
                    autoDetect = prefs[AppSettingsKeys.AUTO_DETECT] ?: it.autoDetect,

                    leftStickSizeFactor = prefs[AppSettingsKeys.LEFT_STICK_SIZE_FACTOR]
                        ?: it.leftStickSizeFactor,
                    rightStickSizeFactor = prefs[AppSettingsKeys.RIGHT_STICK_SIZE_FACTOR]
                        ?: it.rightStickSizeFactor,
                    isLeftJoystickInThrottleMode = prefs[AppSettingsKeys.IS_LEFT_JOYSTICK_IN_THROTTLE_MODE]
                        ?: it.isLeftJoystickInThrottleMode,
                    isRightJoystickInThrottleMode = prefs[AppSettingsKeys.IS_RIGHT_JOYSTICK_IN_THROTTLE_MODE]
                        ?: it.isRightJoystickInThrottleMode,
                    showCircularArea = prefs[AppSettingsKeys.SHOW_CIRCULAR_AREA]
                        ?: it.showCircularArea,
                    showSquareArea = prefs[AppSettingsKeys.SHOW_SQUARE_AREA] ?: it.showSquareArea,
                    showCircleBoundaries = prefs[AppSettingsKeys.SHOW_CIRCLE_BOUNDARIES]
                        ?: it.showCircleBoundaries,
                    knobColor = prefs[AppSettingsKeys.KNOB_COLOR] ?: it.knobColor,

                    rollWeight = prefs[AppSettingsKeys.ROLL_WEIGHT] ?: it.rollWeight,
                    rollOffset = prefs[AppSettingsKeys.ROLL_OFFSET] ?: it.rollOffset,
                    rollExpo = prefs[AppSettingsKeys.ROLL_EXPO] ?: it.rollExpo,

                    pitchWeight = prefs[AppSettingsKeys.PITCH_WEIGHT] ?: it.pitchWeight,
                    pitchOffset = prefs[AppSettingsKeys.PITCH_OFFSET] ?: it.pitchOffset,
                    pitchExpo = prefs[AppSettingsKeys.PITCH_EXPO] ?: it.pitchExpo,

                    yawWeight = prefs[AppSettingsKeys.YAW_WEIGHT] ?: it.yawWeight,
                    yawOffset = prefs[AppSettingsKeys.YAW_OFFSET] ?: it.yawOffset,
                    yawExpo = prefs[AppSettingsKeys.YAW_EXPO] ?: it.yawExpo,

                    throttleWeight = prefs[AppSettingsKeys.THROTTLE_WEIGHT] ?: it.throttleWeight,
                    throttleOffset = prefs[AppSettingsKeys.THROTTLE_OFFSET] ?: it.throttleOffset,
                    throttleExpo = prefs[AppSettingsKeys.THROTTLE_EXPO] ?: it.throttleExpo
                )
            }
        }
    }

    // ─────────────────────────────────────────────
    // CONNECTION
    // ─────────────────────────────────────────────

    suspend fun setHost(value: String) {
        dataStore.edit { it[AppSettingsKeys.HOST] = value }
        _state.update { it.copy(host = value) }
    }

    suspend fun setPort(value: Int) {
        dataStore.edit { it[AppSettingsKeys.PORT] = value }
        _state.update { it.copy(port = value) }
    }

    suspend fun setListenPort(value: Int) {
        dataStore.edit { it[AppSettingsKeys.LISTEN_PORT] = value }
        _state.update { it.copy(listenPort = value) }
    }

    suspend fun setDroneSystemId(value: Int) {
        dataStore.edit { it[AppSettingsKeys.DRONE_SYSTEM_ID] = value }
        _state.update { it.copy(droneSystemId = value) }
    }

    suspend fun setDroneComponentId(value: Int) {
        dataStore.edit { it[AppSettingsKeys.DRONE_COMPONENT_ID] = value }
        _state.update { it.copy(droneComponentId = value) }
    }

    suspend fun setAutoDetect(value: Boolean) {
        dataStore.edit { it[AppSettingsKeys.AUTO_DETECT] = value }
        _state.update { it.copy(autoDetect = value) }
    }

    suspend fun setDetectedConnection(host: String, port: Int, droneSystemId: Int) {
        dataStore.edit {
            it[AppSettingsKeys.HOST] = host
            it[AppSettingsKeys.PORT] = port
            it[AppSettingsKeys.DRONE_SYSTEM_ID] = droneSystemId
        }
        _state.update {
            it.copy(
                host = host,
                port = port,
                droneSystemId = droneSystemId
            )
        }
    }

    // ─────────────────────────────────────────────
    // JOYSTICK
    // ─────────────────────────────────────────────

    suspend fun setLeftStickSizeFactor(value: Float) {
        dataStore.edit { it[AppSettingsKeys.LEFT_STICK_SIZE_FACTOR] = value }
        _state.update { it.copy(leftStickSizeFactor = value) }
    }

    suspend fun setRightStickSizeFactor(value: Float) {
        dataStore.edit { it[AppSettingsKeys.RIGHT_STICK_SIZE_FACTOR] = value }
        _state.update { it.copy(rightStickSizeFactor = value) }
    }

    suspend fun setLeftJoystickThrottleMode(value: Boolean) {
        dataStore.edit { it[AppSettingsKeys.IS_LEFT_JOYSTICK_IN_THROTTLE_MODE] = value }
        _state.update { it.copy(isLeftJoystickInThrottleMode = value) }
    }

    suspend fun setRightJoystickThrottleMode(value: Boolean) {
        dataStore.edit { it[AppSettingsKeys.IS_RIGHT_JOYSTICK_IN_THROTTLE_MODE] = value }
        _state.update { it.copy(isRightJoystickInThrottleMode = value) }
    }

    suspend fun setShowCircularArea(value: Boolean) {
        dataStore.edit { it[AppSettingsKeys.SHOW_CIRCULAR_AREA] = value }
        _state.update { it.copy(showCircularArea = value) }
    }

    suspend fun setShowSquareArea(value: Boolean) {
        dataStore.edit { it[AppSettingsKeys.SHOW_SQUARE_AREA] = value }
        _state.update { it.copy(showSquareArea = value) }
    }

    suspend fun setShowCircleBoundaries(value: Boolean) {
        dataStore.edit { it[AppSettingsKeys.SHOW_CIRCLE_BOUNDARIES] = value }
        _state.update { it.copy(showCircleBoundaries = value) }
    }

    suspend fun setKnobColor(value: Int) {
        dataStore.edit { it[AppSettingsKeys.KNOB_COLOR] = value }
        _state.update { it.copy(knobColor = value) }
    }

    // ─────────────────────────────────────────────
    // CURVES (example one section, same pattern)
    // ─────────────────────────────────────────────

    suspend fun setRollWeight(value: Float) {
        dataStore.edit { it[AppSettingsKeys.ROLL_WEIGHT] = value }
        _state.update { it.copy(rollWeight = value) }
    }

    suspend fun setRollOffset(value: Float) {
        dataStore.edit { it[AppSettingsKeys.ROLL_OFFSET] = value }
        _state.update { it.copy(rollOffset = value) }
    }

    suspend fun setRollExpo(value: Float) {
        dataStore.edit { it[AppSettingsKeys.ROLL_EXPO] = value }
        _state.update { it.copy(rollExpo = value) }
    }

    suspend fun setPitchWeight(value: Float) {
        dataStore.edit { it[AppSettingsKeys.PITCH_WEIGHT] = value }
        _state.update { it.copy(pitchWeight = value) }
    }

    suspend fun setPitchOffset(value: Float) {
        dataStore.edit { it[AppSettingsKeys.PITCH_OFFSET] = value }
        _state.update { it.copy(pitchOffset = value) }
    }

    suspend fun setPitchExpo(value: Float) {
        dataStore.edit { it[AppSettingsKeys.PITCH_EXPO] = value }
        _state.update { it.copy(pitchExpo = value) }
    }

    suspend fun setYawWeight(value: Float) {
        dataStore.edit { it[AppSettingsKeys.YAW_WEIGHT] = value }
        _state.update { it.copy(yawWeight = value) }
    }

    suspend fun setYawOffset(value: Float) {
        dataStore.edit { it[AppSettingsKeys.YAW_OFFSET] = value }
        _state.update { it.copy(yawOffset = value) }
    }

    suspend fun setYawExpo(value: Float) {
        dataStore.edit { it[AppSettingsKeys.YAW_EXPO] = value }
        _state.update { it.copy(yawExpo = value) }
    }

    suspend fun setThrottleWeight(value: Float) {
        dataStore.edit { it[AppSettingsKeys.THROTTLE_WEIGHT] = value }
        _state.update { it.copy(throttleWeight = value) }
    }

    suspend fun setThrottleOffset(value: Float) {
        dataStore.edit { it[AppSettingsKeys.THROTTLE_OFFSET] = value }
        _state.update { it.copy(throttleOffset = value) }
    }

    suspend fun setThrottleExpo(value: Float) {
        dataStore.edit { it[AppSettingsKeys.THROTTLE_EXPO] = value }
        _state.update { it.copy(throttleExpo = value) }
    }
}

data class SettingsState(
    // Connection
    val host: String,
    val port: Int,
    val listenPort: Int,
    val droneSystemId: Int,
    val droneComponentId: Int,
    val autoDetect: Boolean,

    // Joystick visuals
    val leftStickSizeFactor: Float,
    val rightStickSizeFactor: Float,
    val isLeftJoystickInThrottleMode: Boolean,
    val isRightJoystickInThrottleMode: Boolean,
    val showCircularArea: Boolean,
    val showSquareArea: Boolean,
    val showCircleBoundaries: Boolean,
    val knobColor: Int,

    // Roll curve
    val rollWeight: Float,
    val rollOffset: Float,
    val rollExpo: Float,

    // Pitch curve
    val pitchWeight: Float,
    val pitchOffset: Float,
    val pitchExpo: Float,

    // Yaw curve
    val yawWeight: Float,
    val yawOffset: Float,
    val yawExpo: Float,

    // Throttle curve
    val throttleWeight: Float,
    val throttleOffset: Float,
    val throttleExpo: Float
)

object AppSettingsKeys {

    // ─────────────────────────────────────────────
    // Connection
    // ─────────────────────────────────────────────

    val HOST =
        stringPreferencesKey("host")

    val PORT =
        intPreferencesKey("port")

    val LISTEN_PORT =
        intPreferencesKey("listen_port")

    val DRONE_SYSTEM_ID =
        intPreferencesKey("drone_system_id")

    val DRONE_COMPONENT_ID =
        intPreferencesKey("drone_component_id")

    val AUTO_DETECT =
        booleanPreferencesKey("auto_detect")

    // ─────────────────────────────────────────────
    // Joystick visuals
    // ─────────────────────────────────────────────

    val LEFT_STICK_SIZE_FACTOR = floatPreferencesKey("left_stick_size_factor")
    val RIGHT_STICK_SIZE_FACTOR = floatPreferencesKey("right_stick_size_factor")
    val IS_LEFT_JOYSTICK_IN_THROTTLE_MODE =
        booleanPreferencesKey("IS_LEFT_JOYSTICK_IN_THROTTLE_MODE")
    val IS_RIGHT_JOYSTICK_IN_THROTTLE_MODE =
        booleanPreferencesKey("IS_RIGHT_JOYSTICK_IN_THROTTLE_MODE")
    val SHOW_CIRCULAR_AREA = booleanPreferencesKey("show_circular_area")
    val SHOW_SQUARE_AREA = booleanPreferencesKey("show_square_area")
    val SHOW_CIRCLE_BOUNDARIES = booleanPreferencesKey("show_circle_boundaries")
    val KNOB_COLOR = intPreferencesKey("knob_color")

    // ─────────────────────────────────────────────
    // Roll curve
    // ─────────────────────────────────────────────

    val ROLL_WEIGHT =
        floatPreferencesKey("roll_weight")

    val ROLL_OFFSET =
        floatPreferencesKey("roll_offset")

    val ROLL_EXPO =
        floatPreferencesKey("roll_expo")

    // ─────────────────────────────────────────────
    // Pitch curve
    // ─────────────────────────────────────────────

    val PITCH_WEIGHT =
        floatPreferencesKey("pitch_weight")

    val PITCH_OFFSET =
        floatPreferencesKey("pitch_offset")

    val PITCH_EXPO =
        floatPreferencesKey("pitch_expo")

    // ─────────────────────────────────────────────
    // Yaw curve
    // ─────────────────────────────────────────────

    val YAW_WEIGHT =
        floatPreferencesKey("yaw_weight")

    val YAW_OFFSET =
        floatPreferencesKey("yaw_offset")

    val YAW_EXPO =
        floatPreferencesKey("yaw_expo")

    // ─────────────────────────────────────────────
    // Throttle curve
    // ─────────────────────────────────────────────

    val THROTTLE_WEIGHT =
        floatPreferencesKey("throttle_weight")

    val THROTTLE_OFFSET =
        floatPreferencesKey("throttle_offset")

    val THROTTLE_EXPO =
        floatPreferencesKey("throttle_expo")
}
