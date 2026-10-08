package com.goldarte.mavlinkjoystick.ui

import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import androidx.compose.runtime.Composable

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
