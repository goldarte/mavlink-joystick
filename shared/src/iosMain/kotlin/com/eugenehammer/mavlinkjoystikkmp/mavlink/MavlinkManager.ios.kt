package com.eugenehammer.mavlinkjoystikkmp.mavlink

class MavlinkManagerIOS : MavlinkManager {
    override var targetHost: String = ""
    override var targetPort: Int = 0
    override var listenPort: Int = 0
    override var droneSystemId: Int = 0
    override var droneComponentId: Int = 0
    override var autoDetect: Boolean = false
    override var systemId: Int = 0
    override var componentId: Int = 0
    override var onStateChanged: ((armed: Boolean, connected: Boolean) -> Unit)?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var onAttitudeReceived: ((roll: Float, pitch: Float, yaw: Float) -> Unit)?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var onBatteryVoltageReceived: ((voltage: Float) -> Unit)?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var onFlightModeReceived: ((mode: String) -> Unit)?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var onAutopilotNameReceived: ((name: String) -> Unit)?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var onStatustextReceived: ((text: String, severity: Int) -> Unit)?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var onSerialControlReceived: ((data: ByteArray) -> Unit)?
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun sendArmCommand(arm: Boolean) {
        TODO("Not yet implemented")
    }

    override fun sendSerialControl(text: String) {
        TODO("Not yet implemented")
    }

    override fun setChannels(
        roll: Float,
        pitch: Float,
        throttle: Float,
        yaw: Float
    ) {
        TODO("Not yet implemented")
    }
}