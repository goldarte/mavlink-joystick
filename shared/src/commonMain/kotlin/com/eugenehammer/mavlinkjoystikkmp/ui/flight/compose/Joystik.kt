package com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
internal fun Joystick(
    modifier: Modifier = Modifier,
    isThrottleMode: Boolean = false,
    onChanged: (x: Float, y: Float) -> Unit,
) {

    val showCircularArea = true
    val showSquareArea = true
    val showCircleBoundaries = false
    val stickSizeFactor = 0.65f
    val knobColor = Color(0xFFFF5C8D)

    var canvasSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    var isDragging by remember {
        mutableStateOf(false)
    }

    var knobPosition by remember {
        mutableStateOf<Offset?>(null)
    }

    // ════════════════════════════════════════════
    // Geometry
    // ════════════════════════════════════════════

    val width = canvasSize.width.toFloat()
    val height = canvasSize.height.toFloat()

    val center = remember(width, height) {
        Offset(
            x = width / 2f,
            y = height / 2f,
        )
    }

    val radius = remember(
        width,
        height,
        stickSizeFactor,
    ) {
        (min(width, height) / 2f) *
                0.88f *
                stickSizeFactor.coerceIn(0.25f, 1f)
    }

    val knobRadius = radius * 0.22f

    // ════════════════════════════════════════════
    // Default position
    // ════════════════════════════════════════════

    val defaultKnobPosition = remember(
        center,
        radius,
        isThrottleMode,
    ) {
        Offset(
            x = center.x,

            y = if (isThrottleMode) {
                center.y + radius
            } else {
                center.y
            },
        )
    }

    // ════════════════════════════════════════════
    // Final knob position
    // ════════════════════════════════════════════

    val rawKnob =
        knobPosition ?: defaultKnobPosition

    val constrainedKnob = remember(
        rawKnob,
        center,
        radius,
    ) {
        Offset(
            x = rawKnob.x.coerceIn(
                center.x - radius,
                center.x + radius,
            ),

            y = rawKnob.y.coerceIn(
                center.y - radius,
                center.y + radius,
            ),
        )
    }

    val finalKnob = remember(
        constrainedKnob,
        center,
        isDragging,
        isThrottleMode,
    ) {

        if (isDragging) {

            constrainedKnob

        } else {

            Offset(
                x = center.x,

                y = if (isThrottleMode) {
                    constrainedKnob.y
                } else {
                    center.y
                },
            )
        }
    }

    // ════════════════════════════════════════════
    // Output values
    // ════════════════════════════════════════════

    val valueX = remember(
        finalKnob,
        center,
        radius,
    ) {

        if (radius <= 0f) {

            0f

        } else {

            (
                    (finalKnob.x - center.x) / radius
                    )
                .coerceIn(-1f, 1f)
        }
    }

    val valueY = remember(
        finalKnob,
        center,
        radius,
        isThrottleMode,
    ) {

        if (radius <= 0f) {

            0f

        } else if (isThrottleMode) {

            (
                    1f - (
                            (
                                    finalKnob.y - (center.y - radius)
                                    ) / (2f * radius)
                            )
                    )
                .coerceIn(0f, 1f)

        } else {

            (
                    (finalKnob.y - center.y) / radius
                    )
                .coerceIn(-1f, 1f)
        }
    }

    // ════════════════════════════════════════════
    // Side-effect
    // ════════════════════════════════════════════

    LaunchedEffect(
        valueX,
        valueY,
    ) {

        onChanged(
            valueX,
            valueY,
        )
    }

    // ════════════════════════════════════════════
    // UI
    // ════════════════════════════════════════════

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxSize()

            .onSizeChanged {
                canvasSize = it
            }

            .pointerInput(
                center,
                radius,
                isThrottleMode,
            ) {

                detectDragGestures(

                    onDragStart = { offset ->

                        isDragging = true

                        knobPosition = offset
                    },

                    onDragEnd = {

                        isDragging = false
                    },

                    onDragCancel = {

                        isDragging = false
                    },

                    onDrag = { change, _ ->

                        knobPosition = change.position
                    },
                )
            },
    ) {

        // FIX:
        // First composition may happen with zero size.
        // Android RadialGradient crashes with radius <= 0.

        if (
            radius <= 0f ||
            knobRadius <= 0f
        ) {
            return@Canvas
        }

        val ringColor =
            Color.White.copy(alpha = 0.27f)

        val fillColor =
            Color.White.copy(alpha = 0.08f)

        val crossColor =
            Color.White.copy(alpha = 0.2f)

        // ════════════════════════════════════════════
        // Square area
        // ════════════════════════════════════════════

        if (showSquareArea) {

            drawRect(
                color = fillColor,

                topLeft = Offset(
                    center.x - radius,
                    center.y - radius,
                ),

                size = Size(
                    radius * 2f,
                    radius * 2f,
                ),
            )

            drawRect(
                color = ringColor,

                topLeft = Offset(
                    center.x - radius,
                    center.y - radius,
                ),

                size = Size(
                    radius * 2f,
                    radius * 2f,
                ),

                style = Stroke(3.dp.toPx()),
            )

            drawRect(
                color = ringColor,

                topLeft = Offset(
                    center.x - radius * 0.5f,
                    center.y - radius * 0.5f,
                ),

                size = Size(
                    radius,
                    radius,
                ),

                style = Stroke(3.dp.toPx()),
            )
        }

        // ════════════════════════════════════════════
        // Circular area
        // ════════════════════════════════════════════

        if (showCircularArea) {

            val outerRadius =
                radius * sqrt(2f)

            val outerOuterRadius =
                outerRadius * 1.05f

            if (outerOuterRadius < min(center.x, center.y)) {

                drawCircle(
                    color = fillColor,
                    radius = outerRadius,
                    center = center,
                )

                drawCircle(
                    color = ringColor,
                    radius = outerOuterRadius,
                    center = center,
                    style = Stroke(10.dp.toPx()),
                )
            }
        }

        // ════════════════════════════════════════════
        // Circle boundaries
        // ════════════════════════════════════════════

        if (showCircleBoundaries) {

            drawCircle(
                color = fillColor,
                radius = radius,
                center = center,
            )

            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(3.dp.toPx()),
            )

            drawCircle(
                color = ringColor,
                radius = radius * 0.5f,
                center = center,
                style = Stroke(3.dp.toPx()),
            )
        }

        // ════════════════════════════════════════════
        // Crosshair
        // ════════════════════════════════════════════

        drawLine(
            color = crossColor,

            start = Offset(
                center.x - radius,
                center.y,
            ),

            end = Offset(
                center.x + radius,
                center.y,
            ),

            strokeWidth = 1.5.dp.toPx(),
        )

        drawLine(
            color = crossColor,

            start = Offset(
                center.x,
                center.y - radius,
            ),

            end = Offset(
                center.x,
                center.y + radius,
            ),

            strokeWidth = 1.5.dp.toPx(),
        )

        // ════════════════════════════════════════════
        // Glow
        // ════════════════════════════════════════════

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    knobColor.copy(alpha = 0.33f),
                    Color.Transparent,
                ),

                center = finalKnob,

                radius = knobRadius * 2.2f,
            ),

            radius = knobRadius * 2.2f,

            center = finalKnob,
        )

        // ════════════════════════════════════════════
        // Knob
        // ════════════════════════════════════════════

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    knobColor,
                    knobColor.copy(alpha = 0.78f),
                ),

                center = Offset(
                    finalKnob.x,
                    finalKnob.y - knobRadius * 0.3f,
                ),

                radius = knobRadius,
            ),

            radius = knobRadius,

            center = finalKnob,
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.8f),

            radius = knobRadius,

            center = finalKnob,

            style = Stroke(2.5.dp.toPx()),
        )
    }
}