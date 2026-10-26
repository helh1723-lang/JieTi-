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

/** Glass is composited over the live system surface. Never sample captured images. */
class LiquidGlassDrawable(private val density:Float,private val radius:Float=28f,private val clear:Boolean=false,private val button:Boolean=false,private val primary:Boolean=false,private val clock:()->Long={SystemClock.uptimeMillis()},private val frost:()->Float={0f},private val elastic:Boolean=false,private val dark:()->Boolean={false},private val tint:()->Int?={null}):Drawable(),Runnable{
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private val outer=Path();private val inner=Path();private val matrix=Matrix();private val points=FloatArray(6)
 private var running=false;private var origin=0L
 var deformation=BubbleDeformation()
  set(value){field=BubbleDeformation(value.x.takeIf{it.isFinite()}?.coerceIn(-1.1f,1.1f)?:0f,value.y.takeIf{it.isFinite()}?.coerceIn(-1.1f,1.1f)?:0f);invalidateSelf()}
 fun start(){running=true;origin=clock();invalidateSelf()}
 fun stop(){running=false;unscheduleSelf(this)}
 override fun run(){invalidateSelf()}
 private fun contour(path:Path,r:RectF,radius:Float){
  path.reset();val cr=minOf(radius,r.width()/2,r.height()/2).coerceAtLeast(0f)
  val strength=kotlin.math.hypot(deformation.x,deformation.y).coerceAtMost(1f)
  if(!elastic||strength<.0001f){path.addRoundRect(r,cr,cr,Path.Direction.CW);return}
  val length=kotlin.math.hypot(deformation.x,deformation.y);val ux=deformation.x/length;val uy=deformation.y/length
  val extent=(kotlin.math.abs(ux)*r.width()+kotlin.math.abs(uy)*r.height())/2
  fun point(px:Float,py:Float,index:Int){
   val x=px-r.centerX();val y=py-r.centerY();val along=x*ux+y*uy;val across=-x*uy+y*ux
   val tail=((1-along/maxOf(1f,extent))/2).coerceIn(0f,1f)
   // Stronger teardrop shoulders rather than enlarging the touch/window bounds.
   // Pull the body toward the trailing side; keep the extremities in the margin.
   val normalized=(along/maxOf(1f,extent)).coerceIn(-1f,1f)
   val lag=.12f*extent*(1-normalized*normalized)*strength
   val stretched=along*(1+.055f*strength)-lag
   val tapered=across*(1+(.12f-.58f*tail)*strength)
   // Diagonal shoulder control points can otherwise exceed the reserved margin.
   // Keep the complete Bezier hull (including its rim stroke) inside the window.
   points[index]=(r.centerX()+ux*stretched-uy*tapered).coerceIn(r.left-2*density,r.right+2*density)
   points[index+1]=(r.centerY()+uy*stretched+ux*tapered).coerceIn(r.top-2*density,r.bottom+2*density)
  }
  fun move(x:Float,y:Float){point(x,y,0);path.moveTo(points[0],points[1])}
  fun line(x:Float,y:Float){point(x,y,0);path.lineTo(points[0],points[1])}
  fun curve(x1:Float,y1:Float,x2:Float,y2:Float,x3:Float,y3:Float){point(x1,y1,0);point(x2,y2,2);point(x3,y3,4);path.cubicTo(points[0],points[1],points[2],points[3],points[4],points[5])}
  val k=.55228475f;val c=cr*k
  move(r.left+cr,r.top);line(r.right-cr,r.top)
  curve(r.right-cr+c,r.top,r.right,r.top+cr-c,r.right,r.top+cr);line(r.right,r.bottom-cr)
  curve(r.right,r.bottom-cr+c,r.right-cr+c,r.bottom,r.right-cr,r.bottom);line(r.left+cr,r.bottom)
  curve(r.left+cr-c,r.bottom,r.left,r.bottom-cr+c,r.left,r.bottom-cr);line(r.left,r.top+cr)
  curve(r.left,r.top+cr-c,r.left+cr-c,r.top,r.left+cr,r.top);path.close()
 }
 override fun draw(canvas:Canvas){
  val r=RectF(bounds).apply{inset((if(elastic)5f else 1f)*density,(if(elastic)5f else 1f)*density)};val cr=radius*density
  contour(outer,r,cr);paint.style=Paint.Style.FILL;paint.alpha=255;paint.shader=null
  val level=frost().coerceIn(0f,1f);val isDark=dark();val accent=tint()
  if(level>0f){paint.color=if(isDark)Color.argb((level*245).toInt(),32,38,30)else Color.argb((level*245).toInt(),248,249,243);if(accent!=null)paint.color=OverlayColors.blend(paint.color,accent,if(isDark).08f else .10f);canvas.drawPath(outer,paint)}
  paint.alpha=255
  val colors=when{isDark->if(clear)intArrayOf(0x88374032.toInt(),0x18374032,0x78374032)else intArrayOf(0xEC374032.toInt(),0xF42A3225.toInt(),0xE4262F22.toInt());clear&&button->intArrayOf(0x42FFFFFF,0x18FFFFFF,0x30FFFFFF);primary->intArrayOf(0xF4F8FFF1.toInt(),0xE3D7E6CA.toInt(),0xE8BCCDAE.toInt());button->intArrayOf(0xF4FFFFFF.toInt(),0xDAF4F7EE.toInt(),0xE5E1E8DA.toInt());clear->intArrayOf(0xB8FFFFFF.toInt(),0x405F7566,0xA8FFFFFF.toInt());else->intArrayOf(0xECFFFFFF.toInt(),0xF4F5F6EF.toInt(),0xE4E7ECE2.toInt())}
  val tinted=if(accent==null)colors else colors.map{OverlayColors.blend(it,accent,if(isDark).18f else if(primary).38f else .18f)}.toIntArray()
  paint.shader=LinearGradient(0f,r.top,r.right,r.bottom,tinted,floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP);canvas.drawPath(outer,paint)
  // Convex edge lighting stays live and translucent, without a frozen bitmap lens.
  paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density
  paint.shader=LinearGradient(0f,0f,r.right,r.bottom,intArrayOf(0xDFFFFFFF.toInt(),0x0850684C,0x38647357,0xB8FFFFFF.toInt()).map{if(accent==null)it else OverlayColors.blend(it,accent,.65f)}.toIntArray(),null,Shader.TileMode.CLAMP);canvas.drawPath(outer,paint)
  val inset=RectF(r).apply{inset(2*density,2*density)};contour(inner,inset,maxOf(0f,cr-2*density))
  paint.strokeWidth=density;paint.shader=null;paint.color=if(accent==null)0x28607858 else OverlayColors.blend(0x28607858,accent,.8f);canvas.drawPath(inner,paint)
  if(running&&!android.animation.ValueAnimator.areAnimatorsEnabled())stop()
  val phase=if(running)(clock()-origin)%9000/9000f else .12f
  val sweep=SweepGradient(r.centerX(),r.centerY(),intArrayOf(0x00FFFFFF,0x00FFFFFF,0xEFFFFFFF.toInt(),(accent?.let{OverlayColors.blend(0x809EBE9B.toInt(),it,.8f)}?:0x809EBE9B.toInt()),0x00FFFFFF),floatArrayOf(0f,.69f,.79f,.84f,1f))
  matrix.reset();matrix.setRotate(phase*360,r.centerX(),r.centerY());sweep.setLocalMatrix(matrix)
  paint.alpha=255;paint.shader=sweep;paint.strokeWidth=2*density;canvas.drawPath(outer,paint)
  paint.shader=null;paint.style=Paint.Style.FILL
  if(running&&isVisible)scheduleSelf(this,SystemClock.uptimeMillis()+120)
 }
 override fun getOutline(outline:Outline){val r=RectF(bounds).apply{inset((if(elastic)5f else 1f)*density,(if(elastic)5f else 1f)*density)};contour(outer,r,radius*density);outline.setPath(outer);outline.alpha=if(button).32f else .2f}
 override fun setAlpha(alpha:Int){} // Host-view opacity handles lifecycle fades.
 override fun setColorFilter(filter:ColorFilter?){}
 @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

fun View.liquidGlass(radius:Float=28f,clear:Boolean=false,button:Boolean=false,primary:Boolean=false,animate:Boolean=true,frost:()->Float={0f},elastic:Boolean=false):LiquidGlassDrawable {
 val glass=LiquidGlassDrawable(resources.displayMetrics.density,radius,clear,button,primary,frost=frost,elastic=elastic,dark={GlassPalette.isDark(context)},tint={GlassPalette.tint(context)})
 background=glass
 addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
  override fun onViewAttachedToWindow(v:View){if(animate&&!button&&android.animation.ValueAnimator.areAnimatorsEnabled())glass.start()}
  override fun onViewDetachedFromWindow(v:View){glass.stop()}
 })
 return glass
}

