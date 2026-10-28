package com.paco.scanner

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/** Prototype: camera capture, multipage PDF, private folders and sharing. */
class MainActivity : ComponentActivity() {
    private val pages = mutableListOf<File>()
    private lateinit var status: TextView
    private lateinit var folder: EditText
    private var pendingPhoto: File? = null
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingPhoto?.let { pages.add(it) }
        status.text = "${pages.size} page(s) ready"
    }
    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            val dest = File(cacheDir, "import_${System.nanoTime()}.jpg")
            contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            if (dest.length() > 0) pages.add(dest)
        }
        status.text = "${pages.size} page(s) ready"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 50, 36, 24) }
        layout.addView(TextView(this).apply { text = "Paco's Scanner"; textSize = 27f })
        layout.addView(TextView(this).apply { text = "Private, local document scanning • Prototype v0.1" })
        folder = EditText(this).apply { hint = "Folder (e.g. Medical/Receipts)"; setText("Inbox") }
        layout.addView(folder)
        status = TextView(this).apply { text = "0 pages ready"; textSize = 18f }
        layout.addView(status)
        fun button(label: String, action: () -> Unit) { layout.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("Take a picture") {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            pendingPhoto = File(dir, "page_${System.nanoTime()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", pendingPhoto!!)
            takePhoto.launch(uri)
        }
        button("Import pictures") { pickImages.launch("image/*") }
        button("Save pages as PDF") { savePdf() }
        button("Clear current pages") { pages.clear(); status.text = "0 pages ready" }
        layout.addView(TextView(this).apply { text = "Saved PDFs are stored privately in app storage. Sharing uses Android's share sheet (email or an installed fax app)." })
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    private fun savePdf() {
        if (pages.isEmpty()) { Toast.makeText(this, "Add a page first", Toast.LENGTH_SHORT).show(); return }
        val safeSegments = folder.text.toString().split('/').map { it.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "_") }.filter { it.isNotBlank() && it != "." && it != ".." }
        val dir = safeSegments.fold(File(filesDir, "documents")) { parent, segment -> File(parent, segment) }.apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.pdf")
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, imageFile ->
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return@forEachIndexed
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val scale = minOf(555f / bitmap.width, 802f / bitmap.height)
                val w = bitmap.width * scale; val h = bitmap.height * scale
                page.canvas.drawBitmap(bitmap, null, android.graphics.RectF((595-w)/2, (842-h)/2, (595+w)/2, (842+h)/2), null)
                pdf.finishPage(page)
                bitmap.recycle()
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        status.text = "Saved: ${file.name} in ${safeSegments.joinToString("/")}"
        pages.clear()
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivity(Intent.createChooser(share, "Email, fax, or share PDF"))
    }
}