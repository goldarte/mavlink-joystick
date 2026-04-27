package com.goldarte.mavlinkjoystick.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/**
 * Classic artificial horizon (Attitude Indicator).
 *
 * Sky (blue) / Ground (brown) split by a pitch-driven horizon line.
 * The whole scene rotates with roll. Fixed aircraft symbol and scale overlay.
 *
 * Call [setAttitude](rollDeg, pitchDeg) to update.
 */
class ArtificialHorizonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var rollDeg: Float = 0f
    private var pitchDeg: Float = 0f

    fun setAttitude(roll: Float, pitch: Float) {
        rollDeg = roll
        pitchDeg = pitch.coerceIn(-90f, 90f)
        invalidate()
    }

    // ── Paints ────────────────────────────────────────────────────────────────
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
    }
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6D4C41")
    }
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val pitchLadderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val aircraftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#222222")
    }
    private val bezelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rollScalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val rollPointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 1f
    }

    private var cx = 0f
    private var cy = 0f
    private var r = 0f          // inner display radius
    private var bezelR = 0f

    private val clipPath = Path()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f
        cy = h / 2f
        bezelR = min(w, h) / 2f * 0.96f
        r = bezelR * 0.84f
        updateBezelShader()
        updateClipPath()
    }

    private fun updateBezelShader() {
        bezelFillPaint.shader = RadialGradient(
            cx, cy, bezelR,
            intArrayOf(Color.parseColor("#3A3A3A"), Color.parseColor("#111111")),
            null, Shader.TileMode.CLAMP
        )
    }

    private fun updateClipPath() {
        clipPath.reset()
        clipPath.addCircle(cx, cy, r, Path.Direction.CW)
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        // Bezel
        canvas.drawCircle(cx, cy, bezelR, bezelFillPaint)

        // Clip inner display
        canvas.save()
        canvas.clipPath(clipPath)

        // ── Rotating attitude scene ──
        canvas.save()
        canvas.rotate(-rollDeg, cx, cy)

        // Pitch offset: each degree = r/30 pixels
        val pitchOffset = pitchDeg * (r / 30f)

        // Ground
        canvas.drawRect(cx - r * 2, cy + pitchOffset, cx + r * 2, cy + r * 2, groundPaint)
        // Sky
        canvas.drawRect(cx - r * 2, cy - r * 2, cx + r * 2, cy + pitchOffset, skyPaint)

        // Horizon line
        canvas.drawLine(cx - r * 1.5f, cy + pitchOffset, cx + r * 1.5f, cy + pitchOffset, horizonPaint)

        // Pitch ladder (every 10°)
        drawPitchLadder(canvas, pitchOffset)

        canvas.restore()  // end rotating scene

        // ── Fixed overlay: aircraft symbol, roll scale ──
        drawRollScale(canvas)
        drawAircraftSymbol(canvas)

        canvas.restore()  // end clip

        // Bezel ring border
        canvas.drawCircle(cx, cy, bezelR, bezelPaint)

        // Roll pointer (triangle at top of inner circle)
        drawRollPointer(canvas)
    }

    private fun drawPitchLadder(canvas: Canvas, pitchOffset: Float) {
        val steps = intArrayOf(-30, -20, -10, 10, 20, 30)
        for (deg in steps) {
            val y = cy + pitchOffset - deg * (r / 30f)
            val halfLen = if (deg % 20 == 0) r * 0.3f else r * 0.18f
            canvas.drawLine(cx - halfLen, y, cx + halfLen, y, pitchLadderPaint)
            // Labels
            canvas.drawText("${abs(deg)}", cx + halfLen + 14f, y + 7f, textPaint)
            canvas.drawText("${abs(deg)}", cx - halfLen - 14f, y + 7f, textPaint)
        }
    }

    private fun drawRollScale(canvas: Canvas) {
        val scaleR = r * 1.03f
        val marks = intArrayOf(-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60)
        for (deg in marks) {
            val rad = Math.toRadians((deg - 90).toDouble()).toFloat()
            val outer = scaleR
            val inner = scaleR - (if (deg % 30 == 0) 18f else 10f)
            canvas.drawLine(
                cx + outer * cos(rad), cy + outer * sin(rad),
                cx + inner * cos(rad), cy + inner * sin(rad),
                rollScalePaint
            )
        }
    }

    private fun drawAircraftSymbol(canvas: Canvas) {
        val w = r * 0.22f
        // Left wing
        canvas.drawLine(cx - r * 0.6f, cy, cx - r * 0.2f, cy, aircraftPaint)
        canvas.drawLine(cx - r * 0.2f, cy, cx - r * 0.2f, cy + w * 0.6f, aircraftPaint)
        // Right wing
        canvas.drawLine(cx + r * 0.6f, cy, cx + r * 0.2f, cy, aircraftPaint)
        canvas.drawLine(cx + r * 0.2f, cy, cx + r * 0.2f, cy + w * 0.6f, aircraftPaint)
        // Centre dot
        aircraftPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 6f, aircraftPaint)
        aircraftPaint.style = Paint.Style.STROKE
    }

    private fun drawRollPointer(canvas: Canvas) {
        // Triangle pointing inward at the roll angle
        val rad = Math.toRadians((-rollDeg - 90).toDouble()).toFloat()
        val tipDist = r * 1.02f
        val baseDist = r * 1.16f
        val tipX = cx + tipDist * cos(rad)
        val tipY = cy + tipDist * sin(rad)
        val perpRad = rad + Math.PI.toFloat() / 2f
        val bx1 = cx + baseDist * cos(rad) + 8f * cos(perpRad)
        val by1 = cy + baseDist * sin(rad) + 8f * sin(perpRad)
        val bx2 = cx + baseDist * cos(rad) - 8f * cos(perpRad)
        val by2 = cy + baseDist * sin(rad) - 8f * sin(perpRad)
        val path = Path().apply {
            moveTo(tipX, tipY); lineTo(bx1, by1); lineTo(bx2, by2); close()
        }
        canvas.drawPath(path, rollPointerPaint)
    }
}
