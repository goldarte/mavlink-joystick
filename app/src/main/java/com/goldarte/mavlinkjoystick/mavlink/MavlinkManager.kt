package com.goldarte.mavlinkjoystick.mavlink

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
 * Sends RC_CHANNELS_OVERRIDE (msg 70) to control throttle/yaw/pitch/roll.
 * Receives HEARTBEAT (msg 0) to track armed state and connection health.
 *
 * Defaults: host=192.168.4.1 (ESP telemetry AP), port=14550 (GCS port).
 */
class MavlinkManager(
    var targetHost: String = "0.0.0.0",
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

    // ── Drone Discovery ──────────────────────────────────────────────────────
    private var droneSystemId: Int = -1
    private var droneComponentId: Int = -1

    // ── Channels (PWM 1000–2000 µs, centre = 1500) ───────────────────────────
    // ch1=Roll, ch2=Pitch, ch3=Throttle, ch4=Yaw  (Mode 2 mapping)
    private var ch1Roll: Int = 1500
    private var ch2Pitch: Int = 1500
    private var ch3Throttle: Int = 1000   // Throttle defaults low
    private var ch4Yaw: Int = 1500

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

    // ── Public API ───────────────────────────────────────────────────────────

    fun start() {
        if (running.getAndSet(true)) return
        Log.d("MavlinkManager", "Starting MAVLink Manager. Target: $targetHost:$targetPort, Listen: $listenPort")
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
            return
        }
        startSendLoop()
        startReceiveLoop()
    }

    fun stop() {
        running.set(false)
        sendJob?.cancel()
        recvJob?.cancel()
        socket?.close()
        socket = null
    }

    /** Update channel values. All inputs are -1.0..1.0 (throttle 0..1). */
    fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float) {
        ch1Roll      = axisToMicros(roll)
        ch2Pitch     = axisToMicros(-pitch)   // Invert: forward stick = positive pitch
        ch3Throttle  = throttleToMicros(throttle)
        ch4Yaw       = axisToMicros(yaw)
    }

    /** Send MAV_CMD_COMPONENT_ARM_DISARM (400). */
    fun sendArmCommand(arm: Boolean) {
        val command = CommandLong.builder()
            .targetSystem(droneSystemId)
            .targetComponent(droneComponentId)
            .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
            .param1(if (arm) 1f else 0f)
            .build()
        sendMavlinkMessage(command)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun axisToMicros(v: Float) = (1500 + (v.coerceIn(-1f, 1f) * 500)).toInt()
    private fun throttleToMicros(v: Float) = (1000 + v.coerceIn(0f, 1f) * 1000).toInt()

    private fun updateTargetAddress() {
        scope.launch {
            try {
                targetAddress = InetAddress.getByName(targetHost)
            } catch (e: Exception) {
                targetAddress = null
            }
        }
    }

    private fun startSendLoop() {
        sendJob = scope.launch {
            while (running.get()) {
                sendRcOverride()
                sendHeartbeat()
                
                // Refresh connection status
                val nowConnected = (System.currentTimeMillis() - lastHeartbeat) < HEARTBEAT_TIMEOUT_MS
                if (nowConnected != isConnected) {
                    isConnected = nowConnected
                    onStateChanged?.invoke(isArmed, isConnected)
                }
                delay(50)  // 20 Hz
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
                    
                    if (droneSystemId == -1 && droneComponentId == -1 && message.originSystemId != 0 && lastListenAddress is Inet4Address) {
                        Log.i("MavlinkManager", "Discovered Drone: SysID=${message.originSystemId}, CompID=${message.originComponentId} on ${lastListenAddress}")
                        droneSystemId = message.originSystemId
                        droneComponentId = message.originComponentId
                        if (targetHost == "0.0.0.0" && lastListenAddress != null) {
                            targetHost = lastListenAddress!!.hostAddress
                        }
                    }
                    
                    val payload = message.payload
                    Log.i("MavlinkManager", "payload = ${payload}")
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
                    }
                } catch (e: Exception) {
                    Log.e("MavlinkManager", "Error parsing MAVLink message", e)
                }
            }
        }
    }

    private fun sendRcOverride() {
        val rcOverride = RcChannelsOverride.builder()
            .targetSystem(droneSystemId)
            .targetComponent(droneComponentId)
            .chan1Raw(ch1Roll)
            .chan2Raw(ch2Pitch)
            .chan3Raw(ch3Throttle)
            .chan4Raw(ch4Yaw)
            .build()
        sendMavlinkMessage(rcOverride)
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
        try {
            val outputStream = java.io.ByteArrayOutputStream()
            val connection = MavlinkConnection.create(null, outputStream)
            // Use send2 for MAVLink v2 as per user request
            connection.send2(systemId, componentId, payload)
            
            val packetData = outputStream.toByteArray()
            val dp = DatagramPacket(packetData, packetData.size, addr, targetPort)
            socket?.send(dp)
        } catch (e: Exception) {
            // Network may not be available yet
        }
    }

    private fun handleHeartbeat(heartbeat: Heartbeat) {
        lastHeartbeat = System.currentTimeMillis()
        val beforeConnected = isConnected
        isConnected = true
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
