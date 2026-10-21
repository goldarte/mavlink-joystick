// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Copyright (c) 2026 Eugene Molotkov
// Repository: https://github.com/goldarte/mavlink-joystick

package com.goldarte.mavlinkjoystick.ui.settings

/*
 * Input rules for the connection fields.
 *
 * While a field is being edited its value is an arbitrary string: the user may delete a character,
 * paste something from the clipboard or use a hardware keyboard. Sanitizing therefore only drops
 * characters that can never be part of the value, and it must keep every character needed to type
 * one — most importantly the dots between the octets of an IPv4 address. Range checks happen when
 * the settings are saved, so a half-typed value is not rejected on every keystroke.
 */

/** Longest possible dotted-quad IPv4 address: `255.255.255.255`. */
internal const val MAX_IPV4_INPUT_LENGTH = 15

/** Longest possible port: `65535`. */
internal const val MAX_PORT_INPUT_LENGTH = 5

/** Longest possible MAVLink system/component id: `255`. */
internal const val MAX_MAVLINK_ID_INPUT_LENGTH = 3

internal fun sanitizeIpv4Input(raw: String): String =
    raw.filter { it in '0'..'9' || it == '.' }.take(MAX_IPV4_INPUT_LENGTH)

internal fun sanitizePortInput(raw: String): String =
    raw.filter { it in '0'..'9' }.take(MAX_PORT_INPUT_LENGTH)

internal fun sanitizeMavlinkIdInput(raw: String): String =
    raw.filter { it in '0'..'9' }.take(MAX_MAVLINK_ID_INPUT_LENGTH)

/** Returns [input] when it is a dotted-quad IPv4 address, or `null` otherwise. */
internal fun parseIpv4(input: String): String? {
    val octets = input.split('.')
    if (octets.size != 4) return null
    for (octet in octets) {
        val value = octet.toIntOrNull() ?: return null
        if (value !in 0..255) return null
    }
    return input
}

/** Parses a UDP port, or returns `null` when [input] is not a usable one. */
internal fun parsePort(input: String): Int? =
    input.toIntOrNull()?.takeIf { it in 1..65535 }

/** Parses a MAVLink system/component id, or returns `null` when [input] is out of range. */
internal fun parseMavlinkId(input: String): Int? =
    input.toIntOrNull()?.takeIf { it in 1..255 }
