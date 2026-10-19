package com.goldarte.mavlinkjoystick.mavlink

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MANUAL_CONTROL carries the throttle in `z`. The bottom-anchored stick stays in 0..1000, while
 * the centered one uses the full -1000..1000 range with the center at 0.
 */
class ThrottleMappingTest {
    @Test
    fun bottomAnchoredThrottleMapsUpwardsFromZero() {
        val manager = ThrottleManager()

        manager.setChannels(0f, 0f, 0f, 0f)
        assertEquals(0, manager.throttle)

        manager.setChannels(0f, 0f, 0.5f, 0f)
        assertEquals(500, manager.throttle)

        manager.setChannels(0f, 0f, 1f, 0f)
        assertEquals(1000, manager.throttle)
    }

    @Test
    fun bottomAnchoredThrottleClampsAtTheBottom() {
        val manager = ThrottleManager()

        // Negative values cannot be produced by an unipolar stick; they must not reach the drone.
        manager.setChannels(0f, 0f, -0.5f, 0f)

        assertEquals(0, manager.throttle)
    }

    @Test
    fun centeredThrottleUsesTheFullBipolarRange() {
        val manager = ThrottleManager().apply { isThrottleCentered = true }

        manager.setChannels(0f, 0f, -1f, 0f)
        assertEquals(-1000, manager.throttle)

        manager.setChannels(0f, 0f, -0.25f, 0f)
        assertEquals(-250, manager.throttle)

        // 0 is the center, not the bottom.
        manager.setChannels(0f, 0f, 0f, 0f)
        assertEquals(0, manager.throttle)

        manager.setChannels(0f, 0f, 1f, 0f)
        assertEquals(1000, manager.throttle)
    }

    private class ThrottleManager : BaseMavlinkManager(testAppSettings()) {
        override val scope = CoroutineScope(Dispatchers.Unconfined)

        val throttle: Int get() = stickZ

        override fun start() = Unit

        override fun stop() = Unit

        override fun sendArmCommand(arm: Boolean) = Unit

        override fun sendSerialControl(text: String) = Unit
    }
}
