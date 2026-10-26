package cn.jieti.ui

import android.content.Context
import android.webkit.*
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayInputStream

data class RichMessage(val id:String,val role:String,val content:String,val assetIds:List<String> = emptyList(),val completion:String="")
data class RichDiagram(val id:String,val sourceMessageId:String,val html:String,val summary:String)
class RichAnswer(context:Context):WebView(context){
 private val tasks=android.os.Handler(android.os.Looper.getMainLooper())
 private var pending="";private var pendingFollow=true;private var pendingArtifact:String?=null;private var ready=false;private var renderedKey=""
 private var pageKey="";private var animateNext=false
 private var sentMessages=emptyMap<String,RichMessage>();private var sentOrder=emptyList<String>();private var sentDiagrams:List<RichDiagram>?=null;private var mathReady=false
 private var conversation:List<Pair<String,String>>?=null
 private var overlayStyle=false
 private var studyStyle=false
 private var thread:List<RichMessage>?=null;private var diagrams:List<RichDiagram> = emptyList()
 private val mathJs by lazy{context.assets.open("katex/katex.min.js").bufferedReader().use{it.readText()}+"\n"+context.assets.open("katex/contrib/auto-render.min.js").bufferedReader().use{it.readText()}}
 private val mathCss by lazy{context.assets.open("katex/katex.min.css").bufferedReader().use{it.readText()}.replace("url(fonts/","url(https://jieti.local/assets/katex/fonts/")}
 fun showStudyText(text:String){showMarkdown(text,false,true)}
 fun showThread(messages:List<RichMessage>,artifacts:List<RichDiagram>){
  thread=messages;diagrams=artifacts;conversation=null;pendingArtifact=null
  if(!ready)return
  if(renderedKey!="thread"){sentMessages=emptyMap();sentOrder=emptyList();sentDiagrams=null;renderedKey="thread"}
  if(artifacts.isNotEmpty()&&!mathReady){evaluateJavascript("window.setMathResources("+JSONObject.quote(mathJs)+","+JSONObject.quote(mathCss)+")",null);mathReady=true}
  val changed=messages.filter{sentMessages[it.id]!=it}
  val order=messages.map{it.id};val orderChanged=order!=sentOrder
  val diagramsChanged=sentDiagrams!=artifacts
  if(changed.isEmpty()&&!orderChanged&&!diagramsChanged)return
  val json=org.json.JSONArray();for(m in changed){val previous=sentMessages[m.id];val row=JSONObject().put("id",m.id).put("role",m.role).put("assetIds",org.json.JSONArray(m.assetIds)).put("completion",m.completion);if(previous!=null&&previous.role==m.role&&m.content.startsWith(previous.content))row.put("append",m.content.substring(previous.content.length))else row.put("content",m.content);json.put(row)}
  val pages=org.json.JSONArray();if(diagramsChanged)for(a in artifacts)pages.put(JSONObject().put("id",a.id).put("sourceMessageId",a.sourceMessageId).put("html",a.html).put("summary",a.summary))
  evaluateJavascript("window.patchConversation($json,"+(if(orderChanged)org.json.JSONArray(order).toString()else "null")+","+(if(diagramsChanged)pages.toString()else "null")+",$animateNext)",null)
  sentMessages=messages.associateBy{it.id};sentOrder=order;sentDiagrams=artifacts;animateNext=false
 }
 var onOpenFocus:(()->Unit)?=null
 var focusQuestionId:String=""
 fun applyOverlayColor(){if(ready){val hex=(context.applicationContext as? cn.jieti.JietiApp)?.overlayColor?.value?:"#819A87";evaluateJavascript("document.documentElement.style.setProperty('--glass-accent',"+JSONObject.quote(hex)+")",null)}}
 fun setOverlayStyle(){overlayStyle=true;setBackgroundColor(android.graphics.Color.TRANSPARENT);if(ready)evaluateJavascript("document.documentElement.dataset.overlay='true'",null)}
 var onArtifactRendered:((android.graphics.Bitmap)->Unit)?=null
 var onArtifactFailure:((String)->Unit)?=null
 init{
  settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false;settings.setSupportMultipleWindows(false);setBackgroundColor(android.graphics.Color.TRANSPARENT)
  val app=context.applicationContext as? cn.jieti.JietiApp
  settings.textZoom=((app?.uiFontScale?.value?:1f)*context.resources.configuration.fontScale*100).toInt()
  webViewClient=object:WebViewClient(){
   override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{val uri=request?.url;if(uri?.scheme=="https"&&uri.host=="jieti.local"&&uri.path?.startsWith("/focus/")==true){val hash=uri.path!!.removePrefix("/focus/");if(Regex("[a-f0-9]{64}").matches(hash)){onOpenFocus?.invoke();context.startActivity(android.content.Intent(context,FocusActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("assetId",hash).putExtra("questionId",focusQuestionId).putExtra("sourceId",uri.getQueryParameter("source").orEmpty()))}};return true}
   override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest?):WebResourceResponse?{
    val u=request?.url?:return blocked();if(u.scheme=="https"&&u.host=="jieti.local"&&u.path?.startsWith("/media/")==true){val hash=u.path!!.removePrefix("/media/");if(!Regex("[a-f0-9]{64}").matches(hash))return blocked();return runCatching{val file=(context.applicationContext as cn.jieti.JietiApp).store.asset(hash);val signature=file.inputStream().use{it.read()};WebResourceResponse(if(signature==0x89)"image/png"else if(signature==0x52)"image/webp"else "image/jpeg",null,file.inputStream())}.getOrElse{blocked()}};if(u.scheme in listOf("data","about"))return null;if(u.scheme=="https"&&u.host=="jieti.local"&&u.path?.startsWith("/assets/")==true){val file=u.path!!.removePrefix("/assets/");if(file.contains(".."))return blocked();return runCatching{WebResourceResponse(when(file.substringAfterLast('.')){"html"->"text/html";"js"->"text/javascript";"css"->"text/css";"woff2"->"font/woff2";"woff"->"font/woff";else->"application/octet-stream"},"UTF-8",200,"OK",mapOf("Access-Control-Allow-Origin" to "*"),context.assets.open(file))}.getOrElse{blocked()}};return blocked()
   }
   override fun onPageFinished(view:WebView?,url:String?){ready=true;applyOverlayColor();renderedKey="";mathReady=false;val theme=app?.uiTheme?.value?:"system";val dark=theme=="dark"||(theme=="system"&&context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES);evaluateJavascript("document.documentElement.dataset.theme='"+(if(dark)"dark"else "light")+"';document.documentElement.dataset.overlay='$overlayStyle'",null);val html=pendingArtifact;val chat=conversation;if(html!=null)showArtifact(html)else if(thread!=null)showThread(thread!!,diagrams)else if(chat!=null)showConversation(chat)else showMarkdown(pending,pendingFollow,studyStyle)}
   override fun onRenderProcessGone(view:WebView?,detail:RenderProcessGoneDetail?):Boolean {ready=false;onArtifactFailure?.invoke("图解渲染进程已结束，保留文字说明");return true}
  }
  loadUrl("https://jieti.local/assets/answer.html")
 }
 fun prepareTransition(key:String){if(pageKey!=key){animateNext=pageKey.isNotBlank()&&android.animation.ValueAnimator.areAnimatorsEnabled();pageKey=key}}
 fun showConversation(messages:List<Pair<String,String>>){thread=null;studyStyle=false;conversation=messages;pendingArtifact=null;val json=org.json.JSONArray();for((role,content)in messages)json.put(JSONObject().put("role",role).put("content",content));val key="c:$json";if(ready&&renderedKey!=key){renderedKey=key;evaluateJavascript("window.showConversation($json,$animateNext)",null);animateNext=false}}
 fun showMarkdown(markdown:String,followTail:Boolean=true,study:Boolean=false){studyStyle=study;thread=null;conversation=null;pending=markdown;pendingFollow=followTail;pendingArtifact=null;if(ready&&renderedKey!="m:$followTail:$studyStyle:$markdown"){renderedKey="m:$followTail:$studyStyle:$markdown";evaluateJavascript("window.showMarkdown("+JSONObject.quote(markdown)+","+followTail+","+animateNext+","+studyStyle+")",null);animateNext=false}}
 fun showArtifact(html:String){
  thread=null;conversation=null;pendingArtifact=html;if(!ready||renderedKey=="a:$html")return;renderedKey="a:$html"
  val js=mathJs;val css=mathCss
  evaluateJavascript("window.showArtifact("+JSONObject.quote(html)+","+JSONObject.quote(js)+","+JSONObject.quote(css)+","+animateNext+")",null);animateNext=false
  val timeout=Runnable{if(pendingArtifact==html){ready=false;onArtifactFailure?.invoke("图解运行超时，已切换为文字说明");stopLoading()}}
  tasks.postDelayed(timeout,3500)
  tasks.postDelayed({if(ready&&pendingArtifact==html)evaluateJavascript("'alive'"){tasks.removeCallbacks(timeout)}},600)
  tasks.postDelayed({if(ready&&pendingArtifact==html&&onArtifactRendered!=null&&width>0&&height>0){runCatching{val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);draw(android.graphics.Canvas(bitmap));onArtifactRendered?.invoke(bitmap)}}},1500)
 }
 override fun destroy(){ready=false;pendingArtifact=null;tasks.removeCallbacksAndMessages(null);super.destroy()}
 private fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
}
