package cn.jieti.ui

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.jieti.*
import cn.jieti.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

data class FocusSelection(val questionId:String,val originalHash:String,val cropHash:String,val rect:String,val sourceId:String)
class FocusActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val app=application as JietiApp;val hash=intent.getStringExtra("assetId")?:return;val questionId=intent.getStringExtra("questionId").orEmpty();val source=intent.getStringExtra("sourceId").orEmpty()
  if(!Regex("[a-f0-9]{64}").matches(hash)){finish();return}
  setContent{val theme by app.uiTheme.collectAsState();val font by app.uiFontScale.collectAsState();JietiTheme(theme,font){
   var bitmap by remember{mutableStateOf<Bitmap?>(null)};var error by remember{mutableStateOf("")};var selecting by remember{mutableStateOf(false)};var selected by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var zoom by remember{mutableIntStateOf(1)};val coroutine=rememberCoroutineScope();var view by remember{mutableStateOf<FocusImageView?>(null)}
   LaunchedEffect(hash){try{bitmap=withContext(Dispatchers.IO){val opts=BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(app.store.asset(hash).path,opts);var sample=1;while(maxOf(opts.outWidth,opts.outHeight)/sample>4096)sample*=2;BitmapFactory.decodeFile(app.store.asset(hash).path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("无法打开图片")}}catch(e:Exception){error=e.message?:"图片无法打开"}}
   Surface(color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)){
    Row{TextButton(onClick={finish()}){Text("‹ 返回")};Text("原图定位",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f).padding(top=10.dp));TextButton(onClick={zoom=if(zoom>=4)1 else zoom+1;view?.setZoom(zoom.toFloat())}){Text("${zoom}×")}}
    Text("双指缩放，单指移动；点击圈选后划出要问的区域。",style=MaterialTheme.typography.bodySmall)
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    val image=bitmap
    if(image!=null)AndroidView(factory={FocusImageView(it,image).also{v->view=v;v.onSelection={selected=it}}},update={it.selecting=selecting},modifier=Modifier.fillMaxWidth().weight(1f))else Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth().liquidSurface().padding(4.dp)){
     TextButton(onClick={selecting=!selecting}){Text(if(selecting)"完成圈选模式"else "圈选区域")}
     TextButton(onClick={view?.reset();selected=false;zoom=1}){Text("重置")}
     Spacer(Modifier.weight(1f))
     TextButton(enabled=selected&&!busy&&questionId.isNotBlank(),onClick={val target=view?:return@TextButton;busy=true;coroutine.launch{try{val focus=withContext(Dispatchers.IO){val rect=target.selectedPixels();val crop=Bitmap.createBitmap(image!!,rect.left,rect.top,rect.width(),rect.height());val bytes=ByteArrayOutputStream().use{crop.compress(Bitmap.CompressFormat.PNG,100,it);if(crop!==image)crop.recycle();it.toByteArray()};val normalized=buildJsonObject{put("left",rect.left.toDouble()/image.width);put("top",rect.top.toDouble()/image.height);put("right",rect.right.toDouble()/image.width);put("bottom",rect.bottom.toDouble()/image.height)};FocusSelection(questionId,hash,app.store.addAsset(bytes),normalized.toString(),source)};app.pendingFocus.value=focus;finish()}catch(e:Exception){error=e.message?:"圈选保存失败"}finally{busy=false}}}){Text(if(busy)"保存中…"else "问这里")}
    }
    if(questionId.isBlank())Text("当前为资料阅读；从题目引用进入可圈选追问。",style=MaterialTheme.typography.bodySmall)
   }}
  }}
 }
}
/** Selection is in image coordinates, unaffected by pan or zoom. */
class FocusImageView(context:Context,private val bitmap:Bitmap):View(context){
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private val transform=Matrix();private val inverse=Matrix();private val selection=RectF();private var scale=1f;private var x=0f;private var y=0f;private var lastX=0f;private var lastY=0f;private var anchorX=0f;private var anchorY=0f;private var multi=false
 var selecting=false;var onSelection:((Boolean)->Unit)?=null
 private val detector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{val old=scale;scale=(scale*d.scaleFactor).coerceIn(1f,8f);val ratio=scale/old;x=d.focusX-(d.focusX-x)*ratio;y=d.focusY-(d.focusY-y)*ratio;invalidate();return true}})
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){reset()}
 fun reset(){scale=1f;x=0f;y=0f;selection.setEmpty();onSelection?.invoke(false);invalidate()}
 fun setZoom(value:Float){scale=value.coerceIn(1f,8f);x=0f;y=0f;invalidate()}
 private fun matrix(){val fit=minOf(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height);transform.reset();transform.postScale(fit*scale,fit*scale);transform.postTranslate((width-bitmap.width*fit*scale)/2+x,(height-bitmap.height*fit*scale)/2+y);transform.invert(inverse)}
 override fun onDraw(canvas:Canvas){val checkpoint=canvas.save();canvas.clipRect(0,0,width,height);matrix();paint.color=android.graphics.Color.WHITE;paint.style=Paint.Style.FILL;canvas.drawBitmap(bitmap,transform,paint);if(!selection.isEmpty){val rect=RectF(selection);transform.mapRect(rect);paint.color=0x28536C59;canvas.drawRect(rect,paint);paint.color=GlassPalette.ink(context);paint.style=Paint.Style.STROKE;paint.strokeWidth=2*resources.displayMetrics.density;canvas.drawRect(rect,paint);paint.style=Paint.Style.FILL};canvas.restoreToCount(checkpoint)}
 fun selectedPixels():Rect {val left=selection.left.toInt().coerceIn(0,bitmap.width-1);val top=selection.top.toInt().coerceIn(0,bitmap.height-1);return Rect(left,top,selection.right.toInt().coerceIn(left+1,bitmap.width),selection.bottom.toInt().coerceIn(top+1,bitmap.height))}
 private fun point(e:MotionEvent):FloatArray{matrix();val p=floatArrayOf(e.x,e.y);inverse.mapPoints(p);p[0]=p[0].coerceIn(0f,bitmap.width.toFloat());p[1]=p[1].coerceIn(0f,bitmap.height.toFloat());return p}
 override fun onTouchEvent(e:MotionEvent):Boolean{detector.onTouchEvent(e);if(e.pointerCount>1)multi=true
  when(e.actionMasked){MotionEvent.ACTION_DOWN->{multi=false;lastX=e.x;lastY=e.y;val p=point(e);anchorX=p[0];anchorY=p[1];if(selecting){selection.setEmpty();onSelection?.invoke(false)}};MotionEvent.ACTION_MOVE->if(!multi){if(selecting){val p=point(e);selection.set(minOf(anchorX,p[0]),minOf(anchorY,p[1]),maxOf(anchorX,p[0]),maxOf(anchorY,p[1]));onSelection?.invoke(selection.width()>5&&selection.height()>5)}else{x+=e.x-lastX;y+=e.y-lastY};lastX=e.x;lastY=e.y;invalidate()};MotionEvent.ACTION_UP->{performClick();onSelection?.invoke(selection.width()>5&&selection.height()>5)}};return true
 }
 override fun performClick():Boolean{super.performClick();return true}
}
