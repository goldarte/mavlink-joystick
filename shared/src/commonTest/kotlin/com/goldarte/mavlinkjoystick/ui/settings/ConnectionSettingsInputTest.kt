// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.ui.settings

import com.goldarte.mavlinkjoystick.data.AppSettings
import com.goldarte.mavlinkjoystick.mavlink.BaseMavlinkManager
import com.goldarte.mavlinkjoystick.mavlink.testAppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSettingsInputTest {

    @Test
    fun typingAnIpAddressKeepsTheDots() = runSettingsTest { _, vm ->
        vm.onHostChanged("192.")
        assertEquals("192.", vm.host)

        vm.onHostChanged("192.168.4.1")
        assertEquals("192.168.4.1", vm.host)
    }

    @Test
    fun hostInputDropsCharactersThatCannotBePartOfAnIpAddress() = runSettingsTest { _, vm ->
        vm.onHostChanged("192.168.4.1a")
        assertEquals("192.168.4.1", vm.host)

        vm.onHostChanged("1 9 2 . 1 6 8 . 4 . 1")
        assertEquals("192.168.4.1", vm.host)
    }

    @Test
    fun numericFieldsAcceptDigitsOnly() = runSettingsTest { _, vm ->
        vm.onListenPortChanged("14a55")
        assertEquals("1455", vm.state.value.connectionSettingsState.listenPort)

        vm.onTargetPortChanged("14.55")
        assertEquals("1455", vm.state.value.connectionSettingsState.port)

        vm.onDroneSystemIdChanged("1x")
        assertEquals("1", vm.state.value.connectionSettingsState.droneSystemId)

        vm.onDroneComponentIdChanged("2.5")
        assertEquals("25", vm.state.value.connectionSettingsState.droneComponentId)
    }

    @Test
    fun clearingTheListenPortReportsAnErrorInsteadOfCrashing() = runSettingsTest { settings, vm ->
        vm.onListenPortChanged("")
        vm.onSaveConnectionSettingsClicked()

        assertNotNull(vm.state.value.connectionSettingsState.validationError)
        assertEquals(14550, settings.state.value.listenPort)
    }

    @Test
    fun anIncompleteIpAddressIsRejectedAndNotSaved() = runSettingsTest { settings, vm ->
        vm.onAutodetectCheckboxClicked()
        vm.onHostChanged("192.168.4.")
        vm.onSaveConnectionSettingsClicked()

        assertNotNull(vm.state.value.connectionSettingsState.validationError)
        assertEquals("255.255.255.255", settings.state.value.host)
    }

    @Test
    fun editingAFieldClearsThePreviousValidationError() = runSettingsTest { _, vm ->
        vm.onListenPortChanged("")
        vm.onSaveConnectionSettingsClicked()
        assertNotNull(vm.state.value.connectionSettingsState.validationError)

        vm.onListenPortChanged("14551")
        assertNull(vm.state.value.connectionSettingsState.validationError)
    }

    @Test
    fun aValidConnectionIsSaved() = runSettingsTest { settings, vm ->
        vm.onAutodetectCheckboxClicked()
        vm.onHostChanged("192.168.4.1")
        vm.onTargetPortChanged("14551")
        vm.onListenPortChanged("14552")
        vm.onDroneSystemIdChanged("7")
        vm.onDroneComponentIdChanged("8")
        vm.onSaveConnectionSettingsClicked()
        runCurrent()

        assertNull(vm.state.value.connectionSettingsState.validationError)
        assertEquals("192.168.4.1", settings.state.value.host)
        assertEquals(14551, settings.state.value.port)
        assertEquals(14552, settings.state.value.listenPort)
        assertEquals(7, settings.state.value.droneSystemId)
        assertEquals(8, settings.state.value.droneComponentId)
    }

    @Test
    fun targetFieldsOwnedByAutoDetectionDoNotBlockSaving() = runSettingsTest { settings, vm ->
        // Auto-detection is on by default, so the target fields are disabled and the user cannot
        // repair a value left there by an older version. Saving must still work and keep it.
        assertTrue(vm.state.value.connectionSettingsState.autoDetect)

        vm.onHostChanged("192.168.4.")
        vm.onSaveConnectionSettingsClicked()
        runCurrent()

        assertNull(vm.state.value.connectionSettingsState.validationError)
        assertEquals("255.255.255.255", settings.state.value.host)
    }

    @Test
    fun ipv4ParserAcceptsDottedQuadsOnly() {
        assertEquals("255.255.255.255", parseIpv4("255.255.255.255"))
        assertEquals("0.0.0.0", parseIpv4("0.0.0.0"))
        assertNull(parseIpv4(""))
        assertNull(parseIpv4("192.168.4"))
        assertNull(parseIpv4("192.168.4."))
        assertNull(parseIpv4("192.168.4.1.1"))
        assertNull(parseIpv4("192.168.256.1"))
    }

    @Test
    fun portAndIdParsersEnforceTheirRanges() {
        assertEquals(1, parsePort("1"))
        assertEquals(65535, parsePort("65535"))
        assertNull(parsePort("0"))
        assertNull(parsePort("65536"))
        assertNull(parsePort(""))

        assertEquals(255, parseMavlinkId("255"))
        assertNull(parseMavlinkId("0"))
        assertNull(parseMavlinkId("256"))
        assertNull(parseMavlinkId(""))
    }

    private fun runSettingsTest(
        block: suspend TestScope.(AppSettings, SettingsViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings = testAppSettings()
            val vm = SettingsViewModel(settings, FakeManager(backgroundScope))
            runCurrent()
            block(settings, vm)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val SettingsViewModel.host: String
        get() = state.value.connectionSettingsState.host

    private class FakeManager(override val scope: CoroutineScope) :
        BaseMavlinkManager(testAppSettings()) {
        override fun start() = Unit

        override fun stop() = Unit

        override fun sendArmCommand(arm: Boolean) = Unit

        override fun sendSerialControl(text: String) = Unit
    }
}
