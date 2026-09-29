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
    /** Accent used by dialogs, list rows and the settings screen. */
    val accent: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val divider: Int,
    val modeColors: Map<MeasureMode, Int>,
    val isDark: Boolean
) {

    fun colorOf(mode: MeasureMode): Int = modeColors.getValue(mode)

    companion object {

        val LIGHT = Palette(
            background = Color.parseColor("#FFFFFF"),
            panel = Color.parseColor("#2196F3"),
            rulerContrast = Color.parseColor("#777777"),
            rulerAccent = Color.parseColor("#2196F3"),
            iconOnPanel = Color.WHITE,
            accent = Color.parseColor("#2196F3"),
            textPrimary = Color.parseColor("#000000"),
            textSecondary = Color.parseColor("#596369"),
            divider = Color.parseColor("#22000000"),
            modeColors = mapOf(
                MeasureMode.ONE_POINT to Color.parseColor("#6089D5"),
                MeasureMode.TWO_POINT to Color.parseColor("#00A18B"),
                MeasureMode.FOUR_POINT to Color.parseColor("#E65100"),
                MeasureMode.PROTRACTOR to Color.parseColor("#283593")
            ),
            isDark = false
        )

        val DARK = Palette(
            background = Color.parseColor("#111111"),
            panel = Color.parseColor("#2994FF"),
            rulerContrast = Color.parseColor("#DDFFFFFF"),
            rulerAccent = Color.parseColor("#F9A825"),
            iconOnPanel = Color.WHITE,
            accent = Color.parseColor("#448AFF"),
            textPrimary = Color.WHITE,
            textSecondary = Color.parseColor("#ABBBC4"),
            divider = Color.parseColor("#33FFFFFF"),
            modeColors = mapOf(
                MeasureMode.ONE_POINT to Color.parseColor("#FFC107"),
                MeasureMode.TWO_POINT to Color.parseColor("#FFEB3B"),
                MeasureMode.FOUR_POINT to Color.parseColor("#B2FF59"),
                MeasureMode.PROTRACTOR to Color.parseColor("#F44336")
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
