package com.goldarte.mavlinkjoystick.mavlink

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.goldarte.mavlinkjoystick.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
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
        manager.heartbeat(10_000)
        manager.tick(12_999)
        assertTrue(manager.connectionState.value.connected)
        manager.tick(13_000)
        assertFalse(manager.connectionState.value.connected)
        assertFalse(manager.hasTarget)
        manager.heartbeat(20_000)
        assertTrue(manager.connectionState.value.connected)
    }

    @Test
    fun changingNetworkCannotReuseThePreviousHeartbeat() {
        val manager = TestManager()
        manager.heartbeat(10_000)
        manager.networkChanged()
        manager.tick(10_001)
        assertFalse(manager.connectionState.value.connected)
        assertFalse(manager.hasTarget)
        manager.heartbeat(11_000)
        assertTrue(manager.connectionState.value.connected)
    }

    @Test
    fun manualTargetRemainsAvailableAfterNetworkChangeAndTimeout() {
        val manager = TestManager().apply { autoDetect = false }
        manager.networkChanged()
        assertTrue(manager.hasTarget)
        manager.heartbeat(10_000)
        manager.tick(13_000)
        assertFalse(manager.connectionState.value.connected)
        assertTrue(manager.hasTarget)
    }

    private class TestManager : BaseMavlinkManager(testAppSettings()) {
        override val scope = CoroutineScope(Dispatchers.Unconfined)
        val hasTarget get() = inited
        fun networkChanged() = resetDiscovery()
        fun tick(now: Long) = refreshConnection(now)
        fun heartbeat(now: Long) {
            inited = true
            handleHeartbeat(0, MavlinkAutopilot.Generic, 0, now)
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
