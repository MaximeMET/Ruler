package org.openruler.app

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import org.openruler.app.core.DisplayOrientation
import org.openruler.app.core.LengthUnit
import org.openruler.app.core.EdgeToEdge
import org.openruler.app.core.Locales
import java.util.Locale

/**
 * Settings screen. The rows are built in code so that the labels and the current values
 * always stay in sync with the stored preferences.
 */
class SettingsActivity : BaseActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_settings)
        container = findViewById(R.id.settingsContainer)

        EdgeToEdge.fitSystemBars(this, findViewById(R.id.appBar), container)

        findViewById<TextView>(R.id.appBarTitle).setText(R.string.settings)
        findViewById<ImageView>(R.id.buttonBack).apply {
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            setOnClickListener { finish() }
        }
        buildRows()
    }

    /**
     * The rows are always rebuilt from the preferences, but every switch row reuses one
     * layout id, so the framework's instance-state restore hands the same saved value to
     * all of them and overwrites the real state (this happens whenever the theme toggle
     * recreates the activity). Rebuild once it is done.
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        buildRows()
    }

    private fun buildRows() {
        container.removeAllViews()

        addSection(R.string.pref_group_display)
        addValueRow(
            icon = R.drawable.ic_orientation,
            title = R.string.orientation,
            value = orientationLabel(prefs.orientation)
        ) { showOrientationDialog() }

        addSwitchRow(
            icon = R.drawable.ic_screen_on,
            title = R.string.keep_screen_on,
            summary = R.string.keep_screen_on_summary,
            checked = prefs.keepScreenOn
        ) { prefs.keepScreenOn = it }

        addSwitchRow(
            icon = R.drawable.ic_dark_theme,
            title = R.string.use_dark_theme,
            summary = if (prefs.darkTheme) R.string.dark_theme else R.string.light_theme,
            checked = prefs.darkTheme
        ) {
            prefs.darkTheme = it
            recreate()
        }

        addSwitchRow(
            icon = R.drawable.ic_padding,
            title = R.string.padding,
            summary = R.string.padding_summary,
            checked = prefs.edgePadding
        ) { prefs.edgePadding = it }

        addValueRow(
            icon = R.drawable.ic_language,
            title = R.string.language,
            value = Locales.labelOf(prefs.language) ?: getString(R.string.follow_system)
        ) { showLanguageDialog() }

        addSection(R.string.pref_group_measure)
        addValueRow(
            icon = R.drawable.ic_convert,
            title = R.string.measurement,
            value = unitLabel(prefs.unit)
        ) { showUnitDialog() }

        addValueRow(
            icon = R.drawable.ic_calibration,
            title = R.string.calibration,
            value = String.format(Locale.ROOT, "%.2f", prefs.calibration)
        ) {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        addSection(R.string.pref_group_info)
        addValueRow(
            icon = R.drawable.ic_info,
            title = R.string.information,
            value = null
        ) {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun showOrientationDialog() {
        val options = DisplayOrientation.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.orientation)
            .setSingleChoiceItems(
                options.map { orientationLabel(it) }.toTypedArray(),
                options.indexOf(prefs.orientation)
            ) { dialog, which ->
                prefs.orientation = options[which]
                dialog.dismiss()
                buildRows()
            }
            .show()
    }

    private fun orientationLabel(orientation: DisplayOrientation): String = getString(
        when (orientation) {
            DisplayOrientation.PORTRAIT -> R.string.orientation_portrait
            DisplayOrientation.REVERSE_PORTRAIT -> R.string.orientation_portrait_reverse
            DisplayOrientation.LANDSCAPE -> R.string.orientation_landscape
            DisplayOrientation.REVERSE_LANDSCAPE -> R.string.orientation_landscape_reverse
        }
    )

    private fun showUnitDialog() {
        val units = arrayOf(LengthUnit.INCH, LengthUnit.CM, LengthUnit.MM)
        val labels = units.map { unitLabel(it) }.toTypedArray()
        val selected = units.indexOf(prefs.unit).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.measurement)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                prefs.unit = units[which]
                dialog.dismiss()
                buildRows()
            }
            .show()
    }

    /**
     * Language picker: the first entry keeps following the system language, the rest pin
     * one of the translations that ship with the app. Each entry is labelled in its own
     * language so it can be found without reading the current one.
     */
    private fun showLanguageDialog() {
        val tags = listOf(Locales.SYSTEM) + Locales.choices.map { it.tag }
        val labels = listOf(getString(R.string.follow_system)) + Locales.choices.map { it.label }
        val selected = tags.indexOf(prefs.language).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                if (tags[which] != prefs.language) {
                    Locales.apply(this, tags[which])
                    recreate()
                }
            }
            .show()
    }

    private fun unitLabel(unit: LengthUnit): String = when (unit) {
        LengthUnit.CM -> getString(R.string.unit_cm)
        LengthUnit.MM -> getString(R.string.unit_mm)
        LengthUnit.INCH -> getString(R.string.unit_inch)
    }

    // region row helpers

    private fun addSection(title: Int) {
        val view = LayoutInflater.from(this).inflate(R.layout.row_section, container, false) as TextView
        view.setText(title)
        container.addView(view)
    }

    private fun addSwitchRow(
        icon: Int,
        title: Int,
        summary: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.row_setting, container, false)
        row.findViewById<ImageView>(R.id.rowIcon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(palette.accent)
        }
        row.findViewById<TextView>(R.id.rowTitle).setText(title)
        row.findViewById<TextView>(R.id.rowSummary).setText(summary)
        row.findViewById<TextView>(R.id.rowSummary).visibility = View.VISIBLE
        row.findViewById<ImageView>(R.id.rowChevron).visibility = View.GONE
        val switch = row.findViewById<Switch>(R.id.rowSwitch)
        switch.visibility = View.VISIBLE
        // Every row reuses the same id, so the framework would restore one row's saved
        // state into all of them after a recreation (the theme switch calls recreate()).
        // The rows are always rebuilt from the preferences, so keep them out of it.
        switch.isSaveEnabled = false
        row.setOnClickListener {
            val newValue = !switch.isChecked
            switch.isChecked = newValue
            onChange(newValue)
        }
        container.addView(row)
        // Set the state once the view is attached: framework switches ignore a checked
        // state that is assigned while the row is still detached.
        switch.jumpDrawablesToCurrentState()
        switch.isChecked = checked
        switch.jumpDrawablesToCurrentState()
    }

    private fun addValueRow(
        icon: Int,
        title: Int,
        value: String?,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.row_setting, container, false)
        row.findViewById<ImageView>(R.id.rowIcon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(palette.accent)
        }
        row.findViewById<TextView>(R.id.rowTitle).setText(title)
        val summary = row.findViewById<TextView>(R.id.rowSummary)
        if (value != null) {
            summary.text = value
            summary.visibility = View.VISIBLE
        } else {
            summary.visibility = View.GONE
        }
        row.setOnClickListener { onClick() }
        container.addView(row)
    }

    // endregion
}