/** Quiet glass controls; reading content stays on an uninterrupted canvas. */
fun Modifier.liquidSurface(radius:Float=28f,dark:Boolean=false,tint:ComposeColor=ComposeColor(0xFF84927B))=drawWithCache{
 val r=CornerRadius(radius*density);val white=if(dark)ComposeColor(0xFF65705F)else ComposeColor.White
 val body=Brush.linearGradient(listOf(white.copy(alpha=.68f),white.copy(alpha=.09f),white.copy(alpha=.4f)))
 val rim=Brush.linearGradient(listOf(white.copy(alpha=.95f),tint.copy(alpha=.20f),white.copy(alpha=.65f)))
 onDrawBehind{drawRoundRect(body,cornerRadius=r);drawRoundRect(rim,cornerRadius=r,style=Stroke(1.2f*density))}
}

/** Semantic palette shared by native overlay controls and its existing glass surface. */
object GlassPalette {
 fun tint(context:android.content.Context)=OverlayColors.parse((context.applicationContext as? cn.jieti.JietiApp)?.overlayColor?.value?:"#819A87")
 fun isDark(context:android.content.Context):Boolean {val theme=(context.applicationContext as? cn.jieti.JietiApp)?.uiTheme?.value?:"system";return theme=="dark"||(theme=="system"&&context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)}
 fun ink(context:android.content.Context)=if(isDark(context))0xFFE3E7DC.toInt()else 0xFF30392F.toInt()
 fun muted(context:android.content.Context)=if(isDark(context))0xFFC2CABB.toInt()else 0xFF53614E.toInt()
}
