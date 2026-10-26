package cn.jieti.core
import cn.jieti.ai.*
import cn.jieti.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class CoachRulesTest {
 private val stem="设数列 x_n=1/n，求该数列在 n 趋于无穷时的极限，并说明理由。"
 private val source=QuestionSource("s","library","自编诊断练习","",stem+"\n答案：0。")
 private fun row(quote:String=stem)=buildJsonObject{put("sourceId","s");put("quote",quote);put("complete",true);put("concept","数列极限");put("examLabel","2024年数学真题");put("answerQuote","凭空编的答案")}
 @Test fun exactSourcesRejectFabricationTruncationAndAnswerLeak(){
  val item=CoachRules.candidate(row(),listOf(source),"math","2026-10-24",1)!!;assertEquals(stem,item.stem);assertEquals("",item.examLabel);assertEquals("",item.referenceAnswer)
  assertNull(CoachRules.candidate(row(stem+"\n补充条件 a=2"),listOf(source),"math","2026-10-24",1))
  assertNull(CoachRules.candidate(row(stem+"\n答案：0。"),listOf(source),"math","2026-10-24",1))
  assertNull(CoachRules.candidate(row(stem+"…"),listOf(source),"math","2026-10-24",1))
  val multi="已知曲面和连续函数，计算第二型曲面积分 \\iint_S P dy dz。";val multiSource=source.copy(text=multi)
  assertNull(CoachRules.candidate(buildJsonObject{put("sourceId","s");put("quote",multi);put("complete",true);put("concept","一元函数微分")},listOf(multiSource),"math","2026-10-24",1))
  val spaced=CoachRules.candidate(row(stem.replace("，","，\n")),listOf(source),"math","2026-10-24",2)!!;assertEquals(item.questionId,spaced.questionId)
 }
 @Test fun coldStartIsDiagnosticAndEvidenceHashIgnoresObservationChurn(){
  assertTrue(CoachRules.targets(emptyList(),"math").all{it.s("state")=="diagnostic"&&it.s("reason").contains("证据不足")})
  val r=SyncRecord("m","mastery","1",payload=payload(MasterySnapshot("math","极限","developing",listOf("e"),now())),updatedAt=now(),deviceId="test")
  val same=r.copy(rev="2",payload=JsonObject(r.payload+mapOf("observedAt" to JsonPrimitive("later"))))
  assertEquals(CoachRules.fingerprint(listOf(r),"math","goal",60),CoachRules.fingerprint(listOf(same),"math","goal",60))
  assertNotEquals(CoachRules.fingerprint(listOf(r),"math","goal",60),CoachRules.fingerprint(listOf(same.copy(payload=JsonObject(same.payload+mapOf("state" to JsonPrimitive("mastered"))))),"math","goal",60))
 }
 @Test fun recentQuestionsGuideDiagnosisWithoutBeingLabeledAsWrong(){
  val q=SyncRecord("asked","question","r",payload=payload(Question(title="条件概率提问",scope="math",classification="help",concepts=listOf("条件概率"),diagnosis="尚需独立练习验证")),updatedAt=now(),deviceId="test")
  val target=CoachRules.targets(listOf(q),"math").single();assertEquals("条件概率",target.s("concept"));assertTrue(target.s("reason").contains("提问"));assertFalse(target.s("reason").contains("错误证据"));assertNotEquals("mastered",target.s("state"))
 }
 @Test fun helpAndLowConfidenceCannotPromoteIndependentMastery(){
  val review=buildJsonObject{put("reasoningComplete",true);put("confidence",.95)}
  assertTrue(CoachRules.independent("利用夹逼定理计算极限为0。",true,false,review));assertFalse(CoachRules.independent("利用夹逼定理计算极限为0。",true,true,review));assertFalse(CoachRules.independent("选A",true,false,review));assertFalse(CoachRules.independent("利用夹逼定理计算极限为0。",true,false,buildJsonObject{put("reasoningComplete",true);put("confidence",.5)}))
  assertEquals("2026-10-25",CoachRules.day(Instant.parse("2026-10-24T16:01:00Z")))
 }
 @Test fun searchAdaptersParseEachProviderAndDeduplicateCanonicalUrls(){
  val rows=buildJsonArray{add(buildJsonObject{put("title","优质题源");put("url","https://example.org/p?utm_source=test");put("passage",stem)}.toString());add(buildJsonObject{put("title","重复题源");put("url","https://example.org/p");put("passage",stem)})}
  val yuan=QuestionSearch.parse("yuanbao",buildJsonObject{putJsonObject("Response"){put("Pages",rows)}});assertEquals(1,yuan.size)
  assertEquals("https://example.org/p",yuan.first().url)
  val tavily=QuestionSearch.parse("tavily",buildJsonObject{putJsonArray("results"){add(buildJsonObject{put("title","题源");put("url","https://example.org/q");put("content",stem)})}});assertEquals(stem,tavily.single().text)
  val bocha=QuestionSearch.parse("bocha",buildJsonObject{putJsonObject("data"){putJsonObject("webPages"){putJsonArray("value"){add(buildJsonObject{put("name","题源");put("url","https://example.org/b");put("summary",stem)})}}}});assertEquals(stem,bocha.single().text)
  assertNull(QuestionSearch.canonicalUrl("javascript:alert(1)"));assertNull(QuestionSearch.canonicalUrl("https://user:password@example.org/p"))
 }
}
