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
class CoachLiveTest {
 @Test fun corpusPdfAndRealSearchFeedDailyPractice()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val credential=File(app.filesDir,"coach-live-credentials.json");org.junit.Assume.assumeTrue(credential.exists());val creds=wire.parseToJsonElement(credential.readText()).jsonObject;credential.delete()
  val corpus=File(app.filesDir,"coach-live-corpus.pdf");org.junit.Assume.assumeTrue(corpus.exists())
  val store=LearningStore(app,"coach-live-${uid()}.sqlite");val ns="coach-live-${uid()}-";val ctx=object:android.content.ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(ns+name,mode)};val settings=SecureSettings(ctx)
  for(key in listOf("apiKey","baseUrl"))settings.save(key,creds.s(key));for(role in listOf("tutor","coach","archive","verify"))settings.save("model.$role",creds.s("model"));settings.save("search.yuanbao.key",creds.s("searchKey"));settings.save("search.yuanbao.url",creds.s("searchUrl"))
  var visions=0;var docId="";val provider=object:Provider(settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{if(role=="archive"&&++visions>1){store.edit(docId,buildJsonObject{put("paused",true)});throw CancellationException("Read-only corpus acceptance intentionally stops after first page")};return super.chat(role,messages,tools,stream,priority,jsonMode,onDelta)}}
  try{
   val search=QuestionSearch(settings);val web=search.testProvider("yuanbao");assertTrue("Real search must return traceable URLs",web.isNotEmpty());assertTrue(web.all{it.url.startsWith("http")})
   val doc=store.importDocument(Document(title="2024考研数学真题【解析】.pdf",scope="math",assetIds=listOf(store.addAsset(corpus.inputStream())),mime="pdf"));docId=doc.id
   val library=Library(app,store,provider,settings,AtomicInteger());runCatching{library.process(doc.id)};assertEquals(1,store.get(doc.id)!!.payload.i("visualProcessed"));val chunks=store.dao.chunks(doc.id,1).joinToString(""){it.text};assertTrue(chunks.contains("frac")||chunks.contains("int"));assertTrue(chunks.contains("[公式与图表视觉核对]"))
   val coach=LearningCoach(app,store,provider,MemoryEngine(store),library,search){_,_,_->};coach.refresh("math");val plan=store.get("plan:math");assertNotNull(plan);assertEquals("Coach state: "+coach.state.value.error,"active",plan!!.payload.s("status"));val practice=store.all("practice");assertTrue("At least one complete corpus question should be selected: "+coach.state.value.error,practice.any{it.payload.s("sourceType")=="library"})
   for(p in practice){assertEquals(p.payload.s("stem"),p.payload.s("sourceQuote"));assertFalse(p.payload.s("stem").contains("【答案】"));assertTrue(p.payload.s("sourceHash").length==64)}
   val first=practice.first();val bmp=android.graphics.Bitmap.createBitmap(1300,900,android.graphics.Bitmap.Config.ARGB_8888);bmp.eraseColor(android.graphics.Color.WHITE);val canvas=android.graphics.Canvas(bmp);val ink=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.BLACK;textSize=40f;typeface=android.graphics.Typeface.create("cursive",android.graphics.Typeface.NORMAL)}
   for((i,line)in listOf("Synthetic written-work acceptance (not a real student's paper)","f'(x)=exp(cos x) is even; f(0)=0.","Hence f(-x)=-f(x).","For g, substitute t=-u and sin(-x)=-sin(x).","g(-x)=-g(x). Answer C: both are odd.").withIndex())canvas.drawText(line,40f,100f+i*105,ink)
   val photo=store.addAsset(java.io.ByteArrayOutputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it);it.toByteArray()});bmp.recycle();coach.grade(first.id,"",true,listOf(photo));assertEquals("",coach.state.value.error);val photoAttempt=store.get(store.get(first.id)!!.payload.s("lastAttemptId"))!!;assertEquals(listOf(photo),photoAttempt.payload.strings("assetIds"));assertTrue("Real visual review should transcribe visible work",photoAttempt.payload.s("transcription").isNotBlank())
   File(app.filesDir,"coach-live-result.json").writeText(buildJsonObject{put("photoReviewStatus",photoAttempt.payload.s("status"));put("photoOutcome",photoAttempt.payload.s("outcome"));put("photoTranscriptionCharacters",photoAttempt.payload.s("transcription").length);put("photoFeedback",photoAttempt.payload.s("feedback"));put("searchResults",web.size);put("pdfPages",store.get(doc.id)!!.payload.i("pages"));put("visualProcessed",1);put("planStatus",plan.payload.s("status"));put("dailyCount",practice.size);put("dailyStatus",store.setting("coach.dailyStatus.math"));put("error",coach.state.value.error);put("plan",plan.payload);put("practice",JsonArray(practice.map{it.payload}));put("recognizedPage",chunks.substringAfter("[公式与图表视觉核对]"))}.toString())
  }finally{ctx.getSharedPreferences("protected_settings",0).edit().clear().commit();corpus.delete();store.db.close()}
 }
}
