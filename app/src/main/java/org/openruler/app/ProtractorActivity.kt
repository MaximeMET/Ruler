package org.openruler.app

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import org.openruler.app.view.ProtractorView

/**
 * Standalone protractor, useful as a home screen shortcut.
 */
class ProtractorActivity : BaseActivity() {

    override fun themeRes(): Int =
        if (prefs.darkTheme) R.style.Theme_OpenRuler_Dark_Fullscreen
        else R.style.Theme_OpenRuler_Fullscreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_protractor)
        val protractor = findViewById<ProtractorView>(R.id.protractorView)
        protractor.palette = palette
        protractor.start()
        hideSystemBars()
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
}
