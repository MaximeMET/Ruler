package org.openruler.app.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * The small padlock chip shared by the two point band and the protractor arms.
 *
 * A disc in the canvas colour carries the padlock: a filled body with a closed shackle means
 * the end is pinned, an outlined body with a lifted shackle means it can still be dragged.
 * All metrics are proportional to the radius, so both views draw the same chip.
 */
object LockChip {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val shackle = RectF()

    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        locked: Boolean,
        color: Int,
        background: Int
    ) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = background
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = radius * 0.1f
        fillPaint.color = color
        canvas.drawCircle(cx, cy, radius, fillPaint)

        strokePaint.color = color
        strokePaint.strokeWidth = radius * 0.117f
        strokePaint.style = if (locked) Paint.Style.FILL else Paint.Style.STROKE
        val bodyWidth = radius * 0.75f
        val bodyHeight = radius * 0.58f
        val bodyTop = cy - radius * 0.042f
        body.set(cx - bodyWidth / 2f, bodyTop, cx + bodyWidth / 2f, bodyTop + bodyHeight)
        canvas.drawRoundRect(body, radius * 0.125f, radius * 0.125f, strokePaint)

        strokePaint.style = Paint.Style.STROKE
        val shackleR = radius * 0.27f
        // A pinned end gets the closed shackle; a free one has it lifted off the body.
        val lift = if (locked) 0f else radius * 0.117f
        shackle.set(cx - shackleR, bodyTop - shackleR - lift, cx + shackleR, bodyTop + shackleR - lift)
        canvas.drawArc(shackle, 180f, 180f, false, strokePaint)
        if (locked) {
            canvas.drawLine(cx - shackleR, shackle.centerY(), cx - shackleR, bodyTop, strokePaint)
            canvas.drawLine(cx + shackleR, shackle.centerY(), cx + shackleR, bodyTop, strokePaint)
        }
    }
}
