package com.eugenehammer.mavlinkjoystikkmp.mavlink

import com.eugenehammer.mavlinkjoystikkmp.data.AppSettings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.posix.AF_INET
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.INADDR_ANY
import platform.posix.INADDR_BROADCAST
import platform.posix.IPPROTO_UDP
import platform.posix.O_NONBLOCK
import platform.posix.SO_BROADCAST
import platform.posix.SO_REUSEADDR
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.bind
import platform.posix.close
import platform.posix.fcntl
import platform.posix.recvfrom
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socklen_tVar
import platform.posix.socket

/**
 * iOS MAVLink UDP manager.
 *
 * This mirrors the Android manager's behavior for the subset of MAVLink packets the app uses.
 */
@OptIn(ExperimentalForeignApi::class)
class MavlinkManagerIOS(
    appSettings: AppSettings,
) : BaseMavlinkManager(appSettings) {
    override val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sendJob: Job? = null
    private var recvJob: Job? = null
    private var running = false
    private var socketFd: Int = -1
    private var mavlinkSequence: Int = 0
    private var lastListenHost: String? = null
    private var lastListenPort: Int = targetPort

    override fun start() {
        if (running) return
        running = true
        if (!autoDetect) inited = true

        socketFd = openSocket()
        if (socketFd < 0) {
            running = false
            return
        }

        startSendLoop()
        startReceiveLoop()
    }

    override fun stop() {
        running = false
        sendJob?.cancel()
        recvJob?.cancel()
        if (socketFd >= 0) {
            close(socketFd)
            socketFd = -1
        }
        inited = false
    }

    override fun sendArmCommand(arm: Boolean) {
        scope.launch {
            sendMavlinkMessage(
                msgId = MSG_COMMAND_LONG,
                crcExtra = CRC_COMMAND_LONG,
                payload = commandLongPayload(
                    command = MAV_CMD_COMPONENT_ARM_DISARM,
                    targetSystem = droneSystemId,
                    targetComponent = droneComponentId,
                    param1 = if (arm) 1f else 0f,
                )
            )
        }
    }

    override fun sendSerialControl(text: String) {
        scope.launch {
            val bytes = (text + "\n").encodeToByteArray()
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(bytes.size - offset, SERIAL_CONTROL_DATA_LEN)
                val data = ByteArray(SERIAL_CONTROL_DATA_LEN)
                bytes.copyInto(data, destinationOffset = 0, startIndex = offset, endIndex = offset + count)
                sendMavlinkMessage(
                    msgId = MSG_SERIAL_CONTROL,
                    crcExtra = CRC_SERIAL_CONTROL,
                    payload = serialControlPayload(count, data)
                )
                offset += count
            }
        }
        appendConsoleCommand(text)
    }

    private fun startSendLoop() {
        sendJob = scope.launch {
            var lastHeartbeatSentTime = 0L
            while (running) {
                val now = currentTimeMillis()
                if (inited) {
                    sendManualControl()
                    if (now - lastHeartbeatSentTime >= 1000L) {
                        sendHeartbeat()
                        lastHeartbeatSentTime = now
                    }
                }

                refreshConnection(now)
                delay(20)
            }
        }
    }

    private fun startReceiveLoop() {
        recvJob = scope.launch {
            val packet = ByteArray(2048)
            while (running) {
                val received = receivePacket(packet)
                if (received <= 0) {
                    delay(20)
                    continue
                }

                parseMavlinkFrames(packet, received).forEach { message ->
                    handleMessage(message)
                }
            }
        }
    }

    private fun handleMessage(message: MavlinkMessage) {
        if (autoDetect && !inited && message.msgId == MSG_HEARTBEAT) {
            lastListenHost?.let { host ->
                droneSystemId = message.systemId
                targetHost = host
                targetPort = lastListenPort
                inited = true
                isConnected = true
                onStateChanged?.invoke(isArmed, isConnected)
                persistDetectedConnection(targetHost, targetPort, droneSystemId)
            }
        }

        if (message.systemId != droneSystemId || message.componentId != droneComponentId) return

        when (message.msgId) {
            MSG_HEARTBEAT -> handleHeartbeat(message.payload)
            MSG_ATTITUDE -> handleAttitude(message.payload)
            MSG_ATTITUDE_QUATERNION -> handleAttitudeQuaternion(message.payload)
            MSG_BATTERY_STATUS -> handleBatteryStatus(message.payload)
            MSG_STATUSTEXT -> handleStatustext(message.payload)
            MSG_SERIAL_CONTROL -> handleSerialControl(message.payload)
        }
    }

    private fun sendManualControl() {
        sendMavlinkMessage(
            msgId = MSG_MANUAL_CONTROL,
            crcExtra = CRC_MANUAL_CONTROL,
            payload = manualControlPayload()
        )
    }

    private fun sendHeartbeat() {
        sendMavlinkMessage(
            msgId = MSG_HEARTBEAT,
            crcExtra = CRC_HEARTBEAT,
            payload = heartbeatPayload()
        )
    }

    private fun sendMavlinkMessage(msgId: Int, crcExtra: Int, payload: ByteArray) {
        val fd = socketFd
        if (fd < 0) return

        val frame = encodeMavlink2(msgId, systemId, componentId, payload, crcExtra)
        memScoped {
            val addr = alloc<sockaddr_in>()
            addr.sin_len = sizeOf<sockaddr_in>().convert()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = htonsCompat(targetPort.toUShort())
            addr.sin_addr.s_addr = resolveIpv4Address(targetHost)

            frame.usePinned { pinned ->
                sendto(
                    fd,
                    pinned.addressOf(0),
                    frame.size.convert(),
                    0,
                    addr.ptr.reinterpret<sockaddr>(),
                    sizeOf<sockaddr_in>().convert()
                )
            }
        }
    }

    private fun openSocket(): Int {
        val fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        if (fd < 0) return -1

        memScoped {
            val enabled = alloc<IntVar>()
            enabled.value = 1
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, enabled.ptr, sizeOf<IntVar>().convert())
            setsockopt(fd, SOL_SOCKET, SO_BROADCAST, enabled.ptr, sizeOf<IntVar>().convert())

            val addr = alloc<sockaddr_in>()
            addr.sin_len = sizeOf<sockaddr_in>().convert()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = htonsCompat(listenPort.toUShort())
            addr.sin_addr.s_addr = INADDR_ANY

            if (bind(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) != 0) {
                addr.sin_port = htonsCompat(0u)
                if (bind(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) != 0) {
                    close(fd)
                    return -1
                }
            }
        }

        val flags = fcntl(fd, F_GETFL, 0)
        if (flags >= 0) fcntl(fd, F_SETFL, flags or O_NONBLOCK)
        return fd
    }

    private fun receivePacket(packet: ByteArray): Int {
        val fd = socketFd
        if (fd < 0) return -1

        return memScoped {
            val src = alloc<sockaddr_in>()
            val srcLen = alloc<socklen_tVar>()
            srcLen.value = sizeOf<sockaddr_in>().convert()

            val bytes = packet.usePinned { pinned ->
                recvfrom(
                    fd,
                    pinned.addressOf(0),
                    packet.size.convert(),
                    0,
                    src.ptr.reinterpret<sockaddr>(),
                    srcLen.ptr
                )
            }

            if (bytes > 0) {
                lastListenHost = ipv4AddressToString(src.sin_addr.s_addr)
                lastListenPort = ntohsCompat(src.sin_port).toInt()
            }
            bytes.toInt()
        }
    }

    private fun parseMavlinkFrames(data: ByteArray, size: Int): List<MavlinkMessage> {
        val messages = mutableListOf<MavlinkMessage>()
        var index = 0
        while (index < size) {
            val magic = data[index].u8()
            if (magic != MAVLINK_V1_MAGIC && magic != MAVLINK_V2_MAGIC) {
                index++
                continue
            }

            if (magic == MAVLINK_V2_MAGIC) {
                if (index + 12 > size) break
                val payloadLen = data[index + 1].u8()
                val incompatFlags = data[index + 2].u8()
                val signatureLen = if ((incompatFlags and 0x01) != 0) 13 else 0
                val frameLen = 10 + payloadLen + 2 + signatureLen
                if (index + frameLen > size) break

                val msgId = data[index + 7].u8() or (data[index + 8].u8() shl 8) or (data[index + 9].u8() shl 16)
                val crcExtra = crcExtraFor(msgId)
                val checksum = x25Crc(data, index + 1, 9 + payloadLen, crcExtra)
                val received = data[index + 10 + payloadLen].u8() or (data[index + 11 + payloadLen].u8() shl 8)
                if (checksum == received) {
                    messages += MavlinkMessage(
                        msgId = msgId,
                        systemId = data[index + 5].u8(),
                        componentId = data[index + 6].u8(),
                        payload = data.copyOfRange(index + 10, index + 10 + payloadLen)
                    )
                }
                index += frameLen
            } else {
                if (index + 8 > size) break
                val payloadLen = data[index + 1].u8()
                val frameLen = 6 + payloadLen + 2
                if (index + frameLen > size) break

                val msgId = data[index + 5].u8()
                val crcExtra = crcExtraFor(msgId)
                val checksum = x25Crc(data, index + 1, 5 + payloadLen, crcExtra)
                val received = data[index + 6 + payloadLen].u8() or (data[index + 7 + payloadLen].u8() shl 8)
                if (checksum == received) {
                    messages += MavlinkMessage(
                        msgId = msgId,
                        systemId = data[index + 3].u8(),
                        componentId = data[index + 4].u8(),
                        payload = data.copyOfRange(index + 6, index + 6 + payloadLen)
                    )
                }
                index += frameLen
            }
        }
        return messages
    }

    private fun handleHeartbeat(payload: ByteArray) {
        if (payload.size < 9) return

        val customMode = payload.u32(0)
        val autopilot = payload.u8(5)
        val baseMode = payload.u8(6)

        handleHeartbeat(
            customMode = customMode.toLong(),
            autopilot = autopilot.toCommonAutopilot(),
            baseMode = baseMode,
            now = currentTimeMillis()
        )
    }

    private fun handleAttitude(payload: ByteArray) {
        if (payload.size < 28) return
        onAttitudeReceived?.invoke(
            radiansToDegrees(payload.f32(4)),
            radiansToDegrees(payload.f32(8)),
            radiansToDegrees(payload.f32(12))
        )
    }

    private fun handleAttitudeQuaternion(payload: ByteArray) {
        if (payload.size < 32) return
        val w = payload.f32(4).toDouble()
        val x = payload.f32(8).toDouble()
        val y = payload.f32(12).toDouble()
        val z = payload.f32(16).toDouble()

        handleAttitudeQuaternion(w, x, y, z)
    }

    private fun handleBatteryStatus(payload: ByteArray) {
        if (payload.size < 36) return
        var totalMv = 0
        for (i in 0 until 10) {
            val voltage = payload.u16(10 + i * 2)
            if (voltage < 65535) totalMv += voltage
        }
        onBatteryVoltageReceived?.invoke(totalMv.toFloat() / 1000f)
    }

    private fun handleStatustext(payload: ByteArray) {
        if (payload.size < 51) return
        val severity = payload.u8(0)
        val textBytes = payload.copyOfRange(1, minOf(payload.size, 51))
        val textLen = textBytes.indexOf(0).let { if (it >= 0) it else textBytes.size }
        onStatustextReceived?.invoke(textBytes.copyOf(textLen).decodeToString(), severity)
    }

    private fun handleSerialControl(payload: ByteArray) {
        if (payload.size < 9) return
        val count = payload.u8(8).coerceAtMost(SERIAL_CONTROL_DATA_LEN)
        if (payload.size < 9 + count) return
        appendConsoleResponse(payload.copyOfRange(9, 9 + count).decodeToString())
    }

    private fun manualControlPayload(): ByteArray = buildPayload(MANUAL_CONTROL_PAYLOAD_LEN) {
        i16(stickY)
        i16(stickX)
        i16(stickZ)
        i16(stickR)
        u16(0)
        u8(droneSystemId)
    }

    private fun commandLongPayload(
        command: Int,
        targetSystem: Int,
        targetComponent: Int,
        param1: Float = 0f,
    ): ByteArray = buildPayload(COMMAND_LONG_PAYLOAD_LEN) {
        f32(param1)
        f32(0f)
        f32(0f)
        f32(0f)
        f32(0f)
        f32(0f)
        f32(0f)
        u16(command)
        u8(targetSystem)
        u8(targetComponent)
        u8(0)
    }

    private fun serialControlPayload(count: Int, data: ByteArray): ByteArray = buildPayload(SERIAL_CONTROL_PAYLOAD_LEN) {
        u32(0u)
        u16(0)
        u8(SERIAL_CONTROL_DEV_SHELL)
        u8(SERIAL_CONTROL_FLAG_RESPOND)
        u8(count)
        bytes(data.copyOf(SERIAL_CONTROL_DATA_LEN))
    }

    private fun heartbeatPayload(): ByteArray = buildPayload(HEARTBEAT_PAYLOAD_LEN) {
        u32(0u)
        u8(MAV_TYPE_GCS)
        u8(MAV_AUTOPILOT_GENERIC)
        u8(MAV_MODE_FLAG_CUSTOM_MODE_ENABLED)
        u8(0)
        u8(3)
    }

    private fun encodeMavlink2(msgId: Int, sysId: Int, compId: Int, payload: ByteArray, crcExtra: Int): ByteArray {
        val trimmedPayload = payload.trimTrailingZeros()
        val frame = ByteArray(10 + trimmedPayload.size + 2)
        frame[0] = MAVLINK_V2_MAGIC.toByte()
        frame[1] = trimmedPayload.size.toByte()
        frame[2] = 0
        frame[3] = 0
        frame[4] = nextSequence().toByte()
        frame[5] = sysId.toByte()
        frame[6] = compId.toByte()
        frame[7] = (msgId and 0xFF).toByte()
        frame[8] = ((msgId shr 8) and 0xFF).toByte()
        frame[9] = ((msgId shr 16) and 0xFF).toByte()
        trimmedPayload.copyInto(frame, destinationOffset = 10)

        val crc = x25Crc(frame, 1, 9 + trimmedPayload.size, crcExtra)
        frame[10 + trimmedPayload.size] = (crc and 0xFF).toByte()
        frame[11 + trimmedPayload.size] = ((crc shr 8) and 0xFF).toByte()
        return frame
    }

    private fun nextSequence(): Int {
        val seq = mavlinkSequence and 0xFF
        mavlinkSequence = (mavlinkSequence + 1) and 0xFF
        return seq
    }

    private fun crcExtraFor(msgId: Int): Int = when (msgId) {
        MSG_HEARTBEAT -> CRC_HEARTBEAT
        MSG_ATTITUDE -> CRC_ATTITUDE
        MSG_ATTITUDE_QUATERNION -> CRC_ATTITUDE_QUATERNION
        MSG_MANUAL_CONTROL -> CRC_MANUAL_CONTROL
        MSG_COMMAND_LONG -> CRC_COMMAND_LONG
        MSG_SERIAL_CONTROL -> CRC_SERIAL_CONTROL
        MSG_BATTERY_STATUS -> CRC_BATTERY_STATUS
        MSG_STATUSTEXT -> CRC_STATUSTEXT
        else -> 0
    }

    private fun x25Crc(data: ByteArray, offset: Int, length: Int, extra: Int): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crcAccumulate(data[i].u8(), crc)
        }
        return crcAccumulate(extra, crc)
    }

    private fun crcAccumulate(value: Int, current: Int): Int {
        var tmp = value xor (current and 0xFF)
        tmp = tmp xor ((tmp shl 4) and 0xFF)
        return ((current shr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp shr 4)) and 0xFFFF
    }

}

