package com.eugenehammer.mavlinkjoystikkmp.ui.flight

data class FlightScreenState(
    val armed: Boolean,
    val connected: Boolean,

    val batteryVoltage: String,
    val flightMode: String,
    val autopilotName: String,
    val connectionStatus: String,

    val rollDeg: Float,
    val pitchDeg: Float,
    val yawHeading: Float,

    val leftJoystickState: JoystickState,
    val rightJoystickState: JoystickState,
) {
    data class JoystickState(
        val valueX: Float,
        val valueY: Float,
        val isThrottleMode: Boolean,
        val showCircularArea: Boolean,
        val showSquareArea: Boolean,
        val showCircleBoundaries: Boolean,
    )
}

sealed interface FlightScreenEvent {
    data object GoToSettings : FlightScreenEvent
}