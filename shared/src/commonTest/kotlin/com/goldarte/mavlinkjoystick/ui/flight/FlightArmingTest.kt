package com.goldarte.mavlinkjoystick.ui.flight

import androidx.lifecycle.ViewModelStore
import com.goldarte.mavlinkjoystick.mavlink.BaseMavlinkManager
import com.goldarte.mavlinkjoystick.mavlink.MavlinkAutopilot
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FlightArmingTest {
    @Test
    fun armResetsLeftStickAndChannelsBeforeCommandAndKeepsZeroUntilMoved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val settings = testAppSettings()
            settings.setThrottleOffset(0.2f)
            settings.setYawOffset(0.1f)
            val manager = RecordingManager(backgroundScope)
            val vm = FlightViewModel(manager, settings)
            store.put("flight", vm)
            runCurrent()
            vm.onLeftStickChanged(0.4f, 0.8f)
            vm.onRightStickChanged(0.2f, -0.3f)
            val right = vm.uiState.value.rightJoystickState
            val rightChannels = manager.channels.take(2)
            manager.calls.clear()

            vm.onArmClick()
            runCurrent()

            assertEquals(0f, vm.uiState.value.leftJoystickState.valueX)
            assertEquals(0f, vm.uiState.value.leftJoystickState.valueY)
            assertEquals(right, vm.uiState.value.rightJoystickState)
            assertEquals(listOf("channels", "arm:true"), manager.calls)
            assertEquals(rightChannels + listOf(0f, 0f), manager.channelsAtArm)

            // Moving the other stick must not reintroduce throttle/yaw offsets.
            vm.onRightStickChanged(0.5f, 0.1f)
            vm.onLeftStickChanged(0f, 0f)
            assertEquals(listOf(0f, 0f), manager.channels.takeLast(2))
            vm.onLeftStickChanged(0.4f, 0.8f)
            assertTrue(manager.channels[2] > 0f)
            assertTrue(manager.channels[3] > 0f)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun disarmDoesNotResetEitherStick() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val manager = RecordingManager(backgroundScope)
            val vm = FlightViewModel(manager, testAppSettings())
            store.put("flight", vm)
            runCurrent()
            manager.publishArmed()
            runCurrent()
            vm.onLeftStickChanged(0.4f, 0.8f)
            vm.onRightStickChanged(0.2f, -0.3f)
            val before = vm.uiState.value
            manager.calls.clear()

            vm.onArmClick()
            vm.onArmLongClick()
            runCurrent()

            assertEquals(before.leftJoystickState, vm.uiState.value.leftJoystickState)
            assertEquals(before.rightJoystickState, vm.uiState.value.rightJoystickState)
            assertEquals(listOf("arm:false", "arm:false"), manager.calls)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private class RecordingManager(override val scope: CoroutineScope) : BaseMavlinkManager(testAppSettings()) {
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
        fun publishArmed() = handleHeartbeat(0, MavlinkAutopilot.Generic, 0x80, 1)
        override fun start() = Unit
        override fun stop() = Unit
        override fun sendSerialControl(text: String) = Unit
    }
}
