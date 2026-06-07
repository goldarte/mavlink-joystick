package com.goldarte.mavlinkjoystick.utils

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

actual fun Float.format(decimals: Int): String = NSString.stringWithFormat("%.${decimals}f", this)