private fun Int.toCommonAutopilot(): MavlinkAutopilot =
    when (this) {
        MAV_AUTOPILOT_ARDUPILOTMEGA -> MavlinkAutopilot.ArduPilotMega
        MAV_AUTOPILOT_PX4 -> MavlinkAutopilot.Px4
        MAV_AUTOPILOT_GENERIC -> MavlinkAutopilot.Generic
        else -> MavlinkAutopilot.Other
    }

private data class MavlinkMessage(
    val msgId: Int,
    val systemId: Int,
    val componentId: Int,
    val payload: ByteArray,
)

private class PayloadWriter(size: Int) {
    private val data = ByteArray(size)
    private var offset = 0

    fun u8(value: Int) {
        data[offset++] = value.toByte()
    }

    fun u16(value: Int) {
        data[offset++] = (value and 0xFF).toByte()
        data[offset++] = ((value shr 8) and 0xFF).toByte()
    }

    fun u32(value: UInt) {
        data[offset++] = (value and 0xFFu).toByte()
        data[offset++] = ((value shr 8) and 0xFFu).toByte()
        data[offset++] = ((value shr 16) and 0xFFu).toByte()
        data[offset++] = ((value shr 24) and 0xFFu).toByte()
    }

    fun i16(value: Int) = u16(value and 0xFFFF)
    fun f32(value: Float) = u32(value.toRawBits().toUInt())

