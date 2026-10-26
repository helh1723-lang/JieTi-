package cn.jieti

import android.content.ContextWrapper
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

class AutoRefreshTest {
 @Test fun navigationRequestsCoalesceAndOtherCourseWaitsInsteadOfBeingLost()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val store=LearningStore(app,"auto-coach-${uid()}.sqlite")
  val prefix="auto-coach-${uid()}-"
  val context=object:ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(prefix+name,mode)}
  val settings=SecureSettings(context);settings.save("model.coach","fake")
  val calls=AtomicInteger();val entered=CompletableDeferred<Unit>();val proceed=CompletableDeferred<Unit>()
  val provider=object:Provider(settings){
   override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{
    if(calls.incrementAndGet()==1){entered.complete(Unit);proceed.await()}
    return Turn("""{"summary":"根据已有证据安排练习，不推断未验证的掌握度。","steps":[]}""")
   }
  }
  try{
   val date=CoachRules.day()
   for(scope in listOf("math","signals"))for(slot in 1..3)store.put("practice",payload(PracticeItem(day=date,slot=slot,scope=scope)),"daily:$scope:$date:$slot")
   val coach=LearningCoach(app,store,provider,MemoryEngine(store),Library(app,store,provider,settings,AtomicInteger()),QuestionSearch(settings))
   coach.requestAutoRefresh("math",date)
   withTimeout(5000){entered.await()}
   repeat(6){coach.requestAutoRefresh("math",date)}
   coach.requestAutoRefresh("signals",date)
   assertEquals(1,calls.get());proceed.complete(Unit)
   withTimeout(10000){while(store.get("plan:signals")?.payload?.s("status")!="active"||coach.state.value.busy)delay(20)}
   assertEquals("active",store.get("plan:math")!!.payload.s("status"));assertEquals(2,calls.get())
   delay(100);repeat(6){coach.requestAutoRefresh("math",date)};delay(200)
   assertEquals(2,calls.get());assertEquals("",coach.state.value.error)
  }finally{proceed.complete(Unit);store.db.close()}
 }
}
