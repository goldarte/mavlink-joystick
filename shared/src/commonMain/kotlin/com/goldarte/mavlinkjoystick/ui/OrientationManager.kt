package com.goldarte.mavlinkjoystick.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class Orientation {
    Landscape,
    Portrait,
    All
}

expect object OrientationManager {
    fun setOrientation(orientation: Orientation)
}

@Composable
expect fun ToggleOrientationButton(modifier: Modifier = Modifier)

@Composable
expect fun BindActivityToOrientationManager()

@Composable
fun OrientationLock(orientation: Orientation) {
    BindActivityToOrientationManager()
    androidx.compose.runtime.DisposableEffect(orientation) {
        OrientationManager.setOrientation(orientation)
        onDispose {
            // Restore landscape on dispose if it was All/Portrait
            if (orientation != Orientation.Landscape) {
                OrientationManager.setOrientation(Orientation.Landscape)
            }
        }
    }
}
