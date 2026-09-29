package org.openruler.app.core

import android.graphics.Color

/**
 * Colours of one theme. The ruler is drawn on top of a coloured panel, so the palette
 * keeps both the "ink" used for the ticks and the background of the tools panel.
 */
data class Palette(
    /** Window background of the measuring screen. */
    val background: Int,
    /** Background of the top panel that holds the unit switch and the mode buttons. */
    val panel: Int,
    /** Short ticks and labels drawn directly on the background. */
    val rulerContrast: Int,
    /** Long ticks. Replaced by the mode colour while measuring. */
    val rulerAccent: Int,
    /** Icons drawn on top of [panel]. */
    val iconOnPanel: Int,
    /** Colour of the idle top scale, which sits inside the tool bar. */
    val idleScale: Int,
    /** Accent used by dialogs, list rows and the settings screen. */
    val accent: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val divider: Int,
    /** Fill of the unselected half of a segmented control. */
    val toggleSurface: Int,
    /** Outline drawn around the selected half of a segmented control. */
    val toggleStroke: Int,
    val modeColors: Map<MeasureMode, Int>,
    val isDark: Boolean
) {

    fun colorOf(mode: MeasureMode): Int = modeColors.getValue(mode)

    companion object {

        val LIGHT = Palette(
            background = Color.parseColor("#F4F6FB"),
            panel = Color.parseColor("#4F46E5"),
            rulerContrast = Color.parseColor("#64748B"),
            rulerAccent = Color.parseColor("#4F46E5"),
            iconOnPanel = Color.WHITE,
            idleScale = Color.WHITE,
            accent = Color.parseColor("#4F46E5"),
            textPrimary = Color.parseColor("#101623"),
            textSecondary = Color.parseColor("#5A6478"),
            divider = Color.parseColor("#1A0B1220"),
            toggleSurface = Color.parseColor("#FFFFFF"),
            toggleStroke = Color.parseColor("#FFFFFF"),
            modeColors = mapOf(
                MeasureMode.ONE_POINT to Color.parseColor("#2563EB"),
                MeasureMode.TWO_POINT to Color.parseColor("#059669"),
                MeasureMode.FOUR_POINT to Color.parseColor("#EA580C"),
                MeasureMode.PROTRACTOR to Color.parseColor("#7C3AED")
            ),
            isDark = false
        )

        val DARK = Palette(
            background = Color.parseColor("#0B0E14"),
            panel = Color.parseColor("#151A24"),
            rulerContrast = Color.parseColor("#8A93A8"),
            rulerAccent = Color.parseColor("#818CF8"),
            iconOnPanel = Color.WHITE,
            idleScale = Color.parseColor("#A5B4FC"),
            accent = Color.parseColor("#818CF8"),
            textPrimary = Color.parseColor("#E6E9F0"),
            textSecondary = Color.parseColor("#97A0B3"),
            divider = Color.parseColor("#262E3D"),
            toggleSurface = Color.parseColor("#232A38"),
            toggleStroke = Color.parseColor("#818CF8"),
            modeColors = mapOf(
                MeasureMode.ONE_POINT to Color.parseColor("#60A5FA"),
                MeasureMode.TWO_POINT to Color.parseColor("#34D399"),
                MeasureMode.FOUR_POINT to Color.parseColor("#FBBF24"),
                MeasureMode.PROTRACTOR to Color.parseColor("#C084FC")
            ),
            isDark = true
        )

        fun of(dark: Boolean): Palette = if (dark) DARK else LIGHT
    }
}

/**
 * Black or white, whichever stays readable on top of [color]; mirrors the contrast rule
 * used by the ruler when it repaints itself inside the measurement rectangle.
 */
fun contrastOn(color: Int): Int {
    val luminance = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
    return if (luminance > 186) Color.BLACK else Color.WHITE
}

/** Scales the brightness of [color], used for the "soft" handle lines. */
fun dim(color: Int, factor: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return Color.HSVToColor(hsv)
}

/** Applies [alpha] (0..255) to [color]. */
fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
    alpha,
    Color.red(color),
    Color.green(color),
    Color.blue(color)
)
