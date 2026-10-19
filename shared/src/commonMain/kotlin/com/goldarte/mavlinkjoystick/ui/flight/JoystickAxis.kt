package com.goldarte.mavlinkjoystick.ui.flight

import kotlin.math.min

/**
 * Pure geometry of the on-screen stick. It lives outside the composable so the vertical
 * behaviours can be unit tested:
 *
 * - a normal axis is centered and bipolar, up is negative, and it returns to 0 on release;
 * - a bottom-anchored throttle is unipolar 0..1, grows upwards and holds its position on release;
 * - a centered throttle is bipolar -1..1 with up positive and returns to 0 on release, which is
 *   what altitude/position hold modes, robots and games expect.
 */
internal object JoystickAxis {

    data class Values(val x: Float, val y: Float)

    fun valuesFromTouch(
        touchX: Float,
        touchY: Float,
        width: Float,
        height: Float,
        stickSizeFactor: Float,
        isThrottleMode: Boolean,
        isThrottleCentered: Boolean,
    ): Values {
        val cx = width / 2f
        val cy = height / 2f

        val radius = (min(width, height) / 2f) *
                0.88f *
                stickSizeFactor

        val clampedX = touchX.coerceIn(
            cx - radius,
            cx + radius,
        )

        val clampedY = touchY.coerceIn(
            cy - radius,
            cy + radius,
        )

        val valueX = ((clampedX - cx) / radius).coerceIn(-1f, 1f)

        return Values(
            x = valueX,
            y = valueY(clampedY, cy, radius, isThrottleMode, isThrottleCentered),
        )
    }

    fun valueY(
        clampedY: Float,
        cy: Float,
        radius: Float,
        isThrottleMode: Boolean,
        isThrottleCentered: Boolean,
    ): Float = when {
        !isThrottleMode -> ((clampedY - cy) / radius).coerceIn(-1f, 1f)

        // Centered throttle: up on the screen is positive throttle.
        isThrottleCentered -> ((cy - clampedY) / radius).coerceIn(-1f, 1f)

        else -> (1f - ((clampedY - (cy - radius)) / (2f * radius))).coerceIn(0f, 1f)
    }

    /** Vertical knob offset from the center of the stick area. */
    fun knobOffsetY(
        valueY: Float,
        isThrottleMode: Boolean,
        isThrottleCentered: Boolean,
        radius: Float,
    ): Float = when {
        !isThrottleMode -> valueY * radius
        isThrottleCentered -> -valueY * radius
        else -> radius - valueY * radius * 2f
    }

    /**
     * Value the stick takes when the finger is lifted: a bottom-anchored throttle holds the
     * position it was left at, everything else springs back to the center.
     */
    fun valueYAfterRelease(
        valueY: Float,
        isThrottleMode: Boolean,
        isThrottleCentered: Boolean,
    ): Float = if (isThrottleMode && !isThrottleCentered) valueY else 0f
}
