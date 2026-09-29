package org.openruler.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color
import org.openruler.app.core.EdgeToEdge
import org.openruler.app.core.withAlpha

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_about)

        EdgeToEdge.fitSystemBars(this, findViewById(R.id.appBar), findViewById(R.id.aboutContent))

        findViewById<TextView>(R.id.appBarTitle).setText(R.string.information)
        findViewById<ImageView>(R.id.buttonBack).apply {
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            setOnClickListener { finish() }
        }

        findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)

        findViewById<Button>(R.id.buttonSource).apply {
            // Tonal button: a wash of the accent with accent text, which stays readable
            // in both palettes.
            backgroundTintList = ColorStateList.valueOf(withAlpha(palette.accent, 0x2E))
            setTextColor(palette.accent)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))
                runCatching { startActivity(intent) }
            }
        }
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/MaximeMET/ruler"
    }
}
