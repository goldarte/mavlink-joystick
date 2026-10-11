package com.goldarte.mavlinkjoystick.mavlink

import android.content.Context
import android.util.Log
import com.goldarte.mavlinkjoystick.data.AppSettings
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages MAVLink communication over UDP.
 *
 * Sends MANUAL_CONTROL (msg 69) to control throttle/yaw/pitch/roll.
 * Receives HEARTBEAT (msg 0) to track armed state and connection health.
 *
 * Defaults: host=192.168.4.1 (ESP telemetry AP), port=14550 (GCS port).
 *
 * The link follows the Wi-Fi network: Android may prefer mobile data as its default network, and
 * the telemetry access point usually has no internet, so the UDP socket is bound to the currently
 * available Wi-Fi network and the whole transport is rebuilt whenever that network changes.
 */
class MavlinkManagerAndroid internal constructor(
    context: Context?,
    appSettings: AppSettings,
    private val networkBinder: NetworkBinder = AndroidNetworkBinder(context),
) : BaseMavlinkManager(appSettings) {
    // ── Internals ────────────────────────────────────────────────────────────
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var sendJob: Job? = null
    private var recvJob: Job? = null
    override val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val networkDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var targetAddress: InetAddress? = null
    private var lastListenAddress: InetAddress? = null
    private var lastListenPort: Int = targetPort

    private var wifiNetwork: WifiNetwork? = null
    private var broadcastAddress: InetAddress? = null
    private var networkListener: NetworkBinder.Listener? = null
    private var retryJob: Job? = null

    // ── Public API ───────────────────────────────────────────────────────────

    @Synchronized
    override fun start() {
        if (running.getAndSet(true)) return
        val listener = NetworkEvents()
        val attached = networkBinder.attachListener(listener)
        listener.attached = attached
        networkListener = attached

        scope.launch(networkDispatcher) {
            synchronized(this@MavlinkManagerAndroid) {
                if (running.get() && networkListener === attached && socket == null) restartTransport()
            }
        }
    }

    /**
     * Reacts to Wi-Fi changes on [networkDispatcher]. All state is touched under the manager lock,
     * and events from a listener that has been replaced by a new [start] are ignored.
     */
    private inner class NetworkEvents : NetworkBinder.Listener {

        /** The token returned by the binder, used to recognize stale callbacks. */
        var attached: NetworkBinder.Listener? = null

        override fun onNetworkAvailable(network: WifiNetwork) {
            scope.launch(networkDispatcher) {
                synchronized(this@MavlinkManagerAndroid) {
                    if (!running.get() || networkListener == null || networkListener !== attached) return@launch
                    if (!network.isUsable) return@launch
                    // A usable Wi-Fi network appeared: a fresh access point, the drone network
                    // replacing home Wi-Fi, or the bound network that just received its DHCP
                    // address. Repeated events for the network already in use are ignored.
                    if (wifiNetwork == network) return@launch
                    wifiNetwork = network
                    restartTransport()
                }
            }
        }

        override fun onNetworkLost(network: WifiNetwork) {
            scope.launch(networkDispatcher) {
                synchronized(this@MavlinkManagerAndroid) {
                    if (!running.get() || networkListener == null || networkListener !== attached) return@launch
                    // Only tear the transport down when the network that carries MAVLink
                    // disappeared. Losing an unrelated Wi-Fi network must not drop the link.
                    if (wifiNetwork != network) return@launch
                    wifiNetwork = null
                    restartTransport()
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        running.set(false)
        networkListener?.let { networkBinder.detachListener(it) }
        networkListener = null
        wifiNetwork = null
        closeTransport()
        resetDiscovery()
    }

    // Called under the manager lock; old callbacks and loops cannot update a new session.
    private fun closeTransport() {
        retryJob?.cancel()
        sendJob?.cancel()
        recvJob?.cancel()
        // Closing releases the listening port so the next transport can take it over right away.
        socket?.close()
        socket = null
        // The network the transport was bound to is meaningless once its socket is gone; leaving it
        // set would make the next network change look like a no-op.
        wifiNetwork = null
        broadcastAddress = null
    }

    private fun restartTransport() {
        closeTransport()
        resetDiscovery()
        lastListenAddress = null
        updateTargetAddress()
        if (bindNetwork() == null) {
            // No usable Wi-Fi (yet) or the socket could not be created. Retry so the link comes up
            // even when the network callback never fires for an already connected access point.
            Log.w("MavlinkManager", "No usable Wi-Fi network; retrying MAVLink transport")
            retryJob = scope.launch {
                delay(RETRY_DELAY_MS.milliseconds)
                synchronized(this@MavlinkManagerAndroid) {
                    if (isActive && running.get() && socket == null) restartTransport()
                }
            }
            return
        }
        val transport = socket ?: return
        startSendLoop(transport)
        startReceiveLoop(transport)
    }

    /**
     * Opens the UDP socket on the best usable Wi-Fi network and publishes it as the current
     * [socket].
     *
     * Returns `null` when no socket could be created, so the caller can retry instead of silently
     * running without a transport.
     */
    private fun bindNetwork(): DatagramSocket? {
        val candidates = networkBinder.candidateNetworks()
        var lastError: Exception? = null
        for (network in candidates) {
            try {
                val newSocket = openSocket(network)
                // The send/receive loops read this field, so it must point at the new socket before
                // they start; otherwise the freshly created transport is mistaken for a stale one.
                socket = newSocket
                // A transport is up now; a pending retry would only rebuild it pointlessly.
                retryJob?.cancel()
                retryJob = null
                wifiNetwork = network
                broadcastAddress = network.broadcastAddress?.let { address ->
                    runCatching { InetAddress.getByName(address) }.getOrNull()
                }
                Log.i(
                    "MavlinkManager",
                    "MAVLink transport ready on ${network.description}, local port ${newSocket.localPort}"
                )
                return newSocket
            } catch (e: Exception) {
                lastError = e
                socket = null
                Log.w("MavlinkManager", "Cannot use ${network.description} for MAVLink", e)
            }
        }
        if (lastError != null) {
            Log.e("MavlinkManager", "Cannot open UDP socket; retrying", lastError)
        } else {
            Log.w("MavlinkManager", "No usable Wi-Fi network for MAVLink; retrying")
        }
        return null
    }

    private fun openSocket(network: WifiNetwork): DatagramSocket {
        val newSocket = DatagramSocket(null)
        try {
            val boundToConfiguredPort = try {
                newSocket.bind(InetSocketAddress(listenPort))
                true
            } catch (e: Exception) {
                false
            }
            // Joining another network can briefly keep the previous port busy. Losing the GCS port
            // is far worse than losing the configured port, so fall back to an ephemeral one.
            if (!boundToConfiguredPort) {
                newSocket.bind(InetSocketAddress(0))
                Log.w(
                    "MavlinkManager",
                    "Port $listenPort is busy; MAVLink listens on ${newSocket.localPort} instead"
                )
            }
            // Both flags have to be in place before bindSocket().
            newSocket.broadcast = true
            newSocket.soTimeout = 500
            networkBinder.bindSocket(network, newSocket)
            return newSocket
        } catch (e: Exception) {
            newSocket.close()
            throw e
        }
    }

    /** Send MAV_CMD_COMPONENT_ARM_DISARM (400). */
    override fun sendArmCommand(arm: Boolean) {
        scope.launch {
            synchronized(this@MavlinkManagerAndroid) {
                if (arm) {
                    stickZ = 0
                    stickR = 0
                    // Send the reset before ARM, without waiting for the 50 Hz loop.
                    sendManualControl()
                }
                val command = CommandLong.builder()
                    .targetSystem(droneSystemId)
                    .targetComponent(droneComponentId)
                    .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                    .param1(if (arm) 1f else 0f)
                    .build()
                sendMavlinkMessage(command)
            }
        }
    }

    @Synchronized
    override fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float) {
        super.setChannels(roll, pitch, throttle, yaw)
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

    private fun startSendLoop(transport: DatagramSocket) {
        sendJob = scope.launch {
            var lastHeartbeatSentTime = 0L
            while (isActive && running.get() && !transport.isClosed) {
                synchronized(this@MavlinkManagerAndroid) {
                    if (socket !== transport) return@launch
                    val now = System.currentTimeMillis()
                    refreshConnection(now)
                    // The GCS heartbeat is sent before the link is up as well: a drone that only
                    // answers a known GCS would otherwise never learn the new phone address after
                    // the Wi-Fi network (and therefore the phone IP) changed.
                    val discovering = !inited
                    val heartbeatInterval =
                        if (discovering) DISCOVERY_HEARTBEAT_INTERVAL_MS else HEARTBEAT_INTERVAL_MS
                    if (now - lastHeartbeatSentTime >= heartbeatInterval) {
                        sendHeartbeat()
                        if (discovering) {
                            // A saved address from the previous network may be stale; the subnet
                            // broadcast reaches a drone that has not learned this GCS yet.
                            sendHeartbeat(extraAddress = broadcastAddress)
                        }
                        lastHeartbeatSentTime = now
                    }
                    if (inited) {
                        sendManualControl()
                    }
                }
                delay(20.milliseconds)  // 50 Hz
            }
        }
    }

    private fun startReceiveLoop(transport: DatagramSocket) {
        recvJob = scope.launch {
            val receiveContext = coroutineContext
            val datagramPacket = DatagramPacket(ByteArray(2048), 2048)
            var packetAddress: InetAddress? = null
            var packetPort = targetPort

            // Use an InputStream adapter for DatagramSocket
            val inputStream = object : InputStream() {
                private var buffer: ByteArray? = null
                private var pos = 0
                private var limit = 0

                override fun read(): Int {
                    while (receiveContext.isActive && running.get() && !transport.isClosed) {
                        if (pos < limit) {
                            return buffer!![pos++].toInt() and 0xFF
                        }
                        try {
                            datagramPacket.length = datagramPacket.data.size
                            transport.receive(datagramPacket)
                            Log.v(
                                "MavlinkManager",
                                "Received UDP packet: ${datagramPacket.length} bytes from ${datagramPacket.address}:${datagramPacket.port}"
                            )
                            packetAddress = datagramPacket.address
                            packetPort = datagramPacket.port
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

            while (receiveContext.isActive && running.get() && !transport.isClosed) {
                try {
                    val message = connection.next() ?: continue

                    synchronized(this@MavlinkManagerAndroid) {
                        if (socket !== transport || !receiveContext.isActive) return@launch
                        lastListenAddress = packetAddress
                        lastListenPort = packetPort
                        val payload = message.payload

                        // Discovery logic: Update drone ID and target host if we see a heartbeat
                        if (autoDetect && !inited && payload is Heartbeat && isDroneHeartbeat(message)) {
                            tryDiscover(packetAddress, message.originSystemId, message.originComponentId)
                        }
                        // check that message is received from target drone
                        if (message.originSystemId != droneSystemId || message.originComponentId != droneComponentId) return@synchronized
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
                                emitBatteryVoltage(volt)
                            }

                            is Statustext -> {
                                Log.i(
                                    "MavlinkManager",
                                    "Statustext: ${payload.text()} (severity=${
                                        payload.severity().value()
                                    })"
                                )
                                emitStatusText(payload.text(), payload.severity().value())
                            }

                            is SerialControl -> {
                                Log.v("MavlinkManager", "SerialControl: ${payload.count()} bytes")
                                val data = payload.data().copyOfRange(0, payload.count())
                                appendConsoleResponse(String(data, Charsets.UTF_8))
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!receiveContext.isActive || transport.isClosed) return@launch
                    Log.e("MavlinkManager", "Error parsing MAVLink message", e)
                }
            }
        }
    }

    /**
     * A drone heartbeat is either the expected component or the expected system: the telemetry
     * unit may report component id 0 while the configured drone component id is 1.
     */
    private fun isDroneHeartbeat(message: io.dronefleet.mavlink.MavlinkMessage<*>): Boolean =
        message.originComponentId == droneComponentId || message.originSystemId == droneSystemId

    private fun tryDiscover(address: InetAddress?, systemId: Int, componentId: Int) {
        if (address !is Inet4Address) return
        val host = address.hostAddress ?: return
        droneSystemId = systemId

        Log.i(
            "MavlinkManager",
            "Discovered Drone: SysID=$systemId, CompID=$componentId on $host:$lastListenPort"
        )

        targetHost = host
        targetPort = lastListenPort

        updateTargetAddress()
        inited = true
        persistDetectedConnection(targetHost, targetPort, droneSystemId)
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

    private fun sendHeartbeat(extraAddress: InetAddress? = null) {
        val heartbeat = Heartbeat.builder()
            .type(MavType.MAV_TYPE_GCS)
            .autopilot(MavAutopilot.MAV_AUTOPILOT_GENERIC)
            .baseMode(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED) // Standard GCS mode
            .systemStatus(MavState.MAV_STATE_ACTIVE)
            .mavlinkVersion(3) // Standard for MAVLink
            .build()
        sendMavlinkMessage(heartbeat)
        if (extraAddress != null && extraAddress != targetAddress) {
            sendMavlinkMessage(heartbeat, extraAddress)
        }
    }

    @Synchronized
    private fun sendMavlinkMessage(payload: Any, address: InetAddress? = null) {
        val transport = socket ?: return
        val addr = address ?: targetAddress ?: return
        try {
            val outputStream = ByteArrayOutputStream()
            val connection = MavlinkConnection.create(null, outputStream)
            connection.send2(systemId, componentId, payload)
            val packetData = outputStream.toByteArray()
            transport.send(DatagramPacket(packetData, packetData.size, addr, targetPort))
        } catch (e: Exception) {
            Log.e("MavlinkManager", "Error sending MAVLink message to $addr:$targetPort", e)
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
        emitAttitude(
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

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 1000L
        const val DISCOVERY_HEARTBEAT_INTERVAL_MS = 500L
        const val RETRY_DELAY_MS = 1000L
    }
}

private fun MavAutopilot.toCommonAutopilot(): MavlinkAutopilot =
    when (this) {
        MavAutopilot.MAV_AUTOPILOT_ARDUPILOTMEGA -> MavlinkAutopilot.ArduPilotMega
        MavAutopilot.MAV_AUTOPILOT_PX4 -> MavlinkAutopilot.Px4
        MavAutopilot.MAV_AUTOPILOT_GENERIC -> MavlinkAutopilot.Generic
        else -> MavlinkAutopilot.Other
    }
