package com.eugenehammer.mavlinkjoystikkmp.ui.flight.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun Compass(
    modifier: Modifier = Modifier,
    heading: Float = 0f
) {

    val textMeasurer = rememberTextMeasurer()

    val labelStyle = TextStyle(
        color = Color.White,
        fontSize = 14.sp,
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

        val radius = min(size.width, size.height) / 2f * 0.85f

        // ════════════════════════════════════════════
        // Background
        // ════════════════════════════════════════════

        drawCircle(
            color = Color(0xFF1A1A1A),
            radius = radius,
            center = center
        )

        // ════════════════════════════════════════════
        // Decorative arcs
        // ════════════════════════════════════════════

        val arcRadius = radius * 1.08f
        val arcRect = Rect(
            center = center,
            radius = arcRadius
        )

        val arcColor = Color(0xFF333333)

        drawArc(
            color = arcColor,
            startAngle = -105f,
            sweepAngle = 30f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(6.dp.toPx())
        )

        drawArc(
            color = arcColor,
            startAngle = -15f,
            sweepAngle = 30f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(6.dp.toPx())
        )

        drawArc(
            color = arcColor,
            startAngle = 135f,
            sweepAngle = 30f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(6.dp.toPx())
        )

        // ════════════════════════════════════════════
        // Dial (ticks + dots + labels)
        // ════════════════════════════════════════════

        for (i in 0 until 360 step 5) {

            val angleRad = ((i - 90).toDouble() * PI / 180.0)

            val outer = radius * 0.98f

            if (i % 10 == 0) {

                val inner =
                    if (i % 30 == 0) {
                        radius * 0.82f
                    } else {
                        radius * 0.90f
                    }

                drawLine(
                    color = Color.White,
                    start = Offset(
                        cx + outer * cos(angleRad).toFloat(),
                        cy + outer * sin(angleRad).toFloat()
                    ),
                    end = Offset(
                        cx + inner * cos(angleRad).toFloat(),
                        cy + inner * sin(angleRad).toFloat()
                    ),
                    strokeWidth = 1.5.dp.toPx()
                )

            } else {

                val dotDist = radius * 0.94f

                drawCircle(
                    color = Color(0xFFAAAAAA),
                    radius = 2f,
                    center = Offset(
                        cx + dotDist * cos(angleRad).toFloat(),
                        cy + dotDist * sin(angleRad).toFloat()
                    )
                )
            }

            // Cardinal labels

            if (i % 90 == 0) {

                val label = when (i) {
                    0 -> "N"
                    90 -> "E"
                    180 -> "S"
                    270 -> "W"
                    else -> ""
                }

                val textDist = radius * 0.62f

                val pos = Offset(
                    cx + textDist * cos(angleRad).toFloat(),
                    cy + textDist * sin(angleRad).toFloat()
                )

                val result = textMeasurer.measure(
                    text = label,
                    style = labelStyle
                )

                drawText(
                    textLayoutResult = result,
                    topLeft = Offset(
                        pos.x - result.size.width / 2f,
                        pos.y - result.size.height / 2f
                    )
                )
            }
        }

        // ════════════════════════════════════════════
        // Rotating arrow (heading)
        // ════════════════════════════════════════════

        rotate(heading, center) {

            val arrowWidth = radius * 0.38f
            val arrowHeight = radius * 0.55f

            val path = Path().apply {
                moveTo(cx, cy - arrowHeight * 0.55f)
                lineTo(cx - arrowWidth / 2f, cy + arrowHeight * 0.45f)
                lineTo(cx, cy + arrowHeight * 0.15f)
                lineTo(cx + arrowWidth / 2f, cy + arrowHeight * 0.45f)
                close()
            }

            drawPath(
                path = path,
                color = Color(0xFFE53935)
            )

            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(1.5.dp.toPx())
            )
        }
    }
}