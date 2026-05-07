// Copyright (c) 2026 Arthur Golubtsov <goldartt@gmail.com>
// Repository: https://github.com/goldarte/mavlink-joystick
// Assisted by Gemini

package com.goldarte.mavlinkjoystick.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/**
 * A compass view for displaying yaw heading.
 * Features a rotating dial with cardinal points and a fixed red arrow.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var heading: Float = 0f

    fun setHeading(h: Float) {
        heading = h
        invalidate()
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935") // Brighter Red
        style = Paint.Style.FILL
    }

    private val arrowOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeJoin = Paint.Join.MITER
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.BUTT
    }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f * 0.85f
    }

    override fun onDraw(canvas: Canvas) {
        // Draw background circle
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Draw outer arcs (as seen in reference image)
        drawDecorations(canvas)

        // Draw the fixed dial (North at top)
        drawDial(canvas)

        // Draw the rotating red arrow in the center
        canvas.save()
        canvas.rotate(heading, cx, cy)
        drawArrow(canvas)
        canvas.restore()
    }

    private fun drawDial(canvas: Canvas) {
        // Tick marks and labels
        for (i in 0 until 360 step 5) {
            val angle = Math.toRadians(i.toDouble() - 90).toFloat()
            val outer = radius * 0.98f
            
            if (i % 10 == 0) {
                val inner = if (i % 30 == 0) radius * 0.82f else radius * 0.90f
                canvas.drawLine(
                    cx + outer * cos(angle), cy + outer * sin(angle),
                    cx + inner * cos(angle), cy + inner * sin(angle),
                    tickPaint
                )
            } else {
                // Draw dots for 5-degree increments
                val dotDist = radius * 0.94f
                canvas.drawCircle(
                    cx + dotDist * cos(angle), cy + dotDist * sin(angle),
                    2.5f, dotPaint
                )
            }

            // Labels for cardinal points
            if (i % 90 == 0) {
                val label = when (i) {
                    0 -> "N"
                    90 -> "E"
                    180 -> "S"
                    270 -> "W"
                    else -> ""
                }
                val textDist = radius * 0.62f
                val tx = cx + textDist * cos(angle)
                val ty = cy + textDist * sin(angle)
                
                val fontMetrics = textPaint.fontMetrics
                val baseline = ty - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(label, tx, baseline, textPaint)
            }
        }
    }

    private fun drawArrow(canvas: Canvas) {
        val arrowWidth = radius * 0.38f
        val arrowHeight = radius * 0.55f
        val path = Path().apply {
            moveTo(cx, cy - arrowHeight * 0.55f) // Tip (indicating heading)
            lineTo(cx - arrowWidth / 2f, cy + arrowHeight * 0.45f) // Bottom left
            lineTo(cx, cy + arrowHeight * 0.15f) // Bottom notch
            lineTo(cx + arrowWidth / 2f, cy + arrowHeight * 0.45f) // Bottom right
            close()
        }
        canvas.drawPath(path, arrowPaint)
        canvas.drawPath(path, arrowOutlinePaint)
    }

    private fun drawDecorations(canvas: Canvas) {
        // Draw the three grey arcs from the reference image
        val arcRadius = radius * 1.08f
        val rect = RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
        
        // Top arc
        canvas.drawArc(rect, -105f, 30f, false, arcPaint)
        // Right arc
        canvas.drawArc(rect, -15f, 30f, false, arcPaint)
        // Bottom arc (approximate)
        canvas.drawArc(rect, 135f, 30f, false, arcPaint)
    }
}
