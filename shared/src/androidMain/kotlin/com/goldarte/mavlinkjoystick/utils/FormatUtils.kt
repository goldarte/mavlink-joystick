package com.goldarte.mavlinkjoystick.utils

actual fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
