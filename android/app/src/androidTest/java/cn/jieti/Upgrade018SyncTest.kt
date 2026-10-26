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
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
@RunWith(AndroidJUnit4::class) class Upgrade018SyncTest {
 @Test fun realTlsHistoryExchange()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp;val file=File(app.filesDir,"upgrade-018-pairing.json");org.junit.Assume.assumeTrue(file.exists());val prefix="sync018-${uid()}";val fixture=JietiApp();val context=object:ContextWrapper(app){override fun getApplicationContext():Context=fixture;override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("$prefix-$name",mode)};val store=LearningStore(app,"$prefix.sqlite");val settings=SecureSettings(context);val foreground=AtomicInteger();val provider=Provider(settings,foreground);val memory=MemoryEngine(store);val agents=Agents(app,store,provider,memory,Library(app,store,provider,settings,foreground),foreground){_,_,_->};fixture.store=store;fixture.memory=memory;fixture.agents=agents;val sync=SyncClient(context,store,settings)
  try{val photo=store.addAsset("synthetic handwritten work photo transfer".toByteArray());val workQuestion=store.put("question",payload(Question(title="自编图片作答同步验收",scope="math",transcription="求数列1/n的极限")),"sync-work-question");store.put("practice",payload(PracticeItem(day=CoachRules.day(),slot=1,scope="math",concept="极限",stem="求数列1/n的极限",questionId=workQuestion.id,status="reviewed")),"sync-work-practice");store.put("attempt",payload(PracticeAttempt(practiceId="sync-work-practice",questionId=workQuestion.id,scope="math",concept="极限",work="",independent=true,helpUsed=false,outcome="correct",feedback="极限为零",status="reviewed",submittedAt=now(),assetIds=listOf(photo),transcription="手写步骤：n趋于无穷，1/n趋于零。")),"sync-work-attempt");store.put("message",payload(Message(workQuestion.id,"user","手写作答","独立作答",assetIds=listOf(photo))),"sync-work-message");store.put("plan",payload(LearningPlan(scope="math",goal="巩固极限",summary="根据真实作答安排练习",sourceIds=listOf("sync-work-attempt"),status="active",generatedAt=now())),"plan:math");sync.pair(file.readText());sync.synchronize{};val desktopPhoto=store.get("desktop-photo-message")!!.payload.strings("assetIds").single();assertTrue(store.asset(desktopPhoto).length()>0);repeat(10){store.editUser("offline-018",buildJsonObject{put("title","离线第${it+1}次修改")},store.get("offline-018")!!.rev)};sync.synchronize{};assertTrue(store.dao.conflicts().isEmpty());assertEquals("离线第10次修改",store.get("offline-018")!!.payload.s("title"));store.editUser("offline-018",buildJsonObject{put("studentWork","平板补充步骤")},store.get("offline-018")!!.rev);sync.synchronize{};assertEquals("wrong",store.get("offline-018")!!.payload.s("classification"));assertEquals("平板补充步骤",store.get("offline-018")!!.payload.s("studentWork"));assertTrue(store.dao.conflicts().isEmpty());android.util.Log.i("Jieti018","PASS real Android-to-desktop TLS history exchange")}
  finally{agents.cancel();store.db.close();context.getSharedPreferences("protected_settings",0).edit().clear().commit();file.delete()}
 }
}
