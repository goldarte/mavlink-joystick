package com.eugenehammer.mavlinkjoystikkmp.ui.flight

data class FlightState(
    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val heading: Float = 0f,

    val battery: Float = 0f,
    val mode: String = "",

    val armed: Boolean = false,
    val connected: Boolean = false,

    // curves
    val rollWeight: Float = 1f,
    val rollOffset: Float = 0f,
    val rollExpo: Float = 0f,

    val pitchWeight: Float = 1f,
    val pitchOffset: Float = 0f,
    val pitchExpo: Float = 0f,

    val yawWeight: Float = 1f,
    val yawOffset: Float = 0f,
    val yawExpo: Float = 0f,

    val thrWeight: Float = 1f,
    val thrOffset: Float = 0f,
    val thrExpo: Float = 0f
)