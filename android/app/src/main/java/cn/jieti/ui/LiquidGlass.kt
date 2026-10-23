package cn.jieti.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius

/** An optical rim, not a blurred rectangle. The optional lens uses only the
 * explicit capture's in-memory image; idle screens are never sampled or saved. */
class LiquidGlassDrawable(private val density:Float,private val radius:Float=28f,private val clear:Boolean=false,private val image:()->Bitmap?={null},private val button:Boolean=false,private val primary:Boolean=false):Drawable(),Runnable{
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val outer=Path();private val rim=Path();private val matrix=Matrix()
 private var running=false;private var origin=0L
 private val location=IntArray(2);var locate:((IntArray)->Unit)?=null
 fun start(){running=true;origin=SystemClock.uptimeMillis();invalidateSelf()}
 fun stop(){running=false;unscheduleSelf(this)}
 override fun run(){invalidateSelf()}
 override fun draw(canvas:Canvas){
  locate?.invoke(location);screenX=location[0].toFloat();screenY=location[1].toFloat()
  val r=RectF(bounds).apply{inset(density,density)};val cr=radius*density
  outer.reset();outer.addRoundRect(r,cr,cr,Path.Direction.CW)
  paint.style=Paint.Style.FILL;paint.alpha=255
  val colors=when{primary->intArrayOf(0xF4F8FFF1.toInt(),0xE3D7E6CA.toInt(),0xE8BCCDAE.toInt());button->intArrayOf(0xF4FFFFFF.toInt(),0xDAF4F7EE.toInt(),0xE5E1E8DA.toInt());clear->intArrayOf(0xB8FFFFFF.toInt(),0x405F7566,0xA8FFFFFF.toInt());else->intArrayOf(0xECFFFFFF.toInt(),0xF4F5F6EF.toInt(),0xE4E7ECE2.toInt())}
  paint.shader=LinearGradient(0f,r.top,r.right,r.bottom,colors,floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP)
  canvas.drawPath(outer,paint);paint.shader=null
  // A narrow convex lens around the perimeter, using a real source only when
  // one already exists from the user's explicit screenshot action.
  val source=image()?.takeUnless{it.isRecycled}
  if(source!=null){
   rim.set(outer);val inner=RectF(r).apply{inset(8*density,8*density)}
   rim.addRoundRect(inner,maxOf(0f,cr-8*density),maxOf(0f,cr-8*density),Path.Direction.CCW)
   val shader=BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
   matrix.reset();matrix.setScale(1.07f,1.07f);matrix.postTranslate(-(r.centerX()+screenX)*.07f,-(r.centerY()+screenY)*.07f)
   // Map window-local pixels to the captured screen via its current position.
   matrix.postTranslate(-screenX,-screenY);shader.setLocalMatrix(matrix)
   paint.shader=shader;paint.alpha=110;canvas.drawPath(rim,paint);paint.alpha=255;paint.shader=null
  }
  paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density
  paint.shader=LinearGradient(0f,0f,r.right,r.bottom,intArrayOf(0xDFFFFFFF.toInt(),0x0850684C,0x38647357,0xB8FFFFFF.toInt()),null,Shader.TileMode.CLAMP)
  canvas.drawRoundRect(r,cr,cr,paint)
  val inset=RectF(r).apply{inset(2*density,2*density)}
  paint.strokeWidth=density;paint.shader=null;paint.color=0x28607858;canvas.drawRoundRect(inset,cr-2*density,cr-2*density,paint)
  if(running&&!android.animation.ValueAnimator.areAnimatorsEnabled())stop()
  val phase=if(running)(SystemClock.uptimeMillis()-origin)%9000/9000f else .12f
  val sweep=SweepGradient(r.centerX(),r.centerY(),intArrayOf(0x00FFFFFF,0x00FFFFFF,0xEFFFFFFF.toInt(),0x809EBE9B.toInt(),0x00FFFFFF),floatArrayOf(0f,.69f,.79f,.84f,1f))
  matrix.reset();matrix.setRotate(phase*360,r.centerX(),r.centerY());sweep.setLocalMatrix(matrix)
  paint.alpha=255;paint.shader=sweep;paint.strokeWidth=2*density;canvas.drawRoundRect(r,cr,cr,paint)
  paint.shader=null;paint.style=Paint.Style.FILL
  if(running&&isVisible)scheduleSelf(this,SystemClock.uptimeMillis()+50)
 }
 var screenX=0f;var screenY=0f
 override fun getOutline(outline:Outline){outline.setRoundRect(bounds,radius*density);outline.alpha=if(button).32f else .2f}
 override fun setAlpha(alpha:Int){} // Host-view opacity handles lifecycle fades.
 override fun setColorFilter(filter:ColorFilter?){}
 @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

fun View.liquidGlass(radius:Float=28f,clear:Boolean=false,image:()->Bitmap?={null},button:Boolean=false,primary:Boolean=false){
 val glass=LiquidGlassDrawable(resources.displayMetrics.density,radius,clear,image,button,primary)
 glass.locate={getLocationOnScreen(it)}
 background=glass
 addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
  override fun onViewAttachedToWindow(v:View){if(!button&&android.animation.ValueAnimator.areAnimatorsEnabled())glass.start()}
  override fun onViewDetachedFromWindow(v:View){glass.stop()}
 })
 addOnLayoutChangeListener{v,_,_,_,_,_,_,_,_->val point=IntArray(2);v.getLocationOnScreen(point);glass.screenX=point[0].toFloat();glass.screenY=point[1].toFloat()}
}

/** Quiet glass controls; reading content stays on an uninterrupted canvas. */
fun Modifier.liquidSurface(radius:Float=28f,dark:Boolean=false)=drawWithCache{
 val r=CornerRadius(radius*density);val white=if(dark)ComposeColor(0xFF65705F)else ComposeColor.White
 val body=Brush.linearGradient(listOf(white.copy(alpha=.68f),white.copy(alpha=.09f),white.copy(alpha=.4f)))
 val rim=Brush.linearGradient(listOf(white.copy(alpha=.95f),ComposeColor(0xFF84927B).copy(alpha=.12f),white.copy(alpha=.65f)))
 onDrawBehind{drawRoundRect(body,cornerRadius=r);drawRoundRect(rim,cornerRadius=r,style=Stroke(1.2f*density))}
}
