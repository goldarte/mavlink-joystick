package com.eugenehammer.mavlinkjoystikkmp.ui.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsScreenState(
    val selectedTab: SettingsTab,
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
}