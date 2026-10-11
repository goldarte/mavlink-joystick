package com.goldarte.mavlinkjoystick.mavlink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavAutopilot
import io.dronefleet.mavlink.minimal.MavModeFlag
import io.dronefleet.mavlink.minimal.MavState
import io.dronefleet.mavlink.minimal.MavType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Covers the Android link lifecycle around Wi-Fi changes without the Android framework: the
 * [NetworkBinder] is replaced by [FakeNetworkBinder], which behaves like ConnectivityManager.
 */
class MavlinkWifiSwitchTest {

    @Test
    fun dronePoweredOnAfterStartIsDiscovered() = runBlocking {
        val binder = FakeNetworkBinder()
        val manager = createManager(binder)
        try {
            manager.start()
            binder.connect("home-wifi")
            delay(200.milliseconds)
            assertFalse(manager.connectionState.value.connected)

            // The telemetry access point appears and the drone answers the GCS heartbeats that the
            // manager already sends while the link is still down.
            DatagramSocket().use { controller ->
                binder.connect("flix-wifi")
                controller.awaitControllerGreeting(manager, systemId = 7)
            }
            assertTrue(manager.connectionState.value.connected)
        } finally {
            manager.stop()
        }
    }

    @Test
    fun switchingWifiRebuildsTransportAndDiscoversNewController() = runBlocking {
        val binder = FakeNetworkBinder()
        val manager = createManager(binder)
        try {
            manager.start()
            val home = binder.connect("home-wifi")
            DatagramSocket().use { controller -> controller.completeDiscovery(manager, systemId = 1) }
            assertTrue(manager.connectionState.value.connected)

            // Walk out of range of home Wi-Fi and join the drone access point, exactly like the
            // user does after powering the drone on.
            binder.lose("home-wifi")
            val flix = binder.connect("flix-wifi")
            assertTrue(
                awaitSocketsOn(binder, flix),
                "transport must be rebuilt on the new Wi-Fi network, got ${binder.socketsCreatedOn()}",
            )
            assertFalse(
                manager.connectionState.value.connected,
                "the stale drone link must be dropped when the network changes",
            )
            assertTrue(home.handle != flix.handle)

            DatagramSocket().use { controller -> controller.completeDiscovery(manager, systemId = 42) }
            assertEquals(42, manager.droneSystemId)
        } finally {
            manager.stop()
        }
    }

    @Test
    fun droneIsRediscoveredOnTheDroneNetworkAfterItSwitches() = runBlocking {
        val binder = FakeNetworkBinder()
        val manager = createManager(binder)
        try {
            manager.start()
            binder.connect("home-wifi")
            DatagramSocket().use { controller -> controller.completeDiscovery(manager, systemId = 5) }
            assertTrue(manager.connectionState.value.connected)

            // The drone is powered on and the phone joins its access point. Only after the GCS
            // heartbeat is heard on that network can the drone answer, which is the scenario that
            // used to stay silent forever.
            binder.lose("home-wifi")
            binder.connect("flix-wifi")
            DatagramSocket().use { controller ->
                controller.meetManager(manager)
                assertNotNull(
                    controller.awaitMavlinkHeartbeat(3_000.milliseconds),
                    "manager must keep greeting the network while the link is down",
                )
                controller.announceUntilDiscovered(manager, systemId = 11)
            }
            assertEquals(11, manager.droneSystemId)
        } finally {
            manager.stop()
        }
    }

    @Test
    fun transportWaitsForAddressAndRetriesWhenThereIsNoWifi() = runBlocking {
        val binder = FakeNetworkBinder()
        val manager = createManager(binder)
        try {
            manager.start()
            // No usable Wi-Fi yet: the manager must not throw and must not report a link.
            delay(150.milliseconds)
            assertFalse(manager.connectionState.value.connected)
            assertNull(binder.currentNetwork())

            // The access point appears but has no DHCP address yet.
            binder.connect("flix-wifi", usable = false)
            delay(150.milliseconds)
            assertEquals(0, binder.socketsCreatedOn().size)

            // DHCP completes: the pending retry picks the network up.
            val flix = binder.makeUsable("flix-wifi")
            assertTrue(
                awaitSocketsOn(binder, flix),
                "transport must come up once the access point has an address",
            )
        } finally {
            manager.stop()
        }
    }

    @Test
    fun joiningAnotherWifiMovesTheLinkToIt() = runBlocking {
        val binder = FakeNetworkBinder()
        val manager = createManager(binder)
        try {
            manager.start()
            val home = binder.connect("home-wifi")
            DatagramSocket().use { controller -> controller.completeDiscovery(manager, systemId = 3) }
            assertTrue(manager.connectionState.value.connected)

            // A second access point appears while home Wi-Fi is still connected.
            val flix = binder.connect("flix-wifi")
            assertTrue(
                awaitSocketsOn(binder, flix),
                "a new Wi-Fi network must take over the transport, got ${binder.socketsCreatedOn()}",
            )
            assertTrue(home.handle != flix.handle)
            assertFalse(
                manager.connectionState.value.connected,
                "the link is re-established on the new network, not carried over",
            )

            DatagramSocket().use { controller -> controller.completeDiscovery(manager, systemId = 9) }
            assertEquals(9, manager.droneSystemId)
        } finally {
            manager.stop()
        }
    }

