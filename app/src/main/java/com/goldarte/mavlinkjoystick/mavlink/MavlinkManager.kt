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

/**
 * Manages MAVLink communication over UDP.
 *
 * Sends MANUAL_CONTROL (msg 69) to control throttle/yaw/pitch/roll.
 * Receives HEARTBEAT (msg 0) to track armed state and connection health.
 *
 * Defaults: host=192.168.4.1 (ESP telemetry AP), port=14550 (GCS port).
 */
class MavlinkManager(
    private val context: Context,
    var targetHost: String = "255.255.255.255",
    var targetPort: Int = 14550,
    var listenPort: Int = 14550,
    var systemId: Int = 255,      // GCS system ID
    var componentId: Int = 190    // GCS component ID
) {
    // ── State ────────────────────────────────────────────────────────────────
    var isArmed: Boolean = false
        private set
    var isConnected: Boolean = false
        private set
    var onStateChanged: ((armed: Boolean, connected: Boolean) -> Unit)? = null
    var onAttitudeReceived: ((roll: Float, pitch: Float, yaw: Float) -> Unit)? = null
    var onBatteryVoltageReceived: ((voltage: Float) -> Unit)? = null

    // ── Drone Discovery ──────────────────────────────────────────────────────
    private var droneSystemId: Int = 1
    private var droneComponentId: Int = 1
    private var inited: Boolean = false

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

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
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
            connectivityManager.unregisterNetworkCallback(networkCallback)
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
                                droneComponentId = message.originComponentId

                                Log.i(
                                    "MavlinkManager",
                                    "Discovered Drone: SysID=${message.originSystemId}, CompID=${message.originComponentId} on ${addr.hostAddress}:${lastListenPort}"
                                )

                                targetHost = addr.hostAddress!!
                                targetPort = lastListenPort

                                updateTargetAddress()
                                inited = true
                                isConnected = true
                                onStateChanged?.invoke(isArmed, isConnected)
                            }
                        }
                    }

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
        // Try raw bitwise check if flags() doesn't exist
        val nowArmed = (heartbeat.baseMode().value() and 0x80) != 0
        if (nowArmed != isArmed || !beforeConnected) {
            isArmed = nowArmed
            onStateChanged?.invoke(isArmed, isConnected)
        }
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
