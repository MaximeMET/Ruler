package org.openruler.app.core

/**
 * Length units the ruler can be calibrated for.
 *
 * The screen is used as a measuring tape, so everything is derived from the physical
 * pixel density reported by the device and from the calibration coefficient the user
 * sets on the calibration screen.
 */
enum class LengthUnit(val id: String) {
    CM("cm"),
    MM("mm"),
    INCH("inch");

    companion object {
        fun fromId(id: String?): LengthUnit = entries.firstOrNull { it.id == id } ?: CM
    }
}

/** Measurement tools available on the main screen. */
enum class MeasureMode {
    /**
     * Two movable edges, measures the distance between them. Either edge can be pinned,
     * and a typed length pins both at once, which makes the band a fixed ruler.
     */
    TWO_POINT,

    /** Movable rectangle, reports width, height and area. */
    FOUR_POINT,

    /** Half circle protractor with two arms. */
    PROTRACTOR
}

/**
 * Physical scale of the screen: how many pixels one ruler tick takes.
 *
 * A tick is 1 mm for the metric units and 1/8 inch for the imperial one, which is what
 * makes the tick pattern look like an ordinary carpenter's ruler.
 */
object Scale {

    const val MM_PER_INCH = 25.4f
    const val TICKS_PER_INCH = 8
    const val TICKS_PER_CM = 10

    /** Pixels covered by a single tick, including the user calibration. */
    fun pxPerTick(unit: LengthUnit, dpi: Float, calibration: Float): Float {
        val saneDpi = if (dpi.isFinite() && dpi > 1f) dpi else 160f * 3f
        return when (unit) {
            LengthUnit.CM, LengthUnit.MM -> saneDpi / MM_PER_INCH * calibration
            LengthUnit.INCH -> saneDpi / TICKS_PER_INCH * calibration
        }
    }

    /** How many ticks fit into one labelled step. */
    fun ticksPerLabel(unit: LengthUnit): Int = when (unit) {
        LengthUnit.CM, LengthUnit.MM -> TICKS_PER_CM
        LengthUnit.INCH -> TICKS_PER_INCH
    }

    /** Value printed next to every labelled tick. */
    fun labelStep(unit: LengthUnit): Int = when (unit) {
        LengthUnit.CM -> 1
        LengthUnit.MM -> 10
        LengthUnit.INCH -> 1
    }

    /** Real world value of one tick, in [unit]. */
    fun valuePerTick(unit: LengthUnit): Float = when (unit) {
        LengthUnit.CM -> 0.1f
        LengthUnit.MM -> 1f
        LengthUnit.INCH -> 0.125f
    }

    /** Converts a pixel distance into [unit]. */
    fun pxToUnits(px: Float, unit: LengthUnit, dpi: Float, calibration: Float): Float {
        val step = pxPerTick(unit, dpi, calibration)
        if (step <= 0f) return 0f
        return px / step * valuePerTick(unit)
    }

    /** Converts a value in [unit] into a pixel distance. */
    fun unitsToPx(value: Float, unit: LengthUnit, dpi: Float, calibration: Float): Float {
        val step = pxPerTick(unit, dpi, calibration)
        if (step <= 0f) return 0f
        return value / valuePerTick(unit) * step
    }
}
