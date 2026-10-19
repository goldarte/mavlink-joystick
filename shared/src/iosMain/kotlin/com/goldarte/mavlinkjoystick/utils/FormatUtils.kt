// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.utils

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

actual fun Float.format(decimals: Int): String = NSString.stringWithFormat("%.${decimals}f", this)
