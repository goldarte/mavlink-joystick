package com.eugenehammer.mavlinkjoystikkmp.mavlink

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.common.Attitude
import io.dronefleet.mavlink.common.AttitudeQuaternion
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.CommandLong
import io.dronefleet.mavlink.common.ManualControl
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.SerialControl
import io.dronefleet.mavlink.common.SerialControlDev
import io.dronefleet.mavlink.common.SerialControlFlag
import io.dronefleet.mavlink.common.Statustext
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavAutopilot
import io.dronefleet.mavlink.minimal.MavModeFlag
import io.dronefleet.mavlink.minimal.MavState
import io.dronefleet.mavlink.minimal.MavType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages MAVLink communication over UDP.
 *
 * Sends MANUAL_CONTROL (msg 69) to control throttle/yaw/pitch/roll.
 * Receives HEARTBEAT (msg 0) to track armed state and connection health.
 *
 * Defaults: host=192.168.4.1 (ESP telemetry AP), port=14550 (GCS port).
 */
class MavlinkManagerAndroid(
    context: Context?,
    appSettings: AppSettings,
) : BaseMavlinkManager(appSettings) {
    // ── Internals ────────────────────────────────────────────────────────────
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var sendJob: Job? = null
    private var recvJob: Job? = null
    override val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var targetAddress: InetAddress? = null
    private var lastListenAddress: InetAddress? = null
    private var lastListenPort: Int = targetPort

    private val connectivityManager =
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("MavlinkManager", "Network available: resetting discovery (inited = false)")
            resetDiscovery()
        }

        override fun onLost(network: Network) {
            Log.i("MavlinkManager", "Network lost: resetting discovery (inited = false)")
            resetDiscovery()
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    override fun start() {
        if (running.getAndSet(true)) return
        Log.d(
            "MavlinkManager",
            "Starting MAVLink Manager. Target: $targetHost:$targetPort, Listen: $listenPort"
        )

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
            if (!autoDetect) {
                inited = true
            }
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

    override fun stop() {
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

    /** Send MAV_CMD_COMPONENT_ARM_DISARM (400). */
    override fun sendArmCommand(arm: Boolean) {
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
    override fun sendSerialControl(text: String) {
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
        appendConsoleCommand(text)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

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
                refreshConnection(now)
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
                            Log.v(
                                "MavlinkManager",
                                "Received UDP packet: ${datagramPacket.length} bytes from ${datagramPacket.address}:${datagramPacket.port}"
                            )
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
                    if (autoDetect && !inited && payload is Heartbeat) {
                        lastListenAddress?.let { addr ->
                            if (addr is Inet4Address && addr.hostAddress != null) {
                                droneSystemId = message.originSystemId

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
                                persistDetectedConnection(targetHost, targetPort, droneSystemId)
                            }
                        }
                    }
                    // check that message is received from target drone
                    if (message.originSystemId != droneSystemId || message.originComponentId != droneComponentId) continue
                    Log.v(
                        "MavlinkManager",
                        "Received: ${payload.javaClass.simpleName} from ${message.originSystemId}"
                    )
                    when (payload) {
                        is Heartbeat -> handleHeartbeat(payload)
                        is Attitude -> {
                            Log.v(
                                "MavlinkManager",
                                "Attitude: R=${payload.roll()}, P=${payload.pitch()}, Y=${payload.yaw()}"
                            )
                            handleAttitude(payload)
                        }

                        is AttitudeQuaternion -> {
                            Log.v(
                                "MavlinkManager",
                                "AttitudeQuaternion: q=[${payload.q1()}, ${payload.q2()}, ${payload.q3()}, ${payload.q4()}]"
                            )
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
                            Log.i(
                                "MavlinkManager",
                                "Statustext: ${payload.text()} (severity=${
                                    payload.severity().value()
                                })"
                            )
                            onStatustextReceived?.invoke(payload.text(), payload.severity().value())
                        }

                        is SerialControl -> {
                            Log.v("MavlinkManager", "SerialControl: ${payload.count()} bytes")
                            val data = payload.data().copyOfRange(0, payload.count())
                            appendConsoleResponse(String(data, Charsets.UTF_8))
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
                val outputStream = ByteArrayOutputStream()
                val connection = MavlinkConnection.create(null, outputStream)
                // Use send2 for MAVLink v2 as per user request
                connection.send2(systemId, componentId, payload)

                val packetData = outputStream.toByteArray()
                val dp = DatagramPacket(packetData, packetData.size, addr, targetPort)
                socket?.send(dp)
            } catch (e: Exception) {
                Log.e(
                    "MavlinkManager",
                    "Error sending MAVLink message to ${targetHost}:${targetPort}",
                    e
                )
            }
        }
    }

    private fun handleHeartbeat(heartbeat: Heartbeat) {
        Log.i("MavlinkManager", "Got Heartbeat: ${heartbeat}")

        handleHeartbeat(
            customMode = heartbeat.customMode(),
            autopilot = heartbeat.autopilot().entry().toCommonAutopilot(),
            baseMode = heartbeat.baseMode().value(),
            now = System.currentTimeMillis()
        )
    }

    private fun handleAttitude(attitude: Attitude) {
        onAttitudeReceived?.invoke(
            Math.toDegrees(attitude.roll().toDouble()).toFloat(),
            Math.toDegrees(attitude.pitch().toDouble()).toFloat(),
            Math.toDegrees(attitude.yaw().toDouble()).toFloat()
        )
    }

    private fun handleAttitudeQuaternion(aq: AttitudeQuaternion) {
        handleAttitudeQuaternion(
            w = aq.q1().toDouble(),
            x = aq.q2().toDouble(),
            y = aq.q3().toDouble(),
            z = aq.q4().toDouble()
        )
    }
}

private fun MavAutopilot.toCommonAutopilot(): MavlinkAutopilot =
    when (this) {
        MavAutopilot.MAV_AUTOPILOT_ARDUPILOTMEGA -> MavlinkAutopilot.ArduPilotMega
        MavAutopilot.MAV_AUTOPILOT_PX4 -> MavlinkAutopilot.Px4
        MavAutopilot.MAV_AUTOPILOT_GENERIC -> MavlinkAutopilot.Generic
        else -> MavlinkAutopilot.Other
    }