    fun bytes(value: ByteArray) {
        value.copyInto(data, destinationOffset = offset)
        offset += value.size
    }

    fun toByteArray(): ByteArray = data
}

private fun buildPayload(size: Int, block: PayloadWriter.() -> Unit): ByteArray =
    PayloadWriter(size).apply(block).toByteArray()

private fun Byte.u8(): Int = toInt() and 0xFF

private fun ByteArray.u8(offset: Int): Int = this[offset].u8()

private fun ByteArray.u16(offset: Int): Int = this[offset].u8() or (this[offset + 1].u8() shl 8)

private fun ByteArray.u32(offset: Int): UInt =
    (this[offset].u8().toUInt()) or
        (this[offset + 1].u8().toUInt() shl 8) or
        (this[offset + 2].u8().toUInt() shl 16) or
        (this[offset + 3].u8().toUInt() shl 24)

private fun ByteArray.f32(offset: Int): Float = Float.fromBits(u32(offset).toInt())

private fun ByteArray.trimTrailingZeros(): ByteArray {
    var end = size
    while (end > 1 && this[end - 1] == 0.toByte()) end--
    return copyOf(end)
}

private fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalForeignApi::class)
private fun resolveIpv4Address(host: String): UInt {
    if (host == "255.255.255.255") return INADDR_BROADCAST
    return parseIpv4Address(host) ?: INADDR_BROADCAST
}

