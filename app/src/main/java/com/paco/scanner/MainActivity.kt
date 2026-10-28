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

class MainActivity:ComponentActivity(){
 private val pages=mutableListOf<File>()
 private lateinit var status:TextView
 private lateinit var folder:EditText
 private var pendingPhoto:File?=null
 private val takePhoto=registerForActivityResult(ActivityResultContracts.TakePicture()){ok->
  if(ok)pendingPhoto?.let{pages.add(it)}
  updateStatus()
 }
 private val pickImages=registerForActivityResult(ActivityResultContracts.GetMultipleContents()){uris->
  uris.forEach {uri->
   val dest=File(cacheDir,"import_${System.nanoTime()}.img")
   contentResolver.openInputStream(uri)?.use {input->dest.outputStream().use {input.copyTo(it)}}
   if(dest.length()>0)pages.add(dest)
  }
  updateStatus()
 }
 private val straighten=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  if(result.resultCode==RESULT_OK){
   val path=result.data?.getStringExtra("corrected_path")
   if(path!=null && pages.isNotEmpty())pages[pages.lastIndex]=File(path)
   updateStatus()
  }
 }
 private fun updateStatus(){status.text="${pages.size} page(s) ready"}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,50,36,24)}
  layout.addView(TextView(this).apply{text="Paco's Scanner";textSize=27f})
  layout.addView(TextView(this).apply{text="Private document scanning • v0.2 prototype"})
  folder=EditText(this).apply{hint="Folder (e.g. Medical/Receipts)";setText("Inbox")}
  layout.addView(folder)
  status=TextView(this).apply{text="0 pages ready";textSize=18f};layout.addView(status)
  fun button(label:String,action:()->Unit){layout.addView(Button(this).apply{text=label;setOnClickListener{action()}})}
  button("Take a picture"){
   val dir=File(cacheDir,"camera").apply{mkdirs()}
   pendingPhoto=File(dir,"page_${System.nanoTime()}.jpg")
   val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",pendingPhoto!!)
   takePhoto.launch(uri)
  }
  button("Import pictures"){pickImages.launch("image/*")}
  button("Straighten last page"){
   if(pages.isEmpty())Toast.makeText(this,"Add a page first",Toast.LENGTH_SHORT).show()
   else straighten.launch(Intent(this,CropActivity::class.java).putExtra("path",pages.last().absolutePath))
  }
  button("Save pages as PDF"){savePdf()}
  button("Clear current pages"){pages.clear();updateStatus()}
  layout.addView(TextView(this).apply{text="Drag the four corner handles to correct perspective. PDFs stay in private app storage; share to export."})
  setContentView(ScrollView(this).apply{addView(layout)})
 }
 private fun savePdf(){
  if(pages.isEmpty()){Toast.makeText(this,"Add a page first",Toast.LENGTH_SHORT).show();return}
  val segments=folder.text.toString().split('/').map{it.trim().replace(Regex("[^a-zA-Z0-9 _-]"),"_")}.filter{it.isNotBlank()&&it!="."&&it!=".."}
  val dir=segments.fold(File(filesDir,"documents")){parent,s->File(parent,s)}.apply{mkdirs()}
  val file=File(dir,"scan_${System.currentTimeMillis()}.pdf")
  val pdf=PdfDocument()
  var count=0
  try{
   pages.forEach {img->
    val bitmap=BitmapFactory.decodeFile(img.absolutePath)?:return@forEach
    val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,count+1).create())
    val scale=minOf(555f/bitmap.width,802f/bitmap.height)
    val w=bitmap.width*scale;val h=bitmap.height*scale
    page.canvas.drawBitmap(bitmap,null,android.graphics.RectF((595-w)/2,(842-h)/2,(595+w)/2,(842+h)/2),null)
    pdf.finishPage(page);bitmap.recycle();count++
   }
   if(count==0){Toast.makeText(this,"No readable images",Toast.LENGTH_LONG).show();return}
   file.outputStream().use{pdf.writeTo(it)}
  }catch(e:Exception){Toast.makeText(this,"PDF failed: ${e.message}",Toast.LENGTH_LONG).show();return}
  finally{pdf.close()}
  pages.clear();status.text="Saved ${file.name}"
  val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",file)
  val share=Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  startActivity(Intent.createChooser(share,"Email, fax, or share PDF"))
 }
}