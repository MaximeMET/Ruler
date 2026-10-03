package org.openruler.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import org.openruler.app.core.Palette
import java.util.Locale

/**
 * Half circle protractor with two draggable arms.
 *
 * Angles use the mathematical convention: 0° points right, 90° up, 180° left. The wedge
 * between the two arms is filled with the tool colour and the angle is printed below.
 */
class ProtractorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var palette: Palette = Palette.LIGHT
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the angle between the arms every time it changes. */
    var onAngleChanged: ((Float) -> Unit)? = null
    var onTouchDown: (() -> Unit)? = null
    var onTouchUp: (() -> Unit)? = null

    private val arms = floatArrayOf(DEFAULT_A, DEFAULT_B)
    private val pointerIds = intArrayOf(MotionEvent.INVALID_POINTER_ID, MotionEvent.INVALID_POINTER_ID)
    private val armOfPointer = intArrayOf(NO_ARM, NO_ARM)

    private var centerX = 0f
    private var centerY = 0f
    private var tickRadius = 0f
    private var wedgeRadius = 0f

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val wedgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val anglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val wedgePath = Path()
    private val wedgeRect = RectF()
    private val textBounds = Rect()

    private val inset = dp(20f)
    private val tickLong = dp(25f)
    private val tickMid = dp(20f)
    private val tickShort = dp(15f)
    private val gap = dp(5f)

    private var labelHeight = 0f

    init {
        labelPaint.textSize = sp(18f)
        anglePaint.textSize = sp(56f)
        tickPaint.strokeWidth = dp(1.2f)
        anglePaint.getTextBounds("360°", 0, 4, textBounds)
        labelHeight = textBounds.height().toFloat()
        isClickable = true
    }

    /** Angle between the two arms, in degrees. */
    val angle: Float
        get() = abs(arms[0] - arms[1])

    /** Both arm angles, `[0]` = first arm, `[1]` = second arm. */
    fun arms(): FloatArray = floatArrayOf(arms[0], arms[1])

    /** Resets the arms to the 45°/135° default. */
    fun start() {
        arms[0] = DEFAULT_A
        arms[1] = DEFAULT_B
        releasePointers()
        notifyChanged()
    }

    fun restore(a: Float, b: Float) {
        arms[0] = a.coerceIn(0f, 180f)
        arms[1] = b.coerceIn(0f, 180f)
        notifyChanged()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        val bottomLimit = h - labelHeight - 2f * inset
        tickRadius = min(bottomLimit - inset, w / 2f - inset - gap).coerceAtLeast(dp(40f))
        centerY = bottomLimit
        wedgeRadius = hypot(centerX, centerY) * 1.05f
        wedgeRect.set(
            centerX - wedgeRadius, centerY - wedgeRadius,
            centerX + wedgeRadius, centerY + wedgeRadius
        )
        notifyChanged()
    }

    private fun notifyChanged() {
        onAngleChanged?.invoke(angle)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val color = palette.accent
        wedgePaint.color = color
        anglePaint.color = color

        drawWedge(canvas)
        drawScale(canvas, color)

        // The wedge is filled with the tool colour, so the part of the scale that falls
        // inside it would be invisible if it kept that colour. Repaint that part in the
        // canvas colour - the wedge and the canvas are always opposite in lightness - the
        // same way the ruler repaints the ticks inside the measurement rectangle.
        val measuring = angle > 0.05f
        if (measuring) {
            val saved = canvas.save()
            canvas.clipPath(wedgePath)
            drawScale(canvas, palette.background)
            canvas.restoreToCount(saved)
        }

        tickPaint.strokeWidth = dp(1f)
        tickPaint.color = color
        canvas.drawLine(
            centerX - tickRadius - gap, centerY,
            centerX + tickRadius + gap, centerY,
            tickPaint
        )

        // The pivot always sits inside the wedge; keep it visible while measuring.
        dotPaint.color = if (measuring) palette.background else color
        canvas.drawCircle(centerX, centerY, dp(2f), dotPaint)
        canvas.drawText(String.format(Locale.ROOT, "%.1f°", angle), centerX, height - inset, anglePaint)
    }

    private fun drawWedge(canvas: Canvas) {
        if (angle <= 0.05f) return
        val from = min(arms[0], arms[1])
        val sweep = abs(arms[0] - arms[1])
        wedgePath.reset()
        wedgePath.moveTo(centerX, centerY)
        // Screen angles grow clockwise, so a mathematical angle becomes -angle here.
        wedgePath.arcTo(wedgeRect, -from, -sweep)
        wedgePath.close()
        canvas.drawPath(wedgePath, wedgePaint)
    }

    private fun drawScale(canvas: Canvas, color: Int) {
        tickPaint.color = color
        labelPaint.color = color
        // The dial can only be as wide as the screen. A portrait screen fits a much smaller
        // dial, where the 10 degree numbers would touch, so print every third one there.
        val labelEvery = if (tickRadius >= dp(230f)) 10 else 30
        for (i in 1 until 180) {
            val saved = canvas.save()
            canvas.rotate(i - 90f, centerX, centerY)
            val length = when {
                i % 10 == 0 -> tickLong
                i % 5 == 0 -> tickMid
                else -> tickShort
            }
            tickPaint.strokeWidth = if (i % 10 == 0) dp(1.2f) else dp(0.7f)
            canvas.drawLine(
                centerX, centerY - tickRadius,
                centerX, centerY - tickRadius + length,
                tickPaint
            )
            if (i % labelEvery == 0) {
                canvas.drawText(
                    i.toString(),
                    centerX,
                    centerY - tickRadius + tickLong + labelHeight + gap,
                    labelPaint
                )
            }
            canvas.restoreToCount(saved)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                grab(event.getPointerId(index), event.getX(index), event.getY(index))
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouchDown?.invoke()
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    drag(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }

            MotionEvent.ACTION_UP -> {
                releasePointers()
                onTouchUp?.invoke()
            }

            MotionEvent.ACTION_POINTER_UP -> release(event.getPointerId(event.actionIndex))

            MotionEvent.ACTION_CANCEL -> {
                releasePointers()
                onTouchUp?.invoke()
            }
        }
        return true
    }

    /** Assigns a free finger to the arm it points at. */
    private fun grab(id: Int, x: Float, y: Float) {
        val direction = angleOf(x, y) ?: return
        val slot = armOfPointer.indexOfFirst { it == NO_ARM }
        if (slot < 0) return
        val preferred = if (abs(direction - arms[0]) <= abs(direction - arms[1])) 0 else 1
        val chosen = if (armOfPointer.contains(preferred)) 1 - preferred else preferred
        pointerIds[slot] = id
        armOfPointer[slot] = chosen
        arms[chosen] = direction
        notifyChanged()
    }

    private fun drag(id: Int, x: Float, y: Float) {
        if (id == MotionEvent.INVALID_POINTER_ID) return
        val slot = pointerIds.indexOfFirst { it == id }
        if (slot < 0) return
        val arm = armOfPointer[slot]
        if (arm < 0) return
        val direction = angleOf(x, y) ?: return
        arms[arm] = direction
        notifyChanged()
    }

    private fun release(id: Int) {
        if (id == MotionEvent.INVALID_POINTER_ID) return
        val slot = pointerIds.indexOfFirst { it == id }
        if (slot < 0) return
        pointerIds[slot] = MotionEvent.INVALID_POINTER_ID
        armOfPointer[slot] = NO_ARM
    }

    private fun releasePointers() {
        pointerIds[0] = MotionEvent.INVALID_POINTER_ID
        pointerIds[1] = MotionEvent.INVALID_POINTER_ID
        armOfPointer[0] = NO_ARM
        armOfPointer[1] = NO_ARM
    }

    /** Angle of a touch point around the centre, or `null` when it is too close to it. */
    private fun angleOf(x: Float, y: Float): Float? {
        val dx = x - centerX
        val dy = centerY - y
        if (hypot(dx, dy) < dp(12f)) return null
        val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return degrees.coerceIn(0f, 180f)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    private companion object {
        const val NO_ARM = -1
        const val DEFAULT_A = 45f
        const val DEFAULT_B = 135f
    }
}
