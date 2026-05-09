package com.eugenehammer.mavlinkjoystikkmp.ui.flight

import androidx.lifecycle.ViewModel
import com.eugenehammer.mavlinkjoystikkmp.mavlink.MavlinkManager
import com.eugenehammer.mavlinkjoystikkmp.utils.CurveUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FlightViewModel(
    private val mavlinkManager: MavlinkManager,
) : ViewModel() {
    private val _state = MutableStateFlow(FlightState())

    val state = _state.asStateFlow()

    // RAW input

    private var rollRaw = 0f

    private var pitchRaw = 0f

    private var yawRaw = 0f

    private var throttleRaw = 0f

    fun onLeftStick(x: Float, y: Float) {
        yawRaw = x
        throttleRaw = y
        pushChannels()
    }

    fun onRightStick(x: Float, y: Float) {
        rollRaw = x
        pitchRaw = y
        pushChannels()
    }

    private fun pushChannels() {

        val s = _state.value

        val roll = CurveUtils.applyCurve(
            rollRaw, s.rollWeight, s.rollOffset, s.rollExpo
        )

        val pitch = CurveUtils.applyCurve(
            pitchRaw, s.pitchWeight, s.pitchOffset, s.pitchExpo
        )

        val yaw = CurveUtils.applyCurve(
            yawRaw, s.yawWeight, s.yawOffset, s.yawExpo
        )

        val throttle = CurveUtils.applyCurve(
            throttleRaw, s.thrWeight, s.thrOffset, s.thrExpo
        )

        mavlinkManager.setChannels(roll, pitch, throttle, yaw)
    }

    fun setAttitude(roll: Float, pitch: Float, yaw: Float) {
        _state.update {
            it.copy(
                rollDeg = roll, pitchDeg = pitch, heading = ((yaw % 360f) + 360f) % 360f
            )
        }
    }

    fun setBattery(v: Float) {
        _state.update { it.copy(battery = v) }
    }

    fun setFlightMode(mode: String) {
        _state.update { it.copy(mode = mode) }
    }

    fun setConnection(armed: Boolean, connected: Boolean) {
        _state.update {
            it.copy(
                armed = armed, connected = connected

            )
        }
    }
}