package com.paco.scanner
import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import java.io.File
import kotlin.math.*

class CropActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val path=intent.getStringExtra("path") ?: run {finish();return}
  val source=File(path)
  val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
  BitmapFactory.decodeFile(path,bounds)
  if(bounds.outWidth<=0){finish();return}
  val options=BitmapFactory.Options().apply {inSampleSize=max(1,ceil(max(bounds.outWidth,bounds.outHeight)/2200.0).toInt())}
  val bitmap=BitmapFactory.decodeFile(path,options) ?: run {finish();return}
  val editor=CornerView(this,bitmap)
  val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
  column.addView(TextView(this).apply {text="Drag the four circles onto the paper corners";textSize=17f;setPadding(20,20,20,12)})
  column.addView(editor,LinearLayout.LayoutParams(-1,0,1f))
  column.addView(Button(this).apply {text="Straighten & Save";setOnClickListener {
   try {
    val corrected=editor.correct()
    val result=File(source.parentFile,source.nameWithoutExtension+"_straightened.jpg")
    result.outputStream().use {corrected.compress(Bitmap.CompressFormat.JPEG,92,it)}
    corrected.recycle()
    setResult(RESULT_OK,android.content.Intent().putExtra("corrected_path",result.absolutePath))
    finish()
   } catch(e:Exception){Toast.makeText(this@CropActivity,"Correction failed: ${e.message}",Toast.LENGTH_LONG).show()}
  }})
  setContentView(column)
 }
}
private class CornerView(context:android.content.Context,private val bitmap:Bitmap):View(context) {
 private val pts=floatArrayOf(.06f,.06f,.94f,.06f,.94f,.94f,.06f,.94f)
 private val paint=Paint(3)
 private var active=-1
 private var scale=1f
 private var offsetX=0f
 private var offsetY=0f
 override fun onDraw(c:Canvas){
  scale=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
  offsetX=(width-bitmap.width*scale)/2
  offsetY=(height-bitmap.height*scale)/2
  c.drawColor(Color.rgb(28,34,43))
  paint.color=Color.WHITE
  c.drawBitmap(bitmap,null,RectF(offsetX,offsetY,offsetX+bitmap.width*scale,offsetY+bitmap.height*scale),paint)
  val p=Path()
  for(i in 0..3){
   val x=offsetX+pts[2*i]*bitmap.width*scale
   val y=offsetY+pts[2*i+1]*bitmap.height*scale
   if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
  }
  p.close()
  paint.color=Color.CYAN;paint.style=Paint.Style.STROKE;paint.strokeWidth=4f
  c.drawPath(p,paint);paint.style=Paint.Style.FILL
  for(i in 0..3)c.drawCircle(offsetX+pts[2*i]*bitmap.width*scale,offsetY+pts[2*i+1]*bitmap.height*scale,16f,paint)
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{
  when(e.actionMasked){
   MotionEvent.ACTION_DOWN->{active=(0..3).minByOrNull {i->val dx=e.x-(offsetX+pts[2*i]*bitmap.width*scale);val dy=e.y-(offsetY+pts[2*i+1]*bitmap.height*scale);dx*dx+dy*dy}?:-1;return true}
   MotionEvent.ACTION_MOVE->{if(active>=0){pts[2*active]=((e.x-offsetX)/(bitmap.width*scale)).coerceIn(0f,1f);pts[2*active+1]=((e.y-offsetY)/(bitmap.height*scale)).coerceIn(0f,1f);invalidate()};return true}
   MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{active=-1;return true}
  }
  return true
 }
 fun correct():Bitmap{
  val src=FloatArray(8){i->pts[i]*(if(i%2==0)bitmap.width else bitmap.height)}
  fun distance(a:Int,b:Int)=hypot(src[2*a]-src[2*b],src[2*a+1]-src[2*b+1])
  val w=((distance(0,1)+distance(2,3))/2).roundToInt()
  val h=((distance(1,2)+distance(3,0))/2).roundToInt()
  require(w>=50&&h>=50){"Corners too close"}
  val factor=min(1f,2400f/max(w,h))
  val ow=max(1,(w*factor).roundToInt());val oh=max(1,(h*factor).roundToInt())
  val dst=floatArrayOf(0f,0f,ow.toFloat(),0f,ow.toFloat(),oh.toFloat(),0f,oh.toFloat())
  val matrix=Matrix()
  require(matrix.setPolyToPoly(src,0,dst,0,4)){"Invalid corners"}
  return Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888).also {
   val c=Canvas(it);c.drawColor(Color.WHITE)
   c.drawBitmap(bitmap,matrix,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
  }
 }
}