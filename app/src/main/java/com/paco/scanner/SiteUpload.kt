package com.paco.scanner

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sends saved PDFs to the upload service on Mark's site (site-worker/ in this repo), which
 * keeps them in Cloudflare R2 behind Cloudflare Access.
 *
 * The Access service token (Client ID and Client Secret) is typed in once on the phone and
 * kept in this app's private storage, never in the code or the APK, and is left out of
 * phone backups (see res/xml/backup_rules.xml and data_extraction_rules.xml).
 */
internal object SiteUpload {
    private const val PREFS = "site_upload"
    private const val DEFAULT_SITE = "https://pacosworld.com"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun site(c: Context) = prefs(c).getString("site", DEFAULT_SITE) ?: DEFAULT_SITE
    private fun clientId(c: Context) = prefs(c).getString("client_id", "") ?: ""
    private fun clientSecret(c: Context) = prefs(c).getString("client_secret", "") ?: ""
    fun isSetUp(c: Context) = clientId(c).isNotBlank() && clientSecret(c).isNotBlank()
    fun autoUpload(c: Context) = isSetUp(c) && prefs(c).getBoolean("auto", true)
    fun siteName(c: Context) = site(c).removePrefix("https://").trimEnd('/')

    /** Remembers which saved files went up, so the list can show "On site". */
    fun isUploaded(c: Context, file: File) = prefs(c).getStringSet("uploaded", emptySet())!!.contains(file.absolutePath)
    private fun markUploaded(c: Context, file: File) {
        val set = prefs(c).getStringSet("uploaded", emptySet())!!.toMutableSet()
        set.add(file.absolutePath)
        prefs(c).edit().putStringSet("uploaded", set).apply()
    }
    /** Keeps the "On site" mark when a document is renamed on the phone. */
    fun moveMark(c: Context, from: File, to: File) {
        val set = prefs(c).getStringSet("uploaded", emptySet())!!.toMutableSet()
        if (set.remove(from.absolutePath)) { set.add(to.absolutePath); prefs(c).edit().putStringSet("uploaded", set).apply() }
    }

    class Result(val ok: Boolean, val message: String)

    private val main = Handler(Looper.getMainLooper())

    /**
     * Uploads [file] as <folder>/<name>.pdf, off the main thread, and calls [done] on the
     * main thread with what happened. The site never overwrites: a taken name gets " (2)".
     */
    fun upload(activity: Activity, file: File, folder: List<String>, done: (Result) -> Unit) {
        if (!isSetUp(activity)) { done(Result(false, "Set up Upload to my site in Settings first.")); return }
        val app = activity.applicationContext
        Thread {
            val result = try {
                val path = (folder + file.name).joinToString("/") { enc(it) }
                val conn = open(app, "/api/scans/files/$path", "PUT")
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/pdf")
                conn.setFixedLengthStreamingMode(file.length())
                conn.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
                val code = conn.responseCode
                if (code == 201 || code == 200) {
                    markUploaded(app, file)
                    val key = try { JSONObject(conn.inputStream.bufferedReader().readText()).optString("key") } catch (e: Exception) { "" }
                    val savedAs = key.substringAfterLast('/').ifBlank { file.name }
                    Result(true, "Uploaded to ${siteName(app)} as $savedAs")
                } else Result(false, problem(app, code))
            } catch (e: Exception) {
                Result(false, "Couldn't reach ${siteName(app)}. Check the internet connection and try again.")
            }
            main.post { done(result) }
        }.start()
    }

    /** Checks the address and token without sending a document. */
    private fun test(c: Context, done: (Result) -> Unit) {
        Thread {
            val result = try {
                val conn = open(c, "/api/scans/ping", "GET")
                val code = conn.responseCode
                if (code == 200) Result(true, "Connected to ${siteName(c)}. Uploads are ready.") else Result(false, problem(c, code))
            } catch (e: Exception) {
                Result(false, "Couldn't reach ${siteName(c)}. Check the address and the internet connection.")
            }
            main.post { done(result) }
        }.start()
    }

    private fun open(c: Context, path: String, method: String): HttpURLConnection {
        val conn = URL(site(c).trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        // Access answers a bad token with a redirect to its sign-in page; treat that as refused.
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.setRequestProperty("CF-Access-Client-Id", clientId(c))
        conn.setRequestProperty("CF-Access-Client-Secret", clientSecret(c))
        return conn
    }

    private fun problem(c: Context, code: Int) = when (code) {
        in 300..399, 401, 403 -> "${siteName(c)} turned the upload away. Check the Client ID and Client Secret in Settings."
        404 -> "${siteName(c)} doesn't have the upload service yet."
        413 -> "That PDF is too big for the site (50 MB at most)."
        else -> "${siteName(c)} couldn't take the upload (error $code). Try again later."
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Settings screen: site address, Client ID, Client Secret, automatic upload, and a Test button. */
    fun showSetup(activity: Activity, onSaved: () -> Unit = {}) {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        box.addView(TextView(activity).apply {
            text = "Uploads go to private storage on your site. Only your phone (with this token) and you, signed in, can reach them."
        })
        val siteField = EditText(activity).apply { hint = "Site address"; setText(site(activity)); isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val idField = EditText(activity).apply { hint = "Client ID"; setText(clientId(activity)); isSingleLine = true }
        val secretField = EditText(activity).apply {
            hint = if (clientSecret(activity).isBlank()) "Client Secret" else "Client Secret (saved; type to replace)"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val auto = CheckBox(activity).apply { text = "Upload every new scan automatically"; isChecked = prefs(activity).getBoolean("auto", true) }
        listOf(siteField, idField, secretField, auto).forEach { box.addView(it) }

        fun save(): Boolean {
            var s = siteField.text.toString().trim().trimEnd('/')
            if (s.isNotEmpty() && !s.startsWith("http")) s = "https://$s"
            if (!s.startsWith("https://")) { Toast.makeText(activity, "The site address must start with https://", Toast.LENGTH_LONG).show(); return false }
            val e = prefs(activity).edit()
                .putString("site", s)
                .putString("client_id", idField.text.toString().trim())
                .putBoolean("auto", auto.isChecked)
            val secret = secretField.text.toString().trim()
            if (secret.isNotEmpty()) e.putString("client_secret", secret)
            e.apply()
            return true
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Upload to my site")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNeutralButton("Test", null)
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (save()) { dialog.dismiss(); onSaved() }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (!save()) return@setOnClickListener
            if (!isSetUp(activity)) { Toast.makeText(activity, "Enter the Client ID and Client Secret first.", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            Toast.makeText(activity, "Testing…", Toast.LENGTH_SHORT).show()
            test(activity) { Toast.makeText(activity, it.message, Toast.LENGTH_LONG).show() }
        }
    }
}