private fun parseIpv4Address(host: String): UInt? {
    val parts = host.split(".")
    if (parts.size != 4) return null
    val bytes = parts.map { part ->
        val value = part.toIntOrNull() ?: return null
        if (value !in 0..255) return null
        value
    }
    return bytes[0].toUInt() or
        (bytes[1].toUInt() shl 8) or
        (bytes[2].toUInt() shl 16) or
        (bytes[3].toUInt() shl 24)
}

private fun ipv4AddressToString(address: UInt): String =
    "${address and 0xFFu}.${(address shr 8) and 0xFFu}.${(address shr 16) and 0xFFu}.${(address shr 24) and 0xFFu}"

private fun htonsCompat(value: UShort): UShort = ntohsCompat(value)

private fun ntohsCompat(value: UShort): UShort =
    (((value.toInt() and 0xFF) shl 8) or ((value.toInt() shr 8) and 0xFF)).toUShort()

private const val MAVLINK_V1_MAGIC = 0xFE
private const val MAVLINK_V2_MAGIC = 0xFD

private const val MSG_HEARTBEAT = 0
private const val MSG_ATTITUDE = 30
private const val MSG_ATTITUDE_QUATERNION = 31
private const val MSG_MANUAL_CONTROL = 69
private const val MSG_COMMAND_LONG = 76
private const val MSG_SERIAL_CONTROL = 126
private const val MSG_BATTERY_STATUS = 147
private const val MSG_STATUSTEXT = 253

