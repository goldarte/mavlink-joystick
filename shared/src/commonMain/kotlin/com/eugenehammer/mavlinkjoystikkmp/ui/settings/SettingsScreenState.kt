package com.eugenehammer.mavlinkjoystikkmp.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class SettingsScreenState(
    val selectedTab: SettingsTab,
    val connectionSettingsState: ConnectionSettingsState,
    val stickSizeState: StickSizeState,
    val stickAppearanceState: StickAppearanceState,
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

    data class StickSizeState(
        val leftFactor: Float,
        val rightFactor: Float,
    )

    data class StickAppearanceState(
        val showCircularArea: Boolean,
        val showSquareArea: Boolean,
        val showCircleBoundaries: Boolean,
        val knobColor: Color,
    )
}