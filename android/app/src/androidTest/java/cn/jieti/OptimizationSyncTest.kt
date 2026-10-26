package cn.jieti

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ai.*
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.library.Library
import cn.jieti.sync.SyncClient
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class) class OptimizationSyncTest {
 @Test fun singleSessionCancellationResumptionAndIndependentExportStatus()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val file=File(app.filesDir,"optimization-sync-pairing.json");check(file.exists()){"Start the explicitly isolated sync server fixture first"}
  val fixture=JietiApp();val prefix="sync-optimization-${uid()}"
  val context=object:ContextWrapper(app){override fun getApplicationContext():Context=fixture;override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("$prefix-$name",mode)}
  val store=LearningStore(app,"$prefix.sqlite");val settings=SecureSettings(context);val memory=MemoryEngine(store);val foreground=AtomicInteger();val provider=Provider(settings,foreground)
  val agents=Agents(app,store,provider,memory,Library(app,store,provider,settings,foreground),foreground){_,_,_->}
  fixture.store=store;fixture.memory=memory;fixture.agents=agents
  val client=SyncClient(context,store,settings)
  try{
   client.pair(file.readText());store.put("question",payload(Question(title="独立同步验收",scope="test")))
   val transferred=CompletableDeferred<Unit>()
   val first=launch(Dispatchers.IO){client.synchronize{if(it.contains("电脑正在"))transferred.complete(Unit)}}
   withTimeout(15000){transferred.await()}
   try{client.synchronize{};fail("Duplicate synchronization accepted")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("正在进行"))}
   first.cancelAndJoin();assertFalse(client.busy.value);assertTrue(store.setting("lastSync").isNotBlank())
   val result=withTimeout(20000){client.synchronize{}}
   assertTrue(result.contains("数据同步完成"));assertTrue(result.contains("复盘文档已在电脑保存"));assertFalse(client.busy.value)
  }finally{agents.cancel();store.db.close();context.getSharedPreferences("protected_settings",Context.MODE_PRIVATE).edit().clear().commit();file.delete()}
 }
}
