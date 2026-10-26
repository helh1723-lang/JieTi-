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

class CourseDataFlowTest {
 @Test fun liveProgressIsIsolatedFromDocumentIndexingAndDeletionInvalidatesLinks()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val store=LearningStore(app,"course-flow-${uid()}.sqlite")
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
  try{
   val document=store.put("document",payload(Document(title="第三章讲义.pdf",scope="signals",mime="pdf",pages=39)),"doc")
   val mastery=store.put("mastery",payload(MasterySnapshot("signals","CTFT","developing")),"mastery")
   val data=CourseData(store,scope)
   val progressUpdates=AtomicInteger();val sourceUpdates=AtomicInteger()
   val a=scope.launch{data.progress.drop(1).collect{progressUpdates.incrementAndGet()}}
   val b=scope.launch{data.sources.drop(1).collect{sourceUpdates.incrementAndGet()}}
   withTimeout(5000){data.progress.first{it["signals"]?.state("3.4")=="developing"};data.sources.first{it.resolve("signals-3",17)!=null}}
   delay(100);val first=data.progress.value;val index=data.sources.value;val p=progressUpdates.get();val s=sourceUpdates.get()
   repeat(12){n->store.edit(document.id,buildJsonObject{put("processed",n+1);put("status","processing")})}
   delay(350)
   assertSame(first,data.progress.value);assertSame(index,data.sources.value)
   assertEquals(p,progressUpdates.get());assertEquals(s,sourceUpdates.get())
   // Reading the other subject is an in-memory lookup, not a new DB subscription.
   assertEquals(157,first.getValue("math").total);assertEquals(52,first.getValue("signals").total)
   store.edit(mastery.id,buildJsonObject{put("state","needs_refresh")})
   withTimeout(5000){data.progress.first{it["signals"]?.state("3.4")=="needs_refresh"}}
   store.delete(document.id)
   withTimeout(5000){data.sources.first{it.resolve("signals-3",17)==null}}
   a.cancelAndJoin();b.cancelAndJoin()
  }finally{scope.cancel();store.db.close()}
 }
}
