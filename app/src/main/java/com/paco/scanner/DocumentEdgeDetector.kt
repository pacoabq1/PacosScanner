package com.paco.scanner
import android.graphics.Bitmap
import kotlin.math.*

/** Offline experimental corner suggestions for light paper against darker backgrounds. */
internal object DocumentEdgeDetector {
 fun propose(bitmap: Bitmap):FloatArray? {
  val w=240
  val h=max(64,(bitmap.height.toDouble()*w/bitmap.width).roundToInt())
  val small=Bitmap.createScaledBitmap(bitmap,w,h,true)
  val px=IntArray(w*h);small.getPixels(px,0,w,0,0,w,h);small.recycle()
  val gray=IntArray(w*h){i->val p=px[i];((p shr 16 and 255)*299+(p shr 8 and 255)*587+(p and 255)*114)/1000}
  val left=mutableListOf<Pair<Int,Int>>();val right=mutableListOf<Pair<Int,Int>>()
  for(y in h/8 until h*7/8 step 3){
   var l=-1;var r=-1;var dl=25;var dr=25
   for(x in w/20 until w/2){val d=gray[y*w+x+2]-gray[y*w+x-2];if(d>dl){dl=d;l=x}}
   for(x in w/2 until w*19/20){val d=gray[y*w+x-2]-gray[y*w+x+2];if(d>dr){dr=d;r=x}}
   if(l>=0&&r>l+w/4){left.add(y to l);right.add(y to r)}
  }
  if(left.size<max(6,h/35))return null
  fun at(samples:List<Pair<Int,Int>>,y:Int):Float{
   val nearby=samples.sortedBy{abs(it.first-y)}.take(max(4,samples.size/5)).map{it.second}.sorted()
   return nearby[nearby.size/2].toFloat()
  }
  val ys=left.map{it.first}.sorted();val top=ys[ys.size/10];val bottom=ys[ys.size*9/10]
  if(bottom-top<h/3)return null
  val tl=at(left,top);val tr=at(right,top);val bl=at(left,bottom);val br=at(right,bottom)
  if(min(tr-tl,br-bl)<w/3)return null
  return floatArrayOf(tl/w,top.toFloat()/h,tr/w,top.toFloat()/h,br/w,bottom.toFloat()/h,bl/w,bottom.toFloat()/h)
 }
}