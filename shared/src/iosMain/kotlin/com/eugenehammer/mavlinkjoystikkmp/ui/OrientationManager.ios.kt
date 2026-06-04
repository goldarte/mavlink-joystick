package com.eugenehammer.mavlinkjoystikkmp.ui

import platform.UIKit.*
import platform.Foundation.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.*
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
import androidx.compose.ui.unit.dp

actual object OrientationManager {
    
    // Use a more robust way to communicate with Swift if needed, 
    // but a callback on the object should work.
    var onOrientationChangeRequested: ((Orientation) -> Unit)? = null

    actual fun setOrientation(orientation: Orientation) {
        // Run on main thread to be safe for UI updates
        dispatch_async(dispatch_get_main_queue()) {
            onOrientationChangeRequested?.invoke(orientation)
        }
    }
}

@Composable
actual fun BindActivityToOrientationManager() {
    // Not needed for iOS
}

@Composable
actual fun ToggleOrientationButton(modifier: Modifier) {
    IconButton(
        onClick = {
            val windowScene = UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene
            if (windowScene != null) {
                val currentOrientation = windowScene.interfaceOrientation
                val isLandscape = currentOrientation == UIInterfaceOrientationLandscapeLeft || 
                                 currentOrientation == UIInterfaceOrientationLandscapeRight
                
                // If we are in landscape, request portrait. 
                // Note: .all allows both, but for the toggle we force a specific one.
                OrientationManager.setOrientation(
                    if (isLandscape) Orientation.Portrait else Orientation.Landscape
                )
            } else {
                // Fallback for older iOS or when windowScene is missing
                OrientationManager.setOrientation(Orientation.All)
            }
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
