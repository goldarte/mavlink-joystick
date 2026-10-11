package com.goldarte.mavlinkjoystick.mavlink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BroadcastAddressTest {

    @Test
    fun typicalTelemetrySubnets() {
        // The mask has to be applied per byte; mixing up the direction produces 255.255.255.1 here.
        assertEquals("192.168.4.255", broadcastAddressFor("192.168.4.1", 24))
        assertEquals("192.168.4.63", broadcastAddressFor("192.168.4.20", 26))
        assertEquals("10.1.255.255", broadcastAddressFor("10.1.2.3", 16))
        assertEquals("172.16.0.255", broadcastAddressFor("172.16.0.1", 24))
        assertEquals("127.255.255.255", broadcastAddressFor("127.0.0.1", 8))
        assertEquals("192.168.4.7", broadcastAddressFor("192.168.4.7", 32))
        assertEquals("0.0.0.1", broadcastAddressFor("0.0.0.1", 32))
        assertEquals("192.168.4.1", broadcastAddressFor("192.168.4.1", 31))
    }

    @Test
    fun unusableInputReturnsNull() {
        assertNull(broadcastAddressFor("192.168.4.1", 0), "prefix 0 has no broadcast address")
        assertNull(broadcastAddressFor("192.168.4.1", 33))
        assertNull(broadcastAddressFor("not-an-address", 24))
        assertNull(broadcastAddressFor("192.168.4", 24))
        assertNull(broadcastAddressFor("192.168.4.999", 24))
        assertNull(broadcastAddressFor("fe80::1", 64), "IPv6 is not supported by the transport")
    }
}
