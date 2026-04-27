package com.goldarte.mavlinkjoystick.mavlink

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
    var targetHost: String = "192.168.4.1",
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

    // ── Public API ───────────────────────────────────────────────────────────

    fun start() {
        if (running.getAndSet(true)) return
        try {
            // Bind to listenPort to receive and send from the same port (GCS pattern)
            socket = DatagramSocket(listenPort)
            socket?.soTimeout = 500
        } catch (e: Exception) {
            e.printStackTrace()
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
            .targetSystem(1)
            .targetComponent(1)
            .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
            .param1(if (arm) 1f else 0f)
            .build()
        sendMavlinkMessage(command)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun axisToMicros(v: Float) = (1500 + (v.coerceIn(-1f, 1f) * 500)).toInt()
    private fun throttleToMicros(v: Float) = (1000 + v.coerceIn(0f, 1f) * 1000).toInt()

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
                    if (pos >= limit) {
                        try {
                            socket?.receive(datagramPacket)
                            buffer = datagramPacket.data
                            pos = 0
                            limit = datagramPacket.length
                        } catch (e: IOException) {
                            return -1
                        }
                    }
                    return if (limit == 0) -1 else buffer!![pos++].toInt() and 0xFF
                }
            }

            val connection = MavlinkConnection.create(inputStream, null)

            while (running.get()) {
                try {
                    val message = connection.next() ?: continue
                    val payload = message.payload
                    when (payload) {
                        is Heartbeat -> handleHeartbeat(payload)
                        is Attitude -> handleAttitude(payload)
                    }
                } catch (e: Exception) {
                    // Timeout or socket closed – loop
                }
            }
        }
    }

    private fun sendRcOverride() {
        val rcOverride = RcChannelsOverride.builder()
            .targetSystem(1)
            .targetComponent(1)
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
            .baseMode(MavModeFlag.MAV_MODE_FLAG_SAFETY_ARMED) // Just a placeholder
            .systemStatus(MavState.MAV_STATE_ACTIVE)
            .mavlinkVersion(3)
            .build()
        sendMavlinkMessage(heartbeat)
    }

    private fun sendMavlinkMessage(payload: Any) {
        scope.launch {
            try {
                val outputStream = java.io.ByteArrayOutputStream()
                val connection = MavlinkConnection.create(null, outputStream)
                connection.send2(systemId, componentId, payload)
                
                val packetData = outputStream.toByteArray()
                val addr = InetAddress.getByName(targetHost)
                val dp = DatagramPacket(packetData, packetData.size, addr, targetPort)
                socket?.send(dp)
            } catch (e: Exception) {
                // Network may not be available yet
            }
        }
    }

    private fun handleHeartbeat(heartbeat: Heartbeat) {
        lastHeartbeat = System.currentTimeMillis()
        isConnected = true
        // Try raw bitwise check if flags() doesn't exist
        val nowArmed = (heartbeat.baseMode().value() and 0x80) != 0
        if (nowArmed != isArmed) {
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
}
