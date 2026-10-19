// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.utils

/**
 * Formats a Float to a string with a specified number of decimal places.
 * Uses platform-specific formatting tools.
 */
expect fun Float.format(decimals: Int): String
