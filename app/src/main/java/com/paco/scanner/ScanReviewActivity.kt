package com.paco.scanner
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

class ScanReviewActivity:Activity(){
 private lateinit var source:File
 private var currentPath:String=""
 private val cyan=Color.rgb(52,235,229)
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun pill(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(18).toFloat()}
 private val crop=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  if(r.resultCode==RESULT_OK)r.data?.getStringExtra("corrected_path")?.let{currentPath=it;render()}
 }
 override fun onCreate(state:Bundle?){super.onCreate(state);source=File(intent.getStringExtra("path")?:run{finish();return});currentPath=source.absolutePath;render()}
 private fun render(){
  window.statusBarColor=Color.rgb(4,18,34);window.navigationBarColor=Color.rgb(4,18,34)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));setBackgroundColor(Color.rgb(4,18,34))}
  val title=TextView(this).apply{text="Review Scan";textSize=24f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
  root.addView(title)
  root.addView(TextView(this).apply{text="Make sure the entire page is clear and readable.";textSize=13f;setTextColor(Color.rgb(164,198,215));gravity=Gravity.CENTER;setPadding(0,dp(6),0,dp(14))})
  val image=ImageView(this).apply{setImageURI(android.net.Uri.fromFile(File(currentPath)));scaleType=ImageView.ScaleType.FIT_CENTER;background=pill(Color.rgb(10,39,61));setPadding(dp(5),dp(5),dp(5),dp(5))}
  root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(TextView(this).apply{text="✓ Page captured   •   Check corners before keeping";textSize=13f;setTextColor(cyan);gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(10))})
  val adjust=TextView(this).apply{text="◇  Adjust Corners / Straighten";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(dp(12),dp(15),dp(12),dp(15));background=pill(Color.rgb(15,57,82));setOnClickListener{crop.launch(Intent(this@ScanReviewActivity,CropActivity::class.java).putExtra("path",currentPath))}}
  root.addView(adjust,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
  val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val retake=TextView(this).apply{text="Retake";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(16),0,dp(16));background=pill(Color.rgb(38,61,79));setOnClickListener{setResult(RESULT_CANCELED);finish()}}
  val keep=TextView(this).apply{text="✓  Keep Scan";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(0,42,48));gravity=Gravity.CENTER;setPadding(0,dp(16),0,dp(16));background=pill(cyan);setOnClickListener{setResult(RESULT_OK,Intent().putExtra("accepted_path",currentPath));finish()}}
  buttons.addView(retake,LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(6)})
  buttons.addView(keep,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(6)})
  root.addView(buttons);setContentView(root)
 }
}