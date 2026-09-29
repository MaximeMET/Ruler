package org.openruler.app.core

import android.content.Context
import android.content.SharedPreferences

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

    var darkTheme: Boolean
        get() = sp.getBoolean(KEY_DARK, true)
        set(value) = sp.edit().putBoolean(KEY_DARK, value).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_ON, false)
        set(value) = sp.edit().putBoolean(KEY_KEEP_ON, value).apply()

    var edgePadding: Boolean
        get() = sp.getBoolean(KEY_PADDING, false)
        set(value) = sp.edit().putBoolean(KEY_PADDING, value).apply()

    /** `false` = landscape, `true` = reverse landscape. */
    var reverseOrientation: Boolean
        get() = sp.getBoolean(KEY_REVERSE, false)
        set(value) = sp.edit().putBoolean(KEY_REVERSE, value).apply()

    /** Multiplier applied to every tick; 1.0 means "trust the reported pixel density". */
    var calibration: Float
        get() = sp.getFloat(KEY_CALIBRATION, DEFAULT_CALIBRATION)
        set(value) = sp.edit().putFloat(KEY_CALIBRATION, value.coerceIn(MIN_CALIBRATION, MAX_CALIBRATION)).apply()

    fun resetCalibration() {
        calibration = DEFAULT_CALIBRATION
    }

    companion object {
        private const val KEY_UNIT = "preference_unit_measurement"
        private const val KEY_DARK = "preference_dark_mode"
        private const val KEY_KEEP_ON = "preference_keep_screen_on"
        private const val KEY_PADDING = "preference_ruler_padding"
        private const val KEY_REVERSE = "preference_orientation_reverse"
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
