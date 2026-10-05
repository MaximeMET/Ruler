package org.openruler.app.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the one downloaded update file to the system package installer.
 *
 * The file lives in the app cache, so no storage permission is needed; a private content
 * provider is the modern way to share it, because plain `file://` URLs cannot leave the
 * app since Android 7. The provider serves exactly one fixed file and is not exported.
 */
class ApkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = updateFile(context ?: throw FileNotFoundException(uri.toString()))
        if (!file.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = APK_MIME

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"

        private const val FILE_NAME = "update.apk"

        /** The cache file the updater downloads into and the installer reads from. */
        fun updateFile(context: Context): File = File(context.cacheDir, FILE_NAME)

        fun uriFor(context: Context): Uri =
            Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".updates")
                .appendPath(FILE_NAME)
                .build()
    }
}
