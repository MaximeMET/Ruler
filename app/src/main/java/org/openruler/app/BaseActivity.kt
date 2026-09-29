package org.openruler.app

import android.app.Activity
import android.os.Bundle
import org.openruler.app.core.Palette
import org.openruler.app.core.Prefs

/**
 * Applies the light or dark theme before the content view is created, and exposes the
 * current palette to the subclasses.
 */
abstract class BaseActivity : Activity() {

    protected lateinit var prefs: Prefs
    protected lateinit var palette: Palette

    /** Theme to apply; overridden by the measuring screens so they can go full screen. */
    protected open fun themeRes(): Int =
        if (prefs.darkTheme) R.style.Theme_OpenRuler_Dark else R.style.Theme_OpenRuler

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs.get(this)
        palette = Palette.of(prefs.darkTheme)
        setTheme(themeRes())
        super.onCreate(savedInstanceState)
    }

    /** Re-reads the palette, for activities that can change the theme at runtime. */
    protected fun refreshPalette(): Boolean {
        val newPalette = Palette.of(prefs.darkTheme)
        val changed = newPalette != palette
        palette = newPalette
        return changed
    }
}
