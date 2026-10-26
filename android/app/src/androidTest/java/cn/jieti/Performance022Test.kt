package cn.jieti

import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ai.Curriculum
import cn.jieti.core.*
import cn.jieti.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime

class Performance022Test {
 private val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
 @Test fun bulkRefreshPreservesEvidenceAndDoesNotRewriteAnUnchangedProfile()=runBlocking<Unit>{
  val store=LearningStore(app,"performance-memory-${uid()}.sqlite");val memory=MemoryEngine(store)
  val today=java.time.Instant.now();val days=listOf(today.minusSeconds(172800),today.minusSeconds(86400),today.minusSeconds(10))
  for((index,at)in days.withIndex()){
   val user=store.put("message",payload(Message("q$index","user","独立完成推导","独立作答")),"source$index");store.write(user.copy(updatedAt=at.toString()))
   val evidence=store.put("evidence",payload(Evidence("q$index","math","数列极限","correct",true,0,listOf(user.id))),"e$index");store.write(evidence.copy(updatedAt=at.toString()))
  }
  memory.refreshMastery("math","数列极限");val first=store.all("mastery").single();assertEquals("mastered",first.payload.s("state"))
  assertEquals(0,memory.refreshAllMastery());assertEquals(first.rev,store.get(first.id)!!.rev)
  val error=store.put("message",payload(Message("q3","user","独立尝试","独立作答")),"source3")
  store.put("evidence",payload(Evidence("q3","math","数列极限","incorrect",true,0,listOf(error.id))),"e3")
  assertEquals(1,memory.refreshAllMastery());assertNotEquals("mastered",store.get(first.id)!!.payload.s("state"));val changed=store.get(first.id)!!.rev
  assertEquals(0,memory.refreshAllMastery());assertEquals(changed,store.get(first.id)!!.rev)
 }
 @Test fun decodedFlowsAvoidMainAndIgnoreUnrelatedTableInvalidations()=runBlocking<Unit>{
  val store=LearningStore(app,"performance-flow-${uid()}.sqlite");val record=store.put("mastery",payload(MasterySnapshot("math","数列极限","developing")))
  val emissions=AtomicInteger();val received=CompletableDeferred<Unit>();val collector=launch{store.dao.observeKinds(listOf("mastery","document")).decodedRecords().collect{rows->emissions.incrementAndGet();assertEquals(listOf(record.id),rows.map{it.id});received.complete(Unit)}}
  withTimeout(5000){received.await()};store.setSetting("unrelated","yes");delay(200);assertEquals(1,emissions.get());collector.cancelAndJoin()
  assertTrue(store.observeWorkspace("学习规划","").first().isEmpty())
 }
 @Test fun settingCacheDetectsWritesFromAnotherInstance() {
  val prefix="performance-settings-${uid()}-";val context=object:android.content.ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(prefix+name,mode)}
  val first=SecureSettings(context);val second=SecureSettings(context)
  first.save("sample","first");assertEquals("first",first.get("sample"));second.save("sample","second");assertEquals("second",first.get("sample"));assertEquals("a",first.get("missing","a"));assertEquals("b",first.get("missing","b"))
 }
 @Test fun largeCourseSnapshotLeavesMainThreadResponsive()=runBlocking<Unit>{
  val topics=Curriculum.chapters("math").flatMap{it.topics};val rows=(0 until 2500).map{i->SyncRecord("m$i","mastery","1",payload=payload(MasterySnapshot("math",topics[i%topics.size].title,if(i%2==0)"developing"else "mastered")),updatedAt=now(),deviceId="fixture")}
  val ticks=AtomicInteger();val heartbeat=launch(Dispatchers.Main){while(isActive){ticks.incrementAndGet();delay(8)}}
  var result:Curriculum.Progress?=null
  val elapsed=measureNanoTime{withContext(Dispatchers.Default){repeat(12){result=Curriculum.progress(rows,"math")}}}/1_000_000.0
  heartbeat.cancelAndJoin();assertEquals(157,result!!.total);assertTrue("Main looper was blocked",ticks.get()>1)
  android.util.Log.i("JietiPerf","2500 records x 12 snapshots: ${elapsed.toInt()}ms; main heartbeat=${ticks.get()}")
 }
}