    private fun createManager(binder: FakeNetworkBinder): MavlinkManagerAndroid {
        val listenPort = DatagramSocket(0).use { it.localPort }
        return MavlinkManagerAndroid(null, testAppSettings(), binder).apply {
            this.listenPort = listenPort
            // The manager greets the drone before it has learned its address, so the test
            // controller has to be reachable from the very first heartbeat (loopback only).
            targetHost = InetAddress.getLoopbackAddress().hostAddress ?: "127.0.0.1"
        }
    }

    /** Datagram the fake drone/controller saw, with the address it has to answer to. */
    private class Received(val origin: InetAddress, val sourcePort: Int)

    private suspend fun DatagramSocket.awaitMavlinkHeartbeat(timeout: kotlin.time.Duration): Received? {
        soTimeout = 200
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        val packet = DatagramPacket(ByteArray(2048), 2048)
        while (System.currentTimeMillis() < deadline) {
            packet.length = packet.data.size
            val received = try {
                receive(packet)
                packet
            } catch (_: SocketTimeoutException) {
                null
            }
            if (received != null) {
                val message = MavlinkConnection.create(
                    ByteArrayInputStream(received.data, received.offset, received.length),
                    null,
                ).next()
                if (message?.payload is Heartbeat) {
                    return Received(received.address, received.port)
                }
            }
            delay(10.milliseconds)
        }
        return null
    }

    private suspend fun DatagramSocket.completeDiscovery(
        manager: MavlinkManagerAndroid,
        systemId: Int,
    ) {
        meetManager(manager)
        announceUntilDiscovered(manager, systemId)
    }

    /**
     * Ports and link state at the moment an expectation failed. A bound port that differs from the
     * configured one means the transport fell back to an ephemeral port.
     */
    private fun DatagramSocket.linkDiagnostics(manager: MavlinkManagerAndroid): String =
        "listenPort=${manager.listenPort}, boundPort=${manager.boundListenPort}, " +
            "controllerPort=$localPort, droneSystemId=${manager.droneSystemId}, " +
            "connected=${manager.connectionState.value.connected}, targetPort=${manager.targetPort}"

    /**
     * The manager must greet a network with a heartbeat before a drone can answer it. Kept for the
     * tests that assert that greeting.
     */
    private suspend fun DatagramSocket.awaitControllerGreeting(
        manager: MavlinkManagerAndroid,
        systemId: Int,
    ) {
        meetManager(manager)
        assertNotNull(
            awaitMavlinkHeartbeat(3_000.milliseconds),
            "manager must broadcast a heartbeat for discovery",
        )
        announceUntilDiscovered(manager, systemId)
    }

    /**
     * Keeps answering the manager until it discovers this controller. A real drone answers the GCS
     * heartbeat, but the manager is often mid-rebuild here and may even have had to move to a
     * fallback port, so the fake controller re-reads where the manager actually listens instead of
     * racing the rebuild or assuming the configured port.
     */
    private suspend fun DatagramSocket.announceUntilDiscovered(
        manager: MavlinkManagerAndroid,
        systemId: Int,
    ) {
        assertTrue(
            withTimeoutOrNull(5_000.milliseconds) {
                while (manager.droneSystemId != systemId || !manager.connectionState.value.connected) {
                    sendHeartbeatTo(InetAddress.getLoopbackAddress(), manager.listeningPort(), systemId)
                    delay(50.milliseconds)
                }
                true
            } == true,
            "controller $systemId was not discovered (${linkDiagnostics(manager)})",
        )
    }

    /** Points the manager at this controller, like a drone whose address is already known. */
    private fun DatagramSocket.meetManager(manager: MavlinkManagerAndroid) {
        manager.targetHost = InetAddress.getLoopbackAddress().hostAddress ?: "127.0.0.1"
        manager.targetPort = localPort
    }

    private suspend fun awaitSocketsOn(binder: FakeNetworkBinder, network: WifiNetwork): Boolean =
        withTimeoutOrNull(3_000.milliseconds) {
            while (binder.socketsCreatedOn().none { it.handle == network.handle }) {
                delay(20.milliseconds)
            }
            true
        } == true

    private fun DatagramSocket.sendHeartbeatTo(address: InetAddress, port: Int, systemId: Int) {
        val bytes = ByteArrayOutputStream().also {
            MavlinkConnection.create(null, it).send2(
                systemId,
                1,
                Heartbeat.builder()
                    .type(MavType.MAV_TYPE_QUADROTOR)
                    .autopilot(MavAutopilot.MAV_AUTOPILOT_GENERIC)
                    .baseMode(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED)
                    .systemStatus(MavState.MAV_STATE_ACTIVE)
                    .mavlinkVersion(3)
                    .build(),
            )
        }.toByteArray()
        send(DatagramPacket(bytes, bytes.size, address, port))
    }
}
