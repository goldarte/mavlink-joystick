package com.goldarte.mavlinkjoystick

import com.goldarte.mavlinkjoystick.mavlink.MavlinkManager
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.common.ManualControl
import io.dronefleet.mavlink.common.RcChannelsOverride
import io.dronefleet.mavlink.minimal.Heartbeat
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket

class MavlinkManagerTest {

    @Test
    fun testMavlinkV2PacketFormat() {
        // We want to verify that the packets sent are indeed MAVLink v2
        // MAVLink v2 starts with 0xFD
        
        val manager = MavlinkManager(targetHost = "127.0.0.1", targetPort = 14551, listenPort = 14552)
        
        // Since we can't easily mock DatagramSocket easily without refactoring MavlinkManager 
        // to take a socket factory or similar, we will just use a real socket on localhost 
        // and a receiver socket to capture the packet.
        
        val recvSocket = DatagramSocket(14551)
        recvSocket.soTimeout = 2000
        
        manager.start()
        
        val packet = DatagramPacket(ByteArray(1024), 1024)
        try {
            recvSocket.receive(packet)
            val data = packet.data.copyOfRange(0, packet.length)
            
            // Check for MAVLink v2 STX
            assertEquals(0xFD.toByte(), data[0])
            
            // Try to parse it using the library to be sure
            val bais = ByteArrayInputStream(data)
            val connection = MavlinkConnection.create(bais, null)
            val message = connection.next()
            assertNotNull(message)
            assertTrue(message!!.payload is Heartbeat || message!!.payload is RcChannelsOverride || message!!.payload is ManualControl)
            
        } finally {
            manager.stop()
            recvSocket.close()
        }
    }

    @Test
    fun testSetChannels() {
        val manager = MavlinkManager()
        // axisToMicros(1.0) -> 2000
        // axisToMicros(-1.0) -> 1000
        // throttleToMicros(1.0) -> 2000
        // axisToMicros(0.0) -> 1500
        
        manager.setChannels(1.0f, 1.0f, 1.0f, 0.0f)
        
        // We can't access private fields easily, but we can check if they are sent correctly
        // by capturing the RcChannelsOverride message.
        // Pitch is inverted in setChannels: ch2Pitch = axisToMicros(-pitch)
        // so pitch 1.0f -> -1.0f -> 1000
    }
}
