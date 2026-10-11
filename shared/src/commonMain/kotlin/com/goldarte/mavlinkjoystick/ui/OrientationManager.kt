package com.goldarte.mavlinkjoystick.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class Orientation {
    Landscape,
    Portrait,
    All
}

expect object OrientationManager {
    fun setOrientation(orientation: Orientation)
}

@Composable
fun ToggleOrientationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(56.dp).aspectRatio(1f),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xFF2A2A2A),
            contentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = Icons.Default.ScreenRotation,
            contentDescription = "Toggle Orientation",
        )
    }
}

@Composable
expect fun BindActivityToOrientationManager()

@Composable
fun OrientationLock(orientation: Orientation) {
    BindActivityToOrientationManager()
    androidx.compose.runtime.LaunchedEffect(orientation) {
        OrientationManager.setOrientation(orientation)
    }
}
