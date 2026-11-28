package com.paco.scanner

import android.content.Intent
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

class MainActivity : ComponentActivity() {
    private val pages = mutableListOf<File>()
    private lateinit var status: TextView
    private lateinit var folder: EditText
    private var pendingPhoto: File? = null

    /** Google's on-phone document scanner: finds edges, straightens, cleans up, multi-page. */
    private val autoScan = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            var added = 0
            scan?.pages?.forEach { page -> if (addFromUri(page.imageUri, "autoscan")) added++ }
            updateStatus()
            if (added > 0) toast("Added $added page(s)")
        }
    }

    private val review = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("accepted_path")?.let { pages.add(File(it)) }
            pendingPhoto = null
            updateStatus()
        } else {
            pendingPhoto = null
            updateStatus()
            // Retake: open the camera again straight away (Back just returns here).
            if (result.resultCode == ScanReviewActivity.RESULT_RETAKE) launchCamera()
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val photo = pendingPhoto
        if (ok && photo != null && ImageFiles.normalize(photo)) {
            review.launch(Intent(this, ScanReviewActivity::class.java).putExtra("path", photo.absolutePath))
        } else {
            pendingPhoto = null
            updateStatus()
        }
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        var added = 0
        uris.forEach { if (addFromUri(it, "import")) added++ }
        updateStatus()
        if (uris.isNotEmpty() && added < uris.size) toast("${uris.size - added} picture(s) couldn't be read")
    }

    private val straighten = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("corrected_path")
            if (path != null && pages.isNotEmpty()) pages[pages.lastIndex] = File(path)
            updateStatus()
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun updateStatus() { status.text = "${pages.size} page(s) ready" }

    /** Copies an image into app storage, makes it upright, and adds it as a page. */
    private fun addFromUri(uri: Uri, prefix: String): Boolean {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val dest = File(dir, "${prefix}_${System.nanoTime()}.jpg")
        return try {
            contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            if (dest.length() > 0 && ImageFiles.normalize(dest)) { pages.add(dest); true } else { dest.delete(); false }
        } catch (e: Exception) {
            dest.delete(); false
        }
    }

    private fun launchAutoScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender -> autoScan.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e ->
                toast("Google's scanner isn't ready on this phone (${e.message}). Use Take a picture instead.")
            }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val photo = File(dir, "page_${System.nanoTime()}.jpg")
        pendingPhoto = photo
        takePhoto.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", photo))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android may close this screen while the camera is open; bring the pages back.
        savedInstanceState?.getStringArrayList("pages")?.map(::File)?.filter { it.exists() }?.let(pages::addAll)
        pendingPhoto = savedInstanceState?.getString("pendingPhoto")?.let(::File)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 50, 36, 24) }
        layout.addView(TextView(this).apply { text = "Paco's Scanner"; textSize = 27f })
        layout.addView(TextView(this).apply { text = "Scan • review • straighten • create PDF" })
        folder = EditText(this).apply { id = R_ID_FOLDER; hint = "Folder (e.g. Military/Buyback)"; setText("Inbox") }
        layout.addView(folder)
        status = TextView(this).apply { textSize = 18f }
        layout.addView(status)
        updateStatus()
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("Scan document (auto edges)") { launchAutoScan() }
        button("Take a picture") { launchCamera() }
        button("Import pictures") { pickImages.launch("image/*") }
        button("Straighten last page") {
            if (pages.isEmpty()) toast("Add a page first")
            else straighten.launch(Intent(this, CropActivity::class.java).putExtra("path", pages.last().absolutePath))
        }
        button("Save pages as PDF") {
            if (pages.isEmpty()) toast("Add a page first")
            else Documents.askName(this, "Name this document", Documents.defaultName(), "Save") { savePdf(it) }
        }
        button("Clear current pages") { pages.clear(); updateStatus() }
        layout.addView(TextView(this).apply {
            text = "Scan document finds the page edges, straightens and cleans up each page for you. " +
                "Take a picture stops at a review screen where you can adjust corners by hand."
        })
        setContentView(ScrollView(this).apply { addView(layout) })

        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_SCAN, false)) launchAutoScan()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("pages", ArrayList(pages.map { it.absolutePath }))
        pendingPhoto?.let { outState.putString("pendingPhoto", it.absolutePath) }
    }

    private fun savePdf(name: String) {
        if (pages.isEmpty()) { toast("Add a page first"); return }
        val segments = folder.text.toString().split('/')
            .map { it.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "_") }
            .filter { it.isNotBlank() && it != "." && it != ".." }
        val dir = segments.fold(File(filesDir, "documents")) { parent, s -> File(parent, s) }.apply { mkdirs() }
        val file = Documents.uniqueFile(dir, name)
        val pdf = PdfDocument()
        var count = 0
        try {
            pages.forEach { img ->
                val bitmap = ImageFiles.decodeForPdf(img) ?: return@forEach
                // US Letter, turned sideways for wide pages.
                val landscape = bitmap.width > bitmap.height
                val pw = if (landscape) 792 else 612
                val ph = if (landscape) 612 else 792
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, count + 1).create())
                val scale = minOf((pw - 36f) / bitmap.width, (ph - 36f) / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                page.canvas.drawBitmap(bitmap, null, RectF((pw - w) / 2, (ph - h) / 2, (pw + w) / 2, (ph + h) / 2), null)
                pdf.finishPage(page)
                bitmap.recycle()
                count++
            }
            if (count == 0) { toast("No readable images"); return }
            file.outputStream().use { pdf.writeTo(it) }
        } catch (e: Exception) {
            toast("PDF failed: ${e.message}"); return
        } finally {
            pdf.close()
        }
        pages.clear()
        val savedIn = Documents.saveToPhone(this, file, segments)
        status.text = "Saved ${file.name}"
        val message = if (savedIn != null) "Saved to your phone in $savedIn.\n\nYou can find it in My Files under Documents, or on the home screen here."
            else "Saved in the app, but it couldn't be copied to your phone's Documents folder. You can still open or share it from the home screen."
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        box.addView(TextView(this).apply { text = message; textSize = 16f })
        val upload = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, 0) }
        box.addView(upload)
        fun startUpload() {
            upload.text = "Uploading to ${SiteUpload.siteName(this)}…"
            upload.setOnClickListener(null)
            SiteUpload.upload(this, file, segments) { result ->
                upload.text = if (result.ok) "✓ ${result.message}" else "${result.message}\n\nTap here to try again."
                if (!result.ok) upload.setOnClickListener { startUpload() }
            }
        }
        if (SiteUpload.autoUpload(this)) startUpload()
        else {
            upload.text = "⇪ Upload to my site"
            upload.setTextColor(android.graphics.Color.rgb(0, 150, 170))
            upload.setOnClickListener {
                if (SiteUpload.isSetUp(this)) startUpload()
                else SiteUpload.showSetup(this) { if (SiteUpload.isSetUp(this)) startUpload() }
            }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("PDF saved")
            .setView(box)
            .setPositiveButton("Done", null)
            .setNeutralButton("Share") { _, _ -> Documents.share(this, file) }
            .setNegativeButton("Open") { _, _ -> Documents.open(this, file) }
            .show()
    }

    companion object {
        const val EXTRA_START_SCAN = "start_scan"
        private const val R_ID_FOLDER = 1001
    }
}
