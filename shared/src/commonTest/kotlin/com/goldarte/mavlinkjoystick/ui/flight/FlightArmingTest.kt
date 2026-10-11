package com.goldarte.mavlinkjoystick.ui.flight

import androidx.lifecycle.ViewModelStore
import com.goldarte.mavlinkjoystick.mavlink.BaseMavlinkManager
import com.goldarte.mavlinkjoystick.mavlink.MAVLINK_HEARTBEAT_TIMEOUT_MS
import com.goldarte.mavlinkjoystick.mavlink.MavlinkAutopilot
import com.goldarte.mavlinkjoystick.mavlink.SCREEN_RESET_AFTER_DISCONNECT_TIMEOUT_MS
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

    @Test
    fun longDisconnectResetsScreenToAppStartWithoutMovingSticks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val settings = testAppSettings()
            settings.setKnobColor(0xFF112233.toInt())
            val manager = RecordingManager(backgroundScope)
            val vm = FlightViewModel(manager, settings)
            store.put("flight", vm)
            runCurrent()

            // A live link fills the screen with telemetry.
            manager.publishArmed()
            manager.publishLiveTelemetry()
            vm.onLeftStickChanged(0.4f, 0.8f)
            vm.onRightStickChanged(0.2f, -0.3f)
            val leftBefore = vm.uiState.value.leftJoystickState
            val rightBefore = vm.uiState.value.rightJoystickState
            runCurrent()
            assertEquals("17.4V", vm.uiState.value.batteryVoltage)
            assertEquals("STABILIZE", vm.uiState.value.flightMode)
            assertEquals("ArduPilot", vm.uiState.value.autopilotName)
            assertEquals(12.5f, vm.uiState.value.rollDeg)
            assertTrue(vm.uiState.value.armed)

            // A short dropout keeps the readings and the arm button so the drone can reconnect
            // seamlessly: the manager reports the lost link but does not disarm.
            manager.publishConnectionLost()
            runCurrent()
            assertEquals("17.4V", vm.uiState.value.batteryVoltage)
            assertEquals("STABILIZE", vm.uiState.value.flightMode)
            assertEquals(12.5f, vm.uiState.value.rollDeg)
            assertTrue(vm.uiState.value.armed, "a short dropout must not clear the arm button")

            // The drone stayed away, so the screen must look like a fresh app start again.
            manager.publishScreenReset()
            runCurrent()

            val reset = vm.uiState.value
            assertEquals("--.-V", reset.batteryVoltage)
            assertEquals("---", reset.flightMode)
            assertEquals("---", reset.autopilotName)
            assertEquals(null, reset.connectionStatus)
            assertEquals(0f, reset.rollDeg)
            assertEquals(0f, reset.pitchDeg)
            assertEquals(0f, reset.yawHeading)
            // The delayed reset is a fresh app start, so the arm state and the button are cleared.
            assertFalse(manager.isArmed, "the manager clears the arm flag with the screen reset")
            assertFalse(reset.armed, "the arm button comes back disarmed")
            // Sticks and their configured appearance stay where the pilot left them.
            assertEquals(leftBefore, reset.leftJoystickState)
            assertEquals(rightBefore, reset.rightJoystickState)

            // A heartbeat on the reconnected link restores the readings, the cached autopilot name
            // included even though the drone reports the same one as before the reset.
            manager.publishLiveTelemetry()
            runCurrent()
            assertEquals("17.4V", vm.uiState.value.batteryVoltage)
            assertEquals(12.5f, vm.uiState.value.rollDeg)
            assertEquals("STABILIZE", vm.uiState.value.flightMode)
            assertEquals("ArduPilot", vm.uiState.value.autopilotName)
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

        // The link is driven through the manager's own heartbeat/timeout entry points, so the view
        // model sees exactly what the real transport would hand it, arm handling included.
        private var now = 0L

        fun publishArmed() {
            now += 1_000
            handleHeartbeat(0, MavlinkAutopilot.ArduPilotMega, 0x80, now)
        }

        /** Ages the last heartbeat past the timeout, which reports the lost link. */
        fun publishConnectionLost() {
            now += MAVLINK_HEARTBEAT_TIMEOUT_MS
            refreshConnection(now)
        }

        /** Lets the drone stay away for the full screen reset timeout. */
        fun publishScreenReset() {
            now += SCREEN_RESET_AFTER_DISCONNECT_TIMEOUT_MS
            refreshConnection(now)
        }

        fun publishLiveTelemetry() {
            now += 1_000
            handleHeartbeat(0, MavlinkAutopilot.ArduPilotMega, 0x80, now)
            emitAttitude(12.5f, -3.5f, 200f)
            emitBatteryVoltage(17.4f)
        }

        override fun start() = Unit
        override fun stop() = Unit
        override fun sendSerialControl(text: String) = Unit
    }
}
