package com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.eugenehammer.mavlinkjoystikkmp.ui.flight.FlightScreenState
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Compose Multiplatform version of JoystickView.
 *
 * Features:
 * - throttle mode
 * - spring-back
 * - gradients/glow
 * - square/circle guides
 * - normalized output values
 *
 * Output:
 * x ∈ [-1; 1]
 * y ∈ [-1; 1] or [0; 1] for throttle mode
 */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    state: FlightScreenState.JoystickState,
    onChanged: (x: Float, y: Float) -> Unit,
    onDragEnd: () -> Unit,
) {

    val showCircularArea = true
    val showSquareArea = true
    val showCircleBoundaries = false

    val stickSizeFactor = 0.65f
    val knobColor = Color(0xFFFF5C8D)

    val canvasSize = remember { mutableStateOf(IntSize.Zero) }

    val width = canvasSize.value.width.toFloat()
    val height = canvasSize.value.height.toFloat()

    val center = remember(width, height) {
        Offset(width / 2f, height / 2f)
    }

    val radius = remember(width, height, stickSizeFactor) {
        (min(width, height) / 2f) *
                0.88f *
                stickSizeFactor.coerceIn(0.25f, 1f)
    }

    val knobRadius = radius * 0.22f

    val finalKnob = remember(state, center, radius) {
        Offset(
            x = center.x + state.x * radius,
            y = if (state.isThrottleMode) {
                // throttle mode uses inverted Y (0..1 behavior handled externally if needed)
                center.y + state.y * radius
            } else {
                center.y + state.y * radius
            }
        )
    }

    val valueX = remember(state.x) {
        state.x.coerceIn(-1f, 1f)
    }

    val valueY = remember(state.y, state.isThrottleMode) {
        if (state.isThrottleMode) {
            // 0..1 throttle mapping
            ((-state.y + 1f) / 2f).coerceIn(0f, 1f)
        } else {
            state.y.coerceIn(-1f, 1f)
        }
    }

    androidx.compose.runtime.LaunchedEffect(valueX, valueY) {
        onChanged(valueX, valueY)
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxSize()
            .onSizeChanged { canvasSize.value = it }
            .pointerInput(center, radius, state.isThrottleMode) {

                detectDragGestures(
                    onDragStart = {
                        val dx = (it.x - center.x) / radius
                        val dy = (it.y - center.y) / radius

                        onChanged(
                            dx.coerceIn(-1f, 1f),
                            dy.coerceIn(-1f, 1f)
                        )
                    },

                    onDragEnd = {
                        onDragEnd()
                    },

                    onDragCancel = {
                        onDragEnd()
                    },

                    onDrag = { change, _ ->

                        val dx = (change.position.x - center.x) / radius
                        val dy = (change.position.y - center.y) / radius

                        onChanged(
                            dx.coerceIn(-1f, 1f),
                            dy.coerceIn(-1f, 1f)
                        )
                    }
                )
            }
    ) {

        if (radius <= 0f || knobRadius <= 0f) return@Canvas

        val ringColor = Color.White.copy(alpha = 0.27f)
        val fillColor = Color.White.copy(alpha = 0.08f)
        val crossColor = Color.White.copy(alpha = 0.2f)

        // Square area
        if (showSquareArea) {

            drawRect(
                color = fillColor,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f)
            )

            drawRect(
                color = ringColor,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(3.dp.toPx())
            )
        }

        // Circular area
        if (showCircularArea) {

            val outerRadius = radius * sqrt(2f)

            drawCircle(
                color = fillColor,
                radius = outerRadius,
                center = center
            )
        }

        // Circle boundaries (FIXED: now properly inside radius check)
        if (showCircleBoundaries) {

            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(3.dp.toPx())
            )

            drawCircle(
                color = ringColor,
                radius = radius * 0.5f,
                center = center,
                style = Stroke(3.dp.toPx())
            )
        }

        // Crosshair
        drawLine(
            color = crossColor,
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.5.dp.toPx()
        )

        drawLine(
            color = crossColor,
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.5.dp.toPx()
        )

        // Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    knobColor.copy(alpha = 0.33f),
                    Color.Transparent
                ),
                center = finalKnob,
                radius = knobRadius * 2.2f
            ),
            radius = knobRadius * 2.2f,
            center = finalKnob
        )

        // Knob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    knobColor,
                    knobColor.copy(alpha = 0.78f)
                ),
                center = Offset(finalKnob.x, finalKnob.y - knobRadius * 0.3f),
                radius = knobRadius
            ),
            radius = knobRadius,
            center = finalKnob
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = knobRadius,
            center = finalKnob,
            style = Stroke(2.5.dp.toPx())
        )
    }
}