package com.goldarte.mavlinkjoystick.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.goldarte.mavlinkjoystick.utils.CurveUtils

class CurveGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var weight: Float = 1.0f
    private var offset: Float = 0.0f
    private var expo: Float = 0.0f

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5C8D")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun setParams(weight: Float, offset: Float, expo: Float) {
        this.weight = weight
        this.offset = offset
        this.expo = expo
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // Draw grid
        for (i in 0..10) {
            val x = (w / 10f) * i
            canvas.drawLine(x, 0f, x, h, gridPaint)
            val y = (h / 10f) * i
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        // Draw axes
        canvas.drawLine(cx, 0f, cx, h, axisPaint)
        canvas.drawLine(0f, cy, w, cy, axisPaint)

        // Draw curve
        val path = Path()
        val points = 50
        for (i in 0..points) {
            val xInput = (i.toFloat() / points.toFloat()) * 2f - 1f // -1 to 1
            val yOutput = CurveUtils.applyCurve(xInput, weight, offset, expo)
            
            // Map to screen coordinates
            // xInput -1 -> 0, 1 -> w
            // yOutput -1 -> h, 1 -> 0 (inverted Y)
            val px = ((xInput + 1f) / 2f) * w
            val py = h - ((yOutput + 1f) / 2f) * h
            
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, curvePaint)
    }
}
