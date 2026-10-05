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
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import org.openruler.app.core.Palette
import java.util.Locale

/**
 * Half circle protractor with two draggable arms.
 *
 * Angles use the mathematical convention: 0° points right, 90° up, 180° left. The wedge
 * between the two arms is filled with the tool colour and the angle is printed below.
 * Either arm can be pinned with its padlock, and a typed angle pins both at once.
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

    /** Called when the user taps the readout: the host then asks for an angle. */
    var onValueTap: (() -> Unit)? = null
    var onTouchDown: (() -> Unit)? = null
    var onTouchUp: (() -> Unit)? = null

    /** Each arm can be pinned in place, so the dial can hold an exact opening. */
    var firstLocked = false
        private set
    var secondLocked = false
        private set

    private val arms = floatArrayOf(DEFAULT_A, DEFAULT_B)
    private val pointerIds = intArrayOf(MotionEvent.INVALID_POINTER_ID, MotionEvent.INVALID_POINTER_ID)
    private val armOfPointer = intArrayOf(NO_ARM, NO_ARM)

    private var centerX = 0f
    private var centerY = 0f
    private var tickRadius = 0f
    private var wedgeRadius = 0f

    /** `true` while the phone is upright: the dial then runs along the left long edge. */
    private var portrait = false

    /** What the current gesture started on: an arm padlock, the readout, or nothing. */
    private var tapTarget = TAP_NONE
    private var tapStartX = 0f
    private var tapStartY = 0f
    private var tapMoved = false

    /** Hit box of the readout, refreshed on every draw. */
    private val readoutRect = RectF()

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
    private val margin = dp(10f)
    private val tickLong = dp(25f)
    private val tickMid = dp(20f)
    private val tickShort = dp(15f)
    private val gap = dp(5f)
    private val chipRadius = dp(12f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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
        firstLocked = false
        secondLocked = false
        releasePointers()
        tapTarget = TAP_NONE
        tapMoved = false
        notifyChanged()
    }

    fun restore(a: Float, b: Float) {
        arms[0] = a.coerceIn(0f, 180f)
        arms[1] = b.coerceIn(0f, 180f)
        notifyChanged()
    }

    /** Restores the pin state of one arm, used when the activity is recreated. */
    fun setLocked(index: Int, locked: Boolean) {
        if (index == 0) firstLocked = locked else secondLocked = locked
        invalidate()
    }

    /** Pins the opening to [value] degrees around the current bisector and locks both arms. */
    fun setAngle(value: Float) {
        val span = value.coerceIn(0.1f, 180f)
        val half = span / 2f
        val mid = ((arms[0] + arms[1]) / 2f).coerceIn(half, 180f - half)
        arms[0] = mid - half
        arms[1] = mid + half
        firstLocked = true
        secondLocked = true
        releasePointers()
        notifyChanged()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        portrait = h > w
        if (portrait) {
            // The long edge is the one that gets lined up with the object, so in portrait the
            // dial takes a quarter turn: its base line runs down the left side and the scale
            // opens to the right. Only the readout stays horizontal, along the bottom.
            centerX = inset
            centerY = h / 2f
            // The dial is centred on the screen; the readout keeps its strip at the bottom.
            tickRadius = minOf(
                centerY - inset,
                h - labelHeight - 2f * inset - centerY,
                w - 2f * inset
            ).coerceAtLeast(dp(40f))
        } else {
            centerX = w / 2f
            // Same idea along the long edge: the arc and the base line keep equal margins,
            // and the readout sits in the strip under the base line.
            tickRadius = minOf(
                h - 2f * (labelHeight + 2f * inset),
                w / 2f - inset - gap
            ).coerceAtLeast(dp(40f))
            centerY = h - (h - tickRadius) / 2f
        }
        // The wedge has to reach past every corner, whichever way the dial is turned.
        wedgeRadius = maxOf(
            hypot(centerX, centerY),
            hypot(w - centerX, centerY),
            hypot(centerX, h - centerY),
            hypot(w - centerX, h - centerY)
        ) * 1.05f
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

        val saved = canvas.save()
        if (portrait) canvas.rotate(90f, centerX, centerY)

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

        canvas.restoreToCount(saved)

        // The readout keeps its own direction so it can be read while the phone is upright.
        anglePaint.color = color
        val readout = String.format(Locale.ROOT, "%.1f°", angle)
        anglePaint.getTextBounds(readout, 0, readout.length, textBounds)
        val baseline = height - inset
        val readoutWidth = anglePaint.measureText(readout)
        canvas.drawText(readout, width / 2f, baseline, anglePaint)
        readoutRect.set(
            width / 2f - readoutWidth / 2f - margin,
            baseline - textBounds.height() - margin,
            width / 2f + readoutWidth / 2f + margin,
            baseline + margin
        )

        // One padlock per arm, drawn upright in both orientations, just inside the numbers.
        LockChip.draw(canvas, chipX(0), chipY(0), chipRadius, firstLocked, color, palette.background)
        LockChip.draw(canvas, chipX(1), chipY(1), chipRadius, secondLocked, color, palette.background)
    }

    /** Distance of the arm padlocks from the pivot: in the free area inside the numbers. */
    private val chipDistance: Float
        get() = (tickRadius - dp(80f)).coerceAtLeast(dp(40f))

    /**
     * Screen position of the padlock of one arm. The dial frame points 0° to the right and
     * 90° up; in portrait the whole dial carries the same quarter turn the canvas gets.
     */
    private fun chipX(index: Int): Float {
        val radians = Math.toRadians(arms[index].toDouble())
        val dx = cos(radians).toFloat()
        val dy = -sin(radians).toFloat()
        return if (portrait) centerX - dy * chipDistance else centerX + dx * chipDistance
    }

    private fun chipY(index: Int): Float {
        val radians = Math.toRadians(arms[index].toDouble())
        val dx = cos(radians).toFloat()
        val dy = -sin(radians).toFloat()
        return if (portrait) centerY + dx * chipDistance else centerY + dy * chipDistance
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
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                val x = event.getX(index)
                val y = event.getY(index)
                beginGesture(x, y)
                grab(event.getPointerId(index), x, y)
                onTouchDown?.invoke()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                grab(event.getPointerId(index), event.getX(index), event.getY(index))
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    noteMove(id, x, y)
                    drag(id, x, y)
                }
            }

            MotionEvent.ACTION_UP -> {
                finishGesture()
                releasePointers()
                onTouchUp?.invoke()
            }

            MotionEvent.ACTION_POINTER_UP -> release(event.getPointerId(event.actionIndex))

            MotionEvent.ACTION_CANCEL -> {
                releasePointers()
                tapTarget = TAP_NONE
                onTouchUp?.invoke()
            }
        }
        return true
    }

    /** Remembers what the gesture started on, so a clean tap can work the padlocks. */
    private fun beginGesture(x: Float, y: Float) {
        tapStartX = x
        tapStartY = y
        tapMoved = false
        tapTarget = when {
            overChip(0, x, y) -> TAP_CHIP_FIRST
            overChip(1, x, y) -> TAP_CHIP_SECOND
            readoutRect.contains(x, y) -> TAP_READOUT
            else -> TAP_NONE
        }
    }

    /**
     * A gesture that starts on a padlock or the readout only counts as a tap while the finger
     * stays within the touch slop; a real drag on a padlock grabs exactly that arm, and a
     * pinned arm refuses to move.
     */
    private fun noteMove(id: Int, x: Float, y: Float) {
        if (tapMoved) return
        if (hypot(x - tapStartX, y - tapStartY) <= touchSlop) return
        tapMoved = true
        when (tapTarget) {
            TAP_CHIP_FIRST -> if (!firstLocked) {
                tapTarget = TAP_NONE
                grabArm(id, 0, x, y)
            }

            TAP_CHIP_SECOND -> if (!secondLocked) {
                tapTarget = TAP_NONE
                grabArm(id, 1, x, y)
            }

            TAP_READOUT -> {
                tapTarget = TAP_NONE
                grab(id, x, y)
            }
        }
    }

    private fun finishGesture() {
        val target = tapTarget
        tapTarget = TAP_NONE
        if (tapMoved) return
        when (target) {
            TAP_CHIP_FIRST -> {
                firstLocked = !firstLocked
                invalidate()
            }

            TAP_CHIP_SECOND -> {
                secondLocked = !secondLocked
                invalidate()
            }

            TAP_READOUT -> onValueTap?.invoke()
        }
    }

    /** Hands a finger straight to a known arm, used when a drag starts on its padlock. */
    private fun grabArm(id: Int, arm: Int, x: Float, y: Float) {
        val direction = angleOf(x, y) ?: return
        val slot = armOfPointer.indexOfFirst { it == NO_ARM }
        if (slot < 0) return
        pointerIds[slot] = id
        armOfPointer[slot] = arm
        arms[arm] = direction
        notifyChanged()
    }

    private fun overChip(index: Int, x: Float, y: Float): Boolean =
        hypot(x - chipX(index), y - chipY(index)) <= chipRadius * 1.6f

    /** Assigns a free finger to the nearest arm that is not pinned. */
    private fun grab(id: Int, x: Float, y: Float) {
        // A tap on a padlock or the readout never drags an arm.
        if (tapTarget != TAP_NONE) return
        if (firstLocked && secondLocked) return
        val direction = angleOf(x, y) ?: return
        val slot = armOfPointer.indexOfFirst { it == NO_ARM }
        if (slot < 0) return
        val preferred = when {
            firstLocked -> 1
            secondLocked -> 0
            abs(direction - arms[0]) <= abs(direction - arms[1]) -> 0
            else -> 1
        }
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
        if (arm == 0 && firstLocked) return
        if (arm == 1 && secondLocked) return
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
        val degrees = if (portrait) {
            // The dial is turned a quarter turn here: 0° points down the screen, 90° right.
            // Touches past the base line fold onto the nearer end of the scale.
            val raw = Math.toDegrees(atan2(dx.toDouble(), (y - centerY).toDouble())).toFloat()
            when {
                raw < -90f -> 180f
                raw < 0f -> 0f
                else -> raw
            }
        } else {
            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }
        return degrees.coerceIn(0f, 180f)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    private companion object {
        const val NO_ARM = -1
        const val DEFAULT_A = 45f
        const val DEFAULT_B = 135f

        /** Nothing tappable was touched: the gesture may drag an arm. */
        const val TAP_NONE = 0
        const val TAP_CHIP_FIRST = 1
        const val TAP_CHIP_SECOND = 2
        const val TAP_READOUT = 3
    }
}
