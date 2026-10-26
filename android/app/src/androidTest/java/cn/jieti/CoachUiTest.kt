package cn.jieti
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ai.*
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.library.Library
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
class CoachUiTest {
 @Test fun uiSession()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp;org.junit.Assume.assumeTrue(File(app.filesDir,"coach-ui-opt-in").exists());val ready=File(app.filesDir,"coach-ui-ready");val done=File(app.filesDir,"coach-ui-done");done.delete()
  val store=LearningStore(app,"coach-ui-${uid()}.sqlite");val ns="coach-ui-${uid()}-";val ctx=object:android.content.ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(ns+name,mode)};val settings=SecureSettings(ctx);val foreground=AtomicInteger()
  val photoRequests=AtomicInteger();val provider=object:Provider(settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{if(messages.toString().contains("image_url"))photoRequests.incrementAndGet();return Turn("""{"transcription":"因为 n 趋于无穷，所以常数除以 n 的极限等于0。","readable":true,"outcome":"correct","reasoningComplete":true,"confidence":0.95,"feedback":"分母趋于正无穷，因此倒数趋于零。推理过程完整，下一次尝试带参数的变式。"}""")}}
  val memory=MemoryEngine(store);val library=Library(app,store,provider,settings,foreground);val agents=Agents(app,store,provider,memory,library,foreground){_,_,_->};val search=QuestionSearch(settings);val coach=LearningCoach(app,store,provider,memory,library,search){_,_,_->}
  val oldStore=app.store;val oldSettings=app.settings;val oldProvider=app.provider;val oldMemory=app.memory;val oldLibrary=app.library;val oldAgents=app.agents;val oldSearch=app.questionSearch;val oldCoach=app.coach;val oldTheme=app.uiTheme.value
  try{
   store.setSetting("coach.auto","false");store.setSetting("coach.dailyStatus.math","今日三题已就绪");val areas=listOf(buildJsonObject{put("concept","数列极限");put("reason","已有独立作答证据，继续巩固与迁移");put("state","developing")},buildJsonObject{put("concept","积分与应用");put("reason","先补充独立作答证据，再判断当前掌握情况");put("state","diagnostic")})
   store.put("plan",payload(LearningPlan("math","考研数学","从数列极限开始，先独立尝试，再核对关键条件。",areas,(1..7).map{buildJsonObject{put("day",it);put("focus","数列极限");put("task","回顾定义，独立完成一道变式并复盘");put("minutes",45);put("checkpoint","能解释所用方法的适用条件")}},emptyList(),"fixture",now(),"active",45)),"plan:math")
   val uiRun=uid();for(slot in 1..3){val stem="设数列 \\(a_n=\\frac{$slot}{n}\\)，求 \\(n\\to+\\infty\\) 时的极限，并说明理由。";val id="daily:math:${CoachRules.day()}:ui-$uiRun:$slot";val qid="coach-ui-question:$slot";store.put("question",payload(Question(title="自编诊断 · 数列极限 $slot",scope="math",scopeOrigin="user",transcription=stem,concepts=listOf("数列极限"),practiceId=id)),qid);store.put("practice",payload(PracticeItem(day=CoachRules.day(),slot=slot,scope="math",concept="数列极限",stem=stem,sourceType=if(slot==3)"web"else "library",sourceTitle="自编界面验收题（非历年真题）",sourceUrl=if(slot==3)"https://example.org/math"else "",sourceQuote=stem,sourceHash=CoachRules.identity(stem),authenticity="practice_source",questionId=qid,status="ready")),id)}
   app.store=store;app.settings=settings;app.provider=provider;app.memory=memory;app.library=library;app.agents=agents;app.questionSearch=search;app.coach=coach;app.uiTheme.value="light"
   app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));ready.writeText("ready");withTimeout(600000){while(!done.exists())delay(200)}
   assertTrue("Photo upload must reach the native multimodal review",photoRequests.get()>0);assertTrue("Native submit must reach reviewed evidence",store.all("attempt").any{it.payload.s("status")=="reviewed"});assertEquals("developing",store.all("mastery").single().payload.s("state"))
  }finally{agents.cancel();app.store=oldStore;app.settings=oldSettings;app.provider=oldProvider;app.memory=oldMemory;app.library=oldLibrary;app.agents=oldAgents;app.questionSearch=oldSearch;app.coach=oldCoach;app.uiTheme.value=oldTheme;store.db.close();ctx.getSharedPreferences("protected_settings",0).edit().clear().commit();ready.delete();done.delete();File(app.filesDir,"coach-ui-opt-in").delete();app.startActivity(android.content.Intent(app,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))}
 }
}
