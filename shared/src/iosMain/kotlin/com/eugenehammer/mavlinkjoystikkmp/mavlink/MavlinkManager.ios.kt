package com.eugenehammer.mavlinkjoystikkmp.mavlink

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MavlinkManagerIOS : MavlinkManager {

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

    override suspend fun start() {
        TODO("Not yet implemented")
    }

    override suspend fun stop() {
        TODO("Not yet implemented")
    }

    override suspend fun updateConfig(config: MavlinkConfig) {
        TODO("Not yet implemented")
    }

    override suspend fun setChannels(
        roll: Float,
        pitch: Float,
        throttle: Float,
        yaw: Float
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun arm() {
        TODO("Not yet implemented")
    }

    override suspend fun disarm() {
        TODO("Not yet implemented")
    }

    override suspend fun sendSerialControl(text: String) {
        TODO("Not yet implemented")
    }
}