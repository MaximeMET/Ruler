package org.openruler.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import org.openruler.app.core.DisplayOrientation
import org.openruler.app.core.MeasureMode
import org.openruler.app.core.dim
import org.openruler.app.view.MeasureView
import org.openruler.app.view.ProtractorView
import org.openruler.app.view.RulerView
import org.openruler.app.view.UnitToggleView

/**
 * The measuring screen: a full screen ruler with the tool bar on top.
 */
class MainActivity : BaseActivity() {

    private lateinit var ruler: RulerView
    private lateinit var measure: MeasureView
    private lateinit var protractor: ProtractorView
    private lateinit var toolBar: View
    private lateinit var modeRow: LinearLayout
    private lateinit var extraRow: LinearLayout
    private lateinit var unitToggle: UnitToggleView
    private lateinit var closeButton: ImageView
    private lateinit var orientationButton: ImageView
    private lateinit var modeButtons: Map<MeasureMode, ImageView>
    private lateinit var root: View

    private var measuring = false
    private var mode = MeasureMode.ONE_POINT

    override fun themeRes(): Int =
        if (prefs.darkTheme) R.style.Theme_OpenRuler_Dark_Fullscreen
        else R.style.Theme_OpenRuler_Fullscreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        ruler = findViewById(R.id.rulerView)
        measure = findViewById(R.id.measureView)
        protractor = findViewById(R.id.protractorView)
        toolBar = findViewById(R.id.toolBar)
        modeRow = findViewById(R.id.modeRow)
        extraRow = findViewById(R.id.extraRow)
        unitToggle = findViewById(R.id.unitToggle)
        closeButton = findViewById(R.id.buttonClose)
        orientationButton = findViewById(R.id.buttonOrientation)

        modeButtons = mapOf(
            MeasureMode.ONE_POINT to findViewById(R.id.buttonModeOne),
            MeasureMode.TWO_POINT to findViewById(R.id.buttonModeTwo),
            MeasureMode.FOUR_POINT to findViewById(R.id.buttonModeFour),
            MeasureMode.PROTRACTOR to findViewById(R.id.buttonProtractor)
        )

        applyOrientation()
        applyKeepScreenOn()
        hideSystemBars()
        applyPalette()

        unitToggle.onUnitSelected = { unit ->
            prefs.unit = unit
            ruler.unit = unit
            measure.unit = unit
        }
        modeButtons.forEach { (tool, button) ->
            button.setOnClickListener { startMeasuring(tool) }
        }
        findViewById<View>(R.id.buttonCalibration).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        findViewById<View>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        orientationButton.setOnClickListener { toggleOrientation() }
        closeButton.setOnClickListener { stopMeasuring() }

