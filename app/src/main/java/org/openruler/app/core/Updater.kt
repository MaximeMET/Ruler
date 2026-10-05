package org.openruler.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The update check, kept deliberately small: one HTTPS GET against the GitHub releases
 * API and one download of the APK asset the release carries. No third party library, no
 * background traffic - the calls only happen when the user asks for an update.
 */
object Updater {

    /** Latest published release of the project. */
    const val RELEASES_API = "https://api.github.com/repos/MaximeMET/ruler/releases/latest"

    private const val USER_AGENT = "Ruler-Android"

    data class Release(
        val version: String,
        val apkUrl: String,
        val apkApiUrl: String,
        val apkSize: Long,
        val pageUrl: String
    )

    /** `true` when [remote] is a higher dotted version than [current]. */
    fun isNewer(remote: String, current: String): Boolean {
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().trimStart('v', 'V')
            .split('.', '-', '+', '_')
            .mapNotNull { piece -> piece.takeWhile { it.isDigit() }.toIntOrNull() }

    /** Fetches the latest release; throws [IOException] when GitHub cannot be reached. */
    fun fetchLatest(): Release {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP " + connection.responseCode)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl = ""
            var apkApiUrl = ""
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkApiUrl = asset.optString("url")
                    apkSize = asset.optLong("size")
                    break
                }
            }
            return Release(
                version = json.optString("tag_name").trimStart('v', 'V'),
                apkUrl = apkUrl,
                apkApiUrl = apkApiUrl,
                apkSize = apkSize,
                pageUrl = json.optString("html_url")
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads one of [urls] into [target], reporting whole percent steps through
     * [onProgress]. The URLs are tried in order: the asset endpoint on api.github.com
     * first, because that host is the one the update check already proved reachable,
     * with the plain download page as the fallback.
     */
    fun download(urls: List<String>, target: File, onProgress: (Int) -> Unit) {
        var last: IOException? = null
        for (url in urls) {
            try {
                downloadOne(url, target, onProgress)
                return
            } catch (error: IOException) {
                last = error
                target.delete()
            }
        }
        throw last ?: IOException("no download url")
    }

    private fun downloadOne(url: String, target: File, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            // The api.github.com asset endpoint answers with the binary itself only when
            // the request asks for it; the browser download URL ignores the header.
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP " + connection.responseCode)
            }
            val total = connection.contentLength.toLong()
            val parent = target.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = if (total > 0) ((copied * 100) / total).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
