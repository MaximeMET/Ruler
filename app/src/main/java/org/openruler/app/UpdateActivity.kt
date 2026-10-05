package org.openruler.app

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import org.openruler.app.core.ApkProvider
import org.openruler.app.core.EdgeToEdge
import org.openruler.app.core.Updater
import org.openruler.app.core.contrastOn
import org.openruler.app.core.withAlpha
import java.io.File
import java.util.Locale

/**
 * The one screen that talks to the network, and only because the user opened it.
 *
 * It asks the GitHub releases API for the latest version, offers the APK for download
 * when it is newer than the installed build, and finally hands the downloaded file to
 * the system package installer. Downloading needs INTERNET; the handover needs
 * REQUEST_INSTALL_PACKAGES plus the one-time "install unknown apps" switch Android
 * keeps per app. Nothing else is requested and nothing runs outside this screen.
 */
class UpdateActivity : BaseActivity() {

    private enum class State {
        CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, CHECK_FAILED, DOWNLOAD_FAILED
    }

    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var buttonPrimary: Button
    private lateinit var buttonSecondary: Button

    private var state = State.CHECKING
    private var release: Updater.Release? = null
    private var percent = 0

    /** True once the user was sent to the "install unknown apps" system screen. */
    private var permissionAsked = false

    /** Bumped by every new background task so stale replies can be dropped. */
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_update)
        EdgeToEdge.fitSystemBars(this, findViewById(R.id.appBar), findViewById(R.id.updateContent))

        findViewById<TextView>(R.id.appBarTitle).setText(R.string.check_updates)
        findViewById<ImageView>(R.id.buttonBack).apply {
            imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
            setOnClickListener { finish() }
        }
        findViewById<ImageView>(R.id.updateIcon).imageTintList =
            ColorStateList.valueOf(palette.accent)

        statusTitle = findViewById(R.id.updateStatusTitle)
        statusDetail = findViewById(R.id.updateStatusDetail)
        progress = findViewById(R.id.updateProgress)
        buttonPrimary = findViewById(R.id.updatePrimary)
        buttonSecondary = findViewById(R.id.updateSecondary)

        progress.progressTintList = ColorStateList.valueOf(palette.accent)
        progress.indeterminateTintList = ColorStateList.valueOf(palette.accent)

        findViewById<TextView>(R.id.updateCurrent).text =
            getString(R.string.update_current, BuildConfig.VERSION_NAME)

        buttonPrimary.apply {
            // The one filled button on the screen: the accent carries the action.
            backgroundTintList = ColorStateList.valueOf(palette.accent)
            setTextColor(contrastOn(palette.accent))
            setOnClickListener { onPrimary() }
        }
        buttonSecondary.apply {
            // Tonal button, same treatment as the source button on the about screen.
            backgroundTintList = ColorStateList.valueOf(withAlpha(palette.accent, 0x2E))
            setTextColor(palette.accent)
            setOnClickListener { openReleasesPage() }
        }

        checkForUpdates()
    }

    /** Re-renders when the user comes back from the system settings screen. */
    override fun onResume() {
        super.onResume()
        if (permissionAsked) render()
    }

    // region update flow

    private fun checkForUpdates() {
        val ticket = ++generation
        release = null
        state = State.CHECKING
        render()
        Thread {
            val result = runCatching { Updater.fetchLatest() }
            runOnUiThread {
                if (ticket != generation || isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = { latest -> onRelease(latest) },
                    onFailure = { error ->
                        Log.w(TAG, "update check failed", error)
                        state = State.CHECK_FAILED
                        render()
                    }
                )
            }
        }.start()
    }

    private fun onRelease(latest: Updater.Release) {
        release = latest
        state = when {
            !Updater.isNewer(latest.version, BuildConfig.VERSION_NAME) -> {
                // Nothing to install; drop whatever an earlier session left in the cache.
                ApkProvider.updateFile(this).delete()
                State.UP_TO_DATE
            }
            cachedUpdate(latest) != null -> State.READY
            else -> State.AVAILABLE
        }
        render()
    }

    private fun downloadUpdate() {
        val latest = release ?: return
        if (latest.apkUrl.isEmpty()) {
            openReleasesPage()
            return
        }
        val ticket = ++generation
        state = State.DOWNLOADING
        percent = 0
        render()
        Thread {
            val target = ApkProvider.updateFile(this)
            runCatching {
                target.delete()
                Updater.download(candidateUrls(latest), target) { value ->
                    runOnUiThread {
                        if (ticket != generation || isFinishing || isDestroyed) return@runOnUiThread
                        percent = value
                        render()
                    }
                }
            }.fold(
                onSuccess = {
                    runOnUiThread {
                        if (ticket != generation || isFinishing || isDestroyed) return@runOnUiThread
                        state = State.READY
                        render()
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "update download failed", error)
                    target.delete()
                    runOnUiThread {
                        if (ticket != generation || isFinishing || isDestroyed) return@runOnUiThread
                        state = State.DOWNLOAD_FAILED
                        render()
                    }
                }
            )
        }.start()
    }

    private fun installUpdate() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        if (needsPermission) {
            permissionAsked = true
            render()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            runCatching { startActivity(intent) }
            return
        }
        if (!ApkProvider.updateFile(this).exists()) {
            state = State.AVAILABLE
            render()
            return
        }
        val installer = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(ApkProvider.uriFor(this@UpdateActivity), ApkProvider.APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(installer) }
    }

    private fun onPrimary() {
        when (state) {
            State.UP_TO_DATE, State.CHECK_FAILED -> checkForUpdates()
            State.AVAILABLE, State.DOWNLOAD_FAILED ->
                if (release?.apkUrl.isNullOrEmpty()) openReleasesPage() else downloadUpdate()
            State.READY -> installUpdate()
            else -> Unit
        }
    }

    /** The cached APK, but only when it really is the file of [latest]. */
    private fun cachedUpdate(latest: Updater.Release): File? {
        val file = ApkProvider.updateFile(this)
        if (!file.exists()) return null
        if (latest.apkSize > 0 && file.length() == latest.apkSize) return file
        file.delete()
        return null
    }

    /** Both ways to the asset, the API host first; see [Updater.download]. */
    private fun candidateUrls(latest: Updater.Release): List<String> = listOfNotNull(
        latest.apkApiUrl.takeIf { it.isNotEmpty() },
        latest.apkUrl.takeIf { it.isNotEmpty() }
    )

    private fun openReleasesPage() {
        val url = release?.pageUrl?.takeIf { it.isNotEmpty() } ?: RELEASES_PAGE
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // endregion

    // region rendering

    private fun render() {
        permissionAsked = permissionAsked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        val latest = release
        val busy = state == State.CHECKING || state == State.DOWNLOADING
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        when (state) {
            State.CHECKING -> {
                progress.isIndeterminate = true
                statusTitle.setText(R.string.update_checking)
                statusDetail.text = ""
                statusDetail.visibility = View.GONE
                buttonPrimary.visibility = View.GONE
                buttonSecondary.visibility = View.GONE
            }
            State.UP_TO_DATE -> {
                statusTitle.setText(R.string.update_none_title)
                detail(getString(R.string.update_none_detail, latest?.version ?: ""))
                primary(R.string.update_retry)
                buttonSecondary.visibility = View.GONE
            }
            State.AVAILABLE -> {
                statusTitle.text = getString(R.string.update_available_title, latest?.version ?: "")
                val size = latest?.apkSize ?: 0L
                detail(
                    when {
                        latest == null || latest.apkUrl.isEmpty() -> getString(R.string.update_no_apk)
                        size > 0 -> getString(R.string.update_available_detail, sizeText(size))
                        else -> ""
                    }
                )
                primary(if (latest?.apkUrl.isNullOrEmpty()) R.string.update_releases else R.string.update_download)
                buttonSecondary.visibility =
                    if (latest?.apkUrl.isNullOrEmpty()) View.GONE else View.VISIBLE
                buttonSecondary.setText(R.string.update_releases)
            }
            State.DOWNLOADING -> {
                progress.isIndeterminate = false
                progress.progress = percent
                statusTitle.text = getString(R.string.update_downloading, percent)
                val size = latest?.apkSize ?: 0L
                detail(if (size > 0) getString(R.string.update_available_detail, sizeText(size)) else "")
                buttonPrimary.visibility = View.GONE
                buttonSecondary.visibility = View.GONE
            }
            State.READY -> {
                statusTitle.setText(R.string.update_ready_title)
                detail(
                    getString(
                        if (permissionAsked) R.string.update_permission_detail
                        else R.string.update_ready_detail
                    )
                )
                primary(R.string.update_install)
                buttonSecondary.visibility = View.GONE
            }
            State.CHECK_FAILED -> {
                statusTitle.setText(R.string.update_failed_title)
                detail(getString(R.string.update_failed_detail))
                primary(R.string.update_retry)
                buttonSecondary.visibility = View.VISIBLE
                buttonSecondary.setText(R.string.update_releases)
            }
            State.DOWNLOAD_FAILED -> {
                statusTitle.setText(R.string.update_download_failed_title)
                detail(getString(R.string.update_failed_detail))
                primary(R.string.update_retry)
                buttonSecondary.visibility = View.VISIBLE
                buttonSecondary.setText(R.string.update_releases)
            }
        }
    }

    private fun primary(text: Int) {
        buttonPrimary.visibility = View.VISIBLE
        buttonPrimary.setText(text)
    }

    private fun detail(text: String) {
        statusDetail.text = text
        statusDetail.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun sizeText(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    // endregion

    private companion object {
        const val TAG = "RulerUpdate"
        const val RELEASES_PAGE = "https://github.com/MaximeMET/Ruler/releases"
    }
}
