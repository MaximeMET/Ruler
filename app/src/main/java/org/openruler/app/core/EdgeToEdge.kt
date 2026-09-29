package org.openruler.app.core

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets

/**
 * Android 15 draws every app edge to edge, so the platform no longer keeps the app bar
 * clear of the status bar. The measuring screens want the full canvas and hide the bars
 * themselves; the settings style screens want the system bar heights back as padding.
 *
 * [enable] opts the window in and has to run before the content view exists; [fitSystemBars]
 * turns the reported insets into padding. The app bar grows by the status bar height and
 * pushes its content down, so its background covers the status bar area and the buttons stay
 * reachable. Bottom anchored content is pushed above the navigation bar, by its bottom margin
 * when it has one, by padding if not.
 *
 * Releases before API 30 still inset the window themselves, so there is nothing to do.
 */
object EdgeToEdge {

    fun enable(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        activity.window.setDecorFitsSystemWindows(false)
    }

    fun fitSystemBars(activity: Activity, appBar: View, bottomContent: View? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        enable(activity)

        // Base values, captured once: this listener runs again whenever the bars change
        // size, and every pass has to start from the layout's own numbers.
        val appBarHeight = appBar.layoutParams.height
        val paddingLeft = appBar.paddingLeft
        val paddingTop = appBar.paddingTop
        val paddingRight = appBar.paddingRight
        val paddingBottom = appBar.paddingBottom
        val bottomPadding = bottomContent?.paddingBottom ?: 0
        val bottomMargin = (bottomContent?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin

        appBar.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())

            view.layoutParams = view.layoutParams.apply { height = appBarHeight + bars.top }
            view.setPadding(paddingLeft, paddingTop + bars.top, paddingRight, paddingBottom)

            bottomContent?.let { content ->
                if (bottomMargin != null) {
                    val params = content.layoutParams as ViewGroup.MarginLayoutParams
                    params.bottomMargin = bottomMargin + bars.bottom
                    content.layoutParams = params
                } else {
                    content.setPadding(
                        content.paddingLeft,
                        content.paddingTop,
                        content.paddingRight,
                        bottomPadding + bars.bottom
                    )
                }
            }

            insets
        }
        appBar.requestApplyInsets()
    }
}
