package com.goldarte.mavlinkjoystick.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class SettingsScreenState(
    val selectedTab: SettingsTab,
    val connectionSettingsState: ConnectionSettingsState,
    val stickSizeState: StickSizeState,
    val stickAppearanceState: StickAppearanceState,
    val curveSettingsState: StickCurveSettingsState,
) {
    enum class SettingsTab {
        Connection,
        SticksSize,
        SticksAppearance,
        SticksCurve,
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

    data class StickCurveSettingsState(
        val selectedAxis: StickAxis,
        val rollParams: CurveParams,
        val pitchParams: CurveParams,
        val yawParams: CurveParams,
        val throttleParams: CurveParams
    ) {
        enum class StickAxis {
            Roll,
            Pitch,
            Yaw,
            Throttle,
        }

        data class CurveParams(
            val weight: Float,
            val offset: Float,
            val expo: Float,
        )
    }
}