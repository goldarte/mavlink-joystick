// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.eugenehammer.mavlinkjoystikkmp.utils

import kotlin.math.pow

object CurveUtils {
    /**
     * Applies weight, offset, and expo to an input value.
     * x: input in range [-1, 1]
     * weight: multiplier, typically [0, 1]
     * offset: addition, typically [-1, 1]
     * expo: exponential curve, [0, 1] where 0 is linear
     */
    fun applyCurve(x: Float, weight: Float, offset: Float, expo: Float): Float {
        val xExpo = applyExpo(x, expo)
        return (xExpo * weight + offset).coerceIn(-1f, 1f)
    }

    /**
     * EdgeTX-style expo: y = x^3 * expo + x * (1 - expo)
     * This provides a smooth curve through zero.
     */
    fun applyExpo(x: Float, expo: Float): Float {
        if (expo == 0f) return x
        // Common formula: y = x * (1 - expo) + x^3 * expo
        // This is a simple approximation of EdgeTX expo
        return x * (1f - expo) + (x.pow(3)) * expo
    }
}
