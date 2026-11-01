package com.goldarte.mavlinkjoystick.mavlink

import kotlin.test.Test
import kotlin.test.assertEquals

class MavlinkAutopilotTest {
    @Test
    fun mavlinkValuesAreMappedToSupportedAutopilots() {
        assertEquals(MavlinkAutopilot.Generic, mavlinkAutopilotOf(MAV_AUTOPILOT_GENERIC))
        assertEquals(MavlinkAutopilot.ArduPilotMega, mavlinkAutopilotOf(MAV_AUTOPILOT_ARDUPILOTMEGA))
        assertEquals(MavlinkAutopilot.Px4, mavlinkAutopilotOf(MAV_AUTOPILOT_PX4))
        assertEquals(MavlinkAutopilot.Flix, mavlinkAutopilotOf(MAV_AUTOPILOT_FLIX))
        // Unknown autopilots (e.g. ones added after this app was built) must not be misdetected.
        assertEquals(MavlinkAutopilot.Other, mavlinkAutopilotOf(22))
        assertEquals(MavlinkAutopilot.Other, mavlinkAutopilotOf(255))
    }

    @Test
    fun flixHeartbeatReportsItsOwnName() {
        val manager = TestManager()
        val names = mutableListOf<MavlinkEvent>()
        manager.collectEvents { if (it is MavlinkEvent.AutopilotNameReceived) names += it }

        manager.heartbeat(10_000, autopilot = MavlinkAutopilot.Flix)

        assertEquals<List<MavlinkEvent>>(listOf(MavlinkEvent.AutopilotNameReceived("Flix")), names)
    }

    @Test
    fun flixFlightModesAreNamed() {
        val manager = TestManager()
        val modes = mutableListOf<String>()
        manager.collectEvents { if (it is MavlinkEvent.FlightModeReceived) modes += it.mode }

        val expected = listOf(
            0L to "RAW",
            1L to "ACRO",
            2L to "STAB",
            3L to "AUTO",
            // An unknown Flix mode still has to be visible on screen.
            7L to "MODE(7)",
        )
        expected.forEachIndexed { index, (customMode, name) ->
            manager.heartbeat(10_000L + index, autopilot = MavlinkAutopilot.Flix, customMode = customMode)
            assertEquals(name, modes.last(), "custom mode $customMode")
        }
    }

    @Test
    fun genericAutopilotIsNoLongerLabeledAsFlix() {
        val manager = TestManager()
        val names = mutableListOf<MavlinkEvent>()
        val modes = mutableListOf<String>()
        manager.collectEvents { if (it is MavlinkEvent.AutopilotNameReceived) names += it }
        manager.collectEvents { if (it is MavlinkEvent.FlightModeReceived) modes += it.mode }

        // Older Flix firmware and generic vehicles report MAV_AUTOPILOT_GENERIC; their custom modes
        // are unit-specific, so only the raw value can be shown.
        manager.heartbeat(10_000, autopilot = MavlinkAutopilot.Generic, customMode = 2)

        assertEquals<List<MavlinkEvent>>(listOf(MavlinkEvent.AutopilotNameReceived("Generic")), names)
        assertEquals(listOf("MODE(2)"), modes)
    }
}
