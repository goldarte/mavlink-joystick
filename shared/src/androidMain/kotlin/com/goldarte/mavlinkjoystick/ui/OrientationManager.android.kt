package com.goldarte.mavlinkjoystick.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

actual object OrientationManager {
    private var activity: Activity? = null

    fun setActivity(activity: Activity) {
        this.activity = activity
    }

    actual fun setOrientation(orientation: Orientation) {
        activity?.requestedOrientation = when (orientation) {
            Orientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Orientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Orientation.All -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
actual fun BindActivityToOrientationManager() {
    val activity = LocalContext.current.findActivity()
    androidx.compose.runtime.LaunchedEffect(activity) {
        activity?.let {
            OrientationManager.setActivity(it)
        }
    }
}

@Composable
actual fun ToggleOrientationButton(modifier: Modifier) {
    BindActivityToOrientationManager()
    val activity = LocalContext.current.findActivity()
    IconButton(
        onClick = {
            OrientationManager.setOrientation(
                if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    Orientation.Portrait
                } else {
                    Orientation.Landscape
                }
            )
        },
        modifier = modifier
            .size(56.dp)
            .aspectRatio(1f),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xFF2A2A2A),
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = Icons.Default.ScreenRotation,
            contentDescription = "Toggle Orientation"
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
