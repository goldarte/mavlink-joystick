package com.eugenehammer.mavlinkjoystikkmp.ui.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsScreenState(
    val selectedTab: SettingsTab,
    val connectionSettingsState: ConnectionSettingsState,
) {
    enum class SettingsTab(
        val title: String,
    ) {
        Connection("CONNECTION"),
        SticksSize("↳ SIZE"),
        SticksAppearance("↳ APPEARANCE"),
        SticksCurve("↳ CURVE"),
        MavlinkConsole("MAVLINK CONSOLE"),
    }

    data class ConnectionSettingsState(
        val autoDetect: Boolean,
        val host: String,
        val port: String,
        val listenPort: String,
        val droneSystemId: String,
        val droneComponentId: String,
    )
}