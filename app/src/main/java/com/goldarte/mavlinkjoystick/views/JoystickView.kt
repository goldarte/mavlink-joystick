package com.goldarte.mavlinkjoystick.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * A circular joystick that tracks a single pointer.
 *
 * [isThrottleMode] — when true the vertical axis (Y) does NOT spring back to
 * centre so the throttle holds its last value (like a real mode-2 transmitter).
 *
 * Callback: [onChanged](x, y)  where x/y ∈ [-1.0, 1.0].
 * For throttle mode   y ∈ [ 0.0, 1.0] (0 = full down, 1 = full up).
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var isThrottleMode: Boolean = false
    var onChanged: ((x: Float, y: Float) -> Unit)? = null

    // Output values
    var valueX: Float = 0f; private set
    var valueY: Float = 0f; private set

    // ── Geometry ──────────────────────────────────────────────────────────────
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f          // outer ring radius
    private var knobRadius = 0f      // thumb knob radius
    private var knobX = 0f
    private var knobY = 0f

    // ── Paint ─────────────────────────────────────────────────────────────────
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#44FFFFFF")
    }
    private val ringFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#15FFFFFF")
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#33FFFFFF")
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val knobBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#CCFFFFFF")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var isDragging = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = (min(w, h) / 2f) * 0.88f
        knobRadius = radius * 0.22f
        resetKnob()
        updateGradients()
    }

    private fun updateGradients() {
        knobPaint.shader = RadialGradient(
            knobX, knobY - knobRadius * 0.3f,
            knobRadius,
            intArrayOf(Color.parseColor("#FF5C8D"), Color.parseColor("#CC1A4E")),
            null, Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            knobX, knobY,
            knobRadius * 2.2f,
            intArrayOf(Color.parseColor("#55FF5C8D"), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        // Outer square fill
        canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, ringFillPaint)
        // Outer square border
        canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, ringPaint)

        // Cross hair
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint)

        // Inner guidance square
        ringPaint.alpha = 60
        canvas.drawRect(cx - radius * 0.5f, cy - radius * 0.5f, cx + radius * 0.5f, cy + radius * 0.5f, ringPaint)
        ringPaint.alpha = 0xFF

        // Glow
        updateGradients()
        canvas.drawCircle(knobX, knobY, knobRadius * 2.2f, glowPaint)

        // Knob
        knobPaint.shader = RadialGradient(
            knobX, knobY - knobRadius * 0.3f, knobRadius,
            intArrayOf(Color.parseColor("#FF7BAD"), Color.parseColor("#CC1A4E")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
        canvas.drawCircle(knobX, knobY, knobRadius, knobBorderPaint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                activePointerId = event.getPointerId(idx)
                isDragging = true
                moveKnob(event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) return true
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) moveKnob(event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == activePointerId) {
                    isDragging = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    springBack()
                }
            }
        }
        return true
    }

    private fun moveKnob(tx: Float, ty: Float) {
        knobX = tx.coerceIn(cx - radius, cx + radius)
        knobY = ty.coerceIn(cy - radius, cy + radius)
        computeValues()
        invalidate()
    }

    /**
     * Resets the knob to its default position (center, or bottom for throttle).
     */
    fun resetToDefault() {
        resetKnob()
        computeValues()
        invalidate()
    }

    private fun springBack() {
        // X always springs to centre; Y springs only if NOT throttle mode
        val targetX = cx
        val targetY = if (isThrottleMode) knobY else cy
        knobX = targetX
        if (!isThrottleMode) knobY = targetY
        computeValues()
        invalidate()
    }

    private fun resetKnob() {
        knobX = cx
        knobY = if (isThrottleMode) cy + radius else cy   // throttle starts at bottom
    }

    private fun computeValues() {
        valueX = ((knobX - cx) / radius).coerceIn(-1f, 1f)
        valueY = if (isThrottleMode) {
            // Y: top = 1, bottom = 0
            (1f - (knobY - (cy - radius)) / (2f * radius)).coerceIn(0f, 1f)
        } else {
            // Y: up = -1, down = 1 (inverted for aircraft convention)
            ((knobY - cy) / radius).coerceIn(-1f, 1f)
        }
        onChanged?.invoke(valueX, valueY)
    }
}
