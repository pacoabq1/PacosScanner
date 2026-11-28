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
import android.widget.EditText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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


    /** Folder used until the user picks another one. */
    const val DEFAULT_FOLDER = "Inbox"
    private const val PREFS = "scanner"
    private const val KEY_LAST_FOLDER = "last_folder"

    /** The app's own copy of every saved PDF lives under here, one subfolder per folder. */
    fun docsRoot(context: Context) = File(context.filesDir, "documents")

    /** Turns a typed folder (e.g. "Military/Buyback") into safe folder names. */
    fun cleanFolder(input: String): List<String> = input.split('/', '\\')
        .map { it.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "_").trim() }
        .filter { it.isNotBlank() && it != "." && it != ".." }

    /** Every folder the user has, as paths like "Inbox" or "Military/Buyback", Inbox included. */
    fun folderNames(context: Context): List<String> {
        val root = docsRoot(context)
        val found = root.walkTopDown().filter { it.isDirectory && it != root }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .toMutableSet()
        found.add(DEFAULT_FOLDER)
        return found.sortedBy { it.lowercase(Locale.US) }
    }

    fun lastFolder(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_FOLDER, DEFAULT_FOLDER) ?: DEFAULT_FOLDER

    private fun rememberFolder(context: Context, folder: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_FOLDER, folder).apply()

    /**
     * Shows the user's folders to save into, with the last one used already ticked,
     * and a New folder button for making and naming a new one.
     */
    fun pickFolder(activity: Activity, onPicked: (String) -> Unit) {
        val names = folderNames(activity)
        var selected = names.indexOf(lastFolder(activity)).coerceAtLeast(0)
        android.app.AlertDialog.Builder(activity)
            .setTitle("Save in which folder?")
            .setSingleChoiceItems(names.toTypedArray(), selected) { _, i -> selected = i }
            .setPositiveButton("Next") { _, _ ->
                val folder = names[selected]
                rememberFolder(activity, folder)
                onPicked(folder)
            }
            .setNeutralButton("New folder") { _, _ -> askNewFolder(activity, onPicked) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Asks for a folder name, makes the folder, and hands back its path. */
    fun askNewFolder(activity: Activity, onCreated: (String) -> Unit) {
        val field = EditText(activity).apply {
            isSingleLine = true
            hint = "Folder name, e.g. Receipts"
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(activity).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
        android.app.AlertDialog.Builder(activity)
            .setTitle("New folder")
            .setMessage("Use a slash for a folder inside a folder, like Military/Buyback.")
            .setView(box)
            .setPositiveButton("Create") { _, _ ->
                val segments = cleanFolder(field.text.toString())
                if (segments.isEmpty()) {
                    Toast.makeText(activity, "Type a name for the folder first.", Toast.LENGTH_LONG).show()
                    askNewFolder(activity, onCreated)
                    return@setPositiveButton
                }
                val dir = segments.fold(docsRoot(activity)) { parent, s -> File(parent, s) }
                val existed = dir.isDirectory
                if (!existed && !dir.mkdirs()) {
                    Toast.makeText(activity, "Couldn't make that folder.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val path = segments.joinToString("/")
                if (existed) Toast.makeText(activity, "You already have a folder called $path, so it's using that one.", Toast.LENGTH_LONG).show()
                rememberFolder(activity, path)
                onCreated(path)
            }
            .setNegativeButton("Cancel", null)
            .show()
        field.requestFocus()
    }

    /** A readable default name, e.g. "Scan Nov 27 2026 10.06 AM". */
    fun defaultName(): String = "Scan " + SimpleDateFormat("MMM d yyyy h.mm a", Locale.US).format(Date())

    /** Turns what the user typed into a safe file name (without .pdf). */
    fun cleanName(input: String): String {
        val base = input.trim().removeSuffix(".pdf").removeSuffix(".PDF").trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim('.', ' ')
            .take(100)
        return base.ifBlank { defaultName() }
    }

    /** A file named [base].pdf in [dir], adding (2), (3)... if that name is taken. */
    fun uniqueFile(dir: File, base: String, except: File? = null): File {
        var candidate = File(dir, "$base.pdf")
        var n = 2
        while (candidate.exists() && candidate != except) {
            candidate = File(dir, "$base ($n).pdf"); n++
        }
        return candidate
    }

    /** Asks for a name in a small dialog, prefilled with [initial]. */
    fun askName(activity: Activity, title: String, initial: String, button: String, onName: (String) -> Unit) {
        val field = EditText(activity).apply {
            setText(initial); setSelectAllOnFocus(true); isSingleLine = true
            hint = "Document name"
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(activity).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
        android.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(button) { _, _ -> onName(cleanName(field.text.toString())) }
            .setNegativeButton("Cancel", null)
            .show()
        field.requestFocus()
    }

    /**
     * Renames a saved PDF inside the app and its copy in Documents/PacosScanner on the phone.
     * [folder] is the folder path under the app's documents folder (e.g. ["Inbox"]).
     * Returns the renamed file, or null if the rename failed. [phoneCopyRenamed] reports
     * whether the phone copy was found and renamed too.
     */
    fun rename(context: Context, file: File, folder: List<String>, newBase: String, phoneCopyRenamed: (Boolean) -> Unit = {}): File? {
        val target = uniqueFile(file.parentFile ?: return null, newBase, except = file)
        if (target == file) { phoneCopyRenamed(true); return file }
        val phoneOk = renamePhoneCopy(context, folder, file.name, target.name)
        if (!file.renameTo(target)) return null
        phoneCopyRenamed(phoneOk)
        return target
    }

    /**
     * Deletes a saved PDF from the app and its copy in Documents/PacosScanner on the phone.
     * Returns false if the app's copy couldn't be deleted; [phoneCopyDeleted] reports the phone copy.
     */
    fun delete(context: Context, file: File, folder: List<String>, phoneCopyDeleted: (Boolean) -> Unit = {}): Boolean {
        val phoneOk = deletePhoneCopy(context, folder, file.name)
        val ok = !file.exists() || file.delete()
        phoneCopyDeleted(phoneOk)
        return ok
    }

    private fun deletePhoneCopy(context: Context, folder: List<String>, name: String): Boolean {
        val relative = (listOf(Environment.DIRECTORY_DOCUMENTS, PHONE_FOLDER) + folder).joinToString("/")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
                context.contentResolver.delete(MediaStore.Files.getContentUri("external"), selection, arrayOf("$relative/", name)) > 0
            } else {
                @Suppress("DEPRECATION")
                File(File(Environment.getExternalStorageDirectory(), relative), name).delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun renamePhoneCopy(context: Context, folder: List<String>, oldName: String, newName: String): Boolean {
        val relative = (listOf(Environment.DIRECTORY_DOCUMENTS, PHONE_FOLDER) + folder).joinToString("/")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
                val args = arrayOf("$relative/", oldName)
                val id = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else null
                } ?: return false
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
                resolver.update(uri, values, null, null) > 0
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStorageDirectory(), relative)
                File(dir, oldName).renameTo(File(dir, newName))
            }
        } catch (e: Exception) {
            false
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
