package org.openruler.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import org.openruler.app.core.LengthUnit
import org.openruler.app.core.MeasureMode
import org.openruler.app.core.Palette
import org.openruler.app.core.Scale
import org.openruler.app.core.contrastOn

/**
 * The ruler itself: a scale along the top edge, a scale along the bottom edge and a
 * vertical scale on the left, all drawn in real world units.
 *
 * Which scales are visible depends on the measurement tool that is active:
 *  * from-the-edge / between-two-points tools use the horizontal scales,
 *  * the rectangle tool uses the top and the left scale,
 *  * while nothing is being measured the top scale is painted in the panel colour
 *    (white over the blue tool bar) so the screen still reads as one ruler.
 *
 * In portrait the long edge of the screen is the left one, so the vertical scale becomes
 * the ruler the user reads and the two horizontal scales are dropped.
 */
class RulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var unit: LengthUnit = LengthUnit.CM
        set(value) {
            field = value
            invalidate()
        }

    var calibration: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    var indentFromEdge: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** `true` while the screen is portrait: the vertical scale is the main ruler. */
    var portrait: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * Height of the tool bar in pixels. The portrait ruler reaches into the panel, and that
     * part is printed in the panel ink - the same trick that keeps the landscape top scale
     * readable on the coloured bar.
     */
    var panelHeight: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var palette: Palette = Palette.LIGHT
        set(value) {
            field = value
            invalidate()
        }

    var mode: MeasureMode = MeasureMode.ONE_POINT
        set(value) {
            field = value
            invalidate()
        }

    /** Long tick colour; while measuring this is the colour of the active tool. */
    var mainColor: Int = Color.BLUE
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Colour used for the whole top scale while no measurement is running, `null` when the
     * regular palette should be used.
     */
    var topScaleColor: Int? = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    /** Rectangle covered by the active measurement; the scale inside is repainted. */
    var overlayRect: RectF? = null
        set(value) {
            field = value?.let { RectF(it).apply { sort() } }
            invalidate()
        }

    private val shortPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val longPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }

    private val labelBounds = Rect()

    private val longTick = dp(25f)
    private val midTick = dp(20f)
    private val shortTick = dp(15f)
    private val labelGap = dp(7f)
    private val indent = dp(36f)

    private var tickWidth = dp(1.2f)
    private var labelTextHeight = 0f

    init {
        textPaint.textSize = sp(30f)
        textPaint.getTextBounds("99", 0, 2, labelBounds)
        labelTextHeight = labelBounds.height().toFloat()
        shortPaint.strokeWidth = dp(1.2f)
        longPaint.strokeWidth = tickWidth
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val metrics = resources.displayMetrics
        val stepX = Scale.pxPerTick(unit, metrics.xdpi, calibration)
        val stepY = Scale.pxPerTick(unit, metrics.ydpi, calibration)

        if (usesTopScale) {
            val override = topScaleColor
            if (override != null) {
                // Idle screen: the top scale melts into the coloured tool bar.
                drawHorizontalScale(canvas, stepX, top = true, short = override, long = override, text = override)
            } else {
                drawHorizontalScale(
                    canvas, stepX, top = true,
                    short = palette.rulerContrast, long = mainColor, text = palette.rulerContrast
                )
            }
            if (usesBottomScale) {
                drawHorizontalScale(
                    canvas, stepX, top = false,
                    short = palette.rulerContrast, long = mainColor, text = palette.rulerContrast
                )
            }
        }
        if (usesVerticalScale) {
            drawVerticalRuler(canvas, stepY)
        }

        val rect = overlayRect
        if (rect != null && rect.width() > 0f && rect.height() > 0f) {
            val contrast = contrastOn(mainColor)
            val save = canvas.save()
            canvas.clipRect(rect)
            if (usesTopScale) {
                drawHorizontalScale(canvas, stepX, top = true, short = contrast, long = contrast, text = contrast)
                if (usesBottomScale) {
                    drawHorizontalScale(canvas, stepX, top = false, short = contrast, long = contrast, text = contrast)
                }
            }
            if (usesVerticalScale) {
                drawVerticalScale(canvas, stepY, short = contrast, long = contrast, text = contrast)
            }
            canvas.restoreToCount(save)
        }
    }

    private val usesBottomScale: Boolean
        get() = !portrait && mode != MeasureMode.FOUR_POINT

    private val usesTopScale: Boolean
        get() = !portrait

    private val usesVerticalScale: Boolean
        get() = portrait || mode == MeasureMode.FOUR_POINT

    private fun drawVerticalRuler(canvas: Canvas, step: Float) {
        val ink = topScaleColor
        if (!portrait || ink == null || panelHeight <= 0f) {
            drawVerticalScale(
                canvas, step,
                short = palette.rulerContrast, long = mainColor, text = palette.rulerContrast
            )
            return
        }
        val split = panelHeight.coerceAtMost(height.toFloat())
        val panel = canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), split)
        drawVerticalScale(canvas, step, short = ink, long = ink, text = ink)
        canvas.restoreToCount(panel)
        val below = canvas.save()
        canvas.clipRect(0f, split, width.toFloat(), height.toFloat())
        drawVerticalScale(
            canvas, step,
            short = palette.rulerContrast, long = mainColor, text = palette.rulerContrast
        )
        canvas.restoreToCount(below)
    }

    private val originX: Float
        get() = if (indentFromEdge) indent else 0f

    private fun drawHorizontalScale(
        canvas: Canvas,
        step: Float,
        top: Boolean,
        short: Int,
        long: Int,
        text: Int
    ) {
        if (step < 1f) return
        shortPaint.color = short
        longPaint.color = long
        textPaint.color = text

        val perLabel = Scale.ticksPerLabel(unit)
        val labelStep = Scale.labelStep(unit)
        val horizontalLabelsVisible = labelTextHeight + longTick + labelGap < height
        val baseline =
            if (top) longTick + labelTextHeight + labelGap
            else height - (longTick + labelGap)

        val count = (width / step).toInt() + 1
        for (i in 0..count) {
            val x = originX + i * step
            if (x > width) break
            val isLabel = i % perLabel == 0
            val isMid = perLabel > 1 && i % (perLabel / 2) == 0
            val length = when {
                isLabel -> longTick
                isMid -> midTick
                else -> shortTick
            }
            // Ticks never stick out of the view.
            val visible = minOf(length, x, width - x)
            val paint = if (isLabel) longPaint else shortPaint
            if (top) {
                canvas.drawLine(x, 0f, x, visible, paint)
            } else {
                canvas.drawLine(x, height.toFloat(), x, height - visible, paint)
            }
            if (isLabel && horizontalLabelsVisible) {
                // Do not collide with the labels of the vertical scale.
                if (!usesVerticalScale || labelColumnX() < x) {
                    canvas.drawText(((i / perLabel) * labelStep).toString(), x, baseline, textPaint)
                }
            }
        }
    }

    private fun drawVerticalScale(
        canvas: Canvas,
        step: Float,
        short: Int,
        long: Int,
        text: Int
    ) {
        if (step < 1f) return
        shortPaint.color = short
        longPaint.color = long
        textPaint.color = text
        textPaint.textAlign = Paint.Align.LEFT

        val perLabel = Scale.ticksPerLabel(unit)
        val labelStep = Scale.labelStep(unit)
        val count = (height / step).toInt() + 1
        val labelX = originX + midTick + labelGap
        for (i in 0..count) {
            val y = i * step
            if (y > height) break
            val isLabel = i % perLabel == 0
            val isMid = perLabel > 1 && i % (perLabel / 2) == 0
            val length = when {
                isLabel -> longTick
                isMid -> midTick
                else -> shortTick
            }
            val visible = minOf(length, y, height - y)
            val paint = if (isLabel) longPaint else shortPaint
            canvas.drawLine(originX, y, originX + visible, y, paint)
            if (isLabel) {
                val label = ((i / perLabel) * labelStep).toString()
                if (portrait) {
                    // Portrait: the numbers run down the long edge, the way the calibration
                    // ruler prints them, so the scale keeps one drawing routine.
                    val advance = textPaint.measureText(label)
                    val start = (y - advance / 2f).coerceAtLeast(0f)
                    val save = canvas.save()
                    canvas.rotate(90f, labelX, start)
                    canvas.drawText(label, labelX, start, textPaint)
                    canvas.restoreToCount(save)
                } else {
                    canvas.drawText(label, labelX, y + labelTextHeight / 2f, textPaint)
                }
            }
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun labelColumnX(): Float = originX + midTick + labelGap

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
