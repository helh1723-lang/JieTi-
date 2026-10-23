package cn.jieti

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ai.*
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.library.Library
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class) class BugfixAcceptanceTest {
 private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
 private fun store()=LearningStore(app,"acceptance-bugfix-${uid()}.sqlite")
 private fun question(store:LearningStore,assets:List<String> = emptyList())=Question("合成验收题","acceptance",listOf("极限"),"判断有界数列是否收敛","",assets,"help",emptyList(),"","","open")
 @Test fun offlineTextImportSearchEditDeleteAndStaleSync()=runBlocking<Unit>{
  val store=store();val foreground=AtomicInteger();val library=Library(app,store,app.provider,app.settings,foreground)
  try{
   val text=("合成教材：卷积积分必须检查支撑区间，然后进行变量代换。\n").repeat(6000)
   val hash=store.addAsset(text.toByteArray());val doc=store.put("document",payload(Document("合成大文本","acceptance",listOf(hash),"txt","queued",0,0,"",false)))
   val start=System.nanoTime();assertTrue(library.process(doc.id));val elapsed=(System.nanoTime()-start)/1000000
   assertEquals("ready",store.get(doc.id)!!.payload.s("status"));assertTrue(library.search("acceptance","卷积支撑区间").isNotEmpty())
   val ready=store.get(doc.id)!!;store.editUser(doc.id,buildJsonObject{put("title","修改后的教材");put("scope","moved");put("supplementalText","卷积支撑区间补充纠正：本例答案区间为零到一。")},ready.rev)
   assertTrue(library.process(doc.id));assertTrue(library.search("acceptance","卷积").isEmpty());assertTrue(library.search("moved","卷积支撑").any{it.page==0})
   store.delete(doc.id);store.ingest(doc.copy(rev=uid()));assertTrue(store.get(doc.id)!!.deleted);assertTrue(library.search("moved","卷积").isEmpty());assertTrue(store.asset(hash).exists())
   android.util.Log.i("JietiBugfix","Local text import bytes=${text.toByteArray().size} elapsedMs=$elapsed, no embedding calls")
  }finally{store.db.close()}
 }
 @Test fun manualEditsSurviveProgressAndQuestionDeletionCascades()=runBlocking<Unit>{
  val store=store();try{
   val q=store.put("question",payload(question(store)));val m=store.put("message",payload(Message(q.id,"user","临时提问","轻提示",emptyList(),emptyList(),"")))
   store.edit(q.id,buildJsonObject{put("status","done")})
   val saved=store.editUser(q.id,buildJsonObject{put("transcription","用户纠正的题干")},q.rev)
   assertTrue("transcription" in saved.payload.strings("userEditedFields"))
   store.delete(q.id);assertTrue(store.get(m.id)!!.deleted)
   store.ingest(m.copy(id=uid(),rev=uid()));assertTrue(store.messages(q.id).isEmpty())
  }finally{store.db.close()}
 }
 @Test fun textPdfProcessesAllFortyPagesAndDocxWithoutRemoteEmbeddings()=runBlocking<Unit>{
  val store=store();val library=Library(app,store,app.provider,app.settings,AtomicInteger())
  try{
   com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(app)
   val bytes=java.io.ByteArrayOutputStream().use{out->com.tom_roush.pdfbox.pdmodel.PDDocument().use{pdf->repeat(40){n->val page=com.tom_roush.pdfbox.pdmodel.PDPage();pdf.addPage(page);com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdf,page).use{stream->stream.beginText();stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,12f);stream.newLineAtOffset(50f,700f);stream.showText("Synthetic course page $n: convolution support and integration interval. UniquePage$n");stream.endText()}};pdf.save(out)};out.toByteArray()}
   val hash=store.addAsset(bytes);val pdf=store.put("document",payload(Document("合成40页PDF","acceptance",listOf(hash),"pdf","queued",0,0,"",false)))
   val start=System.nanoTime();assertTrue(library.process(pdf.id));val elapsed=(System.nanoTime()-start)/1000000
   assertEquals(40,store.get(pdf.id)!!.payload.i("processed"));assertEquals(40,store.get(pdf.id)!!.payload.i("pages"));assertEquals("ready",store.get(pdf.id)!!.payload.s("status"));assertTrue(library.search("acceptance","UniquePage31").any{it.page==32})
   val docx=java.io.ByteArrayOutputStream().use{out->java.util.zip.ZipOutputStream(out).use{zip->zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"));zip.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>拉普拉斯变换的收敛域必须包含虚轴才能得到频率响应。</w:t></w:r></w:p></w:body></w:document>".toByteArray());zip.closeEntry()};out.toByteArray()}
   val word=store.put("document",payload(Document("合成Word","word",listOf(store.addAsset(docx)),"docx","queued",0,0,"",false)))
   assertTrue(library.process(word.id));assertTrue(library.search("word","拉普拉斯收敛域").isNotEmpty())
   android.util.Log.i("JietiBugfix","Local 40-page text PDF elapsedMs=$elapsed; full page coverage and DOCX passed")
  }finally{store.db.close()}
 }
 @Test fun implicitHabitsNeedIndependentEvidenceRespectCorrectionsAndCanChange()=runBlocking<Unit>{
  val store=store();val memory=MemoryEngine(store)
  suspend fun source(text:String):SyncRecord{val q=store.put("question",payload(question(store)));return store.put("message",payload(Message(q.id,"user",text,"轻提示",emptyList(),emptyList(),"")))}
  try{
   val a=source("能不能先画个图");val b=source("这样画更直观")
   memory.observeStyle("acceptance","visualization","visual",listOf(a.id));assertTrue(memory.recall("acceptance","图").isEmpty())
   memory.observeStyle("acceptance","visualization","visual",listOf(a.id));assertEquals(1,store.all("memory").single().payload.strings("sourceIds").size)
   memory.observeStyle("acceptance","visualization","visual",listOf(b.id));assertTrue(memory.recall("acceptance","图").any{it.payload.s("key").endsWith(":visual")})
   val c=source("不用画图，用公式推导就行");val d=source("别画图，写符号就好")
   memory.observeStyle("acceptance","visualization","symbolic",listOf(c.id));assertEquals(1,memory.recall("acceptance","图").size)
   memory.observeStyle("acceptance","visualization","symbolic",listOf(d.id));assertEquals(1,memory.recall("acceptance","图").size);assertTrue(memory.recall("acceptance","图").single().payload.s("key").endsWith(":symbolic"))
   val active=memory.recall("acceptance","图").single();store.edit(active.id,buildJsonObject{put("locked",true)})
   val e=source("再画图解释");assertFalse(memory.observeStyle("acceptance","visualization","visual",listOf(e.id)))
   store.edit(active.id,buildJsonObject{put("locked",false)});store.forget(active.id);assertFalse(memory.observeStyle("acceptance","visualization","symbolic",listOf(c.id,d.id)))
  }finally{store.db.close()}
 }
 @Test fun modelLearnsFromNaturalUnderstandingFeedbackAndRejectsInventedSources()=runBlocking<Unit>{
  val store=store();val foreground=AtomicInteger();var analyses=0;val memory=MemoryEngine(store)
  val fake=object:Provider(app.settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,onDelta:(String)->Unit):Turn{
   analyses++;val input=wire.parseToJsonElement(messages.last().jsonObject.s("content")).jsonArray
   val user=input.last{it.jsonObject.s("role")=="user"}.jsonObject
   return Turn(buildJsonObject{putJsonArray("observations"){add(buildJsonObject{put("family","visualization");put("choice","visual");putJsonArray("evidence"){add(buildJsonObject{put("sourceId",user.s("id"));put("quote","这种解释一下就跟上了")})}});add(buildJsonObject{put("family","response_length");put("choice","concise");putJsonArray("evidence"){add(buildJsonObject{put("sourceId","invented");put("quote","不存在的用户反馈")})}})}}.toString())
  }}
  val agents=Agents(app,store,fake,memory,Library(app,store,fake,app.settings,foreground),foreground){_,_,_->}
  try{
   repeat(2){val q=store.put("question",payload(question(store)));store.put("message",payload(Message(q.id,"assistant","用面积示意图看重叠区间。","轻提示",emptyList(),emptyList(),"")));store.put("message",payload(Message(q.id,"user","这种解释一下就跟上了，下一步呢？","轻提示",emptyList(),emptyList(),"")));agents.learnStyle(q.id);agents.learnStyle(q.id)}
   assertEquals(2,analyses);val learned=memory.recall("acceptance","讲解");assertEquals(1,learned.size);assertTrue(learned.single().payload.s("key").endsWith(":visual"));assertTrue(store.all("memory").none{it.payload.s("key").contains("response_length")})
  }finally{store.db.close()}
 }
 @Test fun retryReusesUserMessageRejectsDoubleSendAndRetainsOriginalImageAfterLongHistory()=runBlocking<Unit>{
  val store=store();val foreground=AtomicInteger();var calls=0;var sent=JsonArray(emptyList());val firstStarted=CompletableDeferred<Unit>()
  val fake=object:Provider(app.settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,onDelta:(String)->Unit):Turn{
   calls++;sent=messages;if(calls==1){onDelta("未完成");firstStarted.complete(Unit);awaitCancellation()};onDelta("完整回答");return Turn("完整回答")
  }}
  val library=Library(app,store,fake,app.settings,foreground);val agents=Agents(app,store,fake,MemoryEngine(store),library,foreground){_,_,_->}
  try{
   val hash=store.addAsset(byteArrayOf(0x89.toByte(),1,2,3));val q=store.put("question",payload(question(store,listOf(hash))))
   repeat(52){n->store.put("message",payload(Message(q.id,if(n%2==0)"user"else "assistant","历史消息 $n","轻提示",emptyList(),if(n==0)listOf(hash)else emptyList(),"")))}
   assertTrue(agents.send(q.id,"接着讲","完整讲解"));withTimeout(10000){firstStarted.await()};assertFalse(agents.send(q.id,"重复点击","轻提示"))
   agents.cancel();withTimeout(10000){while(agents.state.value.busy||foreground.get()!=0)delay(20)};delay(50)
   val before=store.messages(q.id).count{it.payload.s("role")=="user"};assertTrue(agents.retry())
   withTimeout(10000){while(agents.state.value.busy)delay(20)}
   assertEquals(before,store.messages(q.id).count{it.payload.s("role")=="user"});assertEquals("完整讲解",agents.state.value.mode)
   assertTrue(sent.toString().contains("data:image/png;base64,"));assertTrue(sent.toString().contains("判断有界数列是否收敛"));assertTrue(sent.toString().contains("接着讲"));assertFalse(sent.toString().contains("历史消息 0"));assertEquals("完整回答",store.messages(q.id).last().payload.s("content"))
  }finally{agents.cancel();delay(50);store.db.close()}
 }
 @Test fun stoppingImmediatelyCanRetryWithoutLosingOrDuplicatingQuestion()=runBlocking<Unit>{
  val store=store();val foreground=AtomicInteger();var blocked=true
  val fake=object:Provider(app.settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,onDelta:(String)->Unit):Turn{if(blocked)awaitCancellation();onDelta("重新回答成功");return Turn("重新回答成功")}}
  val agents=Agents(app,store,fake,MemoryEngine(store),Library(app,store,fake,app.settings,foreground),foreground){_,_,_->}
  try{
   repeat(4){n->blocked=true;val q=store.put("question",payload(question(store)));assertTrue(agents.send(q.id,"立即停止后的原问题 $n","检查步骤"));agents.cancel();withTimeout(10000){while(agents.state.value.busy)delay(10)};blocked=false;assertTrue(agents.retry());withTimeout(10000){while(agents.state.value.busy)delay(10)};assertEquals(1,store.messages(q.id).count{it.payload.s("role")=="user"});assertTrue(store.messages(q.id).any{it.payload.s("content")=="立即停止后的原问题 $n"})}
  }finally{agents.cancel();delay(30);store.db.close()}
 }
}
