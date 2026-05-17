package com.eugenehammer.mavlinkjoystikkmp.mavlink

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MavlinkManagerIOS : MavlinkManager {
    override val consoleFlow: Flow<String> = flow { }
    override var targetHost: String = ""
    override var targetPort: Int = 0
    override var listenPort: Int = 0
    override var droneSystemId: Int = 0
    override var droneComponentId: Int = 0
    override var autoDetect: Boolean = false
    override var systemId: Int = 0
    override var componentId: Int = 0
    override var onStateChanged: ((armed: Boolean, connected: Boolean) -> Unit)? = null
    override var onAttitudeReceived: ((roll: Float, pitch: Float, yaw: Float) -> Unit)? = null
    override var onBatteryVoltageReceived: ((voltage: Float) -> Unit)? = null
    override var onFlightModeReceived: ((mode: String) -> Unit)? = null
    override var onAutopilotNameReceived: ((name: String) -> Unit)? = null
    override var onStatustextReceived: ((text: String, severity: Int) -> Unit)? = null

    override fun start() {

    }

    override fun stop() {

    }

    override fun sendArmCommand(arm: Boolean) {

    }

    override fun sendSerialControl(text: String) {

    }

    override fun setChannels(
        roll: Float,
        pitch: Float,
        throttle: Float,
        yaw: Float
    ) {

    }
}