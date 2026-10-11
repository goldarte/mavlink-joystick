package com.goldarte.mavlinkjoystick.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.lang.ref.WeakReference

actual object OrientationManager {
    private var activityReference: WeakReference<Activity>? = null

    internal fun bindActivity(activity: Activity): () -> Unit {
        val reference = WeakReference(activity)
        activityReference = reference
        return {
            // A departing screen must not clear a newer screen's binding.
            if (activityReference === reference) {
                activityReference = null
            }
            reference.clear()
        }
    }

    actual fun setOrientation(orientation: Orientation) {
        activityReference?.get()?.requestedOrientation = when (orientation) {
            Orientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Orientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Orientation.All -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
actual fun BindActivityToOrientationManager() {
    val activity = LocalContext.current.findActivity()
    androidx.compose.runtime.DisposableEffect(activity) {
        val unbind = activity?.let { OrientationManager.bindActivity(it) }
        onDispose { unbind?.invoke() }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
