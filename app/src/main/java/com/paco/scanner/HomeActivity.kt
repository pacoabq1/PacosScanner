package com.paco.scanner

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** First visual implementation of the public concept: home, folders, recent PDFs, theme. */
class HomeActivity : Activity() {
 private var dark=true
 private var bg=0
 private var panel=0
 private var foreground=0
 private var secondary=0
 private val cyan=Color.rgb(27,222,227)
 private lateinit var root:LinearLayout
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun shape(color:Int,radius:Int=16)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
 private fun text(value:String,size:Float=16f,bold:Boolean=false,color:Int=foreground)=TextView(this).apply{
  text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD
 }
 private fun container():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
 private fun row():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 private fun add(parent:LinearLayout,child:View,weight:Float=0f){
  parent.addView(child,if(weight>0)LinearLayout.LayoutParams(0,-2,weight) else LinearLayout.LayoutParams(-2,-2))
 }
 private fun panelButton(label:String,onClick:()->Unit):TextView=text(label,14f,true).apply{
  gravity=Gravity.CENTER;setPadding(dp(14),dp(14),dp(14),dp(14));background=shape(panel,14);setOnClickListener{onClick()}
 }
 private fun docsRoot()=File(filesDir,"documents")
 private fun allPdfs():List<File> = docsRoot().walkTopDown().filter{it.isFile&&it.extension.equals("pdf",true)}.toList().sortedByDescending{it.lastModified()}
 private fun systemDark()=(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES
 private fun chooseTheme(){
  dark=when(getPreferences(MODE_PRIVATE).getString("theme","system")){"dark"->true;"light"->false;else->systemDark()}
  bg=if(dark)Color.rgb(5,22,41) else Color.rgb(243,249,253)
  panel=if(dark)Color.rgb(14,48,73) else Color.WHITE
  foreground=if(dark)Color.WHITE else Color.rgb(13,38,59)
  secondary=if(dark)Color.rgb(167,198,215) else Color.rgb(76,102,121)
  window.statusBarColor=bg;window.navigationBarColor=bg
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);render()}
 override fun onResume(){super.onResume();render()}
 private fun render(){
  chooseTheme()
  root=container().apply{setPadding(dp(22),dp(24),dp(22),dp(24));setBackgroundColor(bg)}
  val scroller=ScrollView(this).apply{isFillViewport=true;addView(root)}
  setContentView(scroller)
  val header=row()
  add(header,text("▣",33f,true,cyan))
  val heading=container().apply{setPadding(dp(12),0,0,0);addView(text("Paco's Scanner",25f,true));addView(text("Scan it. Straighten it. Save it.",12f,false,cyan))}
  add(header,heading,1f)
  add(header,text("⚙",26f,false,secondary).apply{setPadding(dp(10),dp(8),0,dp(8));setOnClickListener{showSettings()}})
  root.addView(header)
  val scan=TextView(this).apply{
   text="▣\nScan Document";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(0,35,47))
   gravity=Gravity.CENTER;setPadding(dp(30),dp(22),dp(30),dp(22))
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(72,244,237),Color.rgb(0,172,198))).apply{cornerRadius=dp(28).toFloat()}
   setOnClickListener{startActivity(Intent(this@HomeActivity,MainActivity::class.java).putExtra(MainActivity.EXTRA_START_SCAN,true))}
  }
  root.addView(scan,LinearLayout.LayoutParams(-1,dp(150)).apply{topMargin=dp(26);bottomMargin=dp(16)})
  val actions=row()
  listOf("📷 Gallery","📁 Folders","✉ Share").forEachIndexed{i,label->
   val b=panelButton(label){when(i){0->startActivity(Intent(this,MainActivity::class.java));1->showFolders();else->showRecent()}}
   actions.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(if(i<2)8 else 0)})
  }
  root.addView(actions)
  section("Your Folders","View all"){showFolders()}
  val names=docsRoot().listFiles()?.filter{it.isDirectory}?.sortedBy{it.name}?.take(4).orEmpty()
  if(names.isEmpty()){
   root.addView(text("Your saved document folders will appear here.",14f,false,secondary).apply{setPadding(0,dp(12),0,dp(12))})
  }else{
   val colors=intArrayOf(0xff32a9ee.toInt(),0xff40c989.toInt(),0xffa27cf2.toInt(),0xfff4b545.toInt())
   names.forEachIndexed{i,f->
    val item=row().apply{setPadding(dp(14),dp(13),dp(14),dp(13));background=shape(panel)}
    add(item,text("📁",25f,false,colors[i%colors.size]))
    val count=f.walkTopDown().count{it.isFile&&it.extension.equals("pdf",true)}
    add(item,container().apply{setPadding(dp(14),0,0,0);addView(text(f.name,16f,true));addView(text("$count documents",12f,false,secondary))},1f)
    item.setOnClickListener{showFolder(f)}
    root.addView(item,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
   }
  }
  section("Recent Documents","View all"){showRecent()}
  val docs=allPdfs().take(5)
  if(docs.isEmpty())root.addView(text("Your first scanned PDF will show up here.",14f,false,secondary).apply{setPadding(0,dp(12),0,dp(12))})
  docs.forEach{file->
   val item=row().apply{setPadding(dp(13),dp(12),dp(13),dp(12));background=shape(panel)}
   add(item,text("▤",30f,true,cyan))
   add(item,container().apply{setPadding(dp(14),0,0,0);addView(text(file.nameWithoutExtension,14f,true));addView(text(SimpleDateFormat("MMM d, yyyy",Locale.getDefault()).format(Date(file.lastModified())),12f,false,secondary))},1f)
   add(item,text("Rename",13f,true,cyan).apply{setPadding(dp(12),dp(8),0,dp(8));setOnClickListener{renameDoc(file)}})
   add(item,text("Delete",13f,true,Color.rgb(240,108,108)).apply{setPadding(dp(12),dp(8),0,dp(8));setOnClickListener{confirmDelete(file)}})
   add(item,text("Share",13f,true,cyan).apply{setPadding(dp(12),dp(8),0,dp(8));setOnClickListener{Documents.share(this@HomeActivity,file)}})
   item.setOnClickListener{Documents.open(this,file)}
   root.addView(item,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  }
  root.addView(text("On-device storage • No account required",12f,false,secondary).apply{gravity=Gravity.CENTER;setPadding(0,dp(26),0,0)})
 }
 private fun section(title:String,action:String,onClick:()->Unit){
  val r=row().apply{setPadding(0,dp(25),0,dp(12))}
  add(r,text(title,20f,true),1f)
  add(r,text(action,13f,true,cyan).apply{setOnClickListener{onClick()}})
  root.addView(r)
 }
 private fun showFolders(){
  val dirs=docsRoot().walkTopDown().filter{it.isDirectory&&it!=docsRoot()}.toList()
  if(dirs.isEmpty()){
   android.app.AlertDialog.Builder(this).setTitle("Document folders").setMessage("No folders yet. Save a PDF to create one.").setPositiveButton("Close",null).show()
   return
  }
  android.app.AlertDialog.Builder(this).setTitle("Document folders")
   .setItems(dirs.map{it.relativeTo(docsRoot()).path}.toTypedArray()){_,i->showFolder(dirs[i])}
   .setPositiveButton("Close",null).show()
 }
 /** Lists the PDFs in one folder; tapping one opens it. */
 private fun showFolder(dir:File){
  val files=dir.walkTopDown().filter{it.isFile&&it.extension.equals("pdf",true)}.toList().sortedByDescending{it.lastModified()}
  showPdfList(dir.relativeTo(docsRoot()).path,files)
 }
 private fun showRecent(){showPdfList("Saved PDFs",allPdfs())}
 /** Tap a PDF to open it; press and hold for Rename and Share. */
 private fun showPdfList(title:String,files:List<File>){
  val b=android.app.AlertDialog.Builder(this).setTitle(title).setPositiveButton("Close",null)
  if(files.isEmpty()){b.setMessage("No PDFs here yet.");b.show();return}
  b.setItems(files.map{it.nameWithoutExtension}.toTypedArray()){_,i->Documents.open(this,files[i])}
  val dialog=b.show()
  dialog.listView?.setOnItemLongClickListener{_,_,i,_->dialog.dismiss();fileActions(files[i]);true}
  Toast.makeText(this,"Tap to open. Press and hold to rename, share or delete.",Toast.LENGTH_SHORT).show()
 }
 private fun fileActions(file:File){
  android.app.AlertDialog.Builder(this).setTitle(file.nameWithoutExtension)
   .setItems(arrayOf("Open","Rename","Share","Delete")){_,i->when(i){0->Documents.open(this,file);1->renameDoc(file);2->Documents.share(this,file);else->confirmDelete(file)}}
   .setNegativeButton("Cancel",null).show()
 }
 private fun folderOf(file:File)=(file.parentFile?:docsRoot()).relativeTo(docsRoot()).path.split('/',File.separatorChar).filter{it.isNotBlank()}
 /** Asks first, then deletes the PDF from the app and from Documents/PacosScanner. */
 private fun confirmDelete(file:File){
  android.app.AlertDialog.Builder(this).setTitle("Delete ${file.nameWithoutExtension}?")
   .setMessage("This removes it from the app and from Documents/PacosScanner on your phone. It can't be undone.")
   .setPositiveButton("Delete"){_,_->
    var phoneOk=true
    val ok=Documents.delete(this,file,folderOf(file)){phoneOk=it}
    val msg=when{
     !ok->"Couldn't delete that document."
     !phoneOk->"Deleted from the app. If a copy is still in Documents/PacosScanner, delete it in My Files."
     else->"Deleted ${file.nameWithoutExtension}"
    }
    Toast.makeText(this,msg,Toast.LENGTH_LONG).show()
    render()
   }
   .setNegativeButton("Cancel",null).show()
 }
 private fun renameDoc(file:File){
  Documents.askName(this,"Rename document",file.nameWithoutExtension,"Rename"){newName->
   val folder=(file.parentFile?:docsRoot()).relativeTo(docsRoot()).path.split('/',File.separatorChar).filter{it.isNotBlank()}
   var phoneOk=true
   val renamed=Documents.rename(this,file,folder,newName){phoneOk=it}
   val msg=when{
    renamed==null->"Couldn't rename that document."
    !phoneOk->"Renamed to ${renamed.nameWithoutExtension} here, but the copy in Documents/PacosScanner kept its old name."
    else->"Renamed to ${renamed.nameWithoutExtension}"
   }
   Toast.makeText(this,msg,Toast.LENGTH_LONG).show()
   render()
  }
 }
 private fun showSettings(){
  val options=arrayOf("Follow phone settings","Dark","Light")
  val selected=when(getPreferences(MODE_PRIVATE).getString("theme","system")){"dark"->1;"light"->2;else->0}
  android.app.AlertDialog.Builder(this).setTitle("Appearance")
   .setSingleChoiceItems(options,selected){dialog,index->
    getPreferences(MODE_PRIVATE).edit().putString("theme",arrayOf("system","dark","light")[index]).apply()
    dialog.dismiss();render()
   }.setNegativeButton("Cancel",null).show()
 }
}