        measure.onRectChanged = { rect -> ruler.overlayRect = rect }

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_MEASURING, false)) {
            val saved = MeasureMode.valueOf(
                savedInstanceState.getString(STATE_MODE, MeasureMode.ONE_POINT.name)
                    ?: MeasureMode.ONE_POINT.name
            )
            startMeasuring(saved)
            if (saved == MeasureMode.PROTRACTOR) {
                protractor.restore(
                    savedInstanceState.getFloat(STATE_ARM_A, 45f),
                    savedInstanceState.getFloat(STATE_ARM_B, 135f)
                )
            } else {
                val xs = savedInstanceState.getFloatArray(STATE_CORNERS_X)
                val ys = savedInstanceState.getFloatArray(STATE_CORNERS_Y)
                if (xs != null && ys != null && xs.size == 2 && ys.size == 2) {
                    measure.restore(
                        listOf(
                            android.graphics.PointF(xs[0], ys[0]),
                            android.graphics.PointF(xs[1], ys[1])
                        )
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed while this activity was in the background.
        applyOrientation()
        applyKeepScreenOn()
        hideSystemBars()
        if (refreshPalette()) {
            applyPalette()
            recreateTheme()
            return
        }
        applyPalette()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
        hideSystemBars()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_MEASURING, measuring)
        outState.putString(STATE_MODE, mode.name)
        if (measuring && mode == MeasureMode.PROTRACTOR) {
            val arms = protractor.arms()
            outState.putFloat(STATE_ARM_A, arms[0])
            outState.putFloat(STATE_ARM_B, arms[1])
        } else if (measuring) {
            val points = measure.cornerPoints()
            outState.putFloatArray(STATE_CORNERS_X, floatArrayOf(points[0].x, points[1].x))
            outState.putFloatArray(STATE_CORNERS_Y, floatArrayOf(points[0].y, points[1].y))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (measuring) stopMeasuring() else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // region measuring

    private fun startMeasuring(tool: MeasureMode) {
        mode = tool
        measuring = true
        val color = palette.accent
        ruler.mode = tool
        ruler.mainColor = color
        ruler.topScaleColor = null

        when (tool) {
            MeasureMode.PROTRACTOR -> {
                measure.visibility = View.GONE
                protractor.visibility = View.VISIBLE
                ruler.visibility = View.INVISIBLE
                protractor.start()
                ruler.overlayRect = null
            }

            else -> {
                protractor.visibility = View.GONE
                ruler.visibility = View.VISIBLE
                measure.visibility = View.VISIBLE
                measure.unit = prefs.unit
                measure.calibration = prefs.calibration
                measure.indentFromEdge = prefs.edgePadding
                measure.start(tool)
                ruler.overlayRect = measure.rect
            }
        }
        styleCloseButton(color)
        setToolBarVisible(false)
        closeButton.visibility = View.VISIBLE
    }

    private fun stopMeasuring() {
        measuring = false
        measure.stop()
        measure.visibility = View.GONE
        protractor.visibility = View.GONE
        ruler.visibility = View.VISIBLE
        ruler.mode = MeasureMode.ONE_POINT
        ruler.mainColor = palette.rulerAccent
        ruler.topScaleColor = palette.idleScale
        ruler.overlayRect = null
        closeButton.visibility = View.GONE
        setToolBarVisible(true)
    }

    private fun styleCloseButton(color: Int) {
        closeButton.background?.mutate()?.setTint(dim(color, 0.85f))
        closeButton.setColorFilter(Color.WHITE)
    }

    private fun setToolBarVisible(visible: Boolean) {
        toolBar.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    // endregion

    // region theme and window

    private fun applyPalette() {
        root.setBackgroundColor(palette.background)
        toolBar.setBackgroundColor(palette.panel)
        unitToggle.palette = palette
        unitToggle.unit = prefs.unit

        ruler.palette = palette
        ruler.unit = prefs.unit
        ruler.calibration = prefs.calibration
        ruler.indentFromEdge = prefs.edgePadding
        if (!measuring) {
            ruler.mainColor = palette.rulerAccent
        }

        measure.palette = palette
        measure.unit = prefs.unit
        measure.calibration = prefs.calibration
        measure.indentFromEdge = prefs.edgePadding

        protractor.palette = palette

        val iconTint = ColorStateList.valueOf(palette.iconOnPanel)
        modeButtons.values.forEach { it.imageTintList = iconTint }
        findViewById<ImageView>(R.id.buttonCalibration).imageTintList = iconTint
        orientationButton.imageTintList = iconTint
        findViewById<ImageView>(R.id.buttonSettings).imageTintList = iconTint
        if (measuring) styleCloseButton(palette.accent)
        if (measuring) ruler.topScaleColor = null else ruler.topScaleColor = palette.idleScale
    }

    private fun recreateTheme() {
        recreate()
    }

    /** The toolbar button: keep the 180 degree flip, swap portrait and landscape. */
    private fun toggleOrientation() {
        prefs.orientation = prefs.orientation.toggled()
        applyOrientation()
    }

    private fun applyOrientation() {
        val orientation = prefs.orientation
        applyOrientationLayout(orientation.portrait)
        val requested = when (orientation) {
            DisplayOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DisplayOrientation.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            DisplayOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            DisplayOrientation.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
        if (requestedOrientation == requested) return
        requestedOrientation = requested
    }

    /**
     * Everything is sized in dp, so the two orientations share one layout: only the panel
     * height, the place of the unit switch and the arrangement of the tool buttons change.
     * In portrait the controls keep to the right of the left hand ruler.
     */
    private fun applyOrientationLayout(portrait: Boolean) {
        ruler.portrait = portrait
        measure.portrait = portrait

        val panelHeight = dp(if (portrait) 220f else 250f)
        ruler.panelHeight = panelHeight.toFloat()
        if (toolBar.layoutParams.height != panelHeight) {
            toolBar.layoutParams.height = panelHeight
            toolBar.requestLayout()
        }

        // Portrait stacks the two button groups; landscape keeps them in a single row.
        modeRow.orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        modeRow.gravity = if (portrait) Gravity.END else Gravity.CENTER_VERTICAL
        (extraRow.layoutParams as LinearLayout.LayoutParams).topMargin = if (portrait) dp(6f) else 0
        extraRow.requestLayout()

        (unitToggle.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = if (portrait) Gravity.TOP or Gravity.END else Gravity.CENTER
            topMargin = if (portrait) dp(40f) else 0
            // The app never mirrors, so the plain right margin is enough; the relative one
            // is only resolved on the next inflation and would be dropped on a live flip.
            rightMargin = if (portrait) dp(24f) else 0
        }
        unitToggle.requestLayout()
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun applyKeepScreenOn() {
        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    // endregion

    private companion object {
        const val STATE_MEASURING = "measuring"
        const val STATE_MODE = "mode"
        const val STATE_CORNERS_X = "corners_x"
        const val STATE_CORNERS_Y = "corners_y"
        const val STATE_ARM_A = "arm_a"
        const val STATE_ARM_B = "arm_b"
    }
}
