package org.openruler.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.openruler.app.core.LengthUnit
import org.openruler.app.core.Palette

/**
 * Two segment switch used for picking the unit, drawn as a pill: the selected half keeps
 * the colour of the tool bar and gets a white outline, the other half is filled white.
 */
class UnitToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var palette: Palette = Palette.LIGHT
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the new unit when the user switches. */
    var onUnitSelected: ((LengthUnit) -> Unit)? = null

    var unit: LengthUnit = LengthUnit.CM
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val leftLabel = LengthUnit.INCH
    private val rightLabel = LengthUnit.CM

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val path = Path()
    private val rect = RectF()
    private val textBounds = android.graphics.Rect()

    private val stroke = dp(1.5f)

    init {
        textPaint.textSize = sp(20f)
        strokePaint.strokeWidth = stroke
        isClickable = true
    }

    private fun label(unit: LengthUnit): String = when (unit) {
        LengthUnit.INCH -> context.getString(org.openruler.app.R.string.unit_inch)
        LengthUnit.CM -> context.getString(org.openruler.app.R.string.unit_cm)
        LengthUnit.MM -> context.getString(org.openruler.app.R.string.unit_mm)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f
        val middle = w / 2f

        // Unselected half: plain surface pill.
        fillPaint.color = palette.toggleSurface
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        // Selected half: tool bar colour with a white outline.
        path.reset()
        val leftSelected = unit == leftLabel
        val selectedHeight = h
        if (leftSelected) {
            path.addRoundRect(
                RectF(stroke / 2f, stroke / 2f, middle, selectedHeight - stroke / 2f),
                floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius),
                Path.Direction.CW
            )
        } else {
            path.addRoundRect(
                RectF(middle, stroke / 2f, w - stroke / 2f, selectedHeight - stroke / 2f),
                floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f),
                Path.Direction.CW
            )
        }
        fillPaint.color = palette.panel
        canvas.drawPath(path, fillPaint)
        strokePaint.color = palette.toggleStroke
        canvas.drawPath(path, strokePaint)

        // Labels.
        textPaint.getTextBounds("cm", 0, 2, textBounds)
        val baseline = h / 2f + textBounds.height() / 2f
        textPaint.color = if (leftSelected) palette.iconOnPanel else palette.accent
        canvas.drawText(label(leftLabel), middle / 2f, baseline, textPaint)
        textPaint.color = if (leftSelected) palette.accent else palette.iconOnPanel
        canvas.drawText(label(rightLabel), middle + middle / 2f, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val selected = if (event.x < width / 2f) leftLabel else rightLabel
                if (selected != unit) {
                    unit = selected
                    onUnitSelected?.invoke(selected)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
