package com.goldarte.mavlinkjoystick.mavlink

import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavAutopilot
import io.dronefleet.mavlink.minimal.MavModeFlag
import io.dronefleet.mavlink.minimal.MavState
import io.dronefleet.mavlink.minimal.MavType
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds

class MavlinkUdpReconnectTest {
    @Test
    fun detectsControllerThatAppearsAfterStartupAndRediscoversAfterLoss() = runBlocking {
        val manager = createManager()
        try {
            manager.start()
            delay(200.milliseconds)
            assertFalse(manager.connectionState.value.connected)
            DatagramSocket().use { controller ->
                connect(manager, controller, systemId = 42)
                assertEquals(42, manager.droneSystemId)
                assertEquals(controller.localPort, manager.targetPort)
            }
            withTimeout(5000) { manager.connectionState.first { !it.connected } }
            DatagramSocket().use { newController ->
                connect(manager, newController, systemId = 43)
                assertEquals(43, manager.droneSystemId)
                assertEquals(newController.localPort, manager.targetPort)
            }
        } finally {
            manager.stop()
        }
        assertFalse(manager.connectionState.value.connected)
    }

    @Test
    fun rapidStopStartDoesNotLeaveOldReceiversOrReuseOldHeartbeat() = runBlocking {
        val manager = createManager()
        try {
            repeat(20) {
                manager.start()
                manager.stop()
            }
            manager.start()
            DatagramSocket().use { controller -> connect(manager, controller, systemId = 42) }
            manager.stop()
            manager.start()
            delay(200.milliseconds)
            assertFalse(manager.connectionState.value.connected)
            DatagramSocket().use { controller -> connect(manager, controller, systemId = 43) }
            assertEquals(43, manager.droneSystemId)
        } finally {
            manager.stop()
        }
    }

    private fun createManager() = MavlinkManagerAndroid(null, testAppSettings()).apply {
        listenPort = DatagramSocket(0).use { it.localPort }
    }

    private suspend fun connect(manager: MavlinkManagerAndroid, controller: DatagramSocket, systemId: Int) {
        val bytes = ByteArrayOutputStream().also {
            MavlinkConnection.create(null, it).send2(
                systemId, 1,
                Heartbeat.builder()
                    .type(MavType.MAV_TYPE_QUADROTOR)
                    .autopilot(MavAutopilot.MAV_AUTOPILOT_GENERIC)
                    .baseMode(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED)
                    .systemStatus(MavState.MAV_STATE_ACTIVE)
                    .mavlinkVersion(3)
                    .build()
            )
        }.toByteArray()
        withTimeout(5000.milliseconds) {
            while (!manager.connectionState.value.connected) {
                controller.send(DatagramPacket(bytes, bytes.size, InetAddress.getLoopbackAddress(), manager.listenPort))
                delay(50.milliseconds)
            }
        }
    }
}
