package com.eugenehammer.mavlinkjoystikkmp.mavlink

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.math.asin
import kotlin.math.atan2

class AndroidMavlinkClient(context: Context) : MavlinkManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state =

        MutableStateFlow(

            MavlinkState(),

            )

    override val state: StateFlow<MavlinkState> =

        _state.asStateFlow()

    private val _events =

        MutableSharedFlow<MavlinkEvent>()

    override val events: SharedFlow<MavlinkEvent> =

        _events.asSharedFlow()

    private var config =

        MavlinkConfig(

            targetHost = "255.255.255.255",

            targetPort = 14550,

            listenPort = 14550,

            droneSystemId = 1,

            droneComponentId = 1,

            autoDetect = true,

            )

    private var socket: DatagramSocket? = null

    private var sendJob: kotlinx.coroutines.Job? = null

    private var receiveJob: kotlinx.coroutines.Job? = null

    private var targetAddress: InetAddress? = null

    private var lastHeartbeat = 0L

    private val heartbeatTimeoutMs = 3000L

    private var inited = false

    private var stickX = 0

    private var stickY = 0

    private var stickZ = 0

    private var stickR = 0

    private var lastListenAddress: InetAddress? = null

    private var lastListenPort: Int = 14550

    private var autopilotName = "---"

    private val connectivityManager =

        context.getSystemService(

            Context.CONNECTIVITY_SERVICE,

            ) as ConnectivityManager

    private val networkCallback =

        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {

                inited = false

                _state.update {

                    it.copy(

                        connected = false,

                        )

                }

            }

            override fun onLost(network: Network) {

                inited = false

                _state.update {

                    it.copy(

                        connected = false,

                        )

                }

            }

        }

    override suspend fun start() {

        updateTargetAddress()

        if (!config.autoDetect) {

            inited = true

        }

        registerConnectivity()

        socket =

            try {

                DatagramSocket(

                    config.listenPort,

                    )

            } catch (_: Exception) {

                DatagramSocket()

            }

        socket?.soTimeout = 500

        sendJob =

            scope.launch {

                sendLoop()

            }

        receiveJob =

            scope.launch {

                receiveLoop()

            }

    }

    override suspend fun stop() {

        sendJob?.cancelAndJoin()

        receiveJob?.cancelAndJoin()

        unregisterConnectivity()

        socket?.close()

        socket = null

        inited = false

    }

    override suspend fun updateConfig(

        config: MavlinkConfig,

        ) {

        this.config = config

        updateTargetAddress()

        _state.update {

            it.copy(

                targetHost = config.targetHost,

                targetPort = config.targetPort,

                )

        }

    }

    override suspend fun setChannels(

        roll: Float,

        pitch: Float,

        throttle: Float,

        yaw: Float,

        ) {

        stickX = axisToManual(roll)

        stickY = axisToManual(-pitch)

        stickZ = throttleToManual(throttle)

        stickR = axisToManual(yaw)

    }

    override suspend fun arm() {

        sendMavlinkMessage(

            CommandLong.builder()

                .targetSystem(config.droneSystemId)

                .targetComponent(config.droneComponentId)

                .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)

                .param1(1f)

                .build(),

            )

    }

    override suspend fun disarm() {

        sendMavlinkMessage(

            CommandLong.builder()

                .targetSystem(config.droneSystemId)

                .targetComponent(config.droneComponentId)

                .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)

                .param1(0f)

                .build(),

            )

    }

    override suspend fun sendSerialControl(

        text: String,

        ) {

        val bytes =

            (text + "\n")

                .toByteArray(Charsets.UTF_8)

        var offset = 0

        while (offset < bytes.size) {

            val count =

                minOf(

                    bytes.size - offset,

                    70,

                    )

            val chunk = ByteArray(70)

            System.arraycopy(

                bytes,

                offset,

                chunk,

                0,

                count,

                )

            sendMavlinkMessage(

                SerialControl.builder()

                    .device(

                        SerialControlDev.SERIAL_CONTROL_DEV_SHELL,

                        )

                    .flags(

                        SerialControlFlag.SERIAL_CONTROL_FLAG_RESPOND,

                        )

                    .timeout(0)

                    .baudrate(0)

                    .count(count)

                    .data(chunk)

                    .build(),

                )

            offset += count

        }

    }

    private suspend fun sendLoop() {

        var lastHeartbeatSentTime = 0L

        while (currentCoroutineContext().isActive) {

            val now = System.currentTimeMillis()

            if (inited) {

                sendManualControl()

                if (now - lastHeartbeatSentTime >= 1000L) {

                    sendHeartbeat()

                    lastHeartbeatSentTime = now

                }

            }

            val connected =

                now - lastHeartbeat <

                        heartbeatTimeoutMs

            _state.update {

                it.copy(

                    connected = connected,

                    )

            }

            delay(20)

        }

    }

    private suspend fun receiveLoop() {

        val datagramPacket =

            DatagramPacket(

                ByteArray(1024),

                1024,

                )

        val inputStream =

            object : InputStream() {

                private var buffer: ByteArray? = null

                private var position = 0

                private var limit = 0

                override fun read(): Int {

                    while (scope.coroutineContext.job.isActive) {

                        if (position < limit) {

                            return (

                                    buffer!![position++]

                                        .toInt() and 0xFF

                                    )

                        }

                        try {

                            socket?.receive(

                                datagramPacket,

                                )

                            lastListenAddress =

                                datagramPacket.address

                            lastListenPort =

                                datagramPacket.port

                            buffer =

                                datagramPacket.data

                            position = 0

                            limit =

                                datagramPacket.length

                        } catch (_: IOException) {

                        }

                    }

                    return -1

                }

            }

        val connection =

            MavlinkConnection.create(

                inputStream,

                null,

                )

        while (currentCoroutineContext().isActive) {

            try {

                val message =

                    connection.next()

                        ?: continue

                val payload =

                    message.payload

                if (

                    config.autoDetect &&

                    !inited &&

                    payload is Heartbeat

                ) {

                    discoverDrone(message)

                }

                if (

                    message.originSystemId != config.droneSystemId ||

                    message.originComponentId != config.droneComponentId

                ) {

                    continue

                }

                when (payload) {

                    is Heartbeat -> {

                        handleHeartbeat(payload)

                    }

                    is Attitude -> {

                        handleAttitude(payload)

                    }

                    is AttitudeQuaternion -> {

                        handleAttitudeQuaternion(payload)

                    }

                    is BatteryStatus -> {

                        val totalMv =

                            payload.voltages()

                                .filter { it < 65535 }

                                .sum()

                        val voltage =

                            totalMv.toFloat() / 1000f

                        _state.update {

                            it.copy(

                                batteryVoltage = voltage,

                                )

                        }

                    }

                    is Statustext -> {

                        _events.emit(

                            MavlinkEvent.StatusText(

                                text = payload.text(),

                                severity = payload.severity().value(),

                                ),

                            )

                    }

                    is SerialControl -> {

                        _events.emit(

                            MavlinkEvent.SerialData(

                                payload.data()

                                    .copyOfRange(

                                        0,

                                        payload.count(),

                                        ),

                                ),

                            )

                    }

                }

            } catch (e: Exception) {

                Log.e(

                    "Mavlink",

                    "Receive error",

                    e,

                    )

            }

        }

    }

    private suspend fun discoverDrone(

        message: io.dronefleet.mavlink.MavlinkMessage<*>,

        ) {

        val addr =

            lastListenAddress

                ?: return

        if (addr !is Inet4Address) {

            return

        }

        config =

            config.copy(

                targetHost = addr.hostAddress!!,

                targetPort = lastListenPort,

                droneSystemId = message.originSystemId,

                )

        updateTargetAddress()

        inited = true

        _state.update {

            it.copy(

                connected = true,

                targetHost = config.targetHost,

                targetPort = config.targetPort,

                )

        }

    }

    private suspend fun sendManualControl() {

        sendMavlinkMessage(

            ManualControl.builder()

                .target(config.droneSystemId)

                .x(stickY)

                .y(stickX)

                .z(stickZ)

                .r(stickR)

                .buttons(0)

                .build(),

            )

    }

    private suspend fun sendHeartbeat() {

        sendMavlinkMessage(

            Heartbeat.builder()

                .type(MavType.MAV_TYPE_GCS)

                .autopilot(

                    MavAutopilot.MAV_AUTOPILOT_GENERIC,

                    )

                .baseMode(

                    MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED,

                    )

                .systemStatus(

                    MavState.MAV_STATE_ACTIVE,

                    )

                .mavlinkVersion(3)

                .build(),

            )

    }

    private suspend fun sendMavlinkMessage(

        payload: Any,

        ) {

        val address =

            targetAddress

                ?: return

        try {

            val output =

                ByteArrayOutputStream()

            val connection =

                MavlinkConnection.create(

                    null,

                    output,

                    )

            connection.send2(

                config.systemId,

                config.componentId,

                payload,

                )

            val packet =

                DatagramPacket(

                    output.toByteArray(),

                    output.size(),

                    address,

                    config.targetPort,

                    )

            socket?.send(packet)

        } catch (e: Exception) {

            Log.e(

                "Mavlink",

                "Send error",

                e,

                )

        }

    }

    private fun handleHeartbeat(

        heartbeat: Heartbeat,

        ) {

        lastHeartbeat =

            System.currentTimeMillis()

        val armed =

            (

                    heartbeat.baseMode().value() and

                            0x80

                    ) != 0

        val autopilot =

            heartbeat.autopilot().entry()

        autopilotName =

            when (autopilot) {

                MavAutopilot.MAV_AUTOPILOT_ARDUPILOTMEGA ->

                    "ArduPilot"

                MavAutopilot.MAV_AUTOPILOT_PX4 ->

                    "PX4"

                MavAutopilot.MAV_AUTOPILOT_GENERIC ->

                    "Flix"

                else ->

                    "---"

            }

        _state.update {

            it.copy(

                armed = armed,

                connected = true,

                flightMode = getFlightModeName(

                    heartbeat,

                    ),

                autopilotName = autopilotName,

                )

        }

    }

    private fun handleAttitude(

        attitude: Attitude,

        ) {

        _state.update {

            it.copy(

                rollDeg = Math.toDegrees(

                    attitude.roll().toDouble(),

                    ).toFloat(),

                pitchDeg = Math.toDegrees(

                    attitude.pitch().toDouble(),

                    ).toFloat(),

                yawDeg = Math.toDegrees(

                    attitude.yaw().toDouble(),

                    ).toFloat(),

                )

        }

    }

    private fun handleAttitudeQuaternion(

        aq: AttitudeQuaternion,

        ) {

        val w = aq.q1()

        val x = aq.q2()

        val y = aq.q3()

        val z = aq.q4()

        val roll =

            atan2(

                2.0 * (w * x + y * z),

                1.0 - 2.0 * (x * x + y * y),

                )

        val pitch =

            asin(

                (

                        2.0 * (w * y - z * x)

                        ).coerceIn(-1.0, 1.0),

                )

        val yaw =

            atan2(

                2.0 * (w * z + x * y),

                1.0 - 2.0 * (y * y + z * z),

                )

        _state.update {

            it.copy(

                rollDeg = Math.toDegrees(roll).toFloat(),

                pitchDeg = Math.toDegrees(pitch).toFloat(),

                yawDeg = Math.toDegrees(yaw).toFloat(),

                )

        }

    }

    private fun getFlightModeName(

        heartbeat: Heartbeat,

        ): String {

        val mode =

            heartbeat.customMode()

        return when (

            heartbeat.autopilot().entry()

        ) {

            MavAutopilot.MAV_AUTOPILOT_ARDUPILOTMEGA -> {

                when (mode.toInt()) {

                    0 -> "STABILIZE"

                    1 -> "ACRO"

                    2 -> "ALT_HOLD"

                    3 -> "AUTO"

                    4 -> "GUIDED"

                    5 -> "LOITER"

                    6 -> "RTL"

                    9 -> "LAND"

                    else -> "MODE($mode)"

                }

            }

            MavAutopilot.MAV_AUTOPILOT_PX4 -> {

                val customMode =

                    mode.toLong()

                val mainMode =

                    ((customMode shr 16) and 0xFF)

                        .toInt()

                val subMode =

                    ((customMode shr 24) and 0xFF)

                        .toInt()

                when (mainMode) {

                    1 -> "MANUAL"

                    2 -> "ALTCTL"

                    3 -> "POSCTL"

                    4 -> {

                        when (subMode) {

                            2 -> "TAKEOFF"

                            3 -> "LOITER"

                            4 -> "MISSION"

                            5 -> "RTL"

                            6 -> "LAND"

                            else -> "AUTO"

                        }

                    }

                    5 -> "ACRO"

                    6 -> "OFFBOARD"

                    7 -> "STABILIZED"

                    else -> "MODE($mainMode:$subMode)"

                }

            }

            else -> "MODE($mode)"

        }

    }

    private fun axisToManual(

        value: Float,

        ): Int {

        return (

                value.coerceIn(-1f, 1f) * 1000f

                ).toInt()

    }

    private fun throttleToManual(

        value: Float,

        ): Int {

        return (

                value.coerceIn(0f, 1f) * 1000f

                ).toInt()

    }

    private fun updateTargetAddress() {

        targetAddress =

            try {

                InetAddress.getByName(

                    config.targetHost,

                    )

            } catch (_: Exception) {

                null

            }

    }

    private fun registerConnectivity() {

        try {

            val request =

                NetworkRequest.Builder()

                    .addCapability(

                        NetworkCapabilities.NET_CAPABILITY_INTERNET,

                        )

                    .build()

            connectivityManager.registerNetworkCallback(

                request,

                networkCallback,

                )

        } catch (e: Exception) {

            Log.e(

                "Mavlink",

                "Connectivity register failed",

                e,

                )

        }

    }

    private fun unregisterConnectivity() {

        try {

            connectivityManager.unregisterNetworkCallback(

                networkCallback,

                )

        } catch (_: Exception) {

        }

    }

}