package com.goldarte.mavlinkjoystick.mavlink

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.goldarte.mavlinkjoystick.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MavlinkConnectionTest {
    @Test
    fun noLinkUntilFirstHeartbeat() {
        val manager = TestManager()
        manager.tick(100)
        assertFalse(manager.connectionState.value.connected)
        manager.heartbeat(10_000)
        assertTrue(manager.connectionState.value.connected)
    }

    @Test
    fun heartbeatTimeoutRestartsDiscoveryAndAllowsReconnection() {
        val manager = TestManager()
        manager.heartbeat(10_000, armed = true)
        manager.tick(12_999)
        assertTrue(manager.connectionState.value.connected)
        assertTrue(manager.connectionState.value.armed)
        manager.tick(13_000)
        assertFalse(manager.connectionState.value.connected)
        assertFalse(manager.connectionState.value.armed)
        assertFalse(manager.isArmed)
        assertFalse(manager.hasTarget)
        manager.heartbeat(20_000)
        assertTrue(manager.connectionState.value.connected)
        assertFalse(manager.connectionState.value.armed)
    }

    @Test
    fun armedHeartbeatAfterReconnectionRestoresArmedState() {
        val manager = TestManager()
        manager.heartbeat(10_000, armed = true)
        manager.tick(13_000)
        assertFalse(manager.connectionState.value.armed)
        manager.heartbeat(20_000, armed = true)
        assertTrue(manager.connectionState.value.connected)
        assertTrue(manager.connectionState.value.armed)
    }

    @Test
    fun stoppingClearsArmedState() {
        val manager = TestManager()
        manager.heartbeat(10_000, armed = true)
        manager.stop()
        assertFalse(manager.connectionState.value.connected)
        assertFalse(manager.connectionState.value.armed)
        assertFalse(manager.isArmed)
    }

    @Test
    fun changingNetworkCannotReuseThePreviousHeartbeat() {
        val manager = TestManager()
        manager.heartbeat(10_000, armed = true)
        manager.networkChanged()
        manager.tick(10_001)
        assertFalse(manager.connectionState.value.connected)
        assertFalse(manager.connectionState.value.armed)
        assertFalse(manager.hasTarget)
        manager.heartbeat(11_000)
        assertTrue(manager.connectionState.value.connected)
        assertFalse(manager.connectionState.value.armed)
    }

    @Test
    fun manualTargetRemainsAvailableAfterNetworkChangeAndTimeout() {
        val manager = TestManager().apply { autoDetect = false }
        manager.networkChanged()
        assertTrue(manager.hasTarget)
        manager.heartbeat(10_000, armed = true)
        manager.tick(13_000)
        assertFalse(manager.connectionState.value.connected)
        assertFalse(manager.connectionState.value.armed)
        assertTrue(manager.hasTarget)
    }

    @Test
    fun heartbeatTimeoutReportsConnectionLostExactlyOnce() {
        val manager = TestManager()
        val seen = mutableListOf<MavlinkEvent>()
        manager.collectEvents { if (it is MavlinkEvent.ConnectionLost) seen += it }
        manager.heartbeat(10_000, armed = true)
        manager.tick(12_999)
        assertTrue(seen.isEmpty())

        // The tick after the timeout stops refreshing the link and reports it once; further ticks
        // must not repeat the event while no new heartbeat arrives.
        manager.tick(13_000)
        assertEquals<List<MavlinkEvent>>(listOf(MavlinkEvent.ConnectionLost), seen)
        manager.tick(20_000)
        manager.tick(30_000)
        assertEquals<List<MavlinkEvent>>(listOf(MavlinkEvent.ConnectionLost), seen)

        // A reconnected link that times out again reports another loss.
        manager.heartbeat(40_000)
        manager.tick(44_000)
        assertEquals<List<MavlinkEvent>>(
            listOf(MavlinkEvent.ConnectionLost, MavlinkEvent.ConnectionLost),
            seen,
        )
    }

    @Test
    fun screenIsResetOnlyAfterTheDisconnectTimeout() {
        val manager = TestManager()
        val seen = mutableListOf<MavlinkEvent>()
        manager.collectEvents { if (it is MavlinkEvent.ScreenReset) seen += it }
        manager.heartbeat(10_000, armed = true)

        // Link timeouts clear the live state, the reset counts from that moment.
        val lostAt = 13_000L
        manager.tick(lostAt)
        assertFalse(manager.connectionState.value.connected)
        assertTrue(seen.isEmpty(), "the screen must survive a short disconnect")

        manager.tick(lostAt + SCREEN_RESET_AFTER_DISCONNECT_TIMEOUT_MS - 1)
        assertTrue(seen.isEmpty())
        manager.tick(lostAt + SCREEN_RESET_AFTER_DISCONNECT_TIMEOUT_MS)
        assertEquals<List<MavlinkEvent>>(listOf(MavlinkEvent.ScreenReset), seen)

        // The reset is not repeated on every following tick.
        manager.tick(lostAt + SCREEN_RESET_AFTER_DISCONNECT_TIMEOUT_MS + 1_000)
        assertEquals<List<MavlinkEvent>>(listOf(MavlinkEvent.ScreenReset), seen)
    }

    @Test
    fun reconnectWithinTheTimeoutKeepsTheScreen() {
        val manager = TestManager()
        val seen = mutableListOf<MavlinkEvent>()
        manager.collectEvents { if (it is MavlinkEvent.ScreenReset) seen += it }
        manager.heartbeat(10_000, armed = true)
        manager.tick(13_000)

        // The drone answers before its readings expire, so the screen is left alone.
        manager.tick(14_000)
        assertTrue(seen.isEmpty(), "the screen must survive a short disconnect, got $seen")
        manager.heartbeat(15_000, armed = true)
        assertTrue(manager.connectionState.value.connected)
        assertTrue(manager.connectionState.value.armed)

        // Still connected, and the reset deadline was cleared by that heartbeat.
        manager.tick(17_999)
        assertTrue(manager.connectionState.value.connected)
        assertTrue(seen.isEmpty())

        // The drone disappears for good: the screen is cleared only after the full timeout.
        manager.tick(18_000)
        assertFalse(manager.connectionState.value.connected)
        manager.tick(27_999)
        assertTrue(seen.isEmpty(), "readings are still on screen, got $seen")
        manager.tick(28_000)
        assertEquals<List<MavlinkEvent>>(listOf(MavlinkEvent.ScreenReset), seen)
    }

    @Test
    fun stoppingDoesNotReportConnectionLost() {
        val manager = TestManager()
        val seen = mutableListOf<MavlinkEvent>()
        manager.collectEvents { if (it is MavlinkEvent.ConnectionLost) seen += it }
        manager.heartbeat(10_000)
        manager.stop()
        manager.tick(20_000)
        assertTrue(seen.isEmpty(), "an explicit stop is not a link timeout, got $seen")
    }

    private class TestManager : BaseMavlinkManager(testAppSettings()) {
        override val scope = CoroutineScope(Dispatchers.Unconfined)
        val hasTarget get() = inited
        fun networkChanged() = resetDiscovery()
        fun tick(now: Long) = refreshConnection(now)
        fun heartbeat(now: Long, armed: Boolean = false) {
            inited = true
            handleHeartbeat(0, MavlinkAutopilot.Generic, if (armed) 0x80 else 0, now)
        }
        fun collectEvents(collector: FlowCollector<MavlinkEvent>) {
            scope.launch { events.collect(collector) }
        }
        override fun start() = Unit
        override fun stop() = resetDiscovery()
        override fun sendArmCommand(arm: Boolean) = Unit
        override fun sendSerialControl(text: String) = Unit
    }
}

internal fun testAppSettings(): AppSettings = AppSettings(object : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        return transform(data.value).also { data.value = it }
    }
})
