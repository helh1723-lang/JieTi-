package cn.jieti.capture

import android.app.*
import android.animation.ValueAnimator
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.view.*
import android.widget.*
import cn.jieti.*
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.ui.GlassPalette
import cn.jieti.ui.RichAnswer
import cn.jieti.ui.RichMessage
import cn.jieti.ui.RichDiagram
import cn.jieti.ui.liquidGlass
import cn.jieti.ui.BubbleDragFeedback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

class CaptureService:Service(){
 private val app get()=application as JietiApp
 private val wm by lazy{getSystemService(WindowManager::class.java)}
 private val main=Handler(Looper.getMainLooper());private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null;private var reader:ImageReader?=null
 private var bubble:View?=null;private var selection:View?=null;private var panel:View?=null;private var screenshotRequested=false
 private var frozen:Bitmap?=null;private var pendingAsset="";private var activeQuestion="";private var appending=false;private var closing=false
 private var conditionLabel:TextView?=null;private var answerView:RichAnswer?=null;private var status:TextView?=null
 private var submitting=false;private var viewingArtifact=false;private var conversationIds:Set<String> = emptySet()
 private var draft="";private var selectedMode=0;private var selectedSubject=0
 private var inputView:EditText?=null
 private var previewBox:LinearLayout?=null;private var previewImage:ImageView?=null
 private var menuBox:LinearLayout?=null;private var sendView:Button?=null;private var thinkingView:Button?=null
 private var bubbleFeedback:BubbleDragFeedback?=null
 private var bubbleSnap:ValueAnimator?=null;private var captureFromPanel=false
 private var panelGeometry:Rect?=null
 private var panelDialog:Dialog?=null;private var panelParams:WindowManager.LayoutParams?=null
 private var glassBlur=40
 private var readingPriority=true
 private var blurEnabled=false
 private val blurListener=java.util.function.Consumer<Boolean>{enabled->main.post{blurEnabled=enabled;applyGlassBlur()}}
 private fun applyGlassBlur(){panelDialog?.window?.setBackgroundBlurRadius(if(blurEnabled)dp(glassBlur*80/100)else 0);panel?.invalidate();menuBox?.invalidate()}
 private fun updateLayout(view:View,lp:WindowManager.LayoutParams){if(view===panel&&panelDialog!=null){panelParams=lp;panelDialog?.window?.attributes=lp}else wm.updateViewLayout(view,lp)}
 private fun hidePanel(){panelDialog?.hide();panel?.visibility=View.INVISIBLE}
 private val easing=android.view.animation.PathInterpolator(.22f,.7f,.2f,1f)
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="stop"){endLearning();return START_NOT_STICKY}
  if(closing)return START_NOT_STICKY
  if(projection!=null)return START_NOT_STICKY
  readingPriority=app.settings.get("overlay.readingPriority","true")=="true"
  glassBlur=app.settings.get("overlay.blur","40").toIntOrNull()?.coerceIn(0,100)?:40
  blurEnabled=wm.isCrossWindowBlurEnabled;wm.addCrossWindowBlurEnabledListener(mainExecutor,blurListener)
  val data=intent?.getParcelableExtra<Intent>("projection")?:run{stopSelf();return START_NOT_STICKY}
  val channel=NotificationChannel("learning","学习悬浮窗",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  val stop=PendingIntent.getService(this,1,Intent(this,CaptureService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
  val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(this,"learning").setSmallIcon(cn.jieti.R.drawable.ic_jieti).setContentTitle("截题 · 学习中").setContentText("点击悬浮球框选题目，截图预览后才会发送").setContentIntent(open).addAction(Notification.Action.Builder(null,"结束学习",stop).build()).setOngoing(true).build()
  startForeground(10,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  try{
   projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("code",Activity.RESULT_CANCELED),data)
   projection!!.registerCallback(object:MediaProjection.Callback(){override fun onStop(){if(!closing)stopSelf()}},main)
   scope.launch{selectedSubject=if(withContext(Dispatchers.IO){app.store.setting("currentScope","math")}=="signals")1 else 0};prepareReader();showBubble()
   scope.launch{app.overlayColor.collect{fun refresh(v:View){v.invalidate();if(v is android.view.ViewGroup)for(i in 0 until v.childCount)refresh(v.getChildAt(i))};panel?.let{refresh(it)};bubble?.let{refresh(it)};answerView?.applyOverlayColor()}}
   scope.launch{app.pendingFocus.collect{focus->if(focus!=null&&focus.questionId==activeQuestion){pendingAsset=focus.cropHash;draft="我问的是圈选的这里：";inputView?.setText(draft);updatePreview();showPanel()}}}
   scope.launch{app.agents.state.collect{s->if(panel!=null&&s.questionId==activeQuestion){status?.text=if(s.busy)s.phase else s.error.ifBlank{"继续问，或先自己试一试"};updateComposer();renderConversation();if(!s.busy&&s.error.isBlank())refreshConversation()}}}
  }catch(e:Exception){Toast.makeText(this,"屏幕捕获已结束，请重新开始学习",Toast.LENGTH_LONG).show();stopSelf()}
  return START_NOT_STICKY
 }
 private fun prepareReader(){
  val bounds=wm.maximumWindowMetrics.bounds;val w=bounds.width();val h=bounds.height();val density=resources.displayMetrics.densityDpi
  val old=reader;val next=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);reader=next
  // While idle drain frames without retaining the user's screen. A capture
  // temporarily leaves the post-hide frame queued, even if the page is static.
  next.setOnImageAvailableListener({r->if(!screenshotRequested)runCatching{r.acquireLatestImage()?.close()}},main)
  if(display==null)display=projection!!.createVirtualDisplay("Jieti Capture",w,h,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,next.surface,null,main)else{display!!.resize(w,h,density);display!!.surface=next.surface};old?.close()
 }
 override fun onConfigurationChanged(newConfig:android.content.res.Configuration){super.onConfigurationChanged(newConfig);if(projection!=null&&!closing){val wasSelecting=selection!=null;val hadPanel=panel!=null;closePanel();remove(selection);selection=null;screenshotRequested=false;prepareReader();if(hadPanel)showPanel()else{remove(bubble);bubble=null;showBubble()};if(wasSelecting){frozen?.recycle();frozen=null;Toast.makeText(this,"画面方向已改变，请重新框选",Toast.LENGTH_SHORT).show()}}}
 private fun params(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;windowAnimations=0;layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun remove(view:View?){if(view!=null){view.animate().cancel();if(view===bubble){bubbleFeedback?.reset();bubbleFeedback=null};if(view===panel&&panelDialog!=null){panelDialog?.dismiss();panelDialog=null;panelParams=null}else runCatching{wm.removeView(view)}}}
 private fun present(view:View,lp:WindowManager.LayoutParams,bubbleStyle:Boolean=false){
  val animate=ValueAnimator.areAnimatorsEnabled()
  if(animate){view.alpha=0f;view.scaleX=if(bubbleStyle).9f else .97f;view.scaleY=view.scaleX;view.translationY=dp(if(bubbleStyle)4 else 8).toFloat()}
  if(view===panel){
   val dialog=Dialog(this,cn.jieti.R.style.OverlayGlassTheme);panelDialog=dialog;panelParams=lp
   val window=dialog.window!!;window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
   window.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply{setColor(0x01FFFFFF);cornerRadius=dp(30).toFloat()})
   dialog.setContentView(view);window.decorView.setPadding(0,0,0,0);window.attributes=lp;dialog.setCancelable(false);dialog.show();window.attributes=lp;applyGlassBlur()
  }else wm.addView(view,lp)
  if(animate)view.post{if(view.isAttachedToWindow&&!closing){view.pivotX=lp.width-dp(36).toFloat();view.pivotY=dp(24).toFloat();view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(if(bubbleStyle)180 else 260).setInterpolator(easing).start()}}
 }
 private fun minimizePanel(){
  val view=panel?:return
  if(!ValueAnimator.areAnimatorsEnabled()){closePanel();showBubble();return}
  view.animate().cancel();view.animate().alpha(0f).scaleX(.97f).scaleY(.97f).translationY(dp(7).toFloat()).setDuration(130).setInterpolator(easing).withEndAction{if(panel===view&&!closing){closePanel();showBubble()}}.start()
 }
 private fun releaseCapture(){display?.release();display=null;reader?.close();reader=null;projection?.stop();projection=null;frozen?.recycle();frozen=null}
 private fun closePanel(){panelParams?.let{panelGeometry=Rect(it.x,it.y,it.x+it.width,it.y+it.height)};conversationJob?.cancel();conversationJob=null;inputView?.let{draft=it.text.toString()};inputView=null;previewBox=null;previewImage=null;menuBox=null;sendView=null;thinkingView=null;remove(panel);panel=null;answerView?.apply{onArtifactRendered=null;onArtifactFailure=null;stopLoading();destroy()};answerView=null;status=null}
 private fun endLearning(){
  if(closing)return;closing=true;bubbleSnap?.cancel();main.removeCallbacksAndMessages(null);scope.cancel();screenshotRequested=false;remove(selection);selection=null;releaseCapture();stopForeground(STOP_FOREGROUND_REMOVE)
  val views=listOfNotNull(bubble,panel)
  if(ValueAnimator.areAnimatorsEnabled()&&views.isNotEmpty()){
   for(view in views){view.animate().cancel();view.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(100).setInterpolator(easing).start()}
   main.postDelayed({remove(bubble);bubble=null;closePanel();stopSelf()},120)
  }else{remove(bubble);bubble=null;closePanel();stopSelf()}
 }
 private fun rounded(color:Int,radius:Int=20)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),0x66FFFFFF)}
 private fun showBubble(){
  if(bubble!=null||closing)return
  val root=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;elevation=dp(6).toFloat()}
  val feedback=BubbleDragFeedback(root,root.liquidGlass(28f,true,elastic=true));bubbleFeedback=feedback
  val text=TextView(this).apply{text="⌖";textSize=30f;gravity=Gravity.CENTER;setTextColor(GlassPalette.ink(this@CaptureService));contentDescription="框选题目，长按打开答疑"}
  root.addView(text,LinearLayout.LayoutParams(dp(52),dp(52)))
  val exit=TextView(this).apply{this.text="×";textSize=23f;gravity=Gravity.CENTER;setTextColor(GlassPalette.ink(this@CaptureService));contentDescription="关闭悬浮窗";setOnClickListener{endLearning()}}
  root.addView(exit,LinearLayout.LayoutParams(dp(44),dp(52)))
  val lp=params(dp(96),dp(52));lp.x=wm.maximumWindowMetrics.bounds.width()-dp(106);lp.y=dp(160).coerceAtMost(wm.maximumWindowMetrics.bounds.height()-dp(70))
  var x=0f;var y=0f;var sx=0;var sy=0;var moved=false;var lastX=0f;var lastY=0f;var lastTime=0L
  val longPress=Runnable{moved=true;feedback.release();showPanel()}
  text.setOnClickListener{appending=false;capture()}
  fun snap(){
   val edge=maxOf(0,wm.maximumWindowMetrics.bounds.width()-lp.width)
   val target=if(lp.x+lp.width/2<wm.maximumWindowMetrics.bounds.width()/2)minOf(dp(5),edge)else maxOf(0,edge-dp(5))
   bubbleSnap?.cancel()
   if(!ValueAnimator.areAnimatorsEnabled()){lp.x=target;updateLayout(root,lp);return}
   bubbleSnap=ValueAnimator.ofInt(lp.x,target).apply{duration=260;interpolator=easing;addUpdateListener{if(bubble===root&&root.isAttachedToWindow&&!closing){lp.x=it.animatedValue as Int;updateLayout(root,lp)}};start()}
  }
  text.setOnTouchListener{v,event->when(event.actionMasked){
   MotionEvent.ACTION_DOWN->{feedback.begin();lastX=event.rawX;lastY=event.rawY;lastTime=event.eventTime;bubbleSnap?.cancel();root.animate().cancel();root.alpha=1f;root.scaleX=1f;root.scaleY=1f;root.translationY=0f;x=event.rawX;y=event.rawY;sx=lp.x;sy=lp.y;moved=false;main.postDelayed(longPress,500)}
   MotionEvent.ACTION_MOVE->{if(kotlin.math.abs(event.rawX-x)+kotlin.math.abs(event.rawY-y)>ViewConfiguration.get(this).scaledTouchSlop){moved=true;main.removeCallbacks(longPress)};if(moved&&root.isAttachedToWindow){lp.x=(sx+event.rawX-x).toInt().coerceIn(0,maxOf(0,wm.maximumWindowMetrics.bounds.width()-lp.width));lp.y=(sy+event.rawY-y).toInt().coerceIn(0,maxOf(0,wm.maximumWindowMetrics.bounds.height()-lp.height));updateLayout(root,lp);feedback.move(event.rawX-lastX,event.rawY-lastY,event.eventTime-lastTime)};lastX=event.rawX;lastY=event.rawY;lastTime=event.eventTime}
   MotionEvent.ACTION_UP->{feedback.release();main.removeCallbacks(longPress);if(!moved)v.performClick()else if(root.isAttachedToWindow)snap()}
   MotionEvent.ACTION_CANCEL->{feedback.release();main.removeCallbacks(longPress);if(root.isAttachedToWindow)snap()}
  };true}
  text.setOnLongClickListener{showPanel();true};bubble=root;present(root,lp,true)
 }
 private fun capture(){
  if(closing||screenshotRequested||selection!=null)return
  if(!appending){activeQuestion="";draft="";conversationRows=emptyList();conversationQuestion="";conversationIds=emptySet();viewingArtifact=false}
  val source=reader?:return
  screenshotRequested=true
  runCatching{source.acquireLatestImage()?.close()}
  bubbleSnap?.cancel();bubbleFeedback?.reset();captureFromPanel=panel!=null
  // Retain the same window and WebView. Hiding must be immediate so the next
  // captured frame contains no overlay or exit/entrance animation.
  bubble?.visibility=View.INVISIBLE;panel?.let{view->
   getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken,0)
   hidePanel()
   panelParams?.let{it.flags=it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;updateLayout(view,it)}
  };menuBox?.visibility=View.GONE
  val deadline=SystemClock.uptimeMillis()+2500
  fun take(){
   if(closing||!screenshotRequested||reader!==source)return
   val image=runCatching{source.acquireLatestImage()}.getOrNull()
   if(image==null){if(SystemClock.uptimeMillis()<deadline)main.postDelayed({take()},50)else{screenshotRequested=false;Toast.makeText(this,"暂时无法取得画面，可尝试重新授权或导入系统截图",Toast.LENGTH_LONG).show();restoreCaptureOrigin()};return}
   screenshotRequested=false
   try{val plane=image.planes[0];val padded=Bitmap.createBitmap(plane.rowStride/plane.pixelStride,image.height,Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(plane.buffer);frozen?.recycle();frozen=Bitmap.createBitmap(padded,0,0,image.width,image.height);padded.recycle()}finally{image.close()}
   showSelection()
  }
  main.postDelayed({take()},150)
 }
 private fun showSelection(){
  val bitmap=frozen?:return;remove(selection)
  val view=object:View(this){
   val paint=Paint(Paint.ANTI_ALIAS_FLAG);val rect=RectF();var anchorX=0f;var anchorY=0f
   override fun onDraw(canvas:Canvas){canvas.drawBitmap(bitmap,null,Rect(0,0,width,height),paint);canvas.drawColor(0x44000000);if(!rect.isEmpty){paint.color=0xFF7B9967.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(2).toFloat();canvas.drawRect(rect,paint);paint.style=Paint.Style.FILL};paint.color=Color.WHITE;paint.textSize=dp(15).toFloat();canvas.drawText("拖动框选题目 · 点按取消",dp(25).toFloat(),dp(52).toFloat(),paint)}
   override fun performClick():Boolean=super.performClick()
   override fun onTouchEvent(e:MotionEvent):Boolean{when(e.actionMasked){MotionEvent.ACTION_DOWN->{anchorX=e.x;anchorY=e.y;rect.setEmpty()};MotionEvent.ACTION_MOVE->{rect.set(minOf(anchorX,e.x),minOf(anchorY,e.y),maxOf(anchorX,e.x),maxOf(anchorY,e.y));invalidate()};MotionEvent.ACTION_CANCEL->{remove(selection);selection=null;restoreCaptureOrigin()};MotionEvent.ACTION_UP->{if(rect.width()<dp(15)||rect.height()<dp(15)){remove(selection);selection=null;restoreCaptureOrigin();return true};val l=(rect.left/width*bitmap.width).toInt().coerceIn(0,bitmap.width-1);val t=(rect.top/height*bitmap.height).toInt().coerceIn(0,bitmap.height-1);val r=(rect.right/width*bitmap.width).toInt().coerceIn(l+1,bitmap.width);val b=(rect.bottom/height*bitmap.height).toInt().coerceIn(t+1,bitmap.height);val cropped=Bitmap.createBitmap(bitmap,l,t,r-l,b-t).let{if(it===bitmap)it.copy(Bitmap.Config.ARGB_8888,false)else it};scope.launch{try{pendingAsset=withContext(Dispatchers.IO){val bytes=ByteArrayOutputStream().use{cropped.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};app.store.addAsset(bytes)};showPanel();panel?.postOnAnimation{remove(selection);selection=null}}catch(e:CancellationException){throw e}catch(e:Exception){remove(selection);selection=null;restoreCaptureOrigin();Toast.makeText(this@CaptureService,"截图保存失败，请重试",Toast.LENGTH_SHORT).show()}finally{cropped.recycle()}}}};return true}
  };selection=view;wm.addView(view,params(-1,-1).apply{flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE})
 }
 private fun restoreCaptureOrigin(){if(captureFromPanel&&panel!=null){showPanel()}else{bubble?.visibility=View.VISIBLE;showBubble()}}
 private fun button(label:String,action:()->Unit)=Button(this).apply{
  text=label;textSize=13f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);isAllCaps=false
  minWidth=dp(48);minimumWidth=dp(48);minHeight=dp(48);minimumHeight=dp(48)
  setPadding(dp(10),0,dp(10),0);stateListAnimator=null;backgroundTintList=null
  background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x28748270),null,rounded(Color.WHITE,24))
  elevation=0f;setTextColor(GlassPalette.ink(this@CaptureService));contentDescription=label
  layoutParams=LinearLayout.LayoutParams(-2,dp(48))
  setOnClickListener{if(!closing)action()}
 }
 private val modes=listOf("轻提示","再提示一点","检查步骤","完整讲解","总结同类题")
 private val thinking=listOf("自动思考","快速","深入思考")
 private fun thinkingIndex()=when(app.settings.get("tutor.thinking","auto")){"off"->1;"on"->2;else->0}
 private fun updateComposer(){
  val busy=submitting||app.agents.state.value.busy
  sendView?.apply{text=if(busy)"■"else "↑";contentDescription=if(busy)"停止回答"else "发送"}
  thinkingView?.apply{text=thinking[thinkingIndex()]+"⌄";isEnabled=!busy}
 }
 private fun updatePreview(){
  previewBox?.visibility=if(pendingAsset.isBlank())View.GONE else View.VISIBLE
  previewImage?.setImageBitmap(if(pendingAsset.isBlank())null else BitmapFactory.decodeFile(app.store.asset(pendingAsset).path))
 }
 private fun submit(){
  val input=inputView?:return
  if(submitting)return
  if(app.agents.state.value.busy){app.agents.cancel();return}
  val text=input.text.toString();val hash=pendingAsset;if(text.isBlank()&&hash.isBlank())return
  val subject=if(selectedSubject==1)"signals"else "math"
  submitting=true;updateComposer()
  scope.launch{try{
   if(!appending||activeQuestion.isBlank()){
    activeQuestion=withContext(Dispatchers.IO){app.store.setSetting("currentScope",subject);app.store.put("question",payload(Question("新题目",subject,emptyList(),"","",if(hash.isBlank())emptyList()else listOf(hash),"help",emptyList(),"","","open",scopeOrigin="user"))).id};appending=true
   }else if(hash.isNotBlank())withContext(Dispatchers.IO){val q=app.store.get(activeQuestion)?:error("题目已不存在");app.store.edit(activeQuestion,buildJsonObject{put("assetIds",JsonArray((q.payload.strings("assetIds")+hash).distinct().map(::JsonPrimitive)))})}
   if(app.agents.send(activeQuestion,text,modes[selectedMode],if(hash.isBlank())emptyList()else listOf(hash))){
    viewingArtifact=false;pendingAsset="";input.setText("");draft="";menuBox?.visibility=View.GONE;updatePreview();refreshConversation()
   }
  }catch(e:CancellationException){throw e}catch(e:Exception){status?.text=e.message?:"发送失败，请重试"}finally{submitting=false;updateComposer()}}
 }
 private fun showDiagram(){scope.launch{
  val artifact=withContext(Dispatchers.IO){app.store.all("artifact").lastOrNull{it.payload.s("questionId")==activeQuestion}}
  if(artifact==null){status?.text="暂无图解，可向导师请求";return@launch}
  answerView?.onArtifactRendered={bitmap->scope.launch(Dispatchers.IO){try{if(artifact.payload.strings("assetIds").isEmpty()){val bytes=ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};val hash=app.store.addAsset(bytes);app.store.edit(artifact.id,buildJsonObject{put("assetIds",JsonArray(listOf(JsonPrimitive(hash))))})}}finally{bitmap.recycle()}}}
  viewingArtifact=true;answerView?.showArtifact(artifact.payload.s("html"))
 }}
 private fun showPanel(){
  if(closing)return
  bubbleSnap?.cancel();remove(bubble);bubble=null
  if(panel!=null){panel?.let{view->panelParams?.let{it.flags=it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv();updateLayout(view,it)};panelDialog?.show();view.visibility=View.VISIBLE;view.invalidate()};updatePreview();updateComposer();refreshConversation();return}
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;liquidGlass(30f,clear=true,frost={if(readingPriority)maxOf(.94f,glassBlur/100f)else glassBlur/100f});setPadding(dp(12),dp(8),dp(12),dp(6));elevation=dp(8).toFloat()}
  val screen=wm.maximumWindowMetrics.bounds;val saved=panelGeometry
  val lp=params(minOf(saved?.width()?:dp(480),screen.width()-dp(20)),minOf(saved?.height()?:dp(660),screen.height()-dp(90))).apply{flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;x=(saved?.left?:screen.width()-width-dp(10)).coerceIn(0,maxOf(0,screen.width()-width));y=(saved?.top?:dp(45)).coerceIn(0,maxOf(0,screen.height()-height));softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE}
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  val title=TextView(this).apply{text="截题 · 随身导师";textSize=15f;setPadding(dp(8),dp(9),0,dp(9));setTextColor(GlassPalette.ink(this@CaptureService))}
  header.addView(title,LinearLayout.LayoutParams(0,-2,1f));header.addView(button("−"){minimizePanel()}.apply{contentDescription="收起"});header.addView(button("×"){endLearning()}.apply{contentDescription="关闭悬浮窗"});root.addView(header)
  var px=0f;var py=0f;var sx=0;var sy=0
  title.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{px=e.rawX;py=e.rawY;sx=lp.x;sy=lp.y};MotionEvent.ACTION_MOVE->{lp.x=(sx+e.rawX-px).toInt().coerceIn(0,maxOf(0,screen.width()-lp.width));lp.y=(sy+e.rawY-py).toInt().coerceIn(0,maxOf(0,screen.height()-lp.height));updateLayout(root,lp)}};true}
  previewBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE}
  previewImage=ImageView(this).apply{setOnClickListener{if(pendingAsset.isNotBlank()){hidePanel();showBubble();startActivity(Intent(this@CaptureService,cn.jieti.ui.FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("assetId",pendingAsset).putExtra("questionId",activeQuestion))}};adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="待发送的题目截图"}
  previewBox!!.addView(previewImage,LinearLayout.LayoutParams(-1,dp(124)))
  val previewActions=LinearLayout(this);previewActions.addView(button("重新框选"){hidePanel();showSelection()});previewActions.addView(button("取消截图"){pendingAsset="";updatePreview()});previewBox!!.addView(previewActions);root.addView(previewBox)
  conditionLabel=TextView(this).apply{text="当前条件：沿用原题";textSize=12f;maxLines=2;setTextColor(GlassPalette.muted(this@CaptureService));setPadding(dp(8),dp(4),dp(8),dp(4))};root.addView(conditionLabel)
  status=TextView(this).apply{text="准备好后再发送，先慢慢想";textSize=11f;setTextColor(GlassPalette.muted(this@CaptureService));setPadding(dp(8),dp(3),0,dp(6))};root.addView(status)
  answerView=RichAnswer(this).apply{focusQuestionId=activeQuestion;onOpenFocus={hidePanel();showBubble()};setOverlayStyle();clipToOutline=true;outlineProvider=object:ViewOutlineProvider(){override fun getOutline(view:View,outline:Outline){outline.setRoundRect(0,0,view.width,view.height,dp(18).toFloat())}}};root.addView(answerView,LinearLayout.LayoutParams(-1,0,1f))
  answerView?.onArtifactFailure={message->viewingArtifact=false;status?.text=message;renderConversation()}
  val menu=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;liquidGlass(22f,clear=true,animate=false,frost={maxOf(.82f,glassBlur/100f)});setPadding(dp(8),dp(8),dp(8),dp(8))};menuBox=menu
  fun menuAction(label:String,action:()->Unit){menu.addView(button(label){menu.visibility=View.GONE;action()}.apply{gravity=Gravity.START or Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(-1,dp(48)))}
  fun showChoices(labels:List<String>,choose:(Int)->Unit){menu.removeAllViews();labels.forEachIndexed{index,label->menuAction(label){choose(index)}};menu.visibility=View.VISIBLE}
  fun showActions(){
   menu.removeAllViews()
   menuAction(if(readingPriority)"阅读优先：已开启"else "阅读优先：已关闭"){readingPriority=!readingPriority;app.settings.save("overlay.readingPriority",readingPriority.toString());applyGlassBlur()}
   menuAction("截图 / 补拍本题"){appending=activeQuestion.isNotBlank();capture()}
   menuAction("玻璃模糊度：$glassBlur%"){ 
    menu.removeAllViews()
    val label=TextView(this).apply{text="玻璃模糊度 $glassBlur%";textSize=14f;setTextColor(GlassPalette.ink(this@CaptureService));setPadding(dp(10),dp(8),dp(10),dp(6))};menu.addView(label)
    val slider=SeekBar(this).apply{max=100;progress=glassBlur;contentDescription="玻璃模糊度";progressTintList=android.content.res.ColorStateList.valueOf(0xFF657E60.toInt());thumbTintList=progressTintList}
    slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
     override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean){glassBlur=value;label.text="玻璃模糊度 $value%";applyGlassBlur()}
     override fun onStartTrackingTouch(bar:SeekBar?){}
     override fun onStopTrackingTouch(bar:SeekBar?){app.settings.save("overlay.blur",glassBlur.toString())}
    });menu.addView(slider,LinearLayout.LayoutParams(-1,dp(48)))
    menu.addView(TextView(this).apply{text=if(blurEnabled)"实时背景模糊 · 文字保持清晰"else "系统未开放实时模糊，当前调整玻璃雾度，背景保持实时透视";textSize=12f;setTextColor(GlassPalette.muted(this@CaptureService));setPadding(dp(10),0,dp(10),dp(8))})
    menu.visibility=View.VISIBLE
   }
   menuAction("查看 / 调整当前条件"){
    if(activeQuestion.isBlank()){status?.text="发送一道题后可调整条件"}else scope.launch{val q=withContext(Dispatchers.IO){app.store.get(activeQuestion)}?:return@launch;menu.removeAllViews();val field=EditText(this@CaptureService).apply{setText(q.payload.s("effectiveConditions"));hint="当前生效条件，留空恢复原题";setTextColor(GlassPalette.ink(this@CaptureService));setHintTextColor(GlassPalette.muted(this@CaptureService));minLines=2;maxLines=5};menu.addView(field);menu.addView(button("应用条件"){val text=field.text.toString();scope.launch{withContext(Dispatchers.IO){app.store.editUser(q.id,buildJsonObject{put("effectiveConditions",text)},q.rev)};menu.visibility=View.GONE;refreshConversation()}});menu.addView(button("恢复原题"){scope.launch{withContext(Dispatchers.IO){app.store.editUser(q.id,buildJsonObject{put("effectiveConditions","");put("temporaryAssumption","");put("assumptionSourceId","")},q.rev)};menu.visibility=View.GONE;refreshConversation()}});menu.visibility=View.VISIBLE}
   }
   menuAction("教学方式："+modes[selectedMode]){showChoices(modes){selectedMode=it;status?.text="教学方式："+modes[it]}}
   menuAction("学科："+if(selectedSubject==1)"信号与系统"else "数学"){if(activeQuestion.isNotBlank())status?.text="本题学科已固定，完成后可切换新题学科"else showChoices(listOf("数学","信号与系统")){selectedSubject=it}}
   menuAction("查看图解 / 返回对话"){if(viewingArtifact){viewingArtifact=false;renderConversation()}else showDiagram()}
   menuAction("重试上次提问"){if(!app.agents.retry())status?.text="当前没有需要重试的提问"}
   menuAction("完成本题"){if(app.agents.state.value.busy||submitting){status?.text="请先停止或等待当前回答"}else{if(activeQuestion.isNotBlank())scope.launch(Dispatchers.IO){app.store.edit(activeQuestion,buildJsonObject{put("status","done")});app.agents.queue("archive",activeQuestion)};pendingAsset="";appending=false;activeQuestion="";draft="";viewingArtifact=false;conversationRows=emptyList();conversationIds=emptySet();conversationQuestion="";closePanel();showBubble()}}
  }
  val menuScroll=object:ScrollView(this){override fun onMeasure(widthSpec:Int,heightSpec:Int){super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(minOf(dp(240),lp.height/2),MeasureSpec.AT_MOST))}}.apply{isFillViewport=false;addView(menu)}
  root.addView(menuScroll,LinearLayout.LayoutParams(-1,-2))
  // One quiet shared surface. Individual controls have only a touch ripple.
  val composer=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;liquidGlass(26f,clear=true,button=true);setPadding(dp(2),dp(2),dp(2),dp(2))}
  composer.addView(button("+"){if(menu.visibility==View.VISIBLE)menu.visibility=View.GONE else{showActions();menu.visibility=View.VISIBLE}}.apply{textSize=24f;contentDescription="更多选项"})
  val input=EditText(this).apply{hint="说说你卡在哪里…";contentDescription="向导师提问";textSize=14f;maxLines=4;minLines=1;setText(draft);background=null;setTextColor(GlassPalette.ink(this@CaptureService));setHintTextColor(GlassPalette.muted(this@CaptureService));setPadding(dp(4),dp(10),dp(4),dp(10));isSingleLine=false}
  if(draft.isBlank())input.setText(LocalDrafts(this).read("overlay-input")?.text?:"")
  input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){draft=s?.toString()?:"";LocalDrafts(this@CaptureService).save("overlay-input",draft)};override fun afterTextChanged(s:android.text.Editable?){} })
  inputView=input;composer.addView(input,LinearLayout.LayoutParams(0,-2,1f))
  thinkingView=button(thinking[thinkingIndex()]+"⌄"){showChoices(thinking){app.settings.save("tutor.thinking",listOf("auto","off","on")[it]);updateComposer()}}.apply{textSize=11f;setPadding(dp(4),0,dp(4),0);contentDescription="选择思考程度"}
  composer.addView(thinkingView);sendView=button("↑"){submit()}.apply{textSize=23f;contentDescription="发送"};composer.addView(sendView);root.addView(composer,LinearLayout.LayoutParams(-1,-2))
  val resize=TextView(this).apply{text="⌟";gravity=Gravity.END;textSize=16f;setTextColor(GlassPalette.muted(this@CaptureService));contentDescription="调整窗口大小"}
  resize.setOnTouchListener{_,e->if(e.actionMasked==MotionEvent.ACTION_MOVE){lp.width=(e.rawX-lp.x).toInt().coerceIn(minOf(dp(320),screen.width()-lp.x),screen.width()-lp.x);lp.height=(e.rawY-lp.y).toInt().coerceIn(minOf(dp(360),screen.height()-lp.y),screen.height()-lp.y);updateLayout(root,lp)};true};root.addView(resize,LinearLayout.LayoutParams(-1,dp(20)))
  panel=root;present(root,lp);updatePreview();updateComposer();refreshConversation()
 }
  private var conversationRows:List<RichMessage> = emptyList();private var conversationDiagrams:List<RichDiagram> = emptyList();private var conversationQuestion="";private var conversationJob:Job?=null
 private fun renderConversation(){
  answerView?.focusQuestionId=activeQuestion
  if(viewingArtifact||conversationQuestion!=activeQuestion)return;val s=app.agents.state.value;if(s.questionId==activeQuestion)status?.text=if(s.busy)s.phase else s.error.ifBlank{"继续问，或先自己试一试"};val tail=if(s.questionId==activeQuestion&&s.text.isNotBlank()&&s.responseId !in conversationIds)listOf(RichMessage("stream","assistant",s.text))else emptyList();answerView?.showThread(conversationRows+tail,conversationDiagrams)}
 private fun refreshConversation(){
  if(activeQuestion.isBlank())return
  if(conversationJob?.isActive==true&&conversationQuestion==activeQuestion){renderConversation();return}
  conversationJob?.cancel()
  val id=activeQuestion;if(conversationQuestion!=id){conversationDiagrams=emptyList();conversationRows=emptyList();conversationIds=emptySet()};conversationQuestion=id
  conversationJob=scope.launch{app.store.dao.observeScreen(id).distinctUntilChanged().collect{rows->
   val question=rows.firstOrNull{it.id==id}?.let{wire.decodeFromString<SyncRecord>(it.json)};conditionLabel?.text="当前条件："+question?.payload?.s("effectiveConditions").orEmpty().ifBlank{"沿用原题"}
   val contents=withContext(Dispatchers.IO){rows.map{wire.decodeFromString<SyncRecord>(it.json)}.filter{!it.deleted&&it.payload.s("questionId")==id}.sortedBy{it.updatedAt}};val messages=contents.filter{it.kind=="message"}
   val deleted=withContext(Dispatchers.IO){app.store.get(id)?.deleted==true};if(activeQuestion==id&&deleted){activeQuestion="";appending=false;conversationRows=emptyList();conversationIds=emptySet();status?.text="这道题已删除。可以开始新的提问。";answerView?.showMarkdown("这道题已删除。原有对话已退出当前视图。",false);return@collect}
   if(activeQuestion==id){conversationRows=messages.map{RichMessage(it.id,it.payload.s("role"),it.payload.s("content"),it.payload.strings("assetIds"),it.payload.s("completion"))};conversationDiagrams=contents.filter{it.kind=="artifact"&&it.payload.s("status")=="ready"}.map{RichDiagram(it.id,it.payload.s("sourceMessageId"),it.payload.s("html"),it.payload.s("summary"))};conversationIds=messages.map{it.id}.toSet();renderConversation()}
  }}
 }

 override fun onDestroy(){runCatching{wm.removeCrossWindowBlurEnabledListener(blurListener)};closing=true;bubbleSnap?.cancel();main.removeCallbacksAndMessages(null);scope.cancel();screenshotRequested=false;remove(bubble);bubble=null;remove(selection);selection=null;closePanel();releaseCapture();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}