private const val CRC_HEARTBEAT = 50
private const val CRC_ATTITUDE = 39
private const val CRC_ATTITUDE_QUATERNION = 246
private const val CRC_MANUAL_CONTROL = 243
private const val CRC_COMMAND_LONG = 152
private const val CRC_SERIAL_CONTROL = 220
private const val CRC_BATTERY_STATUS = 154
private const val CRC_STATUSTEXT = 83

private const val HEARTBEAT_PAYLOAD_LEN = 9
private const val MANUAL_CONTROL_PAYLOAD_LEN = 11
private const val COMMAND_LONG_PAYLOAD_LEN = 33
private const val SERIAL_CONTROL_PAYLOAD_LEN = 79
private const val SERIAL_CONTROL_DATA_LEN = 70

private const val MAV_CMD_COMPONENT_ARM_DISARM = 400
private const val MAV_TYPE_GCS = 6
private const val MAV_AUTOPILOT_GENERIC = 0
private const val MAV_AUTOPILOT_ARDUPILOTMEGA = 3
private const val MAV_AUTOPILOT_PX4 = 12
private const val MAV_MODE_FLAG_CUSTOM_MODE_ENABLED = 1
private const val SERIAL_CONTROL_DEV_SHELL = 10
private const val SERIAL_CONTROL_FLAG_RESPOND = 1
