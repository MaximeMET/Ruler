package org.openruler.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.appBarTitle).setText(R.string.information)
        findViewById<ImageView>(R.id.buttonBack).apply {
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            setOnClickListener { finish() }
        }

        findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)

        findViewById<Button>(R.id.buttonSource).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))
            runCatching { startActivity(intent) }
        }
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/"
    }
}
