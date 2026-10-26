package cn.jieti

import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ai.*
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.library.Library
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Explicitly opt in by piping credentials into this private temporary file.
 * No credentials in instrumentation arguments, source, logs or test reports. */
@RunWith(AndroidJUnit4::class) class LiveProviderAcceptanceTest {
 @Test fun liveOverlaySession()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val credentialsFile=File(app.filesDir,"live-overlay-credentials.json")
  assumeTrue("Explicit live UI acceptance only",credentialsFile.exists())
  val credentials=try{wire.parseToJsonElement(credentialsFile.readText()).jsonObject}finally{credentialsFile.delete()}
  val context=object:ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("overlay-test-$name",mode)}
  val settings=SecureSettings(context);val foreground=AtomicInteger();val provider=Provider(settings,foreground)
  val store=LearningStore(app,"overlay-acceptance-${uid()}.sqlite");val memory=MemoryEngine(store);val library=Library(app,store,provider,settings,foreground)
  val agents=Agents(app,store,provider,memory,library,foreground){_,_,_->}
  val oldStore=app.store;val oldSettings=app.settings;val oldProvider=app.provider;val oldAgents=app.agents;val oldLibrary=app.library;val oldMemory=app.memory
  val ready=File(app.filesDir,"overlay-test-ready");val done=File(app.filesDir,"overlay-test-done");done.delete()
  try{
   settings.save("apiKey",credentials.s("apiKey"));settings.save("baseUrl",credentials.s("baseUrl"));settings.save("model.tutor","Qwen3.5-35B-A3B");settings.save("tutor.thinking","off")
   store.setSetting("learnPreferences","false");store.setSetting("autoEvolution","false")
   store.put("question",payload(Question("合成验收：2 + 3 = ?","math",emptyList(),"2+3=?","",emptyList(),"help",emptyList(),"","","open")))
   app.store=store;app.settings=settings;app.provider=provider;app.agents=agents;app.library=library;app.memory=memory
   ready.writeText("ready")
   withTimeout(600000){while(!done.exists())delay(200)}
   assertEquals("",agents.state.value.error)
   assertTrue("UI must complete a real request",store.all("message").any{it.payload.s("role")=="assistant"&&it.payload.s("content").contains("5")})
   android.util.Log.i("JietiLive","PASS real overlay screenshot-send-followup session")
  }finally{
   app.stopService(android.content.Intent(app,cn.jieti.capture.CaptureService::class.java));agents.cancel();delay(300)
   app.store=oldStore;app.settings=oldSettings;app.provider=oldProvider;app.agents=oldAgents;app.library=oldLibrary;app.memory=oldMemory
   context.getSharedPreferences("protected_settings",Context.MODE_PRIVATE).edit().clear().commit();ready.delete();done.delete();store.db.close()
  }
 }
 @Test fun liveAppProviderAndAgentFlows()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val file=File(app.filesDir,"live-test-credentials.json")
  assumeTrue("Live API acceptance requires an explicit credential handoff",file.exists())
  val credentials=try{wire.parseToJsonElement(file.readText()).jsonObject}finally{file.delete()}
  val context=object:ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("live-test-$name",mode)}
  val settings=SecureSettings(context);val foreground=AtomicInteger();var latestTutor=JsonArray(emptyList())
  val provider=object:Provider(settings,foreground){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{if(role=="tutor"){latestTutor=messages;File(app.cacheDir,"synthetic-live-tutor-request.json").writeText(messages.toString())};return super.chat(role,messages,tools,stream,priority,jsonMode,onDelta)}}
  val store=LearningStore(app,"live-acceptance-${uid()}.sqlite")
  fun pass(name:String){android.util.Log.i("JietiLive","PASS $name")}
  try{
   settings.save("apiKey",credentials.s("apiKey"));settings.save("baseUrl",credentials.s("baseUrl"))
   val model=credentials.s("model").ifBlank{"Qwen3.5-397B-A17B"}
   for(role in listOf("tutor","archive","verify","artifact","strategy"))settings.save("model.$role",model)
   settings.save("model.embedding","Qwen3-Embedding-0.6B");settings.save("tutor.thinking","off")
   assertTrue(provider.models().contains(model));pass("GET models")
   settings.save("apiKey","invalid-synthetic-acceptance-key")
   try{provider.chat("tutor",buildJsonArray{add(Provider.message("user","验收：只回复OK"))},stream=false);fail("Invalid credentials accepted")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("凭据"))}finally{settings.save("apiKey",credentials.s("apiKey"))};pass("invalid credentials error mapping")
   var deltas="";val stream=provider.chat("tutor",buildJsonArray{add(Provider.message("user","只回复数字4：2+2等于多少？"))}){deltas+=it}
   assertTrue(stream.text.contains("4"));assertEquals(stream.text,deltas);pass("stream text + quick thinking")
   settings.save("tutor.thinking","on")
   assertTrue(provider.chat("tutor",buildJsonArray{add(Provider.message("user","1+1等于多少？只回复数字。"))}).text.contains("2"));pass("deep thinking stream")
   settings.save("tutor.thinking","off")
   val cancellationStart=System.nanoTime()
   try{withTimeout(350){provider.chat("tutor",buildJsonArray{add(Provider.message("user","写一篇1000字的合成教学说明。"))})};fail("Expected timed cancellation")}catch(_:TimeoutCancellationException){}
   assertTrue("Cancellation blocked",System.nanoTime()-cancellationStart<5_000_000_000L);pass("in-flight HTTP cancellation")
   for(role in listOf("archive","verify","artifact","strategy")){
    val result=provider.json(role,"这是合成API验收。仅返回 {\"ok\":true}","验证JSON返回")
    assertTrue("$role JSON",result.b("ok"));pass("$role non-stream JSON")
   }
   val vectors=provider.embeddings(listOf("合成课程：卷积支撑区间","synthetic convolution"))
   assertEquals(2,vectors.size);assertTrue(vectors.all{it.size==1024&&it.all(Float::isFinite)});pass("POST embeddings batch 1024")
   val tools=buildJsonArray{add(buildJsonObject{put("type","function");putJsonObject("function"){put("name","lookup_test");put("description","查询验收口令，必须通过此工具获取，不得猜测");putJsonObject("parameters"){put("type","object");putJsonObject("properties"){} }}})}
   val messages=buildJsonArray{add(Provider.message("user","调用 lookup_test 工具查询口令，然后告诉我口令。必须调用工具。"))}
   val turn=provider.chat("tutor",messages,tools)
   assertTrue("tool call absent",turn.toolCalls.isNotEmpty());val call=turn.toolCalls.first().jsonObject
   val continuation=buildJsonArray{addAll(messages);add(buildJsonObject{put("role","assistant");put("content",turn.text);put("tool_calls",turn.toolCalls)});add(buildJsonObject{put("role","tool");put("tool_call_id",call.s("id"));put("content","SYNTHETIC-7429")})}
   assertTrue(provider.chat("tutor",continuation).text.contains("7429"));pass("stream function arguments + tool continuation")
   val bitmap=Bitmap.createBitmap(640,180,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE)
   Canvas(bitmap).drawText("SYNTHETIC: 2 + 3 = ?",25f,100f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=38f})
   val bytes=ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};bitmap.recycle()
   val hash=store.addAsset(bytes);val library=Library(app,store,provider,settings,foreground)
   val doc=store.put("document",payload(Document("合成截图OCR","live",listOf(hash),"png","queued",0,0,"",false)))
   assertTrue(library.process(doc.id));assertEquals("ready",store.get(doc.id)!!.payload.s("status"));assertTrue(library.search("live","SYNTHETIC").isNotEmpty());pass("vision OCR -> local search")
   val agents=Agents(app,store,provider,MemoryEngine(store),library,foreground){_,_,_->}
   store.setSetting("learnPreferences","false");store.setSetting("autoEvolution","false")
   val q=store.put("question",payload(Question("合成截图题","live",listOf("加法"),"2+3=?","",listOf(hash),"help",emptyList(),"","","open")))
   suspend fun awaitReply(){withTimeout(240000){while(agents.state.value.busy)delay(80)};assertEquals("",agents.state.value.error);assertTrue(agents.state.value.text.isNotBlank())}
   assertTrue(agents.send(q.id,"请完整讲解图片中的加法题，不生成图解。","完整讲解",listOf(hash)));awaitReply();assertTrue(agents.state.value.text.contains("5"));pass("Agents screenshot tutor persisted")
   val followup="接着上一题：把第二个加数改成4，结果是多少？只写结果。"
   assertTrue(agents.send(q.id,followup,"完整讲解"));awaitReply()
   assertEquals(followup,latestTutor.last().jsonObject.s("content"));assertEquals("user",latestTutor.last().jsonObject.s("role"))
   val followupAnswer=agents.state.value.text
   if(followupAnswer.contains("6"))pass("Agents same-question follow-up")else android.util.Log.w("JietiLive","FAIL latest-condition answer: "+followupAnswer.take(150))
   agents.archive(q.id);assertTrue(store.get(q.id)!!.payload.s("solution").isNotBlank());pass("archive + verify persistence")
   agents.createArtifact(q.id,"轻提示","生成一张极简SVG，用五个圆点表示2加3，页面背景透明，正文简短。")
   assertTrue(store.all("artifact").any{it.payload.s("questionId")==q.id&&it.payload.s("html").length>=30});pass("interactive artifact JSON persisted")
   assertTrue("Latest-condition answer: "+followupAnswer.take(150),followupAnswer.contains("6"))
   // Credentials are deliberately scoped to this test's settings and restored below.
  }finally{
   context.getSharedPreferences("protected_settings",Context.MODE_PRIVATE).edit().clear().commit()
   store.db.close()
  }
 }
}
