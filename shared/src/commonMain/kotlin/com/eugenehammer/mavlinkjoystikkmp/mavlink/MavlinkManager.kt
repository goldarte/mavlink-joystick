package com.eugenehammer.mavlinkjoystikkmp.mavlink

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MavlinkManager {

    val state: StateFlow<MavlinkState>

    val events: SharedFlow<MavlinkEvent>

    suspend fun start()

    suspend fun stop()

    suspend fun updateConfig(

        config: MavlinkConfig,

        )

    suspend fun setChannels(

        roll: Float,

        pitch: Float,

        throttle: Float,

        yaw: Float,

        )

    suspend fun arm()

    suspend fun disarm()

    suspend fun sendSerialControl(

        text: String,

        )

}

data class MavlinkState(
    val armed: Boolean = false,
    val connected: Boolean = false,

    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val yawDeg: Float = 0f,

    val batteryVoltage: Float? = null,

    val flightMode: String = "---",
    val autopilotName: String = "---",

    val targetHost: String = "",
    val targetPort: Int = 0,
)

sealed interface MavlinkEvent {

    data class StatusText(
        val text: String,
        val severity: Int,
    ) : MavlinkEvent

    data class SerialData(
        val data: ByteArray,
    ) : MavlinkEvent
}

data class MavlinkConfig(

    val targetHost: String,

    val targetPort: Int,

    val listenPort: Int,

    val droneSystemId: Int,

    val droneComponentId: Int,

    val autoDetect: Boolean,

    val systemId: Int = 255,

    val componentId: Int = 190,

    )