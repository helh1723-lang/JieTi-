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
import cn.jieti.ui.RichAnswer
import cn.jieti.ui.liquidGlass
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
 private var answerView:RichAnswer?=null;private var status:TextView?=null
 private var submitting=false;private var viewingArtifact=false;private var conversationIds:Set<String> = emptySet()
 private var draft="";private var selectedMode=0;private var selectedSubject=0
 private var inputView:EditText?=null
 private var panelGeometry:Rect?=null
 private val easing=android.view.animation.PathInterpolator(.22f,.7f,.2f,1f)
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="stop"){endLearning();return START_NOT_STICKY}
  if(closing)return START_NOT_STICKY
  if(projection!=null)return START_NOT_STICKY
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
   scope.launch{app.agents.state.collect{s->if(panel!=null&&s.questionId==activeQuestion){status?.text=if(s.busy)"导师正在思考…"else s.error.ifBlank{"继续问，或先自己试一试"};renderConversation();if(!s.busy&&s.error.isBlank())refreshConversation()}}}
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
 override fun onConfigurationChanged(newConfig:android.content.res.Configuration){super.onConfigurationChanged(newConfig);if(projection!=null&&!closing){val wasSelecting=selection!=null;remove(selection);selection=null;screenshotRequested=false;prepareReader();if(panel!=null)showPanel()else{remove(bubble);bubble=null;showBubble()};if(wasSelecting){frozen?.recycle();frozen=null;Toast.makeText(this,"画面方向已改变，请重新框选",Toast.LENGTH_SHORT).show()}}}
 private fun params(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun remove(view:View?){if(view!=null){view.animate().cancel();runCatching{wm.removeView(view)}}}
 private fun present(view:View,lp:WindowManager.LayoutParams,bubbleStyle:Boolean=false){
  val animate=ValueAnimator.areAnimatorsEnabled()
  if(animate){view.alpha=0f;view.scaleX=if(bubbleStyle).9f else .97f;view.scaleY=view.scaleX;view.translationY=dp(if(bubbleStyle)4 else 8).toFloat()}
  wm.addView(view,lp)
  if(animate)view.post{if(view.isAttachedToWindow&&!closing){view.pivotX=lp.width-dp(36).toFloat();view.pivotY=dp(24).toFloat();view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(if(bubbleStyle)180 else 260).setInterpolator(easing).start()}}
 }
 private fun minimizePanel(){
  val view=panel?:return
  if(!ValueAnimator.areAnimatorsEnabled()){closePanel();showBubble();return}
  view.animate().cancel();view.animate().alpha(0f).scaleX(.97f).scaleY(.97f).translationY(dp(7).toFloat()).setDuration(130).setInterpolator(easing).withEndAction{if(panel===view&&!closing){closePanel();showBubble()}}.start()
 }
 private fun releaseCapture(){display?.release();display=null;reader?.close();reader=null;projection?.stop();projection=null;frozen?.recycle();frozen=null}
 private fun closePanel(){(panel?.layoutParams as? WindowManager.LayoutParams)?.let{panelGeometry=Rect(it.x,it.y,it.x+it.width,it.y+it.height)};conversationJob?.cancel();conversationJob=null;inputView?.let{draft=it.text.toString()};inputView=null;remove(panel);panel=null;answerView?.apply{onArtifactRendered=null;onArtifactFailure=null;stopLoading();destroy()};answerView=null;status=null}
 private fun endLearning(){
  if(closing)return;closing=true;main.removeCallbacksAndMessages(null);scope.cancel();screenshotRequested=false;remove(selection);selection=null;releaseCapture();stopForeground(STOP_FOREGROUND_REMOVE)
  val views=listOfNotNull(bubble,panel)
  if(ValueAnimator.areAnimatorsEnabled()&&views.isNotEmpty()){
   for(view in views){view.animate().cancel();view.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(100).setInterpolator(easing).start()}
   main.postDelayed({remove(bubble);bubble=null;closePanel();stopSelf()},120)
  }else{remove(bubble);bubble=null;closePanel();stopSelf()}
 }
 private fun rounded(color:Int,radius:Int=20)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),0x66FFFFFF)}
 private fun showBubble(){
  if(bubble!=null||closing)return
  val root=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;liquidGlass(28f,true);elevation=dp(6).toFloat()}
  val text=TextView(this).apply{text="⌖";textSize=30f;gravity=Gravity.CENTER;setTextColor(0xFF334B39.toInt());contentDescription="框选题目，长按打开答疑"}
  root.addView(text,LinearLayout.LayoutParams(dp(52),dp(52)))
  val exit=TextView(this).apply{this.text="×";textSize=23f;gravity=Gravity.CENTER;setTextColor(0xFF455740.toInt());contentDescription="关闭悬浮窗";setOnClickListener{endLearning()}}
  root.addView(exit,LinearLayout.LayoutParams(dp(44),dp(52)))
  val lp=params(dp(96),dp(52));lp.x=wm.maximumWindowMetrics.bounds.width()-dp(106);lp.y=dp(160).coerceAtMost(wm.maximumWindowMetrics.bounds.height()-dp(70))
  var x=0f;var y=0f;var sx=0;var sy=0;var moved=false
  val longPress=Runnable{moved=true;showPanel()}
  text.setOnClickListener{appending=false;capture()}
  text.setOnTouchListener{v,event->when(event.actionMasked){MotionEvent.ACTION_DOWN->{root.animate().cancel();lp.x+=root.translationX.toInt();root.translationX=0f;root.alpha=1f;root.scaleX=1f;root.scaleY=1f;root.translationY=0f;wm.updateViewLayout(root,lp);x=event.rawX;y=event.rawY;sx=lp.x;sy=lp.y;moved=false;main.postDelayed(longPress,500)};MotionEvent.ACTION_MOVE->{if(kotlin.math.abs(event.rawX-x)+kotlin.math.abs(event.rawY-y)>dp(6)){moved=true;main.removeCallbacks(longPress)};if(root.isAttachedToWindow){lp.x=(sx+event.rawX-x).toInt().coerceIn(0,maxOf(0,wm.maximumWindowMetrics.bounds.width()-lp.width));lp.y=(sy+event.rawY-y).toInt().coerceIn(0,maxOf(0,wm.maximumWindowMetrics.bounds.height()-dp(70)));wm.updateViewLayout(root,lp)}};MotionEvent.ACTION_UP->{main.removeCallbacks(longPress);if(!moved)v.performClick()else if(root.isAttachedToWindow){val target=if(lp.x<wm.maximumWindowMetrics.bounds.width()/2)dp(5)else wm.maximumWindowMetrics.bounds.width()-lp.width-dp(5);if(ValueAnimator.areAnimatorsEnabled())root.animate().translationX((target-lp.x).toFloat()).setDuration(240).setInterpolator(easing).withEndAction{if(bubble===root&&root.isAttachedToWindow&&!closing){lp.x=target;root.translationX=0f;wm.updateViewLayout(root,lp)}}.start()else{lp.x=target;wm.updateViewLayout(root,lp)}}};MotionEvent.ACTION_CANCEL->main.removeCallbacks(longPress)};true}
  text.setOnLongClickListener{showPanel();true};bubble=root;present(root,lp,true)
 }
 private fun capture(){
  if(closing)return
  if(!appending){activeQuestion="";draft="";conversationRows=emptyList();conversationQuestion="";conversationIds=emptySet();viewingArtifact=false}
  val source=reader?:return
  screenshotRequested=true
  runCatching{source.acquireLatestImage()?.close()}
  remove(bubble);bubble=null;closePanel()
  val deadline=SystemClock.uptimeMillis()+2500
  fun take(){
   if(closing||!screenshotRequested||reader!==source)return
   val image=runCatching{source.acquireLatestImage()}.getOrNull()
   if(image==null){if(SystemClock.uptimeMillis()<deadline)main.postDelayed({take()},50)else{screenshotRequested=false;Toast.makeText(this,"暂时无法取得画面，可尝试重新授权或导入系统截图",Toast.LENGTH_LONG).show();showBubble()};return}
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
   override fun onTouchEvent(e:MotionEvent):Boolean{when(e.actionMasked){MotionEvent.ACTION_DOWN->{anchorX=e.x;anchorY=e.y;rect.setEmpty()};MotionEvent.ACTION_MOVE->{rect.set(minOf(anchorX,e.x),minOf(anchorY,e.y),maxOf(anchorX,e.x),maxOf(anchorY,e.y));invalidate()};MotionEvent.ACTION_UP->{remove(selection);selection=null;if(rect.width()<dp(15)||rect.height()<dp(15)){showBubble();return true};val l=(rect.left/width*bitmap.width).toInt().coerceIn(0,bitmap.width-1);val t=(rect.top/height*bitmap.height).toInt().coerceIn(0,bitmap.height-1);val r=(rect.right/width*bitmap.width).toInt().coerceIn(l+1,bitmap.width);val b=(rect.bottom/height*bitmap.height).toInt().coerceIn(t+1,bitmap.height);val cropped=Bitmap.createBitmap(bitmap,l,t,r-l,b-t);val bytes=ByteArrayOutputStream().use{cropped.compress(Bitmap.CompressFormat.PNG,100,it);cropped.recycle();it.toByteArray()};pendingAsset=app.store.addAsset(bytes);showPanel()}};return true}
  };selection=view;wm.addView(view,params(-1,-1).apply{flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE})
 }
 private fun button(label:String,action:()->Unit)=Button(this).apply{
  text=label;textSize=13f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);isAllCaps=false
  minWidth=dp(48);minimumWidth=dp(48);minHeight=dp(44);minimumHeight=dp(44)
  setPadding(dp(11),0,dp(11),0);stateListAnimator=null;backgroundTintList=null
  liquidGlass(22f,button=true,primary=label=="发送")
  background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x38FFFFFF),background,rounded(Color.WHITE,22))
  elevation=dp(2).toFloat();outlineProvider=ViewOutlineProvider.BACKGROUND
  layoutParams=LinearLayout.LayoutParams(-2,dp(44)).apply{marginEnd=dp(4);topMargin=dp(4);bottomMargin=dp(4)}
  setTextColor(if(label=="发送")0xFF263E29.toInt()else if(label.startsWith("关闭"))0xFF754337.toInt()else 0xFF354332.toInt())
  setOnTouchListener{view,event->if(ValueAnimator.areAnimatorsEnabled()){
   val down=event.actionMasked==MotionEvent.ACTION_DOWN
   if(down||event.actionMasked in listOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL))view.animate().scaleX(if(down).965f else 1f).scaleY(if(down).965f else 1f).translationY(if(down)dp(1).toFloat()else 0f).translationZ(if(down)0f else dp(1).toFloat()).setDuration(if(down)80 else 220).setInterpolator(easing).start()
  };false}
  setOnClickListener{if(!closing)action()}
 }

 private fun showPanel(){
  if(closing)return
  closePanel();remove(bubble);bubble=null
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;liquidGlass(30f,image={frozen});setPadding(dp(16),dp(12),dp(16),dp(10));elevation=dp(12).toFloat()}
  val screen=wm.maximumWindowMetrics.bounds;val saved=panelGeometry
  val lp=params(minOf(saved?.width()?:dp(480),screen.width()-dp(20)),minOf(saved?.height()?:dp(660),screen.height()-dp(90))).apply{flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;x=(saved?.left?:screen.width()-width-dp(10)).coerceIn(0,maxOf(0,screen.width()-width));y=(saved?.top?:dp(45)).coerceIn(0,maxOf(0,screen.height()-height));softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE}
  val header=LinearLayout(this);val title=TextView(this).apply{text=if(pendingAsset.isNotBlank())"截题 · 预览并提问"else "截题 · 随身导师";textSize=15f;setPadding(dp(8),dp(9),0,dp(9));setTextColor(0xFF30392F.toInt())};header.addView(title,LinearLayout.LayoutParams(0,-2,1f));header.addView(button("收起"){minimizePanel()});header.addView(button("关闭 ×"){endLearning()}.apply{contentDescription="关闭悬浮窗"});root.addView(header)
  var px=0f;var py=0f;var sx=0;var sy=0
  title.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{px=e.rawX;py=e.rawY;sx=lp.x;sy=lp.y};MotionEvent.ACTION_MOVE->{lp.x=(sx+e.rawX-px).toInt().coerceIn(0,maxOf(0,screen.width()-lp.width));lp.y=(sy+e.rawY-py).toInt().coerceIn(0,maxOf(0,screen.height()-lp.height));wm.updateViewLayout(root,lp)}};true}
  if(pendingAsset.isNotBlank()){
   val preview=ImageView(this).apply{setImageBitmap(BitmapFactory.decodeFile(app.store.asset(pendingAsset).path));adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER};root.addView(preview,LinearLayout.LayoutParams(-1,dp(140)))
   val row=LinearLayout(this);row.addView(button("重新框选"){closePanel();showSelection()});row.addView(button("取消截图"){pendingAsset="";showPanel()});root.addView(row)
  }
  status=TextView(this).apply{text="准备好后再发送，先慢慢想";textSize=11f;setTextColor(0xFF747B6D.toInt());setPadding(dp(8),dp(5),0,dp(10))};root.addView(status)
  answerView=RichAnswer(this).apply{clipToOutline=true;outlineProvider=object:ViewOutlineProvider(){override fun getOutline(view:View,outline:Outline){outline.setRoundRect(0,0,view.width,view.height,dp(18).toFloat())}}};root.addView(answerView,LinearLayout.LayoutParams(-1,0,1f))
  answerView?.onArtifactFailure={message->status?.text=message;scope.launch{val a=withContext(Dispatchers.IO){app.store.all("artifact").lastOrNull{it.payload.s("questionId")==activeQuestion}};val fallback=TextView(this@CaptureService).apply{text=a?.payload?.s("summary")?:message;textSize=14f;setPadding(dp(12),dp(12),dp(12),dp(12))};val previous=answerView;val index=root.indexOfChild(previous);if(index>=0){root.removeView(previous);previous?.destroy();answerView=null;root.addView(fallback,index,LinearLayout.LayoutParams(-1,0,1f))}}}
  val subjects=Spinner(this);subjects.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("数学一","858 信号与系统"));subjects.setSelection(selectedSubject);subjects.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){selectedSubject=position};override fun onNothingSelected(parent:AdapterView<*>?) {}}
  val modes=Spinner(this);val choices=listOf("轻提示","再提示一点","检查步骤","完整讲解","总结同类题");modes.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,choices);modes.setSelection(selectedMode);modes.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){selectedMode=position};override fun onNothingSelected(parent:AdapterView<*>?) {}};val modeRow=LinearLayout(this);modeRow.addView(subjects,LinearLayout.LayoutParams(0,dp(44),1f));modeRow.addView(modes,LinearLayout.LayoutParams(0,dp(44),1f));root.addView(modeRow)
  val input=EditText(this).apply{hint="说说你卡在哪里…";contentDescription="向导师提问";textSize=14f;maxLines=4;minLines=1;setText(draft);liquidGlass(24f,true);setTextColor(0xFF30392F.toInt());setHintTextColor(0xFF7B8273.toInt());setPadding(dp(16),dp(11),dp(16),dp(11));isSingleLine=false};input.setOnFocusChangeListener{view,focused->if(ValueAnimator.areAnimatorsEnabled())view.animate().translationZ(if(focused)dp(3).toFloat()else 0f).setDuration(190).setInterpolator(easing).start()};inputView=input;root.addView(input,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6);bottomMargin=dp(6)})
  val actions=LinearLayout(this);actions.addView(button("发送"){
   val text=input.text.toString();val hash=pendingAsset;if(text.isBlank()&&hash.isBlank())return@button
   if(submitting||app.agents.state.value.busy){Toast.makeText(this@CaptureService,"请先停止或等待当前回答",Toast.LENGTH_SHORT).show();return@button}
   val subject=if(subjects.selectedItemPosition==1)"signals"else "math"
   submitting=true
   scope.launch{try{if(!appending||activeQuestion.isBlank()){val scopeName=subject;activeQuestion=withContext(Dispatchers.IO){app.store.setSetting("currentScope",scopeName);app.store.put("question",payload(Question("新题目",scopeName,emptyList(),"","",if(hash.isBlank())emptyList()else listOf(hash),"help",emptyList(),"","","open"))).id};appending=true}
    else if(hash.isNotBlank()){withContext(Dispatchers.IO){val q=app.store.get(activeQuestion)!!;app.store.edit(activeQuestion,buildJsonObject{put("assetIds",JsonArray((q.payload.strings("assetIds")+hash).distinct().map(::JsonPrimitive)))})}}
    val accepted=app.agents.send(activeQuestion,text,modes.selectedItem.toString(),if(hash.isBlank())emptyList()else listOf(hash));if(accepted){viewingArtifact=false;pendingAsset="";frozen?.recycle();frozen=null;input.setText("");draft="";if(hash.isNotBlank())showPanel()else refreshConversation()}
   }catch(e:CancellationException){throw e}catch(e:Exception){Toast.makeText(this@CaptureService,e.message?:"发送失败，请重试",Toast.LENGTH_LONG).show()}finally{submitting=false}}
  });actions.addView(button("停止"){app.agents.cancel()});actions.addView(button("重试"){app.agents.retry()});actions.addView(button("对话"){viewingArtifact=false;renderConversation()});actions.addView(button("图解"){scope.launch{val artifact=withContext(Dispatchers.IO){app.store.all("artifact").lastOrNull{it.payload.s("questionId")==activeQuestion}};if(artifact==null){Toast.makeText(this@CaptureService,"暂无图解，可向导师请求",Toast.LENGTH_SHORT).show()}else{answerView?.onArtifactRendered={bitmap->scope.launch(Dispatchers.IO){if(artifact.payload.strings("assetIds").isEmpty()){val bytes=ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray()};val hash=app.store.addAsset(bytes);app.store.edit(artifact.id,buildJsonObject{put("assetIds",JsonArray(listOf(JsonPrimitive(hash))))})}}};viewingArtifact=true;answerView?.showArtifact(artifact.payload.s("html"))}}});actions.addView(button("补拍本题"){appending=true;capture()});actions.addView(button("完成"){if(activeQuestion.isNotBlank())scope.launch(Dispatchers.IO){app.store.edit(activeQuestion,buildJsonObject{put("status","done")});app.agents.queue("archive",activeQuestion)};closePanel();pendingAsset="";appending=false;activeQuestion="";draft="";viewingArtifact=false;showBubble()});root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;clipToPadding=false;setPadding(0,dp(1),0,dp(3));addView(actions)})
  val resize=TextView(this).apply{text="↘";gravity=Gravity.END;textSize=18f;setTextColor(0xFF8A957F.toInt())};resize.setOnTouchListener{_,e->if(e.actionMasked==MotionEvent.ACTION_MOVE){lp.width=(e.rawX-lp.x).toInt().coerceIn(minOf(dp(320),screen.width()-lp.x),screen.width()-lp.x);lp.height=(e.rawY-lp.y).toInt().coerceIn(minOf(dp(360),screen.height()-lp.y),screen.height()-lp.y);wm.updateViewLayout(root,lp)};true};root.addView(resize,LinearLayout.LayoutParams(-1,dp(18)))
  panel=root;present(root,lp);refreshConversation()
 }
 private var conversationRows:List<Pair<String,String>> = emptyList();private var conversationQuestion="";private var conversationJob:Job?=null
 private fun renderConversation(){if(viewingArtifact||conversationQuestion!=activeQuestion)return;val s=app.agents.state.value;if(s.questionId==activeQuestion)status?.text=if(s.busy)"导师正在思考…"else s.error.ifBlank{"继续问，或先自己试一试"};val tail=if(s.questionId==activeQuestion&&s.text.isNotBlank()&&s.responseId !in conversationIds)listOf("assistant" to s.text)else emptyList();answerView?.setBackgroundColor(if(conversationRows.isEmpty()&&tail.isEmpty())Color.TRANSPARENT else 0xF0FFFEF8.toInt());answerView?.showConversation(conversationRows+tail)}
 private fun refreshConversation(){
  if(activeQuestion.isBlank())return
  if(conversationJob?.isActive==true&&conversationQuestion==activeQuestion){renderConversation();return}
  conversationJob?.cancel()
  val id=activeQuestion;if(conversationQuestion!=id){conversationRows=emptyList();conversationIds=emptySet()};conversationQuestion=id
  conversationJob=scope.launch{app.store.dao.observeMessages(id).distinctUntilChanged().collect{rows->
   val messages=withContext(Dispatchers.IO){rows.map{wire.decodeFromString<SyncRecord>(it.json)}.filter{!it.deleted}.sortedBy{it.updatedAt}}
   val deleted=withContext(Dispatchers.IO){app.store.get(id)?.deleted==true};if(activeQuestion==id&&deleted){activeQuestion="";appending=false;conversationRows=emptyList();conversationIds=emptySet();status?.text="这道题已删除。可以开始新的提问。";answerView?.showMarkdown("这道题已删除。原有对话已退出当前视图。",false);return@collect}
   if(activeQuestion==id){conversationRows=messages.map{it.payload.s("role") to it.payload.s("content")};conversationIds=messages.map{it.id}.toSet();renderConversation()}
  }}
 }

 override fun onDestroy(){closing=true;main.removeCallbacksAndMessages(null);scope.cancel();screenshotRequested=false;remove(bubble);bubble=null;remove(selection);selection=null;closePanel();releaseCapture();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}
