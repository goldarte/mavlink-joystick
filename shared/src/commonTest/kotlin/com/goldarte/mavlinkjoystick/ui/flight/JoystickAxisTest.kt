package com.goldarte.mavlinkjoystick.ui.flight

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The touch mapping behind the stick, covering the three vertical behaviours. A square canvas of
 * 1000x1000 with a full-size stick is used so the numbers stay readable: the stick radius is 440
 * and the top/bottom of the travel are 60/940.
 */
class JoystickAxisTest {
    private val width = 1000f
    private val height = 1000f
    private val center = 500f
    private val radius = 440f

    @Test
    fun bottomThrottleIsUnipolarAndHoldsItsPosition() {
        val bottom = throttleY(touchY = 940f, isCentered = false)
        val middle = throttleY(touchY = 500f, isCentered = false)
        val top = throttleY(touchY = 60f, isCentered = false)

        assertEquals(0f, bottom)
        assertEquals(0.5f, middle)
        assertEquals(1f, top)

        // The knob sits where the touch was: bottom of the travel for 0, the top for 1.
        assertEquals(radius, JoystickAxis.knobOffsetY(bottom, true, false, radius))
        assertEquals(-radius, JoystickAxis.knobOffsetY(top, true, false, radius))

        // Letting go must not move the bottom-anchored throttle.
        assertEquals(bottom, JoystickAxis.valueYAfterRelease(bottom, true, false))
        assertEquals(top, JoystickAxis.valueYAfterRelease(top, true, false))
    }

    @Test
    fun centeredThrottleIsBipolarWithUpPositiveAndReturnsToCenter() {
        val bottom = throttleY(touchY = 940f, isCentered = true)
        val middle = throttleY(touchY = 500f, isCentered = true)
        val top = throttleY(touchY = 60f, isCentered = true)

        assertEquals(-1f, bottom)
        assertEquals(0f, middle)
        assertEquals(1f, top)

        // Up on the screen must draw the knob above the center in both bipolar modes.
        assertEquals(-radius, JoystickAxis.knobOffsetY(top, true, true, radius))
        assertEquals(radius, JoystickAxis.knobOffsetY(bottom, true, true, radius))

        // Auto centering: the released stick always reports the center.
        assertEquals(0f, JoystickAxis.valueYAfterRelease(bottom, true, true))
        assertEquals(0f, JoystickAxis.valueYAfterRelease(top, true, true))
    }

    @Test
    fun ordinaryAxisIsUnchangedAndStillSpringsBack() {
        val bottom = valueY(touchY = 940f)
        val middle = valueY(touchY = 500f)
        val top = valueY(touchY = 60f)

        assertEquals(1f, bottom)
        assertEquals(0f, middle)
        assertEquals(-1f, top)

        assertEquals(radius, JoystickAxis.knobOffsetY(bottom, false, false, radius))
        assertEquals(0f, JoystickAxis.valueYAfterRelease(top, false, false))
        assertEquals(0f, JoystickAxis.valueYAfterRelease(top, false, true))
    }

    @Test
    fun touchIsClampedToTheStickArea() {
        val values = JoystickAxis.valuesFromTouch(
            touchX = 5_000f,
            touchY = -5_000f,
            width = width,
            height = height,
            stickSizeFactor = 1f,
            isThrottleMode = true,
            isThrottleCentered = true,
        )

        assertEquals(1f, values.x)
        assertEquals(1f, values.y)
    }

    private fun valueY(touchY: Float) = JoystickAxis.valuesFromTouch(
        touchX = center,
        touchY = touchY,
        width = width,
        height = height,
        stickSizeFactor = 1f,
        isThrottleMode = false,
        isThrottleCentered = false,
    ).y

    private fun throttleY(touchY: Float, isCentered: Boolean) = JoystickAxis.valuesFromTouch(
        touchX = center,
        touchY = touchY,
        width = width,
        height = height,
        stickSizeFactor = 1f,
        isThrottleMode = true,
        isThrottleCentered = isCentered,
    ).y
}
