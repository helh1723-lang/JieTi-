package cn.jieti

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ai.*
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.library.Library
import cn.jieti.ui.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class) class StudyUpgradeTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private val app get()=instrumentation.targetContext.applicationContext as JietiApp
 @Test fun originalReviewFormulaeAndInlineDiagramStayOnSamePage(){
  lateinit var view:RichAnswer
  fun js(code:String):String{val latch=CountDownLatch(1);var result="";instrumentation.runOnMainSync{view.evaluateJavascript(code){result=it;latch.countDown()}};assertTrue(latch.await(10,TimeUnit.SECONDS));return result}
  instrumentation.runOnMainSync{view=RichAnswer(app)}
  try{
   val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(js("typeof window.showMarkdown")!="\"function\""&&System.nanoTime()<until)Thread.sleep(100)
   instrumentation.runOnMainSync{view.showStudyText("## 原题\n\n设Φ为标准正态分布函数，X1与X2独立。\n\nF_{X1,Y}(x,y)=1/2Φ(x)Φ(y)+1/2Φ(min{x,y})。\n\n\\[\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}\\]")}
   assertTrue(js("document.querySelectorAll('.katex').length").toInt()>=4)
   assertEquals("0",js("document.querySelectorAll('.katex-error').length"))
   assertEquals("0",js("document.querySelectorAll('p>div.math-block').length"))
   js("window.studyMarker=123")
   js("window.addEventListener('message',e=>{if(e.source===document.querySelector('iframe')?.contentWindow)window.frameAcceptance=e.data})")
   val html="<p>条件分支</p><div id='math'>\\(X_3=0\\)</div><svg viewBox='0 0 100 50'><text x='5' y='25'>分支</text></svg><script>document.addEventListener('DOMContentLoaded',()=>document.fonts.ready.then(()=>parent.postMessage({math:document.querySelectorAll('.katex').length,fontErrors:[...document.fonts].filter(f=>f.status==='error').length},'*')))</script>"
   instrumentation.runOnMainSync{view.showThread(listOf(RichMessage("a","assistant","先按开关变量分支")),listOf(RichDiagram("d","a",html,"按 X3 分类"),RichDiagram("other","other-message",html,"不得显示")))}
   assertEquals("1",js("document.querySelectorAll('.inline-diagrams iframe').length"))
   assertEquals("false",js("document.body.textContent.includes('不得显示')"))
   assertEquals("\"allow-scripts\"",js("document.querySelector('iframe').getAttribute('sandbox')"))
   val frameUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(js("window.frameAcceptance?.math||0")=="0"&&System.nanoTime()<frameUntil)Thread.sleep(100)
   assertEquals("1",js("window.frameAcceptance.math"));assertEquals("0",js("window.frameAcceptance.fontErrors"))
   instrumentation.runOnMainSync{view.showThread(listOf(RichMessage("a","assistant","先按开关变量分支，然后检查独立性")),listOf(RichDiagram("d","a",html,"按 X3 分类")))}
   assertEquals("123",js("window.studyMarker"));assertEquals("1",js("document.querySelectorAll('iframe').length"));assertEquals("\"conversation\"",js("document.querySelector('main').className"))
  }finally{instrumentation.runOnMainSync{view.destroy()}}
 }
 @Test fun groundedArtifactsRejectEmptyInputAndNotesProtectManualEdits()=runBlocking<Unit>{
  val store=LearningStore(app,"study-upgrade-${uid()}.sqlite");val foreground=AtomicInteger();var calls=0;var request=""
  val fake=object:Provider(app.settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{
   calls++;request=messages.toString();return Turn(if(role=="artifact")"""{"grounded":true,"html":"<html><body><p>概率分支</p></body></html>","summary":"按开关变量分支"}"""else """{"content":"## 考场启动\n按开关变量分支，检查独立性，再检查边缘分布是否一致。\n## 迁移自测\n改变开关概率，观察两支的权重。"}""")
  }}
  val agents=Agents(app,store,fake,MemoryEngine(store),Library(app,store,fake,app.settings,foreground),foreground){_,_,_->}
  try{
   val q=store.put("question",payload(Question(title="新题目",scope="math")))
   try{agents.createArtifact(q.id,"轻提示","制作图解");fail("Must reject missing source")}catch(_:IllegalArgumentException){}
   assertEquals(0,calls)
   val hash=store.addAsset(byteArrayOf(0x89.toByte(),1,2));store.edit(q.id,buildJsonObject{put("assetIds",JsonArray(listOf(JsonPrimitive(hash))))})
   store.put("message",payload(Message(questionId=q.id,role="user",content="按X3=0或1的分支解释联合分布")))
   val answer=store.put("message",payload(Message(questionId=q.id,role="assistant",content="先看开关条件")))
   agents.createArtifact(q.id,"轻提示","展示分类")
   assertTrue(request.contains("data:image/png;base64,"));assertTrue(request.contains("按X3=0或1"));assertEquals(answer.id,store.all("artifact").single().payload.s("sourceMessageId"))
   agents.summarizeNotes(q.id);val note=StudyContext.note(store.all("note"),q.id)!!;assertEquals("agent",note.payload.s("origin"))
   val before=calls;agents.summarizeNotes(q.id);assertEquals(before,calls)
   store.put("note",payload(Note(q.id,"我的手动修正","user","")),note.id)
   store.saveAgentNote(q.id,"迟到的Agent结果","new-hash",note)
   agents.summarizeNotes(q.id);assertEquals(before,calls);assertEquals("我的手动修正",store.get(note.id)!!.payload.s("content"))
   store.put("note",payload(Note(q.id,"用户明确反馈：本次讲法不合适")))
   assertEquals(note.id,StudyContext.note(store.all("note"),q.id)!!.id)
   store.delete(q.id);store.saveAgentNote(q.id,"删除后的迟到结果","x",null);assertTrue(store.all("note").isEmpty())
  }finally{store.db.close()}
 }
 // Explicitly selected only for the user's USB-authorized real case; no key extraction.
 @Test fun liveAdaptiveConversation()=runBlocking<Unit>{
  val store=LearningStore(app,"adaptive-live-${uid()}.sqlite");val foreground=AtomicInteger()
  var requestedDiagram=false
  val provider=object:Provider(app.settings,foreground){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{
   val result=super.chat(role,messages,tools,stream,priority,jsonMode,onDelta);if(role=="tutor"&&result.toolCalls.any{it.jsonObject["function"]?.jsonObject?.s("name")=="interactive_page"})requestedDiagram=true;return result
  }}
  val agents=Agents(app,store,provider,MemoryEngine(store),Library(app,store,provider,app.settings,foreground),foreground){_,_,_->}
  try{
   val q=store.put("question",payload(Question(title="合成验收：TCP连接状态",scope="acceptance")))
   assertTrue(agents.send(q.id,"解释 TCP 三次握手各报文如何改变连接状态，我容易把 SYN 和 ACK 的方向弄反。请用适合这个内容的表达形式，帮助我理解。","完整讲解"))
   withTimeout(300000){while(agents.state.value.busy)delay(200)}
   assertEquals("",agents.state.value.error)
   val response=store.messages(q.id).last{it.payload.s("role")=="assistant"}
   if(requestedDiagram)withTimeout(300000){while(store.all("artifact").isEmpty())delay(500)}
   val artifact=store.all("artifact").firstOrNull()
   if(artifact!=null){assertEquals(response.id,artifact.payload.s("sourceMessageId"));assertTrue(artifact.payload.s("summary").contains("握手")||artifact.payload.s("summary").contains("TCP"))}
   else assertTrue("A state/packet comparison table is also an appropriate dynamic format",response.payload.s("content").lines().count{it.trim().startsWith("|")}>=4)
   java.io.File(app.filesDir,"adaptive-live.json").writeText(buildJsonObject{put("answer",response.payload.s("content"));put("format",if(artifact!=null)"diagram"else "table");put("summary",artifact?.payload?.s("summary")?:"");put("sourceLinked",artifact?.payload?.s("sourceMessageId")==response.id)}.toString())
  }finally{agents.cancel();store.db.close()}
 }
 @Test fun liveTabletProbabilityDiagramAndNotes()=runBlocking<Unit>{
  val id=InstrumentationRegistry.getArguments().getString("questionId")?:error("Pass questionId explicitly for real-device acceptance")
  val q=app.store.get(id)?:error("Real question missing");assertFalse(q.deleted)
  app.agents.createArtifact(id,"检查步骤","围绕本题全概率公式和X3的两种分支，解释联合事件为什么要合并同源不等式。明确核对原图，不采用之前错误的抛物线图。")
  app.agents.summarizeNotes(id)
  val artifact=app.store.all("artifact").last{it.payload.s("questionId")==id}
  val note=StudyContext.note(app.store.all("note"),id)!!
  assertTrue(artifact.payload.s("summary").contains("概率")||artifact.payload.s("summary").contains("分支"))
  assertFalse(artifact.payload.s("html").contains("x² − 2x − 3"));assertTrue(note.payload.s("content").length>50)
  java.io.File(app.filesDir,"study-upgrade-live.json").writeText(buildJsonObject{put("artifactId",artifact.id);put("summary",artifact.payload.s("summary"));put("note",note.payload.s("content"))}.toString())
 }
}
