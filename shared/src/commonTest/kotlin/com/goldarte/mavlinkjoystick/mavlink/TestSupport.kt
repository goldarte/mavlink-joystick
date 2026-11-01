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

/** Exposes the heartbeat and link-timeout handling of [BaseMavlinkManager] to the tests. */
internal class TestManager : BaseMavlinkManager(testAppSettings()) {
    override val scope = CoroutineScope(Dispatchers.Unconfined)
    val hasTarget get() = inited
    fun networkChanged() = resetDiscovery()
    fun tick(now: Long) = refreshConnection(now)
    fun heartbeat(
        now: Long,
        armed: Boolean = false,
        autopilot: MavlinkAutopilot = MavlinkAutopilot.Generic,
        customMode: Long = 0,
    ) {
        inited = true
        handleHeartbeat(customMode, autopilot, if (armed) 0x80 else 0, now)
    }
    fun collectEvents(collector: FlowCollector<MavlinkEvent>) {
        scope.launch { events.collect(collector) }
    }
    override fun start() = Unit
    override fun stop() = resetDiscovery()
    override fun sendArmCommand(arm: Boolean) = Unit
    override fun sendSerialControl(text: String) = Unit
}

internal fun testAppSettings(): AppSettings = AppSettings(object : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        return transform(data.value).also { data.value = it }
    }
})
