package cn.jieti
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.library.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

@RunWith(AndroidJUnit4::class) class AcceptanceTest{
 @Test fun explicitRenewalAfterForgettingAndServerMerge()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  assertEquals(2,app.store.db.openHelper.readableDatabase.version)
  val scope="acceptance-"+uid();app.memory.explicit("记住，以后回答简短",scope,"new-user-1")
  val old=app.store.all("memory").first{it.payload.s("scope")==scope};app.store.forget(old.id)
  app.store.ingest(old.copy(rev=uid()));assertTrue(app.store.get(old.id)!!.deleted)
  app.memory.explicit("记住，以后回答详细",scope,"new-user-2")
  assertTrue(app.memory.recall(scope,"回答").any{it.payload.s("value").contains("详细")})
  val fresh=app.store.all("memory").last{it.payload.s("scope")==scope}
  val uploaded=fresh.rev;val remote=fresh.copy(rev=uid(),baseRev="server-parent",payload=kotlinx.serialization.json.JsonObject(fresh.payload+mapOf("locked" to kotlinx.serialization.json.JsonPrimitive(true))))
  app.store.acceptServer(remote,uploaded);assertTrue(app.store.get(fresh.id)!!.payload.b("locked"))
  app.store.forget(fresh.id)
 }
 @Test fun seedExplicitlySyntheticUiExample()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val bitmap=android.graphics.Bitmap.createBitmap(1100,360,android.graphics.Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.rgb(35,45,60);textSize=36f};canvas.drawText("自编验收题：有界数列一定收敛吗？",35f,80f,paint);canvas.drawText("请给出理由或反例，并检查结论的条件。",35f,150f,paint);canvas.drawText("本图为程序验收数据，不是你的学习记录。",35f,260f,paint)
  val bytes=java.io.ByteArrayOutputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray()};val hash=app.store.addAsset(bytes)
  app.store.put("question",payload(Question("自编验收：有界与收敛","math",listOf("数列极限"),"判断有界数列是否一定收敛。","无真实学生过程",listOf(hash),"help",emptyList(),"验收示例：先检查有界与收敛之间的逻辑关系。","反例为 $ a_n=(-1)^n $。它有界但不收敛。","done")),"acceptance:androidq")
  app.store.put("message",payload(Message("acceptance:androidq","user","只给一点提示。","轻提示",emptyList(),listOf(hash),"")),"acceptance:androiduser")
  app.store.put("message",payload(Message("acceptance:androidq","assistant","有界只限制活动范围。想想一个在两个数之间反复跳动的数列：\n\n$ a_n=(-1)^n $\n\n（以上为公式渲染验收示例。）","轻提示",listOf("seed:teaching"),emptyList(),"")),"acceptance:androidassistant")
  val html="""<h3>自编验收 · 序列观察</h3><p>拖动滑块，观察序列项：</p><input id="n" type="range" min="1" max="10" value="1"><p id="v"></p><script>function update(){document.getElementById('v').textContent='n = '+n.value+'，示例数值 = '+Math.sin(+n.value).toFixed(3)}n.oninput=update;update();</script>"""
  app.store.put("artifact",payload(Artifact("acceptance:androidq","轻提示",html,"验收用交互页面：滑块改变 n，数值随之变化。","ready",emptyList())),"acceptance:artifact")
 }
 @Test fun roomPersistenceForgettingAndFullTextSearch()=runBlocking{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  val m=app.store.put("memory",payload(MemoryItem("preference","math","acceptance-test","验收用临时记忆",listOf("acceptance-only"),"explicit",1.0,now(),"active",false)))
  app.store.forget(m.id);val old=m.copy(rev=uid());app.store.ingest(old);assertTrue(app.store.get(m.id)!!.deleted)
  app.store.dao.chunk(ChunkRow("acceptance:chunk","acceptance:document","math",1,"连续有界数列",Library.tokenize("连续有界数列")))
  assertTrue(app.store.dao.search("math","\"有界\"","").any{it.id=="acceptance:chunk"})
 }
 @Test fun fiftyThousandVectorIndexAndKnownNeighbor(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=MyObjectBox.builder().androidContext(context).name("acceptance-vectors-"+System.currentTimeMillis()).build()
  try{
   val box=store.boxFor(VectorChunk::class.java);val random=Random(858);var query=FloatArray(1024)
   val start=System.nanoTime()
   repeat(500){batch->val values=ArrayList<VectorChunk>();repeat(100){offset->val n=batch*100+offset;val v=FloatArray(1024){random.nextFloat()-.5f};if(n==858)query=v.copyOf();values.add(VectorChunk(0,"chunk:$n","math","synthetic-1024",v))};box.put(values)}
   val indexedMs=(System.nanoTime()-start)/1000000
   val searchStart=System.nanoTime();val found=box.query(VectorChunk_.vector.nearestNeighbors(query,10)).build().use{it.find()};val searchMs=(System.nanoTime()-searchStart)/1000000
   assertEquals(50000L,box.count());assertTrue(found.any{it.chunkId=="chunk:858"})
   android.util.Log.i("JietiAcceptance","50000 vectors: indexMs=$indexedMs searchMs=$searchMs")
  }finally{store.close();store.deleteAllFiles()}
 }
}
