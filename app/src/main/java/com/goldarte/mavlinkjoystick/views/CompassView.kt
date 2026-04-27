package com.goldarte.mavlinkjoystick.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Vertical scrolling compass tape.
 *
 * Current heading is shown in the centre with a pointer triangle.
 * The tape scrolls up/down as heading changes; cardinals (N/E/S/W)
 * are highlighted in amber. Call [setHeading] with degrees 0–359.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var headingDeg: Float = 0f

    fun setHeading(deg: Float) {
        headingDeg = ((deg % 360f) + 360f) % 360f
        invalidate()
    }

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#111418")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val tickMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val tickMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 28f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        textSize = 32f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5C8D")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5C8D")
        style = Paint.Style.FILL
    }
    private val headingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val headingBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5C8D")
        style = Paint.Style.FILL
    }

    // Fade gradient (top/bottom)
    private var fadePaint = Paint()
    private var fadeShader: LinearGradient? = null

    private val cardinals = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")

    // ── Layout ────────────────────────────────────────────────────────────────
    private var cx = 0f
    private var cy = 0f
    private var w = 0f
    private var h = 0f

    // Pixels per degree
    private val pixPerDeg get() = h / 60f   // 60° visible range

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat(); h = nh.toFloat()
        cx = w / 2f; cy = h / 2f
        labelPaint.textSize   = (w * 0.22f).coerceIn(18f, 36f)
        cardinalPaint.textSize = (w * 0.26f).coerceIn(20f, 40f)
        headingTextPaint.textSize = (w * 0.24f).coerceIn(18f, 34f)

        fadeShader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.parseColor("#FF111418"),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.parseColor("#FF111418")
            ),
            floatArrayOf(0f, 0.22f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        fadePaint.shader = fadeShader
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ── Tape ──────────────────────────────────────────────────────────────
        val ppd = pixPerDeg
        val range = 40  // degrees above/below centre to render

        for (offset in -range..range) {
            val deg = ((headingDeg.toInt() + offset) % 360 + 360) % 360
            val y = cy + offset * ppd
            if (y < 0 || y > h) continue

            val isCardinal = cardinals.containsKey(deg)
            val isMajor10  = deg % 10 == 0
            val isMajor30  = deg % 30 == 0

            // Tick lines on RIGHT side (labels also right-aligned)
            when {
                isCardinal || isMajor30 -> {
                    val tickLen = w * 0.30f
                    canvas.drawLine(w - tickLen, y, w, y, tickMajorPaint)
                }
                isMajor10 -> {
                    val tickLen = w * 0.18f
                    canvas.drawLine(w - tickLen, y, w, y, tickMinorPaint)
                }
                else -> {
                    val tickLen = w * 0.09f
                    canvas.drawLine(w - tickLen, y, w, y, tickMinorPaint)
                }
            }

            // Labels
            if (isCardinal || isMajor30) {
                val p = if (isCardinal) cardinalPaint else labelPaint
                val label = cardinals[deg] ?: deg.toString()
                val textY = y + p.textSize * 0.35f
                canvas.drawText(label, w - w * 0.34f, textY, p)
            }
        }

        // ── Fade overlay ──────────────────────────────────────────────────────
        canvas.drawRect(0f, 0f, w, h, fadePaint)

        // ── Centre pointer (left-side triangle) ───────────────────────────────
        val ptSize = w * 0.16f
        val path = Path().apply {
            moveTo(0f, cy)
            lineTo(ptSize, cy - ptSize * 0.6f)
            lineTo(ptSize, cy + ptSize * 0.6f)
            close()
        }
        canvas.drawPath(path, pointerPaint)

        // Centre line across full width
        canvas.drawLine(0f, cy, w, cy, centerLinePaint)

        // ── Heading readout box (bottom) ───────────────────────────────────────
        val boxH = h * 0.12f
        val boxTop = h - boxH
        val boxRect = RectF(0f, boxTop, w, h)
        canvas.drawRect(boxRect, headingBoxPaint)

        val hdgText = "%03d°".format(headingDeg.toInt() % 360)
        canvas.drawText(hdgText, cx, h - boxH * 0.18f, headingTextPaint)

        // Border
        canvas.drawRect(0f, 0f, w, h, borderPaint)
    }
}
