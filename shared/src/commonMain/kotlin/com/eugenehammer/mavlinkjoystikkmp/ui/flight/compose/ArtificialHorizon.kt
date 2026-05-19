package com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Compose Multiplatform artificial horizon.
 *
 * rollDeg:
 *  -90..90 typical
 *
 * pitchDeg:
 *  -90..90
 */
@Composable
internal fun ArtificialHorizon(
    modifier: Modifier = Modifier,
    rollDeg: Float = 0f,
    pitchDeg: Float = 0f,
) {

    val textMeasurer = rememberTextMeasurer()

    val ladderTextStyle = TextStyle(
        color = Color.White,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxSize()
    ) {

        val cx = size.width / 2f
        val cy = size.height / 2f

        val center = Offset(cx, cy)

        val bezelRadius =
            min(size.width, size.height) / 2f * 0.96f

        val innerRadius =
            bezelRadius * 0.84f

        // ════════════════════════════════════════════
        // Bezel background
        // ════════════════════════════════════════════

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF3A3A3A),
                    Color(0xFF111111)
                ),
                center = center,
                radius = bezelRadius
            ),
            radius = bezelRadius,
            center = center
        )

        // ════════════════════════════════════════════
        // Clip display area
        // ════════════════════════════════════════════

        val displayClipPath = Path().apply {

            addOval(
                Rect(
                    center = center,
                    radius = innerRadius
                )
            )
        }

        clipPath(displayClipPath) {

            // ════════════════════════════════════════
            // Rotating attitude scene
            // ════════════════════════════════════════

            rotate(
                degrees = -rollDeg,
                pivot = center
            ) {

                val constrainedPitch =
                    pitchDeg.coerceIn(-90f, 90f)

                // 1 degree = innerRadius / 30 px
                val pitchOffset =
                    constrainedPitch *
                            (innerRadius / 30f)

                // ════════════════════════════════════
                // Sky
                // ════════════════════════════════════

                drawRect(
                    color = Color(0xFF1565C0),

                    topLeft = Offset(
                        x = cx - innerRadius * 2f,
                        y = cy - innerRadius * 2f
                    ),

                    size = Size(
                        width = innerRadius * 4f,
                        height = innerRadius * 2f + pitchOffset
                    )
                )

                // ════════════════════════════════════
                // Ground
                // ════════════════════════════════════

                drawRect(
                    color = Color(0xFF6D4C41),

                    topLeft = Offset(
                        x = cx - innerRadius * 2f,
                        y = cy + pitchOffset
                    ),

                    size = Size(
                        width = innerRadius * 4f,
                        height = innerRadius * 2f
                    )
                )

                // ════════════════════════════════════
                // Horizon line
                // ════════════════════════════════════

                drawLine(
                    color = Color.White,

                    start = Offset(
                        x = cx - innerRadius * 1.5f,
                        y = cy + pitchOffset
                    ),

                    end = Offset(
                        x = cx + innerRadius * 1.5f,
                        y = cy + pitchOffset
                    ),

                    strokeWidth = 2.dp.toPx()
                )

                // ════════════════════════════════════
                // Pitch ladder
                // ════════════════════════════════════

                val ladderSteps =
                    listOf(-30, -20, -10, 10, 20, 30)

                ladderSteps.forEach { deg ->

                    val y =
                        cy + pitchOffset -
                                deg * (innerRadius / 30f)

                    val halfLength =
                        if (deg % 20 == 0) {
                            innerRadius * 0.3f
                        } else {
                            innerRadius * 0.18f
                        }

                    drawLine(
                        color = Color.White,

                        start = Offset(
                            x = cx - halfLength,
                            y = y
                        ),

                        end = Offset(
                            x = cx + halfLength,
                            y = y
                        ),

                        strokeWidth = 1.5.dp.toPx()
                    )

                    val label = abs(deg).toString()

                    val leftText =
                        textMeasurer.measure(
                            text = label,
                            style = ladderTextStyle
                        )

                    val rightText =
                        textMeasurer.measure(
                            text = label,
                            style = ladderTextStyle
                        )

                    // Right label

                    drawText(
                        textLayoutResult = rightText,

                        topLeft = Offset(
                            x = cx +
                                    halfLength +
                                    12.dp.toPx(),

                            y = y -
                                    rightText.size.height / 2f
                        )
                    )

                    // Left label

                    drawText(
                        textLayoutResult = leftText,

                        topLeft = Offset(
                            x = cx -
                                    halfLength -
                                    leftText.size.width -
                                    12.dp.toPx(),

                            y = y -
                                    leftText.size.height / 2f
                        )
                    )
                }
            }

            // ════════════════════════════════════════
            // Roll scale
            // ════════════════════════════════════════

            val scaleRadius =
                innerRadius * 1.03f

            val marks = listOf(
                -60, -45, -30,
                -20, -10,
                0,
                10, 20,
                30, 45, 60
            )

            marks.forEach { deg ->

                val rad = ((deg - 90).toDouble() * PI / 180.0)

                val outer = scaleRadius

                val inner =
                    scaleRadius -
                            if (deg % 30 == 0) {
                                18f
                            } else {
                                10f
                            }

                drawLine(
                    color = Color.White,

                    start = Offset(
                        x = cx +
                                outer * cos(rad).toFloat(),

                        y = cy +
                                outer * sin(rad).toFloat()
                    ),

                    end = Offset(
                        x = cx +
                                inner * cos(rad).toFloat(),

                        y = cy +
                                inner * sin(rad).toFloat()
                    ),

                    strokeWidth = 2.dp.toPx()
                )
            }

            // ════════════════════════════════════════
            // Aircraft symbol
            // ════════════════════════════════════════

            val aircraftColor =
                Color(0xFFFFD600)

            val wingHeight =
                innerRadius * 0.22f

            // Left wing

            drawLine(
                color = aircraftColor,

                start = Offset(
                    x = cx - innerRadius * 0.6f,
                    y = cy
                ),

                end = Offset(
                    x = cx - innerRadius * 0.2f,
                    y = cy
                ),

                strokeWidth = 4.dp.toPx()
            )

            drawLine(
                color = aircraftColor,

                start = Offset(
                    x = cx - innerRadius * 0.2f,
                    y = cy
                ),

                end = Offset(
                    x = cx - innerRadius * 0.2f,
                    y = cy + wingHeight * 0.6f
                ),

                strokeWidth = 4.dp.toPx()
            )

            // Right wing

            drawLine(
                color = aircraftColor,

                start = Offset(
                    x = cx + innerRadius * 0.6f,
                    y = cy
                ),

                end = Offset(
                    x = cx + innerRadius * 0.2f,
                    y = cy
                ),

                strokeWidth = 4.dp.toPx()
            )

            drawLine(
                color = aircraftColor,

                start = Offset(
                    x = cx + innerRadius * 0.2f,
                    y = cy
                ),

                end = Offset(
                    x = cx + innerRadius * 0.2f,
                    y = cy + wingHeight * 0.6f
                ),

                strokeWidth = 4.dp.toPx()
            )

            // Center dot

            drawCircle(
                color = aircraftColor,
                radius = 6.dp.toPx(),
                center = center
            )
        }

        // ════════════════════════════════════════════
        // Bezel border
        // ════════════════════════════════════════════

        drawCircle(
            color = Color(0xFF222222),

            radius = bezelRadius,

            center = center,

            style = Stroke(
                width = 5.dp.toPx()
            )
        )

        // ════════════════════════════════════════════
        // Roll pointer
        // ════════════════════════════════════════════

        val rad = ((-rollDeg - 90).toDouble() * PI / 180.0)

        val tipDistance =
            innerRadius * 1.02f

        val baseDistance =
            innerRadius * 1.16f

        val tip = Offset(
            x = cx +
                    tipDistance *
                    cos(rad).toFloat(),

            y = cy +
                    tipDistance *
                    sin(rad).toFloat()
        )

        val perpRad = rad + PI / 2.0

        val base1 = Offset(
            x = cx +
                    baseDistance *
                    cos(rad).toFloat() +
                    8f *
                    cos(perpRad).toFloat(),

            y = cy +
                    baseDistance *
                    sin(rad).toFloat() +
                    8f *
                    sin(perpRad).toFloat()
        )

        val base2 = Offset(
            x = cx +
                    baseDistance *
                    cos(rad).toFloat() -
                    8f *
                    cos(perpRad).toFloat(),

            y = cy +
                    baseDistance *
                    sin(rad).toFloat() -
                    8f *
                    sin(perpRad).toFloat()
        )

        val trianglePath = Path().apply {

            moveTo(tip.x, tip.y)

            lineTo(base1.x, base1.y)

            lineTo(base2.x, base2.y)

            close()
        }

        drawPath(
            path = trianglePath,
            color = Color(0xFFFFD600)
        )
    }
}