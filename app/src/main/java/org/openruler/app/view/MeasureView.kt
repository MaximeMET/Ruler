package org.openruler.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.openruler.app.core.LengthUnit
import org.openruler.app.core.MeasureMode
import org.openruler.app.core.Palette
import org.openruler.app.core.Scale
import org.openruler.app.core.contrastOn
import org.openruler.app.core.dim
import java.util.Locale

/**
 * Draggable measurement overlay.
 *
 * Two corner points define the measured area; what they mean depends on the active tool:
 *  * [MeasureMode.TWO_POINT] – both edges move, the readout is the distance between them.
 *    Either edge can be pinned, and a typed length pins both of them at once, which turns
 *    the band into a fixed ruler,
 *  * [MeasureMode.FOUR_POINT] – both corners move freely, the readout adds width, height
 *    and area.
 *
 * Landscape measures along the long edge of the screen, so the edges of the two point
 * tool are vertical. Portrait turns the tool a quarter turn: the edges become horizontal
 * and the readout is the distance between the two lines.
 */
class MeasureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var unit: LengthUnit = LengthUnit.CM
        set(value) {
            field = value
            refresh()
        }

    var calibration: Float = 1f
        set(value) {
            field = value
            refresh()
        }

    var palette: Palette = Palette.LIGHT
        set(value) {
            field = value
            invalidate()
        }

    /** `true` while the screen is portrait, when the measured axis runs down the screen. */
    var portrait: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var mode: MeasureMode = MeasureMode.TWO_POINT
        private set

    /** Called whenever the measured rectangle changes. */
    var onRectChanged: ((RectF) -> Unit)? = null

    /** Called when the user taps the readout: the host then asks for a length. */
    var onValueTap: (() -> Unit)? = null
    var onTouchDown: (() -> Unit)? = null
    var onTouchUp: (() -> Unit)? = null

    /** Each line can be pinned in place, so the band can be used as a fixed ruler. */
    var leftLocked: Boolean = false
        private set
    var rightLocked: Boolean = false
        private set

    private val left = PointF()
    private val right = PointF()

    private var firstPointer = MotionEvent.INVALID_POINTER_ID
    private var secondPointer = MotionEvent.INVALID_POINTER_ID
    private var firstHandleIsLeft = false

    /** What the current gesture started on: a line, the readout, or nothing special. */
    private var tapTarget = TAP_NONE
    private var tapStartX = 0f
    private var tapStartY = 0f
    private var tapMoved = false

    /** Hit box of the readout, refreshed on every draw. */
    private val readoutRect = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val textBounds = Rect()
    private val chipRadius = dp(12f)
    private val chipMargin = dp(26f)
    /** In landscape the chips float above the bottom scale instead of sitting on its ticks. */
    private val chipBottomOffset = dp(76f)
    /** The close button floats over the top right corner; chips step left to stay tappable. */
    private val chipCloseZone = dp(66f)
    private val chipCloseShift = dp(46f)
    private val chipEdgeGap = dp(4f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val margin = dp(10f)
    private val tickHalf = dp(5f)
    private val handleStroke = dp(1f)

    private var measuredWidthValue = 0f
    private var measuredHeightValue = 0f
    private var measuredAreaValue = 0f

    /** Set when [start] is called before the view has been measured. */
    private var pendingMode: MeasureMode? = null

    /** `true` while the tool is on screen; keeps stale rectangles away from the ruler. */
    private var active = false

    init {
        valuePaint.textSize = sp(60f)
        unitPaint.textSize = sp(22f)
        smallPaint.textSize = sp(22f)
        areaPaint.textSize = sp(18f)
        handlePaint.strokeWidth = handleStroke
        linePaint.strokeWidth = dp(0.5f)
    }

    /** Re-seeds the corner points for [newMode]. */
    fun start(newMode: MeasureMode) {
        mode = newMode
        active = true
        if (width == 0 || height == 0) {
            // The view has not been laid out yet; seed the handles as soon as it has a size.
            pendingMode = newMode
            return
        }
        pendingMode = null
        leftLocked = false
        rightLocked = false
        seedPoints()
        releasePointers()
        tapTarget = TAP_NONE
        tapMoved = false
        refresh()
    }

    /** Called when the user closes the tool: stops reporting rectangle changes. */
    fun stop() {
        active = false
        pendingMode = null
        releasePointers()
        tapTarget = TAP_NONE
        tapMoved = false
    }

    private fun seedPoints() {
        val metrics = resources.displayMetrics
        val stepX = Scale.pxPerTick(unit, metrics.xdpi, calibration)
        val stepY = Scale.pxPerTick(unit, metrics.ydpi, calibration)

        when (mode) {
            MeasureMode.TWO_POINT -> if (portrait) {
                left.set(0f, quantize(height * 0.2f, stepY))
                right.set(width.toFloat(), quantize(height * 0.75f, stepY))
            } else {
                left.set(quantize(width * 0.2f, stepX), 0f)
                right.set(quantize(width * 0.75f, stepX), height.toFloat())
            }
            MeasureMode.FOUR_POINT -> {
                left.set(quantize(width * 0.2f, stepX), quantize(height * 0.2f, stepY))
                right.set(quantize(width * 0.75f, stepX), quantize(height * 0.75f, stepY))
            }
            MeasureMode.PROTRACTOR -> Unit
        }
    }

    /** The currently measured rectangle, normalised so that left < right and top < bottom. */
    val rect: RectF
        get() = RectF(
            min(left.x, right.x),
            min(left.y, right.y),
            max(left.x, right.x),
            max(left.y, right.y)
        )

    /** Corner points of the rectangle, used to restore state. */
    fun cornerPoints(): List<PointF> = listOf(PointF(left), PointF(right))

    fun restore(points: List<PointF>) {
        if (points.size < 2) return
        left.set(points[0])
        right.set(points[1])
        // The restored corners win over the default seed that [start] queued up.
        pendingMode = null
        refresh()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pending = pendingMode
        if (pending != null) {
            mode = pending
            pendingMode = null
            seedPoints()
        } else if (active && oldw > 0 && oldh > 0 && (w != oldw || h != oldh)) {
            // A rotation or a window resize while a tool is open: the old corners do not
            // mean anything on the new axes, so start the tool again on the new geometry.
            // A band that was pinned to an exact length keeps that length, though.
            val pinned = if (mode == MeasureMode.TWO_POINT && leftLocked && rightLocked) {
                measuredWidthValue
            } else {
                0f
            }
            seedPoints()
            if (pinned > 0f) setLength(pinned)
        } else if (oldw > 0 && oldh > 0) {
            val scaleX = w.toFloat() / oldw
            val scaleY = h.toFloat() / oldh
            left.set(left.x * scaleX, left.y * scaleY)
            right.set(right.x * scaleX, right.y * scaleY)
        }
        refresh()
    }

    /** Recomputes the reported values and redraws. */
    fun refresh() {
        if (width == 0 || height == 0) return
        val metrics = resources.displayMetrics
        val r = rect
        if (portrait && mode != MeasureMode.FOUR_POINT) {
            measuredWidthValue = Scale.pxToUnits(r.height(), unit, metrics.ydpi, calibration)
            measuredHeightValue = 0f
            measuredAreaValue = 0f
        } else {
            measuredWidthValue = Scale.pxToUnits(r.width(), unit, metrics.xdpi, calibration)
            measuredHeightValue = Scale.pxToUnits(r.height(), unit, metrics.ydpi, calibration)
            measuredAreaValue = measuredWidthValue * measuredHeightValue
        }
        if (active) onRectChanged?.invoke(r)
        invalidate()
    }

    // region drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val toolColor = palette.accent
        val r = rect

        fillPaint.color = toolColor
        canvas.drawRect(r, fillPaint)

        // A grip runs along each line inside the band: the band's own fill hides the plain
        // tool colour, so every grip uses the dimmed variant.
        handlePaint.color = dim(toolColor, 0.7f)
        if (mode == MeasureMode.FOUR_POINT) {
            canvas.drawLine(r.left, right.y, r.right, right.y, handlePaint)
            canvas.drawLine(right.x, r.top, right.x, r.bottom, handlePaint)
        } else if (portrait) {
            canvas.drawLine(r.left, left.y, r.right, left.y, handlePaint)
            canvas.drawLine(r.left, right.y, r.right, right.y, handlePaint)
        } else {
            canvas.drawLine(left.x, r.top, left.x, r.bottom, handlePaint)
            canvas.drawLine(right.x, r.top, right.x, r.bottom, handlePaint)
        }

        linePaint.color = toolColor
        valuePaint.color = toolColor
        unitPaint.color = toolColor
        smallPaint.color = toolColor
        areaPaint.color = toolColor

        val unitText = unitLabel()
        val valueText = String.format(Locale.ROOT, "%.2f", measuredWidthValue)
        val valueWidth = textWidth(valuePaint, valueText)
        val unitWidth = textWidth(smallPaint, unitText)
        valuePaint.getTextBounds("99,99", 0, 5, textBounds)
        val textHeight = textBounds.height().toFloat()
        readoutRect.setEmpty()

        if (mode == MeasureMode.FOUR_POINT) {
            val heightText = String.format(Locale.ROOT, "%.2f", measuredHeightValue)
            val heightWidth = textWidth(valuePaint, heightText)

            // Width of the rectangle: dimension line below it, label in the middle of the line.
            val widthBlock = valueWidth + margin + unitWidth
            val widthBaseline = (r.bottom + margin + textHeight)
                .coerceAtMost(height - margin)
            val widthCenter = r.centerX()
                .coerceInSafe(widthBlock / 2f + margin, width - widthBlock / 2f - margin)
            val widthLeft = widthCenter - widthBlock / 2f
            drawDualText(canvas, valueText, widthLeft + valueWidth, widthBaseline, valuePaint, r, toolColor)
            drawDualText(canvas, unitText, widthLeft + valueWidth + margin, widthBaseline, smallPaint, r, toolColor)
            drawWidthDimension(
                canvas, r, widthBaseline - textHeight / 2f,
                widthLeft - margin, widthLeft + widthBlock + margin, toolColor
            )

            // Height of the rectangle: dimension line on the right, label in the middle of it.
            val heightBlock = heightWidth + margin + unitWidth
            val heightBaseline = (r.centerY() + textHeight / 2f)
                .coerceInSafe(textHeight + margin, height - margin)
            val heightLeft = (r.right + margin)
                .coerceAtMost(width - heightBlock - margin)
            drawDualText(canvas, heightText, heightLeft + heightWidth, heightBaseline, valuePaint, r, toolColor)
            drawDualText(canvas, unitText, heightLeft + heightWidth + margin, heightBaseline, smallPaint, r, toolColor)
            drawHeightDimension(
                canvas, r, heightLeft + heightBlock / 2f,
                heightBaseline - textHeight - margin, heightBaseline + margin, toolColor
            )

            drawArea(canvas, unitText)
        } else if (portrait) {
            // Single readout, centred under the movable edge.
            val blockWidth = valueWidth + margin + unitWidth
            val center = r.centerX()
                .coerceInSafe(blockWidth / 2f + margin, width - blockWidth / 2f - margin)
            val blockLeft = center - blockWidth / 2f
            val baseline = (r.bottom + margin + textHeight).coerceAtMost(height - margin)
            drawDualText(canvas, valueText, blockLeft + valueWidth, baseline, valuePaint, r, toolColor)
            drawDualText(canvas, unitText, blockLeft + valueWidth + margin, baseline, smallPaint, r, toolColor)
            readoutRect.set(
                blockLeft - margin, baseline - textHeight - margin,
                blockLeft + blockWidth + margin, baseline + margin
            )
        } else {
            // Single readout, vertically centred next to the band.
            val baseline = r.centerY() + textHeight / 2f
            val blockStart = min(
                r.right + margin,
                width - margin - valueWidth - unitWidth - margin
            ).coerceAtLeast(margin)
            drawDualText(canvas, valueText, blockStart + valueWidth, baseline, valuePaint, r, toolColor)
            drawDualText(canvas, unitText, blockStart + valueWidth + margin, baseline, smallPaint, r, toolColor)
            readoutRect.set(
                blockStart - margin, baseline - textHeight - margin,
                blockStart + valueWidth + unitWidth + margin, baseline + margin
            )
        }

        // The lock chips sit at the far end of each line, on top of everything else.
        if (mode != MeasureMode.FOUR_POINT) {
            LockChip.draw(
                canvas, lockChipX(left), lockChipY(left), chipRadius,
                leftLocked, toolColor, palette.background
            )
            LockChip.draw(
                canvas, lockChipX(right), lockChipY(right), chipRadius,
                rightLocked, toolColor, palette.background
            )
        }
    }

    /**
     * Where the lock chip of [point] sits: portrait anchors the chips to the right edge,
     * landscape lifts them above the bottom scale. Both keep clear of the screen edge, and
     * a chip that would hide under the close button in the top right corner steps left of it.
     */
    private fun lockChipX(point: PointF): Float = if (portrait) {
        if (point.y < chipCloseZone) width - chipMargin - chipCloseShift else width - chipMargin
    } else {
        point.x.coerceIn(chipRadius + chipEdgeGap, width - chipRadius - chipEdgeGap)
    }

    private fun lockChipY(point: PointF): Float = if (portrait) {
        point.y.coerceIn(chipRadius + chipEdgeGap, height - chipRadius - chipEdgeGap)
    } else {
        height - chipBottomOffset
    }

    private fun drawArea(canvas: Canvas, unitText: String) {
        val text = String.format(Locale.ROOT, "S = %.2f %s²", measuredAreaValue, unitText)
        areaPaint.getTextBounds(text, 0, text.length, textBounds)
        val baseline = height - dp(24f)
        canvas.drawText(text, width - dp(24f), baseline, areaPaint)
    }

    private fun drawWidthDimension(
        canvas: Canvas,
        r: RectF,
        y: Float,
        gapStart: Float,
        gapEnd: Float,
        color: Int
    ) {
        linePaint.color = color
        canvas.drawLine(gapEnd, y, r.right, y, linePaint)
        canvas.drawLine(r.left, y, gapStart, y, linePaint)
        canvas.drawLine(r.left, y - tickHalf, r.left, y + tickHalf, linePaint)
        canvas.drawLine(r.right, y - tickHalf, r.right, y + tickHalf, linePaint)
    }

    private fun drawHeightDimension(
        canvas: Canvas,
        r: RectF,
        x: Float,
        gapStart: Float,
        gapEnd: Float,
        color: Int
    ) {
        linePaint.color = color
        canvas.drawLine(x, gapEnd, x, r.bottom, linePaint)
        canvas.drawLine(x, r.top, x, gapStart, linePaint)
        canvas.drawLine(x - tickHalf, r.top, x + tickHalf, r.top, linePaint)
        canvas.drawLine(x - tickHalf, r.bottom, x + tickHalf, r.bottom, linePaint)
    }

    /**
     * Draws [text] in the tool colour, then repaints the part that falls inside the measured
     * rectangle in black or white so it stays readable.
     */
    private fun drawDualText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        paint: Paint,
        area: RectF,
        toolColor: Int
    ) {
        paint.color = toolColor
        canvas.drawText(text, x, baseline, paint)
        val save = canvas.save()
        canvas.clipRect(area)
        paint.color = contrastOn(toolColor)
        canvas.drawText(text, x, baseline, paint)
        canvas.restoreToCount(save)
    }

    private fun textWidth(paint: Paint, text: String): Float =
        paint.measureText(text)

    fun unitLabel(): String = when (unit) {
        LengthUnit.CM -> "cm"
        LengthUnit.MM -> "mm"
        LengthUnit.INCH -> "inch"
    }

    /** Current reading, used to pre-fill the length dialog. */
    fun measuredValue(): Float = measuredWidthValue

    /** Pins the distance between the lines to [value] and locks both of them. */
    fun setLength(value: Float) {
        if (value <= 0f || width == 0 || height == 0) return
        val metrics = resources.displayMetrics
        val vertical = portrait && mode != MeasureMode.FOUR_POINT
        val dpi = if (vertical) metrics.ydpi else metrics.xdpi
        val axis = if (vertical) height.toFloat() else width.toFloat()
        val span = Scale.unitsToPx(value, unit, dpi, calibration).coerceIn(0f, axis)
        val half = span / 2f
        val current = if (vertical) (left.y + right.y) / 2f else (left.x + right.x) / 2f
        val center = current.coerceIn(half, axis - half)
        if (vertical) {
            left.set(0f, center - half)
            right.set(width.toFloat(), center + half)
        } else {
            left.set(center - half, 0f)
            right.set(center + half, height.toFloat())
        }
        leftLocked = true
        rightLocked = true
        releasePointers()
        refresh()
    }

    /** Restores the pin state of one line, used when the activity is recreated. */
    fun setLocked(isLeft: Boolean, locked: Boolean) {
        if (isLeft) leftLocked = locked else rightLocked = locked
        invalidate()
    }

    // endregion

    // region touch handling

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
                    move(id, x, y)
                }
            }

            MotionEvent.ACTION_UP -> {
                finishGesture()
                releasePointers()
                onTouchUp?.invoke()
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                release(event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    tapTarget = TAP_NONE
                    onTouchUp?.invoke()
                }
            }
        }
        return true
    }

    /** Remembers what the gesture started on, so a clean tap can work the lock chips. */
    private fun beginGesture(x: Float, y: Float) {
        tapStartX = x
        tapStartY = y
        tapMoved = false
        tapTarget = when {
            mode != MeasureMode.FOUR_POINT && overChip(left, x, y) -> TAP_LOCK_LEFT
            mode != MeasureMode.FOUR_POINT && overChip(right, x, y) -> TAP_LOCK_RIGHT
            readoutRect.contains(x, y) -> TAP_READOUT
            else -> TAP_NONE
        }
    }

    /**
     * A gesture that starts on a chip or the readout only counts as a tap while the finger
     * stays within the touch slop; a real drag that started on the chip of an unlocked line
     * grabs exactly that line, and the chip of a locked line refuses to move anything.
     */
    private fun noteMove(id: Int, x: Float, y: Float) {
        if (tapMoved) return
        if (hypot(x - tapStartX, y - tapStartY) <= touchSlop) return
        tapMoved = true
        when (tapTarget) {
            TAP_LOCK_LEFT -> if (!leftLocked) {
                tapTarget = TAP_NONE
                firstPointer = id
                firstHandleIsLeft = true
                apply(true, x, y)
            }

            TAP_LOCK_RIGHT -> if (!rightLocked) {
                tapTarget = TAP_NONE
                firstPointer = id
                firstHandleIsLeft = false
                apply(false, x, y)
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
            TAP_LOCK_LEFT -> {
                leftLocked = !leftLocked
                invalidate()
            }

            TAP_LOCK_RIGHT -> {
                rightLocked = !rightLocked
                invalidate()
            }

            TAP_READOUT -> onValueTap?.invoke()
        }
    }

    private fun overChip(point: PointF, x: Float, y: Float): Boolean =
        hypot(x - lockChipX(point), y - lockChipY(point)) <= chipRadius * 1.6f

    private fun grab(id: Int, x: Float, y: Float) {
        // A tap on a chip or the readout never drags a line.
        if (tapTarget != TAP_NONE) return
        if (mode == MeasureMode.FOUR_POINT) {
            when {
                firstPointer == MotionEvent.INVALID_POINTER_ID -> {
                    val takeLeft = distanceTo(left, x, y) < distanceTo(right, x, y)
                    firstPointer = id
                    firstHandleIsLeft = takeLeft
                    apply(takeLeft, x, y)
                }

                secondPointer == MotionEvent.INVALID_POINTER_ID -> {
                    secondPointer = id
                    apply(!firstHandleIsLeft, x, y)
                }
            }
            return
        }
        // Line tool: locked lines stay put, so the finger grabs the nearest free one.
        when {
            firstPointer == MotionEvent.INVALID_POINTER_ID -> {
                if (leftLocked && rightLocked) return
                val takeLeft = when {
                    rightLocked -> true
                    leftLocked -> false
                    else -> distanceTo(left, x, y) < distanceTo(right, x, y)
                }
                firstPointer = id
                firstHandleIsLeft = takeLeft
                apply(takeLeft, x, y)
            }

            secondPointer == MotionEvent.INVALID_POINTER_ID -> {
                val other = !firstHandleIsLeft
                if (if (other) leftLocked else rightLocked) return
                secondPointer = id
                apply(other, x, y)
            }
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        if (firstPointer != MotionEvent.INVALID_POINTER_ID && id == firstPointer) {
            apply(firstHandleIsLeft, x, y)
        } else if (secondPointer != MotionEvent.INVALID_POINTER_ID && id == secondPointer) {
            apply(!firstHandleIsLeft, x, y)
        }
    }

    private fun apply(toLeft: Boolean, x: Float, y: Float) {
        val target = if (toLeft) left else right
        if (mode != MeasureMode.FOUR_POINT && (if (toLeft) leftLocked else rightLocked)) return
        if (portrait && mode != MeasureMode.FOUR_POINT) {
            target.y = y.coerceIn(0f, height.toFloat())
        } else {
            target.x = x.coerceIn(0f, width.toFloat())
        }
        if (mode == MeasureMode.FOUR_POINT) {
            target.y = y.coerceIn(0f, height.toFloat())
        }
        refresh()
    }

    private fun release(id: Int) {
        if (id == firstPointer) firstPointer = MotionEvent.INVALID_POINTER_ID
        if (id == secondPointer) secondPointer = MotionEvent.INVALID_POINTER_ID
    }

    private fun releasePointers() {
        firstPointer = MotionEvent.INVALID_POINTER_ID
        secondPointer = MotionEvent.INVALID_POINTER_ID
    }

    private fun distanceTo(point: PointF, x: Float, y: Float): Float {
        val vertical = portrait && mode != MeasureMode.FOUR_POINT
        val dx = if (vertical) 0f else point.x - x
        val dy = if (vertical || mode == MeasureMode.FOUR_POINT) point.y - y else 0f
        return dx * dx + dy * dy
    }

    // endregion

    /** Snaps a coordinate to half a tick, which is how the tools are seeded. */
    private fun quantize(value: Float, step: Float): Float {
        if (step <= 0f) return value
        return ((value * 2f / step).toInt() * step) / 2f
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    /** `coerceIn` that tolerates an empty range, which happens on very small screens. */
    private fun Float.coerceInSafe(min: Float, max: Float): Float =
        if (min <= max) coerceIn(min, max) else this

    private companion object {
        /** Nothing tappable was touched: the gesture may drag a line. */
        const val TAP_NONE = 0
        const val TAP_LOCK_LEFT = 1
        const val TAP_LOCK_RIGHT = 2
        const val TAP_READOUT = 3
    }
}
