package org.openruler.app.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Physical orientation the measuring screens are pinned to.
 *
 * The phone is often used flat on a table, where the orientation sensor cannot tell which
 * way up it lies, so the orientation is fixed instead of following the sensor. Portrait is
 * the default; [reverse] flips the screen by 180 degrees for the same physical position.
 */
enum class DisplayOrientation(val id: String, val portrait: Boolean, val reverse: Boolean) {
    PORTRAIT("portrait", portrait = true, reverse = false),
    REVERSE_PORTRAIT("reverse_portrait", portrait = true, reverse = true),
    LANDSCAPE("landscape", portrait = false, reverse = false),
    REVERSE_LANDSCAPE("reverse_landscape", portrait = false, reverse = true);

    /** Portrait to landscape (or back), keeping the 180 degree flip. */
    fun toggled(): DisplayOrientation = when (this) {
        PORTRAIT, REVERSE_PORTRAIT -> if (reverse) REVERSE_LANDSCAPE else LANDSCAPE
        LANDSCAPE, REVERSE_LANDSCAPE -> if (reverse) REVERSE_PORTRAIT else PORTRAIT
    }

    companion object {
        fun fromId(id: String?): DisplayOrientation = entries.firstOrNull { it.id == id } ?: PORTRAIT
    }
}

/**
 * Thin wrapper around [SharedPreferences]; one instance per process is enough because
 * every activity only ever reads or writes single primitive values.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("org.openruler.app.settings", Context.MODE_PRIVATE)

    var unit: LengthUnit
        get() = LengthUnit.fromId(sp.getString(KEY_UNIT, null))
        set(value) = sp.edit().putString(KEY_UNIT, value.id).apply()

    /** BCP-47 language tag, or [Locales.SYSTEM] to follow the system language. */
    var language: String
        get() = sp.getString(KEY_LANGUAGE, Locales.SYSTEM) ?: Locales.SYSTEM
        set(value) = sp.edit().putString(KEY_LANGUAGE, value).apply()

    var darkTheme: Boolean
        get() = sp.getBoolean(KEY_DARK, true)
        set(value) = sp.edit().putBoolean(KEY_DARK, value).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_ON, false)
        set(value) = sp.edit().putBoolean(KEY_KEEP_ON, value).apply()

    var edgePadding: Boolean
        get() = sp.getBoolean(KEY_PADDING, false)
        set(value) = sp.edit().putBoolean(KEY_PADDING, value).apply()

    var orientation: DisplayOrientation
        get() = DisplayOrientation.fromId(sp.getString(KEY_ORIENTATION, null))
        set(value) = sp.edit().putString(KEY_ORIENTATION, value.id).apply()

    /** Multiplier applied to every tick; 1.0 means "trust the reported pixel density". */
    var calibration: Float
        get() = sp.getFloat(KEY_CALIBRATION, DEFAULT_CALIBRATION)
        set(value) = sp.edit().putFloat(KEY_CALIBRATION, value.coerceIn(MIN_CALIBRATION, MAX_CALIBRATION)).apply()

    fun resetCalibration() {
        calibration = DEFAULT_CALIBRATION
    }

    companion object {
        private const val KEY_UNIT = "preference_unit_measurement"
        private const val KEY_LANGUAGE = "preference_language"
        private const val KEY_DARK = "preference_dark_mode"
        private const val KEY_KEEP_ON = "preference_keep_screen_on"
        private const val KEY_PADDING = "preference_ruler_padding"
        private const val KEY_ORIENTATION = "preference_orientation"
        private const val KEY_CALIBRATION = "preference_ruler_calibration"

        const val DEFAULT_CALIBRATION = 1.0f
        const val MIN_CALIBRATION = 0.4f
        const val MAX_CALIBRATION = 2.0f

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
