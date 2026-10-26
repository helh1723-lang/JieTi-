package cn.jieti
import android.graphics.*
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
import java.io.ByteArrayOutputStream
class HandwritingAcceptanceTest {
 @Test fun photoOnlyWorkIsPersistedReviewedAndBlurDoesNotChangeAbility()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp;val store=LearningStore(app,"handwriting-${uid()}.sqlite");var readable=true;var calls=0
  val provider=object:Provider(app.settings){override suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?,stream:Boolean,priority:Boolean,jsonMode:Boolean,onDelta:(String)->Unit):Turn{calls++;assertTrue(messages.last().jsonObject["content"]!!.jsonArray.any{it.jsonObject.s("type")=="image_url"});return Turn(buildJsonObject{put("transcription",if(readable)"因为 n 趋于正无穷，1/n 趋于0，故极限为0。"else "第二行分母[无法辨认]");put("readable",readable);put("outcome","correct");put("reasoningComplete",true);put("confidence",.98);put("feedback",if(readable)"所上传步骤使用倒数极限，推理过程完整，结果正确。"else "第二行分母不清楚，请补拍该行，暂不判断对错。")}.toString())}}
  try{
   val bitmap=Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);Canvas(bitmap).drawText("n -> infinity; 1/n -> 0",45f,150f,Paint().apply{color=Color.BLACK;textSize=44f});val hash=store.addAsset(ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,95,it);it.toByteArray()});bitmap.recycle()
   val coach=LearningCoach(app,store,provider,MemoryEngine(store),Library(app,store,provider,app.settings,AtomicInteger()),QuestionSearch(app.settings)){_,_,_->}
   for(slot in 1..2){val id="daily:math:${CoachRules.day()}:$slot";val q=store.put("question",payload(Question(title="自编图片作答验收 $slot",scope="math",transcription="设数列 a_n=$slot/n，求 n 趋于无穷时的极限。",scopeOrigin="user",practiceId=id)));store.put("practice",payload(PracticeItem(day=CoachRules.day(),slot=slot,scope="math",concept="数列极限",stem=q.payload.s("transcription"),questionId=q.id,status="ready")),id)
    if(slot==2){store.editUser(q.id,buildJsonObject{put("scope","signals")},store.get(q.id)!!.rev);coach.grade(id,"",true,listOf(hash));assertTrue(coach.state.value.error.contains("学科"));assertEquals(1,store.all("attempt").size);store.editUser(q.id,buildJsonObject{put("scope","math")},store.get(q.id)!!.rev)};readable=slot==1;coach.grade(id,"",true,listOf(hash));assertEquals("",coach.state.value.error);val attempt=store.get(store.get(id)!!.payload.s("lastAttemptId"))!!;assertEquals(listOf(hash),attempt.payload.strings("assetIds"));assertTrue(attempt.payload.s("transcription").isNotBlank());assertEquals(if(readable)"correct"else "unknown",attempt.payload.s("outcome"));assertEquals(readable,attempt.payload.b("independent"));assertEquals(listOf(hash),store.messages(q.id).first().payload.strings("assetIds"));coach.grade(id,"",true,listOf(hash));assertEquals(slot,calls)
   }
   assertEquals("developing",store.all("mastery").single().payload.s("state"));assertEquals(1,store.all("evidence").count{it.payload.b("independent")})
  }finally{store.db.close()}
 }
}
