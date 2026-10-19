package com.goldarte.mavlinkjoystick.ui.flight

import androidx.lifecycle.ViewModelStore
import com.goldarte.mavlinkjoystick.mavlink.BaseMavlinkManager
import com.goldarte.mavlinkjoystick.mavlink.testAppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FlightThrottleTest {

    @Test
    fun centeredThrottleIsBipolarAndReachesTheTransport() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val settings = testAppSettings()
            settings.setThrottleCentered(true)
            val manager = RecordingManager(backgroundScope)
            val vm = FlightViewModel(manager, settings)
            store.put("flight", vm)
            runCurrent()

            assertTrue(manager.isThrottleCentered, "the transport must encode the bipolar range")
            assertTrue(vm.uiState.value.leftJoystickState.isThrottleCentered)
            assertTrue(vm.uiState.value.rightJoystickState.isThrottleCentered)

            // A centered stick reaches -1..1, so a pull below the center is a negative throttle.
            vm.onLeftStickChanged(0f, -0.6f)
            assertEquals(-0.6f, manager.channels[2])

            vm.onLeftStickChanged(0f, 0.4f)
            assertEquals(0.4f, manager.channels[2])
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun armingCenteredThrottleResetsItToTheCenterAndKeepsItThere() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val settings = testAppSettings()
            settings.setThrottleCentered(true)
            val manager = RecordingManager(backgroundScope)
            val vm = FlightViewModel(manager, settings)
            store.put("flight", vm)
            runCurrent()
            vm.onLeftStickChanged(0f, -0.7f)
            manager.calls.clear()

            vm.onArmClick()
            runCurrent()

            // Center, not the bottom: the throttle must not keep the last commanded value.
            assertEquals(listOf("channels", "arm:true"), manager.calls)
            assertEquals(0f, manager.channelsAtArm[2])

            // It stays at the center until the pilot moves the stick again.
            vm.onRightStickChanged(0.5f, 0.1f)
            assertEquals(0f, manager.channels[2])
            vm.onLeftStickChanged(0f, 0.8f)
            assertEquals(0.8f, manager.channels[2])
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun bottomAnchoredThrottleKeepsItsLegacyBehaviour() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val manager = RecordingManager(backgroundScope)
            val vm = FlightViewModel(manager, testAppSettings())
            store.put("flight", vm)
            runCurrent()

            assertFalse(manager.isThrottleCentered)
            assertFalse(vm.uiState.value.leftJoystickState.isThrottleCentered)

            vm.onLeftStickChanged(0f, 0.3f)
            assertEquals(0.3f, manager.channels[2])

            // The bottom-anchored throttle is armed at the bottom of its 0..1 range.
            vm.onArmClick()
            runCurrent()
            assertEquals(0f, manager.channelsAtArm[2])
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private class RecordingManager(override val scope: CoroutineScope) :
        BaseMavlinkManager(testAppSettings()) {
        val calls = mutableListOf<String>()
        var channels = listOf(0f, 0f, 0f, 0f)
        var channelsAtArm = emptyList<Float>()

        override fun setChannels(roll: Float, pitch: Float, throttle: Float, yaw: Float) {
            channels = listOf(roll, pitch, throttle, yaw)
            calls += "channels"
        }

        override fun sendArmCommand(arm: Boolean) {
            channelsAtArm = channels
            calls += "arm:$arm"
        }

        override fun start() = Unit

        override fun stop() = Unit

        override fun sendSerialControl(text: String) = Unit
    }
}
