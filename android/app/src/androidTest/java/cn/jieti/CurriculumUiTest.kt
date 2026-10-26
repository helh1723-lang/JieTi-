package cn.jieti

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ai.*
import cn.jieti.core.*
import cn.jieti.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class CurriculumUiTest {
 @Test fun lecturePageResolutionRejectsMissingAndWrongScope(){
  fun doc(id:String,title:String,scope:String,pages:Int)=SyncRecord(id,"document","1",payload=buildJsonObject{put("title",title);put("scope",scope);put("pages",pages)},updatedAt=now(),deviceId="fixture")
  val chapter=Curriculum.chapters("signals")[3];val topic=chapter.topics.first{it.id=="3.4"}
  assertEquals("direct" to 17,cn.jieti.ui.courseSource(listOf(doc("direct","第三章讲义.pdf","signals",39)),chapter,topic))
  assertEquals("combined" to 69,cn.jieti.ui.courseSource(listOf(doc("combined","1-5章讲义.pdf","signals",136)),chapter,topic))
  assertNull(cn.jieti.ui.courseSource(listOf(doc("wrong","第三章讲义.pdf","math",39)),chapter,topic))
  val missing=Curriculum.chapters("signals")[6];assertNull(cn.jieti.ui.courseSource(listOf(doc("combined","1-5章讲义.pdf","signals",136)),missing,missing.topics.first()))
 }
 @Test fun isolatedUiSession()=runBlocking<Unit>{
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  assumeTrue(File(app.filesDir,"curriculum-ui-opt-in").exists())
  val ready=File(app.filesDir,"curriculum-ui-ready");val done=File(app.filesDir,"curriculum-ui-done");done.delete()
  val store=LearningStore(app,"curriculum-ui-${uid()}.sqlite");val oldStore=app.store;val oldTheme=app.uiTheme.value;val oldFont=app.uiFontScale.value
  try{
   store.setSetting("coach.auto","false");store.setSetting("currentScope","signals")
   for((concept,state)in listOf("傅里叶级数" to "mastered","傅里叶变换" to "needs_refresh","冲激信号" to "developing"))store.put("mastery",payload(MasterySnapshot("signals",concept,state,emptyList(),now())))
   val area=buildJsonObject{put("concept","傅里叶变换");put("state","needs_refresh");put("reason","验收样例：复核时移和频移的适用条件");put("priority",5)}
   val steps=(1..7).map{buildJsonObject{put("day",it);put("focus","傅里叶变换");put("minutes",45);put("task","独立说明时移性质，并完成一道变式。");put("checkpoint","条件与公式一致")}}
   store.put("plan",payload(LearningPlan("signals","858系统复习","界面验收样例：巩固傅里叶变换，结合性质练习。",listOf(area),steps,emptyList(),"fixture",now(),"active",45)),"plan:signals")
   val qid="curriculum-fixture-question";val id="daily:signals:${CoachRules.day()}:1";val stem="已知单位冲激信号，说明其筛选性质及适用条件。"
   store.put("question",payload(Question(title="自编界面验收题",scope="signals",concepts=listOf("冲激信号"),transcription=stem,practiceId=id)),qid)
   store.put("practice",payload(PracticeItem(day=CoachRules.day(),slot=1,scope="signals",concept="冲激信号",stem=stem,sourceType="library",sourceTitle="自编界面验收题（非历年真题）",questionId=qid,status="ready")),id)
   app.store=store;app.uiTheme.value="light"
   app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));ready.writeText("ready")
   withTimeout(600000){while(!done.exists()){
    File(app.filesDir,"curriculum-ui-theme").takeIf{it.exists()}?.let{app.uiTheme.value=it.readText().trim();it.delete()}
    File(app.filesDir,"curriculum-ui-font").takeIf{it.exists()}?.let{app.uiFontScale.value=it.readText().trim().toFloat();it.delete()}
    delay(150)
   }}
  }finally{app.store=oldStore;app.uiTheme.value=oldTheme;app.uiFontScale.value=oldFont;ready.delete();File(app.filesDir,"curriculum-ui-opt-in").delete()}
 }
}
