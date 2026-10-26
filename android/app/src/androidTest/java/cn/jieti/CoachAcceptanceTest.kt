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
import java.util.concurrent.atomic.AtomicInteger
class CoachAcceptanceTest {
 private val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
 private val stems=listOf("设数列 x_n=1/n，求该数列在 n 趋于无穷时的极限，并说明理由。","已知数列 y_n=2/n，求其极限，并写出详细的推理过程。","设数列 z_n=3/n，求在 n 趋于无穷时的极限，并说明依据。")
 @Test fun dualSourcesDailyIdempotenceReviewAndEvidenceRace()=runBlocking<Unit>{
  val store=LearningStore(app,"coach-${uid()}.sqlite");val namespace="coach-${uid()}-";val context=object:android.content.ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(namespace+name,mode)};val settings=SecureSettings(context);settings.save("model.coach","fake");settings.save("model.verify","fake");var selectionCalls=0;var reviews=0;var helpDuringReview=false;var mutateReview=false;var mutatePlan=false;var confidence=.95
  val fake=object:Provider(settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{
   val system=messages.first().jsonObject.s("content");val input=(messages.last().jsonObject["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
   if(role=="verify"){reviews++;if(helpDuringReview){val item=store.all("practice").first{it.payload.i("slot")==2};store.edit(item.id,buildJsonObject{put("helpUsed",true)})};if(mutateReview){val p=store.all("practice").first{it.payload.i("slot")==3};val q=store.get(p.payload.s("questionId"))!!;store.editUser(q.id,buildJsonObject{put("effectiveConditions","条件改成 n=2")},q.rev)};return Turn(buildJsonObject{put("outcome","correct");put("reasoningComplete",true);put("confidence",confidence);put("feedback","分母趋于正无穷，利用倒数极限可得结果为零。继续核对定义的适用条件。")}.toString())}
   if(system.contains("每日三题选题")){selectionCalls++;val sources=wire.parseToJsonElement(input).jsonObject["sources"]!!.jsonArray.map{it.jsonObject};val local=sources.first{it.s("type")=="library"};val web=sources.first{it.s("type")=="web"};return Turn(buildJsonObject{putJsonArray("items"){for(i in stems.indices)add(buildJsonObject{put("sourceId",(if(i<2)local else web).s("id"));put("concept","数列极限");put("quote",stems[i]);put("answerQuote","");put("examLabel","");put("complete",true)})}}.toString())}
   if(mutatePlan)store.setSetting("coach.goal.math","更新后的学习目标")
   return Turn("""{"summary":"先诊断数列极限，再安排针对性练习。","steps":[{"day":1,"focus":"数列极限","task":"独立解题并解释适用条件","minutes":30,"checkpoint":"能写出推理过程"}]}""")
  }}
  val search=object:QuestionSearch(settings){override suspend fun search(query:String)=listOf(QuestionSource("web:test","web","自编开放练习","https://example.org/math",stems[2]));override suspend fun expand(source:QuestionSource)=source}
  val library=Library(app,store,fake,settings,AtomicInteger());val coach=LearningCoach(app,store,fake,MemoryEngine(store),library,search){_,_,_->}
  try{
   val text=stems.take(2).joinToString("\n\n");val doc=store.importDocument(Document(title="自编数列极限诊断.txt",scope="math",assetIds=listOf(store.addAsset(text.toByteArray())),mime="txt"));library.process(doc.id)
   coach.refresh("math");assertEquals("",coach.state.value.error);assertEquals(3,store.all("practice").size);assertEquals(7,store.get("plan:math")!!.value<LearningPlan>().steps.size);assertEquals(setOf("library","web"),store.all("practice").map{it.payload.s("sourceType")}.toSet());val revs=store.all("practice").associate{it.id to it.rev};coach.refresh("math");assertEquals(1,selectionCalls);assertEquals(revs,store.all("practice").associate{it.id to it.rev})
   val items=store.all("practice").sortedBy{it.payload.i("slot")};val answer="因为 n 趋于正无穷，常数除以 n 的极限等于0。"
   coach.grade(items[0].id,answer,true);assertEquals("",coach.state.value.error);assertEquals("developing",store.all("mastery").single().payload.s("state"));assertTrue(store.all("attempt").single().payload.b("independent"));coach.grade(items[0].id,answer,true);assertEquals(1,reviews)
   helpDuringReview=true;coach.grade(items[1].id,answer,true);assertFalse(store.get(items[1].id)!!.payload.s("lastAttemptId").let{store.get(it)!!.payload.b("independent")})
   helpDuringReview=false;mutateReview=true;coach.grade(items[2].id,answer,true);assertTrue(coach.state.value.error.contains("更新"));assertEquals("ready",store.get(items[2].id)!!.payload.s("status"));assertEquals(2,store.all("evidence").size)
   mutateReview=false;confidence=.4;val q=store.get(items[2].payload.s("questionId"))!!;store.editUser(q.id,buildJsonObject{put("effectiveConditions","")},q.rev);coach.grade(items[2].id,answer+"再次核对。",true);assertEquals("unknown",store.get(items[2].id)!!.payload.s("lastOutcome"))
   mutatePlan=true;store.setSetting("coach.goal.math","准备重新规划");coach.refresh("math",false);assertTrue(coach.state.value.error.contains("更新"));assertEquals("baseline",store.get("plan:math")!!.payload.s("status"))
   store.delete(items[0].payload.s("questionId"));assertTrue(store.get(items[0].id)!!.deleted);assertTrue(store.all("attempt").none{it.payload.s("practiceId")==items[0].id})
  }finally{store.db.close();context.getSharedPreferences("protected_settings",0).edit().clear().commit()}
 }
}
