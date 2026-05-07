// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick.mavlink

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.common.*
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavAutopilot
import io.dronefleet.mavlink.minimal.MavModeFlag
import io.dronefleet.mavlink.minimal.MavState
import io.dronefleet.mavlink.minimal.MavType
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.edit

/**
 * Manages MAVLink communication over UDP.
 *
 * Sends MANUAL_CONTROL (msg 69) to control throttle/yaw/pitch/roll.
 * Receives HEARTBEAT (msg 0) to track armed state and connection health.
 *
 * Defaults: host=192.168.4.1 (ESP telemetry AP), port=14550 (GCS port).
 */
class MavlinkManager internal constructor(
    private val context: Context?
) {
    var targetHost: String = "255.255.255.255"
    var targetPort: Int = 14550
    var listenPort: Int = 14550
    var droneSystemId: Int = 1
    var droneComponentId: Int = 1
    var systemId: Int = 255      // GCS system ID
    var componentId: Int = 190    // GCS component ID

    companion object {
        @Volatile
        private var INSTANCE: MavlinkManager? = null

        fun getInstance(context: Context): MavlinkManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MavlinkManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ── State ────────────────────────────────────────────────────────────────
    var isArmed: Boolean = false
        private set
    var isConnected: Boolean = false
        private set
    var onStateChanged: ((armed: Boolean, connected: Boolean) -> Unit)? = null
    var onAttitudeReceived: ((roll: Float, pitch: Float, yaw: Float) -> Unit)? = null
    var onBatteryVoltageReceived: ((voltage: Float) -> Unit)? = null
    var onFlightModeReceived: ((mode: String) -> Unit)? = null
    var onAutopilotNameReceived: ((name: String) -> Unit)? = null
    var onStatustextReceived: ((text: String, severity: Int) -> Unit)? = null
    var onSerialControlReceived: ((data: ByteArray) -> Unit)? = null
    // ── Drone ID Logic ───────────────────────────────────────────────────────
    internal var inited: Boolean = false

    // ── Manual Control (-1000..1000) ────────────────────────────────────────
    private var stickX: Int = 0 // Roll
    private var stickY: Int = 0 // Pitch
    private var stickZ: Int = 0 // Throttle (0..1000 or -1000..1000)
    private var stickR: Int = 0 // Yaw

    // ── Internals ────────────────────────────────────────────────────────────
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var sendJob: Job? = null
    private var recvJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastHeartbeat: Long = 0L
    private val HEARTBEAT_TIMEOUT_MS = 3000L

    private var targetAddress: InetAddress? = null
    private var lastListenAddress: InetAddress? = null
    private var lastListenPort: Int = targetPort
    private var autopilot_name: String = "---"

    private val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("MavlinkManager", "Network available: resetting discovery (inited = false)")
            inited = false
            isConnected = false
            onStateChanged?.invoke(isArmed, isConnected)
        }

        override fun onLost(network: Network) {
            Log.i("MavlinkManager", "Network lost: resetting discovery (inited = false)")
            inited = false
            isConnected = false
            onStateChanged?.invoke(isArmed, isConnected)
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun start() {
        if (running.getAndSet(true)) return
        Log.d("MavlinkManager", "Starting MAVLink Manager. Target: $targetHost:$targetPort, Listen: $listenPort")
        
        // Register for network changes
        try {
            connectivityManager?.let { cm ->
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, networkCallback)
            }
        } catch (e: Exception) {
            Log.e("MavlinkManager", "Failed to register network callback", e)
        }

        scope.launch {
            updateTargetAddress()
            try {
                // Bind to listenPort. If fails (e.g. port taken by QGC), bind to any available port
                socket = try {
                    DatagramSocket(listenPort).also {
                        Log.d("MavlinkManager", "Bound to port ${it.localPort}")
                    }
                } catch (e: Exception) {
                    Log.w("MavlinkManager", "Failed to bind to $listenPort, using ephemeral port")
                    DatagramSocket()
                }
                socket?.soTimeout = 500
            } catch (e: Exception) {
                Log.e("MavlinkManager", "Critical error opening socket", e)
                running.set(false)
                return@launch
            }
            startSendLoop()
            startReceiveLoop()
        }
    }

    fun stop() {
        running.set(false)
        sendJob?.cancel()
        recvJob?.cancel()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
        socket?.close()
        socket = null
        inited = false
    }

    /** Update control values. All inputs are -1.0..1.0 (throttle 0..1). */
    fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float) {
        stickX = axisToManual(roll)
        stickY = axisToManual(-pitch) // Invert: forward stick = positive pitch
        stickZ = throttleToManual(throttle)
        stickR = axisToManual(yaw)
    }

    /** Send MAV_CMD_COMPONENT_ARM_DISARM (400). */
    fun sendArmCommand(arm: Boolean) {
        scope.launch {
            val command = CommandLong.builder()
                .targetSystem(droneSystemId)
                .targetComponent(droneComponentId)
                .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                .param1(if (arm) 1f else 0f)
                .build()
            sendMavlinkMessage(command)
        }
    }

    /** Send a command via SERIAL_CONTROL (msg #126) with DEV_SHELL flag. */
    fun sendSerialControl(text: String) {
        scope.launch {
            val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
            
            // SerialControl payload 'data' is 70 bytes.
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(bytes.size - offset, 70)
                val chunk = ByteArray(70)
                System.arraycopy(bytes, offset, chunk, 0, count)
                
                val sc = SerialControl.builder()
                    .device(SerialControlDev.SERIAL_CONTROL_DEV_SHELL)
                    .flags(SerialControlFlag.SERIAL_CONTROL_FLAG_RESPOND)
                    .timeout(0)
                    .baudrate(0)
                    .count(count)
                    .data(chunk)
                    .build()
                
                sendMavlinkMessage(sc)
                offset += count
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun axisToManual(v: Float) = (v.coerceIn(-1f, 1f) * 1000).toInt()
    private fun throttleToManual(v: Float) = (v.coerceIn(0f, 1f) * 1000).toInt()

    private fun updateTargetAddress() {
        try {
            targetAddress = InetAddress.getByName(targetHost)
        } catch (e: Exception) {
            targetAddress = null
        }
    }

    private fun startSendLoop() {
        sendJob = scope.launch {
            var lastHeartbeatSentTime = 0L
            while (running.get()) {
                val now = System.currentTimeMillis()
                if (inited) {
                    sendManualControl()
                    if (now - lastHeartbeatSentTime >= 1000L) {
                        sendHeartbeat()
                        lastHeartbeatSentTime = now
                    }
                }
                
                // Refresh connection status
                val nowConnected = (now - lastHeartbeat) < HEARTBEAT_TIMEOUT_MS
                if (nowConnected != isConnected) {
                    isConnected = nowConnected
                    onStateChanged?.invoke(isArmed, isConnected)
                }
                delay(20)  // 50 Hz
            }
        }
    }

    private fun startReceiveLoop() {
        recvJob = scope.launch {
            val datagramPacket = DatagramPacket(ByteArray(1024), 1024)
            
            // Use an InputStream adapter for DatagramSocket
            val inputStream = object : InputStream() {
                private var buffer: ByteArray? = null
                private var pos = 0
                private var limit = 0

                override fun read(): Int {
                    while (running.get()) {
                        if (pos < limit) {
                            return buffer!![pos++].toInt() and 0xFF
                        }
                        try {
                            socket?.receive(datagramPacket)
                            Log.v("MavlinkManager", "Received UDP packet: ${datagramPacket.length} bytes from ${datagramPacket.address}:${datagramPacket.port}")
                            lastListenAddress = datagramPacket.address
                            lastListenPort = datagramPacket.port
                            buffer = datagramPacket.data
                            pos = 0
                            limit = datagramPacket.length
                        } catch (e: IOException) {
                            // Timeout or socket closed – keep trying until running is false
                        }
                    }
                    return -1
                }
            }

            val connection = MavlinkConnection.create(inputStream, null)

            while (running.get()) {
                try {
                    val message = connection.next() ?: continue
                    
                    val payload = message.payload

                    // Discovery logic: Update drone ID and target host if we see a heartbeat
                    if (!inited && payload is Heartbeat) {
                        lastListenAddress?.let { addr ->
                            if (addr is Inet4Address && addr.hostAddress != null) {
                                droneSystemId = message.originSystemId

                                Log.i(
                                    "MavlinkManager",
                                    "Discovered Drone: SysID=${message.originSystemId}, CompID=${message.originComponentId} on ${addr.hostAddress}:${lastListenPort}"
                                )

                                targetHost = addr.hostAddress!!
                                targetPort = lastListenPort

                                val prefs = context?.getSharedPreferences("mavlink_prefs", Context.MODE_PRIVATE)!!
                                prefs.edit(commit = true) {
                                    putString("host", targetHost)
                                    putInt("port", targetPort)
                                    putInt("drone_system_id", droneSystemId)
                                }

                                updateTargetAddress()
                                inited = true
                                isConnected = true
                                onStateChanged?.invoke(isArmed, isConnected)
                            }
                        }
                    }
                    // check that message is received from target drone
                    if (message.originSystemId != droneSystemId || message.originComponentId != droneComponentId) continue
                    Log.v("MavlinkManager", "Received: ${payload.javaClass.simpleName} from ${message.originSystemId}")
                    when (payload) {
                        is Heartbeat -> handleHeartbeat(payload)
                        is Attitude -> {
                            Log.v("MavlinkManager", "Attitude: R=${payload.roll()}, P=${payload.pitch()}, Y=${payload.yaw()}")
                            handleAttitude(payload)
                        }
                        is AttitudeQuaternion -> {
                            Log.v("MavlinkManager", "AttitudeQuaternion: q=[${payload.q1()}, ${payload.q2()}, ${payload.q3()}, ${payload.q4()}]")
                            handleAttitudeQuaternion(payload)
                        }
                        is BatteryStatus -> {
                            // Sum all non-UINT16_MAX voltages
                            val totalMv = payload.voltages().filter { it < 65535 }.sum()
                            val volt = totalMv.toFloat() / 1000f
                            Log.v("MavlinkManager", "Battery: $volt V")
                            onBatteryVoltageReceived?.invoke(volt)
                        }
                        is Statustext -> {
                            Log.i("MavlinkManager", "Statustext: ${payload.text()} (severity=${payload.severity().value()})")
                            onStatustextReceived?.invoke(payload.text(), payload.severity().value())
                        }
                        is SerialControl -> {
                            Log.v("MavlinkManager", "SerialControl: ${payload.count()} bytes")
                            val data = payload.data().copyOfRange(0, payload.count())
                            onSerialControlReceived?.invoke(data)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MavlinkManager", "Error parsing MAVLink message", e)
                }
            }
        }
    }

    private fun sendManualControl() {
        val manualControl = ManualControl.builder()
            .target(droneSystemId)
            .x(stickY) // Pitch
            .y(stickX) // Roll
            .z(stickZ) // Throttle
            .r(stickR) // Yaw
            .buttons(0)
            .build()
        sendMavlinkMessage(manualControl)
    }

    private fun sendHeartbeat() {
        val heartbeat = Heartbeat.builder()
            .type(MavType.MAV_TYPE_GCS)
            .autopilot(MavAutopilot.MAV_AUTOPILOT_GENERIC)
            .baseMode(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED) // Standard GCS mode
            .systemStatus(MavState.MAV_STATE_ACTIVE)
            .mavlinkVersion(3) // Standard for MAVLink
            .build()
        sendMavlinkMessage(heartbeat)
    }

    private fun sendMavlinkMessage(payload: Any) {
        val addr = targetAddress ?: return
        scope.launch {
            try {
                val outputStream = java.io.ByteArrayOutputStream()
                val connection = MavlinkConnection.create(null, outputStream)
                // Use send2 for MAVLink v2 as per user request
                connection.send2(systemId, componentId, payload)

                val packetData = outputStream.toByteArray()
                val dp = DatagramPacket(packetData, packetData.size, addr, targetPort)
                socket?.send(dp)
            } catch (e: Exception) {
                Log.e("MavlinkManager", "Error sending MAVLink message to ${targetHost}:${targetPort}", e)
            }
        }
    }

    private fun handleHeartbeat(heartbeat: Heartbeat) {
        lastHeartbeat = System.currentTimeMillis()
        val beforeConnected = isConnected
        isConnected = true
        Log.i("MavlinkManager", "Got Heartbeat: ${heartbeat}")

        // Flight mode
        val modeName = getFlightModeName(heartbeat)
        onFlightModeReceived?.invoke(modeName)

        val autopilot = heartbeat.autopilot().entry()
        val current_autopilot_name = autopilot_name
        if (autopilot == MavAutopilot.MAV_AUTOPILOT_ARDUPILOTMEGA) {
            autopilot_name = "ArduPilot"
        } else if (autopilot == MavAutopilot.MAV_AUTOPILOT_PX4) {
            autopilot_name = "PX4"
        } else if (autopilot == MavAutopilot.MAV_AUTOPILOT_GENERIC){
            autopilot_name = "Flix"
        } else {
            autopilot_name = ""
        }

        if (current_autopilot_name != autopilot_name) {
            onAutopilotNameReceived?.invoke(autopilot_name)
        }

        // Try raw bitwise check if flags() doesn't exist
        val nowArmed = (heartbeat.baseMode().value() and 0x80) != 0
        if (nowArmed != isArmed || !beforeConnected) {
            isArmed = nowArmed
            onStateChanged?.invoke(isArmed, isConnected)
        }
    }

    private fun getFlightModeName(heartbeat: Heartbeat): String {
        val mode = heartbeat.customMode()
        val autopilot = heartbeat.autopilot().entry()

        if (autopilot == MavAutopilot.MAV_AUTOPILOT_ARDUPILOTMEGA) {
            return when (mode.toInt()) {
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
        } else if (autopilot == MavAutopilot.MAV_AUTOPILOT_PX4) {
            val customMode = mode.toLong()
            val mainMode = ((customMode shr 16) and 0xFF).toInt()
            val subMode = ((customMode shr 24) and 0xFF).toInt()

            return when (mainMode) {
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
        } else if (autopilot == MavAutopilot.MAV_AUTOPILOT_GENERIC) {
            // just detecting drone as flix
            return when (mode.toInt()) {
                0 -> "RAW"
                1 -> "ACRO"
                2 -> "STAB"
                3 -> "AUTO"
                else -> "MODE($mode)"
            }
        }
        return "MODE($mode)"
    }

    private fun handleAttitude(attitude: Attitude) {
        onAttitudeReceived?.invoke(
            Math.toDegrees(attitude.roll().toDouble()).toFloat(),
            Math.toDegrees(attitude.pitch().toDouble()).toFloat(),
            Math.toDegrees(attitude.yaw().toDouble()).toFloat()
        )
    }

    private fun handleAttitudeQuaternion(aq: AttitudeQuaternion) {
        val w = aq.q1()
        val x = aq.q2()
        val y = aq.q3()
        val z = aq.q4()

        // https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles#Quaternion_to_Euler_angles_conversion
        val roll  = Math.atan2(2.0 * (w * x + y * z), 1.0 - 2.0 * (x * x + y * y))
        val pitch = Math.asin((2.0 * (w * y - z * x)).coerceIn(-1.0, 1.0))
        val yaw   = Math.atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z))

        onAttitudeReceived?.invoke(
            Math.toDegrees(roll).toFloat(),
            Math.toDegrees(pitch).toFloat(),
            Math.toDegrees(yaw).toFloat()
        )
    }
}
