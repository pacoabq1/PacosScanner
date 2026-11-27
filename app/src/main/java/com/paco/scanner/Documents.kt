package com.paco.scanner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Saving, opening and sharing scanned PDFs. */
internal object Documents {
    /** Top folder on the phone that holds every scan, visible in My Files under Documents. */
    const val PHONE_FOLDER = "PacosScanner"

    /**
     * Copies a finished PDF into Documents/PacosScanner/<folder> on the phone, where the
     * phone's file manager (My Files) can see it and it survives uninstalling the app.
     * Returns the folder it was saved in, for showing to the user, or null if it failed.
     */
    fun saveToPhone(context: Context, pdf: File, folder: List<String>): String? {
        val relative = (listOf(Environment.DIRECTORY_DOCUMENTS, PHONE_FOLDER) + folder).joinToString("/")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, pdf.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
                resolver.openOutputStream(uri)?.use { out -> pdf.inputStream().use { it.copyTo(out) } } ?: return null
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStorageDirectory(), relative).apply { mkdirs() }
                pdf.copyTo(File(dir, pdf.name), overwrite = true)
            }
            relative
        } catch (e: Exception) {
            null
        }
    }

    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Opens a PDF in the phone's PDF viewer. */
    fun open(activity: Activity, file: File) {
        if (!file.exists()) {
            Toast.makeText(activity, "That document is no longer here.", Toast.LENGTH_LONG).show()
            return
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(activity, file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "No PDF viewer found on this phone.", Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the share sheet (text, email, fax apps and so on) for a PDF. */
    fun share(activity: Activity, file: File) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uriFor(activity, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, "Email, fax, or share PDF"))
    }
}
