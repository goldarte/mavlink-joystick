package com.eugenehammer.mavlinkjoystikkmp.mavlink

interface MavlinkManager {
    var targetHost: String
    var targetPort: Int
    var listenPort: Int
    var droneSystemId: Int
    var droneComponentId: Int
    var autoDetect: Boolean
    var systemId: Int      // GCS system ID
    var componentId: Int    // GCS component ID

    var onStateChanged: ((armed: Boolean, connected: Boolean) -> Unit)?
    var onAttitudeReceived: ((roll: Float, pitch: Float, yaw: Float) -> Unit)?
    var onBatteryVoltageReceived: ((voltage: Float) -> Unit)?
    var onFlightModeReceived: ((mode: String) -> Unit)?
    var onAutopilotNameReceived: ((name: String) -> Unit)?
    var onStatustextReceived: ((text: String, severity: Int) -> Unit)?
    var onSerialControlReceived: ((data: ByteArray) -> Unit)?

    fun start()
    fun stop()
    fun sendArmCommand(arm: Boolean)
    fun sendSerialControl(text: String)
    fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float)
}