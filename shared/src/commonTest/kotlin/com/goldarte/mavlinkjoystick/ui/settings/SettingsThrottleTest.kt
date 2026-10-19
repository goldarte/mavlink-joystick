package com.goldarte.mavlinkjoystick.ui.settings

import com.goldarte.mavlinkjoystick.data.AppSettings
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
class SettingsThrottleTest {

    @Test
    fun throttleTabDefaultsToTheBottomAnchoredOption() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = SettingsViewModel(testAppSettings(), FakeManager(backgroundScope))
            runCurrent()

            assertFalse(vm.state.value.throttleSettingsState.isCentered)

            vm.onTabSelected(SettingsScreenState.SettingsTab.Throttle)
            assertEquals(SettingsScreenState.SettingsTab.Throttle, vm.state.value.selectedTab)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun choosingTheCenteredOptionPersistsItAndComesBackToTheScreen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings: AppSettings = testAppSettings()
            val vm = SettingsViewModel(settings, FakeManager(backgroundScope))
            runCurrent()

            vm.onThrottleCenteredChange(true)
            runCurrent()

            assertTrue(settings.state.value.isThrottleCentered, "the choice must be persisted")
            assertTrue(vm.state.value.throttleSettingsState.isCentered)

            vm.onThrottleCenteredChange(false)
            runCurrent()

            assertFalse(settings.state.value.isThrottleCentered)
            assertFalse(vm.state.value.throttleSettingsState.isCentered)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeManager(override val scope: CoroutineScope) :
        BaseMavlinkManager(testAppSettings()) {
        override fun start() = Unit

        override fun stop() = Unit

        override fun sendArmCommand(arm: Boolean) = Unit

        override fun sendSerialControl(text: String) = Unit
    }
}
