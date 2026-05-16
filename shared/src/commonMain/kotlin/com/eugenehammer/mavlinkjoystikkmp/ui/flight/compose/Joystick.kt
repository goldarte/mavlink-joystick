package com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.eugenehammer.mavlinkjoystikkmp.ui.flight.FlightScreenState
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun Joystick(
    state: FlightScreenState.JoystickState,
    onChanged: (Float, Float) -> Unit,
) {
    var canvasWidth = 0f
    var canvasHeight = 0f

    val coercedStickSizeFactor = remember(state.stickSizeFactor) {
        state.stickSizeFactor.coerceIn(0.25f, 1f)
    }

    val currentOnChanged by rememberUpdatedState(onChanged)
    val currentState by rememberUpdatedState(state)

    Canvas(
        modifier = Modifier
            .aspectRatio(1f)
            .onSizeChanged {
                canvasWidth = it.width.toFloat()
                canvasHeight = it.height.toFloat()
            }
            .pointerInput(
                canvasWidth,
                canvasHeight,
                coercedStickSizeFactor,
            ) {
                detectDragGestures(
                    onDragStart = { offset ->
                        updateStateFromTouch(
                            touchX = offset.x,
                            touchY = offset.y,
                            width = canvasWidth,
                            height = canvasHeight,
                            stickSizeFactor = coercedStickSizeFactor,
                            isThrottleMode = currentState.isThrottleMode,
                            onChanged = currentOnChanged,
                        )
                    },
                    onDrag = { change, _ ->
                        updateStateFromTouch(
                            touchX = change.position.x,
                            touchY = change.position.y,
                            width = canvasWidth,
                            height = canvasHeight,
                            stickSizeFactor = coercedStickSizeFactor,
                            isThrottleMode = currentState.isThrottleMode,
                            onChanged = currentOnChanged,
                        )

                        change.consume()
                    },
                    onDragEnd = {
                        if (currentState.isThrottleMode) {
                            currentOnChanged(0f, currentState.valueY)
                        } else {
                            currentOnChanged(0f, 0f)
                        }
                    },
                )
            },
    ) {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        val cx = size.width / 2f
        val cy = size.height / 2f

        val radius = (min(size.width, size.height) / 2f) * 0.88f * coercedStickSizeFactor

        val knobRadius = radius * 0.22f

        val knobX = cx + (state.valueX * radius)

        val knobY = if (state.isThrottleMode) {
            cy + radius - (state.valueY * radius * 2f)
        } else {
            cy + (state.valueY * radius)
        }

        val knobOffset = Offset(knobX, knobY)

        val ringColor = Color.White.copy(alpha = 0.26f)
        val fillColor = Color.White.copy(alpha = 0.08f)
        val crossColor = Color.White.copy(alpha = 0.2f)

        if (state.showSquareArea) {
            drawRect(
                color = fillColor,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
            )

            drawRect(
                color = ringColor,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 3f),
            )

            drawRect(
                color = ringColor,
                topLeft = Offset(
                    cx - radius * 0.5f,
                    cy - radius * 0.5f,
                ),
                size = Size(radius, radius),
                style = Stroke(width = 3f),
            )
        }

        if (state.showCircularArea) {
            val outerRadius = radius * sqrt(2f)
            val outerOuterRadius = outerRadius * 1.05f

            if (outerOuterRadius < min(cx, cy)) {
                drawCircle(
                    color = fillColor,
                    radius = outerRadius,
                    center = Offset(cx, cy),
                )

                drawCircle(
                    color = ringColor,
                    radius = outerOuterRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 10f),
                )
            }
        }

        if (state.showCircleBoundaries) {
            drawCircle(
                color = fillColor,
                radius = radius,
                center = Offset(cx, cy),
            )

            drawCircle(
                color = ringColor,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 3f),
            )

            drawCircle(
                color = ringColor,
                radius = radius * 0.5f,
                center = Offset(cx, cy),
                style = Stroke(width = 3f),
            )
        }

        drawLine(
            color = crossColor,
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
        )

        drawLine(
            color = crossColor,
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    state.knobColor.copy(alpha = 0.33f),
                    Color.Transparent,
                ),
                center = knobOffset,
                radius = knobRadius * 2.2f,
            ),
            radius = knobRadius * 2.2f,
            center = knobOffset,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    state.knobColor,
                    state.knobColor.copy(alpha = 0.8f),
                ),
                center = Offset(
                    knobOffset.x,
                    knobOffset.y - knobRadius * 0.3f,
                ),
                radius = knobRadius,
            ),
            radius = knobRadius,
            center = knobOffset,
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = knobRadius,
            center = knobOffset,
            style = Stroke(width = 2.5f),
        )
    }
}

private fun updateStateFromTouch(
    touchX: Float,
    touchY: Float,
    width: Float,
    height: Float,
    stickSizeFactor: Float,
    isThrottleMode: Boolean,
    onChanged: (Float, Float) -> Unit,
) {
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

    val valueY = if (isThrottleMode) {
        (1f - ((clampedY - (cy - radius)) / (2f * radius))).coerceIn(0f, 1f)
    } else {
        ((clampedY - cy) / radius).coerceIn(-1f, 1f)
    }

    onChanged(valueX, valueY)
}