package org.openruler.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.openruler.app.core.LengthUnit
import org.openruler.app.core.Palette
import org.openruler.app.core.Scale
import java.util.Locale

/**
 * Calibration ruler.
 *
 * The screen is used in portrait, so the ruler is drawn rotated 90° and runs along the
 * long edge of the phone. A bank card is 85.60 mm long all over the world, so the drawn
 * outline gives the user a physical reference: line the card up with the outline and
 * adjust until the ticks match.
 */
class CalibrationRulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var palette: Palette = Palette.LIGHT
        set(value) {
            field = value
            invalidate()
        }

    var unit: LengthUnit = LengthUnit.CM
        set(value) {
            field = value
            invalidate()
        }

    var coefficient: Float = 1f
        private set

    /** Called while the user drags or taps the buttons. */
    var onCoefficientChanged: ((Float) -> Unit)? = null

    private var lastTouchY = 0f
    private var dragging = false

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }

    private val textBounds = Rect()
    private val cardRect = RectF()

    private val tickLong = dp(25f)
    private val tickMid = dp(20f)
    private val tickShort = dp(15f)
    private val labelGap = dp(5f)

    private var labelHeight = 0f

    init {
        textPaint.textSize = sp(30f)
        labelPaint.textSize = sp(25f)
        valuePaint.textSize = sp(25f)
        tickPaint.strokeWidth = dp(1.2f)
        cardPaint.strokeWidth = dp(1.5f)
        textPaint.getTextBounds("99", 0, 2, textBounds)
        labelHeight = textBounds.height().toFloat()
        isClickable = true
    }

    fun setCoefficient(value: Float, notify: Boolean = false) {
        coefficient = value.coerceIn(MIN, MAX)
        if (notify) onCoefficientChanged?.invoke(coefficient)
        invalidate()
    }

    fun reset() {
        setCoefficient(1f, notify = true)
    }

    fun nudge(delta: Float) {
        setCoefficient(coefficient + delta, notify = true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val save = canvas.save()
        // Rotate the drawing frame so that "x" runs down the screen and "y" to the left.
        canvas.translate(width.toFloat(), 0f)
        canvas.rotate(90f)

        val rulerLength = height.toFloat()
        val band = width.toFloat()
        val laneStart = band * 0.30f
        val laneEnd = band * 0.70f
        val origin = dp(10f)

        val step = Scale.pxPerTick(unit, resources.displayMetrics.xdpi, coefficient)
        val ticksPerLabel = Scale.ticksPerLabel(unit)
        val labelStep = Scale.labelStep(unit)
        val cardLength = Scale.pxPerTick(LengthUnit.MM, resources.displayMetrics.xdpi, coefficient) * CARD_LENGTH_MM

        tickPaint.color = palette.accent
        cardPaint.color = palette.accent
        markerPaint.color = palette.accent
        textPaint.color = palette.accent
        labelPaint.color = palette.accent
        valuePaint.color = palette.contrastText()

        // Ticks above and below the card lane.
        val count = ((rulerLength - origin) / step).toInt()
        for (i in 0..count) {
            val x = origin + i * step
            if (x > rulerLength) break
            val isLabel = i % ticksPerLabel == 0
            val isMid = ticksPerLabel > 1 && i % (ticksPerLabel / 2) == 0
            val length = when {
                isLabel -> tickLong
                isMid -> tickMid
                else -> tickShort
            }
            tickPaint.color = if (isLabel) palette.accent else palette.rulerContrast
            tickPaint.strokeWidth = if (isLabel) dp(1.2f) else dp(0.7f)
            canvas.drawLine(x, laneStart, x, laneStart - length, tickPaint)
            canvas.drawLine(x, laneEnd, x, laneEnd + length, tickPaint)
            if (isLabel) {
                val label = ((i / ticksPerLabel) * labelStep).toString()
                labelPaint.color = palette.accent
                canvas.drawText(label, x, laneStart - tickLong - labelGap, labelPaint)
                canvas.drawText(label, x, laneEnd + tickLong + labelHeight + labelGap, labelPaint)
            }
        }

        // Bank card outline: exactly 85.60 mm long.
        cardRect.set(origin, laneStart, origin + cardLength, laneEnd)
        canvas.drawRect(cardRect, cardPaint)
        canvas.drawRect(origin, laneStart, origin + dp(2f), laneEnd, markerPaint)

        // Texts inside the outline.
        val cardValue = when (unit) {
            LengthUnit.CM -> CARD_LENGTH_MM / 10f
            LengthUnit.MM -> CARD_LENGTH_MM
            LengthUnit.INCH -> CARD_LENGTH_MM / 25.4f
        }
        val cardText = String.format(
            Locale.ROOT,
            "%s %.2f %s",
            context.getString(org.openruler.app.R.string.credit_card_length).replace(":", ""),
            cardValue,
            unitLabel()
        )
        canvas.drawText(cardText, origin + cardLength / 2f, (laneStart + laneEnd) / 2f + labelHeight / 2f, textPaint)

        val coefficientLabel = context.getString(org.openruler.app.R.string.coefficient)
        val coefficientValue = String.format(Locale.ROOT, "%.2f", coefficient)
        val labelWidth = labelPaint.measureText(coefficientLabel)
        val valueWidth = valuePaint.measureText(coefficientValue)
        val blockStart = rulerLength - dp(16f) - labelWidth - dp(6f) - valueWidth
        // Draw it in the lane outside the ticks so it never covers the scale.
        val textRow = band * 0.14f
        labelPaint.color = palette.textSecondary
        canvas.drawText(coefficientLabel, blockStart, textRow, labelPaint)
        valuePaint.color = palette.accent
        canvas.drawText(coefficientValue, blockStart + labelWidth + dp(6f), textRow, valuePaint)

        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                dragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val delta = (event.y - lastTouchY) * DRAG_SENSITIVITY
                    lastTouchY = event.y
                    setCoefficient(coefficient + delta, notify = true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun unitLabel(): String = when (unit) {
        LengthUnit.CM -> "cm"
        LengthUnit.MM -> "mm"
        LengthUnit.INCH -> "inch"
    }

    private fun Palette.contrastText(): Int = textSecondary

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    companion object {
        /** ISO/IEC 7810 ID-1 card length. */
        const val CARD_LENGTH_MM = 85.6f

        const val MIN = 0.4f
        const val MAX = 2.0f

        /** Coefficient change per pixel dragged. */
        private const val DRAG_SENSITIVITY = 7.0E-4f
    }
}
