package cn.jieti

import android.graphics.*
import androidx.room.withTransaction
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class) class OptimizationAcceptanceTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private val app get()=instrumentation.targetContext.applicationContext as JietiApp
 @Test fun retrievalBeyondFirst200RejectsNoiseAndDeletedDocuments()=runBlocking<Unit>{
  val store=LearningStore(app,"retrieval-optimization-${uid()}.sqlite");val foreground=AtomicInteger();val library=Library(app,store,app.provider,app.settings,foreground)
  try{
   val wrong=store.put("document",payload(Document(title="干扰资料",scope="test")),"noise")
   val right=store.put("document",payload(Document(title="信号教材",scope="test")),"target")
   store.db.withTransaction{
    repeat(330){val text="卷积是一个数学工具，这里介绍通用定义";store.dao.chunk(ChunkRow("noise:%04d".format(it),wrong.id,"test",it+1,text,Library.tokenize(text)))}
    val text="卷积积分的支撑区间由两个信号的非零区间相加确定。有限支撑信号可据此确定积分上下限。"
    store.dao.chunk(ChunkRow("zz-target",right.id,"test",99,text,Library.tokenize(text)))
   }
   val found=library.search("test","请解释卷积积分的支撑区间")
   assertEquals(right.id,found.first().documentId);assertTrue(found.none{it.documentId==wrong.id})
   assertTrue(library.search("test","傅里叶变换的收敛条件").isEmpty());assertTrue(library.search("other","卷积积分支撑区间").isEmpty())
   store.delete(right.id);assertTrue(library.search("test","卷积积分支撑区间").isEmpty())
   val mislabeled=store.put("document",payload(Document(title="858基础讲义",scope="math")))
   store.dao.chunk(ChunkRow("wrong-course",mislabeled.id,"math",1,"数学多重积分的计算方法",Library.tokenize("数学多重积分的计算方法")))
   assertTrue("Math must reject 858 documents even if imported under the wrong subject",library.search("math","多重积分的计算方法").isEmpty())
  }finally{store.db.close()}
 }
 @Test fun mathQuestionOverridesStale858SelectionAndReferencesOnlyThisAnswer()=runBlocking<Unit>{
  val store=LearningStore(app,"subject-routing-${uid()}.sqlite");val foreground=AtomicInteger();val fake=object:Provider(app.settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{assertFalse(messages.first().jsonObject.s("content").contains("858基础讲义"));onDelta("先确定积分区域");return Turn("先确定积分区域")}}
  val agents=Agents(app,store,fake,MemoryEngine(store),Library(app,store,fake,app.settings,foreground),foreground){_,_,_->}
  try{
   val q=store.put("question",payload(Question(title="新题",scope="signals")))
   val correct=store.put("document",payload(Document(title="数学多重积分",scope="math")))
   val wrong=store.put("document",payload(Document(title="858基础讲义",scope="signals")))
   for((doc,subject)in listOf(correct to "math",wrong to "signals")){val text="多重积分的积分区域决定积分上下限";store.dao.chunk(ChunkRow(doc.id,doc.id,subject,1,text,Library.tokenize(text)))}
   assertTrue(agents.send(q.id,"考研数学题：多重积分的积分区域怎么确定？","轻提示"));withTimeout(10000){while(agents.state.value.busy)delay(20)}
   assertEquals("",agents.state.value.error);assertEquals("math",store.get(q.id)!!.payload.s("scope"))
   val citations=store.all("citation");assertTrue(citations.isNotEmpty());assertTrue(citations.all{it.payload.s("documentId")==correct.id&&it.payload.s("sourceMessageId")==agents.state.value.responseId})
  }finally{agents.cancel();store.db.close()}
 }
 @Test fun suppressionDraftPersistenceAndInterruptedAnswerIsolation()=runBlocking<Unit>{
  val store=LearningStore(app,"answer-optimization-${uid()}.sqlite");val memory=MemoryEngine(store);val foreground=AtomicInteger();var tasks=0
  val fake=object:Provider(app.settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{onDelta("这是未完成的推导");throw IncompleteAnswer("truncated","合成截断")}}
  val agents=Agents(app,store,fake,memory,Library(app,store,fake,app.settings,foreground),foreground){_,_,_->tasks++}
  try{
   val q=store.put("question",payload(Question(title="合成故障验收",scope="test")))
   assertTrue(agents.send(q.id,"不要记住这次的错误","轻提示"))
   withTimeout(15000){while(agents.state.value.busy)delay(25)}
   assertTrue(agents.state.value.error.contains("合成截断"));assertEquals(0,tasks)
   val partial=store.all("message").single{it.payload.s("role")=="assistant"};assertEquals("truncated",partial.payload.s("completion"));assertTrue(partial.payload.s("content").isNotBlank())
   assertTrue(store.messages(q.id).none{it.payload.s("role")=="assistant"});assertTrue(store.all("memory").isEmpty());assertTrue(store.all("forget").any{agents.state.value.userMessageId in it.payload.strings("sourceIds")})
   assertTrue(agents.retry());withTimeout(15000){while(agents.state.value.busy)delay(25)};assertEquals(1,store.all("message").count{it.payload.s("role")=="user"})
   val key="acceptance-${uid()}";LocalDrafts(app).save(key,"切换后仍在","old-rev");assertEquals(LocalDrafts.Draft("切换后仍在","old-rev"),LocalDrafts(app).read(key));LocalDrafts(app).remove(key)
  }finally{agents.cancel();store.db.close()}
 }
 @Test fun readingGlassMaintainsContrastAcrossBackgrounds(){
  fun luminance(color:Int):Double{fun channel(v:Int):Double{val x=v/255.0;return if(x<=.04045)x/12.92 else Math.pow((x+.055)/1.055,2.4)};return .2126*channel(Color.red(color))+.7152*channel(Color.green(color))+.0722*channel(Color.blue(color))}
  for(dark in listOf(false,true))for(background in listOf(Color.WHITE,Color.BLACK,Color.RED,Color.GREEN,Color.BLUE)){
   val bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888);bitmap.eraseColor(background)
   val glass=LiquidGlassDrawable(1f,30f,clear=true,frost={.94f},dark={dark});glass.setBounds(0,0,200,200);glass.draw(Canvas(bitmap))
   val fill=luminance(bitmap.getPixel(100,100));val ink=luminance(if(dark)0xFFE3E7DC.toInt()else 0xFF30392F.toInt());val contrast=(maxOf(fill,ink)+.05)/(minOf(fill,ink)+.05)
   assertTrue("Contrast $contrast, dark=$dark",contrast>=4.5);bitmap.recycle()
  }
 }
 @Test fun imagesLoadAndStreamingKeepsHistoryDiagramAndImageNodes(){
  lateinit var view:RichAnswer
  fun js(code:String):String{val latch=CountDownLatch(1);var result="";instrumentation.runOnMainSync{view.evaluateJavascript(code){result=it;latch.countDown()}};assertTrue(latch.await(10,TimeUnit.SECONDS));return result}
  val bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN)
  val hash=app.store.addAsset(ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray()})
  instrumentation.runOnMainSync{view=RichAnswer(app);view.layout(0,0,800,1200);view.setOverlayStyle()}
  try{
   val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(js("typeof window.patchConversation")!="\"function\""&&System.nanoTime()<deadline)Thread.sleep(50)
   val messages=listOf(RichMessage("u","user","看看这张图",listOf(hash)),RichMessage("a","assistant","先看条件"))+(1..80).map{RichMessage("history:$it",if(it%2==0)"user"else "assistant","历史公式 \\(x_${it}=\\frac{1}{$it}\\) 和条件检查。") }
   val diagrams=listOf(RichDiagram("d","a","<p>合成图解</p>","图解说明"))
   instrumentation.runOnMainSync{view.showThread(messages,diagrams)}
   js("window.scrollTo(0,0)")
   val imageDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(js("document.querySelector('.chat-images img')?.naturalWidth||0")=="0"&&System.nanoTime()<imageDeadline)Thread.sleep(50)
   assertEquals("24",js("document.querySelector('.chat-images img').naturalWidth"))
   js("window.savedImage=document.querySelector('.chat-images img');window.savedFrame=document.querySelector('iframe');window.savedArticle=document.querySelector('article');window.transfers=[];const originalPatch=window.patchConversation;window.patchConversation=(updates,order,diagrams,animate)=>{window.transfers.push({count:updates.length,diagrams:diagrams===null,append:updates[0]?.append,full:updates[0]?.content});originalPatch(updates,order,diagrams,animate);}")
   repeat(30){n->instrumentation.runOnMainSync{view.showThread(messages+RichMessage("stream","assistant","新增内容".repeat(n+1)),diagrams)}}
   assertEquals("true",js("window.savedImage===document.querySelector('.chat-images img')&&window.savedFrame===document.querySelector('iframe')&&window.savedArticle===document.querySelector('article')"))
   assertEquals("true",js("window.transfers.every(t=>t.count===1&&t.diagrams)"));assertEquals("true",js("window.transfers.slice(1).every(t=>t.full===undefined&&t.append==='新增内容')"))
   assertEquals("true",js("document.querySelector('iframe').sandbox.contains('allow-scripts')&&!document.querySelector('iframe').sandbox.contains('allow-same-origin')"))
  }finally{instrumentation.runOnMainSync{view.destroy()}}
 }
}
