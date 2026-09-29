package org.openruler.app

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.openruler.app.view.CalibrationRulerView

/**
 * Calibration screen: align a bank card with the drawn outline and adjust the coefficient
 * until the ticks match the card.
 */
class CalibrationActivity : BaseActivity() {

    private lateinit var rulerView: CalibrationRulerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        rulerView = findViewById(R.id.calibrationView)
        rulerView.palette = palette
        rulerView.unit = prefs.unit
        rulerView.setCoefficient(prefs.calibration)

        findViewById<TextView>(R.id.appBarTitle).setText(R.string.calibration)

        val reset = findViewById<ImageView>(R.id.buttonReset)
        val save = findViewById<ImageView>(R.id.buttonSave)
        reset.visibility = android.view.View.VISIBLE
        save.visibility = android.view.View.VISIBLE
        reset.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        save.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        findViewById<ImageView>(R.id.buttonBack).imageTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)

        findViewById<ImageView>(R.id.buttonBack).setOnClickListener { finish() }
        reset.setOnClickListener {
            rulerView.reset()
            Toast.makeText(this, R.string.reset, Toast.LENGTH_SHORT).show()
        }
        save.setOnClickListener {
            prefs.calibration = rulerView.coefficient
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.buttonMinus).setOnClickListener { rulerView.nudge(-STEP) }
        findViewById<ImageView>(R.id.buttonPlus).setOnClickListener { rulerView.nudge(STEP) }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen also keeps the value: a calibration that is lost by pressing
        // "back" would force the user to redo the whole alignment.
        if (isFinishing) prefs.calibration = rulerView.coefficient
    }

    private companion object {
        const val STEP = 0.005f
    }
}
