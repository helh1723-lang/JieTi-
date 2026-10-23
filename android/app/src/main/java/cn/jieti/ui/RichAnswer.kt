package cn.jieti.ui

import android.content.Context
import android.webkit.*
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayInputStream

class RichAnswer(context:Context):WebView(context){
 private val tasks=android.os.Handler(android.os.Looper.getMainLooper())
 private var pending="";private var pendingFollow=true;private var pendingArtifact:String?=null;private var ready=false;private var renderedKey=""
 private var pageKey="";private var animateNext=false
 private var conversation:List<Pair<String,String>>?=null
 var onArtifactRendered:((android.graphics.Bitmap)->Unit)?=null
 var onArtifactFailure:((String)->Unit)?=null
 init{
  settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false;settings.setSupportMultipleWindows(false);setBackgroundColor(android.graphics.Color.TRANSPARENT)
  val app=context.applicationContext as? cn.jieti.JietiApp
  settings.textZoom=((app?.uiFontScale?.value?:1f)*context.resources.configuration.fontScale*100).toInt()
  webViewClient=object:WebViewClient(){
   override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true
   override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest?):WebResourceResponse?{
    val u=request?.url?:return blocked();if(u.scheme in listOf("data","about"))return null;if(u.scheme=="https"&&u.host=="jieti.local"&&u.path?.startsWith("/assets/")==true){val file=u.path!!.removePrefix("/assets/");if(file.contains(".."))return blocked();return runCatching{WebResourceResponse(when(file.substringAfterLast('.')){"html"->"text/html";"js"->"text/javascript";"css"->"text/css";"woff2"->"font/woff2";"woff"->"font/woff";else->"application/octet-stream"},"UTF-8",context.assets.open(file))}.getOrElse{blocked()}};return blocked()
   }
   override fun onPageFinished(view:WebView?,url:String?){ready=true;renderedKey="";val theme=app?.uiTheme?.value?:"system";val dark=theme=="dark"||(theme=="system"&&context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES);evaluateJavascript("document.documentElement.dataset.theme='"+(if(dark)"dark"else "light")+"'",null);val html=pendingArtifact;val chat=conversation;if(html!=null)showArtifact(html)else if(chat!=null)showConversation(chat)else showMarkdown(pending,pendingFollow)}
   override fun onRenderProcessGone(view:WebView?,detail:RenderProcessGoneDetail?):Boolean {ready=false;onArtifactFailure?.invoke("图解渲染进程已结束，保留文字说明");return true}
  }
  loadUrl("https://jieti.local/assets/answer.html")
 }
 fun prepareTransition(key:String){if(pageKey!=key){animateNext=pageKey.isNotBlank()&&android.animation.ValueAnimator.areAnimatorsEnabled();pageKey=key}}
 fun showConversation(messages:List<Pair<String,String>>){conversation=messages;pendingArtifact=null;val json=org.json.JSONArray();for((role,content)in messages)json.put(JSONObject().put("role",role).put("content",content));val key="c:$json";if(ready&&renderedKey!=key){renderedKey=key;evaluateJavascript("window.showConversation($json,$animateNext)",null);animateNext=false}}
 fun showMarkdown(markdown:String,followTail:Boolean=true){conversation=null;pending=markdown;pendingFollow=followTail;pendingArtifact=null;if(ready&&renderedKey!="m:$followTail:$markdown"){renderedKey="m:$followTail:$markdown";evaluateJavascript("window.showMarkdown("+JSONObject.quote(markdown)+","+followTail+","+animateNext+")",null);animateNext=false}}
 fun showArtifact(html:String){
  conversation=null;pendingArtifact=html;if(!ready||renderedKey=="a:$html")return;renderedKey="a:$html"
  val js=context.assets.open("katex/katex.min.js").bufferedReader().use{it.readText()}
  val css=context.assets.open("katex/katex.min.css").bufferedReader().use{it.readText()}.replace("url(fonts/","url(https://jieti.local/assets/katex/fonts/")
  evaluateJavascript("window.showArtifact("+JSONObject.quote(html)+","+JSONObject.quote(js)+","+JSONObject.quote(css)+","+animateNext+")",null);animateNext=false
  val timeout=Runnable{if(pendingArtifact==html){ready=false;onArtifactFailure?.invoke("图解运行超时，已切换为文字说明");stopLoading()}}
  tasks.postDelayed(timeout,3500)
  tasks.postDelayed({if(ready&&pendingArtifact==html)evaluateJavascript("'alive'"){tasks.removeCallbacks(timeout)}},600)
  tasks.postDelayed({if(ready&&pendingArtifact==html&&width>0&&height>0){runCatching{val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);draw(android.graphics.Canvas(bitmap));onArtifactRendered?.invoke(bitmap)}}},1500)
 }
 override fun destroy(){ready=false;pendingArtifact=null;tasks.removeCallbacksAndMessages(null);super.destroy()}
 private fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
}
