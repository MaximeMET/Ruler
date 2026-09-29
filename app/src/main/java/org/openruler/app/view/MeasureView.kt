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
import kotlin.math.abs
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
 *  * [MeasureMode.ONE_POINT] – the right edge moves, the left edge stays at the zero tick,
 *  * [MeasureMode.TWO_POINT] – both edges move, the readout is the distance between them,
 *  * [MeasureMode.FOUR_POINT] – both corners move freely, the readout adds width, height
 *    and area.
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

    var mode: MeasureMode = MeasureMode.ONE_POINT
        private set

    var indentFromEdge: Boolean = false

    /** Called whenever the measured rectangle changes. */
    var onRectChanged: ((RectF) -> Unit)? = null
    var onTouchDown: (() -> Unit)? = null
    var onTouchUp: (() -> Unit)? = null

    private val left = PointF()
    private val right = PointF()

    private var firstPointer = MotionEvent.INVALID_POINTER_ID
    private var secondPointer = MotionEvent.INVALID_POINTER_ID
    private var firstHandleIsLeft = false

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
        seedPoints()
        releasePointers()
        refresh()
    }

    /** Called when the user closes the tool: stops reporting rectangle changes. */
    fun stop() {
        active = false
        pendingMode = null
        releasePointers()
    }

    private fun seedPoints() {
        val metrics = resources.displayMetrics
        val stepX = Scale.pxPerTick(unit, metrics.xdpi, calibration)
        val stepY = Scale.pxPerTick(unit, metrics.ydpi, calibration)
        val indent = if (indentFromEdge) dp(36f) else 0f

        when (mode) {
            MeasureMode.ONE_POINT -> {
                left.set(indent, 0f)
                right.set(quantize(width * 0.75f, stepX), height.toFloat())
            }
            MeasureMode.TWO_POINT -> {
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
        refresh()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val scaleX = if (oldw > 0) w.toFloat() / oldw else 0f
        val scaleY = if (oldh > 0) h.toFloat() / oldh else 0f
        if (scaleX > 0f && scaleY > 0f) {
            left.set(left.x * scaleX, left.y * scaleY)
            right.set(right.x * scaleX, right.y * scaleY)
        } else if (pendingMode != null) {
            pendingMode?.let { mode = it }
            pendingMode = null
            seedPoints()
        }
        refresh()
    }

    /** Recomputes the reported values and redraws. */
    fun refresh() {
        if (width == 0 || height == 0) return
        val metrics = resources.displayMetrics
        val r = rect
        measuredWidthValue = Scale.pxToUnits(r.width(), unit, metrics.xdpi, calibration)
        measuredHeightValue = Scale.pxToUnits(r.height(), unit, metrics.ydpi, calibration)
        measuredAreaValue = measuredWidthValue * measuredHeightValue
        if (active) onRectChanged?.invoke(r)
        invalidate()
    }

    // region drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val modeColor = palette.colorOf(mode)
        val r = rect

        fillPaint.color = modeColor
        canvas.drawRect(r, fillPaint)

        handlePaint.color = dim(modeColor, 0.7f)
        canvas.drawLine(right.x, r.top, right.x, r.bottom, handlePaint)
        if (mode == MeasureMode.FOUR_POINT) {
            canvas.drawLine(r.left, right.y, r.right, right.y, handlePaint)
        }

        linePaint.color = modeColor
        valuePaint.color = modeColor
        unitPaint.color = modeColor
        smallPaint.color = modeColor
        areaPaint.color = modeColor

        val unitText = unitLabel()
        val valueText = String.format(Locale.ROOT, "%.2f", measuredWidthValue)
        val valueWidth = textWidth(valuePaint, valueText)
        val unitWidth = textWidth(smallPaint, unitText)
        valuePaint.getTextBounds("99,99", 0, 5, textBounds)
        val textHeight = textBounds.height().toFloat()

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
            drawDualText(canvas, valueText, widthLeft + valueWidth, widthBaseline, valuePaint, r, modeColor)
            drawDualText(canvas, unitText, widthLeft + valueWidth + margin, widthBaseline, smallPaint, r, modeColor)
            drawWidthDimension(
                canvas, r, widthBaseline - textHeight / 2f,
                widthLeft - margin, widthLeft + widthBlock + margin, modeColor
            )

            // Height of the rectangle: dimension line on the right, label in the middle of it.
            val heightBlock = heightWidth + margin + unitWidth
            val heightBaseline = (r.centerY() + textHeight / 2f)
                .coerceInSafe(textHeight + margin, height - margin)
            val heightLeft = (r.right + margin)
                .coerceAtMost(width - heightBlock - margin)
            drawDualText(canvas, heightText, heightLeft + heightWidth, heightBaseline, valuePaint, r, modeColor)
            drawDualText(canvas, unitText, heightLeft + heightWidth + margin, heightBaseline, smallPaint, r, modeColor)
            drawHeightDimension(
                canvas, r, heightLeft + heightBlock / 2f,
                heightBaseline - textHeight - margin, heightBaseline + margin, modeColor
            )

            drawArea(canvas, unitText)
        } else {
            // Single readout, vertically centred next to the band.
            val baseline = r.centerY() + textHeight / 2f
            val blockStart = min(
                r.right + margin,
                width - margin - valueWidth - unitWidth - margin
            ).coerceAtLeast(margin)
            drawDualText(canvas, valueText, blockStart + valueWidth, baseline, valuePaint, r, modeColor)
            drawDualText(canvas, unitText, blockStart + valueWidth + margin, baseline, smallPaint, r, modeColor)
        }
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
        modeColor: Int
    ) {
        paint.color = modeColor
        canvas.drawText(text, x, baseline, paint)
        val save = canvas.save()
        canvas.clipRect(area)
        paint.color = contrastOn(modeColor)
        canvas.drawText(text, x, baseline, paint)
        canvas.restoreToCount(save)
    }

    private fun textWidth(paint: Paint, text: String): Float =
        paint.measureText(text)

    private fun unitLabel(): String = when (unit) {
        LengthUnit.CM -> "cm"
        LengthUnit.MM -> "mm"
        LengthUnit.INCH -> "inch"
    }

    // endregion

    // region touch handling

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                grab(event.getPointerId(index), event.getX(index), event.getY(index))
                onTouchDown?.invoke()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                grab(event.getPointerId(index), event.getX(index), event.getY(index))
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    move(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }

            MotionEvent.ACTION_UP -> {
                releasePointers()
                onTouchUp?.invoke()
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                release(event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) onTouchUp?.invoke()
            }
        }
        return true
    }

    private fun grab(id: Int, x: Float, y: Float) {
        val canGrabLeft = mode == MeasureMode.TWO_POINT || mode == MeasureMode.FOUR_POINT
        when {
            firstPointer == MotionEvent.INVALID_POINTER_ID -> {
                val takeLeft = canGrabLeft && distanceTo(left, x, y) < distanceTo(right, x, y)
                firstPointer = id
                firstHandleIsLeft = takeLeft
                apply(takeLeft, x, y)
            }

            secondPointer == MotionEvent.INVALID_POINTER_ID && canGrabLeft -> {
                secondPointer = id
                apply(!firstHandleIsLeft, x, y)
            }
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        if (id == firstPointer) {
            apply(firstHandleIsLeft, x, y)
        } else if (id != MotionEvent.INVALID_POINTER_ID && id == secondPointer) {
            apply(!firstHandleIsLeft, x, y)
        } else if (secondPointer == MotionEvent.INVALID_POINTER_ID &&
            (mode == MeasureMode.TWO_POINT || mode == MeasureMode.FOUR_POINT)
        ) {
            secondPointer = id
            apply(!firstHandleIsLeft, x, y)
        }
    }

    private fun apply(toLeft: Boolean, x: Float, y: Float) {
        val target = if (toLeft) left else right
        target.x = x.coerceIn(0f, width.toFloat())
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
        val dx = point.x - x
        val dy = if (mode == MeasureMode.FOUR_POINT) point.y - y else 0f
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
}
