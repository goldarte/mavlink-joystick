// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.ui.flight

import androidx.compose.ui.graphics.Color

data class FlightScreenState(
    val armed: Boolean,

    val batteryVoltage: String,
    val flightMode: String,
    val autopilotName: String,
    val connectionStatus: String?,

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
        /** Only meaningful with [isThrottleMode]: the stick is centered instead of bottom-anchored. */
        val isThrottleCentered: Boolean,
        val showCircularArea: Boolean,
        val showSquareArea: Boolean,
        val showCircleBoundaries: Boolean,
        val stickSizeFactor: Float,
        val knobColor: Color,
    )
}

sealed interface FlightScreenEvent {
    data object GoToMenu : FlightScreenEvent
}