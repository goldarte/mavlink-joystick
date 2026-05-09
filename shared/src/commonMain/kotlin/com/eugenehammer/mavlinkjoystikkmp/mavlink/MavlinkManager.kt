package com.eugenehammer.mavlinkjoystikkmp.mavlink

expect class MavlinkManager {

    fun start()

    fun stop()

    /** Update control values. All inputs are -1.0..1.0 (throttle 0..1). */
    fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float)

    /** Send MAV_CMD_COMPONENT_ARM_DISARM (400). */
    fun sendArmCommand(arm: Boolean)

    /** Send a command via SERIAL_CONTROL (msg #126) with DEV_SHELL flag. */
    fun sendSerialControl(text: String)
}