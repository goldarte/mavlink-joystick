package com.eugenehammer.mavlinkjoystikkmp.mavlink

actual class MavlinkManager {
    actual fun start() {
    }

    actual fun stop() {
    }

    actual fun setChannels(
        roll: Float,
        pitch: Float,
        throttle: Float,
        yaw: Float
    ) {
    }

    actual fun sendArmCommand(arm: Boolean) {
    }

    actual fun sendSerialControl(text: String) {
    }
}