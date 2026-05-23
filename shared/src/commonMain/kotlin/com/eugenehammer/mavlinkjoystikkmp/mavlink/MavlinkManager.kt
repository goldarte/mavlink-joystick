package com.eugenehammer.mavlinkjoystikkmp.mavlink

import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.atan2

interface MavlinkManager {
    val consoleFlow: Flow<String>

    var targetHost: String
    var targetPort: Int
    var listenPort: Int
    var droneSystemId: Int
    var droneComponentId: Int
    var autoDetect: Boolean
    var systemId: Int      // GCS system ID
    var componentId: Int    // GCS component ID

    var onStateChanged: ((armed: Boolean, connected: Boolean) -> Unit)?
    var onAttitudeReceived: ((roll: Float, pitch: Float, yaw: Float) -> Unit)?
    var onBatteryVoltageReceived: ((voltage: Float) -> Unit)?
    var onFlightModeReceived: ((mode: String) -> Unit)?
    var onAutopilotNameReceived: ((name: String) -> Unit)?
    var onStatustextReceived: ((text: String, severity: Int) -> Unit)?

    fun start()
    fun stop()
    fun sendArmCommand(arm: Boolean)
    fun sendSerialControl(text: String)
    fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float)
}

abstract class BaseMavlinkManager(
    private val appSettings: AppSettings,
) : MavlinkManager {
    protected abstract val scope: CoroutineScope

    override val consoleFlow: MutableStateFlow<String> = MutableStateFlow("")
    override var targetHost: String = "255.255.255.255"
    override var targetPort: Int = 14550
    override var listenPort: Int = 14550
    override var droneSystemId: Int = 1
    override var droneComponentId: Int = 1
    override var autoDetect: Boolean = true
    override var systemId: Int = 255
    override var componentId: Int = 190

    var isArmed: Boolean = false
        protected set
    var isConnected: Boolean = false
        protected set

    override var onStateChanged: ((armed: Boolean, connected: Boolean) -> Unit)? = null
    override var onAttitudeReceived: ((roll: Float, pitch: Float, yaw: Float) -> Unit)? = null
    override var onBatteryVoltageReceived: ((voltage: Float) -> Unit)? = null
    override var onFlightModeReceived: ((mode: String) -> Unit)? = null
    override var onAutopilotNameReceived: ((name: String) -> Unit)? = null
    override var onStatustextReceived: ((text: String, severity: Int) -> Unit)? = null

    protected var inited: Boolean = false
    protected var stickX: Int = 0
    protected var stickY: Int = 0
    protected var stickZ: Int = 0
    protected var stickR: Int = 0
    protected var lastHeartbeat: Long = 0L

    private var autopilotName: String = "---"

    override fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float) {
        stickX = axisToManual(roll)
        stickY = axisToManual(-pitch)
        stickZ = throttleToManual(throttle)
        stickR = axisToManual(yaw)
    }

    protected fun appendConsoleCommand(text: String) {
        consoleFlow.value += "> $text\n"
    }

    protected fun appendConsoleResponse(text: String) {
        consoleFlow.value += text
    }

    protected fun resetDiscovery() {
        inited = false
        isConnected = false
        onStateChanged?.invoke(isArmed, isConnected)
    }

    protected fun refreshConnection(now: Long, timeoutMillis: Long = MAVLINK_HEARTBEAT_TIMEOUT_MS) {
        val nowConnected = (now - lastHeartbeat) < timeoutMillis
        if (nowConnected != isConnected) {
            isConnected = nowConnected
            onStateChanged?.invoke(isArmed, isConnected)
        }
    }

    protected fun persistDetectedConnection(host: String, port: Int, droneSystemId: Int) {
        scope.launch {
            try {
                appSettings.setDetectedConnection(host, port, droneSystemId)
            } catch (_: Exception) {
                // Keep MAVLink communication running even if settings persistence fails.
            }
        }
    }

    protected fun handleHeartbeat(
        customMode: Long,
        autopilot: MavlinkAutopilot,
        baseMode: Int,
        now: Long,
    ) {
        lastHeartbeat = now
        val beforeConnected = isConnected
        isConnected = true

        onFlightModeReceived?.invoke(getFlightModeName(customMode, autopilot))

        val newAutopilotName = getAutopilotName(autopilot)
        if (autopilotName != newAutopilotName) {
            autopilotName = newAutopilotName
            onAutopilotNameReceived?.invoke(autopilotName)
        }

        val nowArmed = (baseMode and MAV_MODE_FLAG_SAFETY_ARMED) != 0
        if (nowArmed != isArmed || beforeConnected != isConnected) {
            isArmed = nowArmed
            onStateChanged?.invoke(isArmed, isConnected)
        }
    }

    protected fun handleAttitudeQuaternion(w: Double, x: Double, y: Double, z: Double) {
        val roll = atan2(2.0 * (w * x + y * z), 1.0 - 2.0 * (x * x + y * y))
        val pitch = asin((2.0 * (w * y - z * x)).coerceIn(-1.0, 1.0))
        val yaw = atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z))

        onAttitudeReceived?.invoke(
            radiansToDegrees(roll),
            radiansToDegrees(pitch),
            radiansToDegrees(yaw)
        )
    }

    protected fun axisToManual(value: Float): Int = (value.coerceIn(-1f, 1f) * 1000).toInt()

    protected fun throttleToManual(value: Float): Int = (value.coerceIn(0f, 1f) * 1000).toInt()

    protected fun radiansToDegrees(value: Float): Float = (value * 180f / kotlin.math.PI.toFloat())

    private fun radiansToDegrees(value: Double): Float = (value * 180.0 / kotlin.math.PI).toFloat()

    private fun getAutopilotName(autopilot: MavlinkAutopilot): String =
        when (autopilot) {
            MavlinkAutopilot.ArduPilotMega -> "ArduPilot"
            MavlinkAutopilot.Px4 -> "PX4"
            MavlinkAutopilot.Generic -> "Flix"
            MavlinkAutopilot.Other -> ""
        }

    private fun getFlightModeName(mode: Long, autopilot: MavlinkAutopilot): String =
        when (autopilot) {
            MavlinkAutopilot.ArduPilotMega -> when (mode.toInt()) {
                0 -> "STABILIZE"
                1 -> "ACRO"
                2 -> "ALT_HOLD"
                3 -> "AUTO"
                4 -> "GUIDED"
                5 -> "LOITER"
                6 -> "RTL"
                7 -> "CIRCLE"
                9 -> "LAND"
                11 -> "DRIFT"
                13 -> "SPORT"
                14 -> "FLIP"
                15 -> "AUTOTUNE"
                16 -> "POSHOLD"
                17 -> "BRAKE"
                18 -> "THROW"
                19 -> "AVOID_ADSB"
                20 -> "GUIDED_NOGPS"
                21 -> "SMART_RTL"
                22 -> "FLOWHOLD"
                23 -> "FOLLOW"
                24 -> "ZIGZAG"
                25 -> "SYSTEMID"
                26 -> "AUTOROTATE"
                27 -> "AUTO_RTL"
                else -> "MODE($mode)"
            }
            MavlinkAutopilot.Px4 -> {
                val mainMode = ((mode shr 16) and 0xFF).toInt()
                val subMode = ((mode shr 24) and 0xFF).toInt()
                when (mainMode) {
                    1 -> "MANUAL"
                    2 -> "ALTCTL"
                    3 -> "POSCTL"
                    4 -> when (subMode) {
                        2 -> "TAKEOFF"
                        3 -> "LOITER"
                        4 -> "MISSION"
                        5 -> "RTL"
                        6 -> "LAND"
                        8 -> "FOLLOW"
                        else -> "AUTO"
                    }
                    5 -> "ACRO"
                    6 -> "OFFBOARD"
                    7 -> "STABILIZED"
                    else -> "MODE($mainMode:$subMode)"
                }
            }
            MavlinkAutopilot.Generic -> when (mode.toInt()) {
                0 -> "RAW"
                1 -> "ACRO"
                2 -> "STAB"
                3 -> "AUTO"
                else -> "MODE($mode)"
            }
            MavlinkAutopilot.Other -> "MODE($mode)"
        }
}

enum class MavlinkAutopilot {
    ArduPilotMega,
    Px4,
    Generic,
    Other,
}

const val MAVLINK_HEARTBEAT_TIMEOUT_MS = 3000L
private const val MAV_MODE_FLAG_SAFETY_ARMED = 0x80
