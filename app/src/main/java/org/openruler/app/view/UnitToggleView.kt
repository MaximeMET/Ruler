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
 * Pill switch used for picking the unit: the selected segment keeps the colour of the tool
 * bar and gets a white outline, the other segments are filled with the surface colour.
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

    /** Units offered by the switch, in the order they appear on screen. */
    private val segments = listOf(LengthUnit.INCH, LengthUnit.CM, LengthUnit.MM)

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
        val segmentWidth = w / segments.size

        // Unselected segments: plain surface pill.
        fillPaint.color = palette.toggleSurface
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        // Selected segment: tool bar colour with a white outline. Only the corners that
        // touch the outside of the pill stay round, so the segments read as one control.
        // A unit picked in the settings screen may not be on the pill at all; then nothing
        // is highlighted and a tap here simply switches back to one of the three below.
        val selectedIndex = segments.indexOf(unit)
        if (selectedIndex >= 0) {
            val left = selectedIndex * segmentWidth
            val right = left + segmentWidth
            val roundLeft = if (selectedIndex == 0) radius else 0f
            val roundRight = if (selectedIndex == segments.lastIndex) radius else 0f
            path.reset()
            path.addRoundRect(
                RectF(left + stroke / 2f, stroke / 2f, right - stroke / 2f, h - stroke / 2f),
                floatArrayOf(
                    roundLeft, roundLeft,
                    roundRight, roundRight,
                    roundRight, roundRight,
                    roundLeft, roundLeft
                ),
                Path.Direction.CW
            )
            fillPaint.color = palette.panel
            canvas.drawPath(path, fillPaint)
            strokePaint.color = palette.toggleStroke
            canvas.drawPath(path, strokePaint)
        }

        // One label per segment.
        textPaint.getTextBounds("cm", 0, 2, textBounds)
        val baseline = h / 2f + textBounds.height() / 2f
        segments.forEachIndexed { index, entry ->
            textPaint.color = if (index == selectedIndex) palette.iconOnPanel else palette.accent
            canvas.drawText(label(entry), segmentWidth * (index + 0.5f), baseline, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val segmentWidth = width.toFloat() / segments.size
                val index = (event.x / segmentWidth).toInt().coerceIn(0, segments.lastIndex)
                val selected = segments[index]
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
