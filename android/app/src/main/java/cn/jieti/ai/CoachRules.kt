package cn.jieti.ai

import cn.jieti.core.*
import cn.jieti.data.*
import kotlinx.serialization.json.*
import java.time.*

object CoachRules {
 fun day(at:Instant=Instant.now())=at.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString()
 fun normalized(text:String)=text.replace(Regex("\\s+"),"").trim()
 fun identity(stem:String)=sha(normalized(stem).toByteArray())
 fun fingerprint(records:List<SyncRecord>,scope:String,goal:String,minutes:Int):String {
  val keys=mapOf("memory" to listOf("scope","key","value","status","origin","locked","sourceIds"),"mastery" to listOf("scope","concept","state","evidenceIds"),"evidence" to listOf("scope","concept","outcome","independent","hintLevel","sourceIds","sourceHash"),"question" to listOf("scope","concepts","classification","diagnosis","studentWork","analysisState"),"attempt" to listOf("scope","concept","outcome","independent","status","sourceHash"))
  return sha(buildJsonObject{put("scope",scope);put("curriculumVersion",CurriculumCatalog.version);put("goal",goal);put("minutes",minutes);putJsonArray("inputs"){for(r in records.filter{!it.deleted&&it.kind in keys}.sortedBy{it.id})add(buildJsonObject{put("id",r.id);for(k in keys.getValue(r.kind))put(k,r.payload[k]?:JsonNull)})}}.toString().toByteArray())
 }
 fun targets(records:List<SyncRecord>,scope:String):List<JsonObject>{
  val source=records.filter{!it.deleted&&it.payload.s("scope")==scope};val concepts=linkedSetOf<String>()
  source.filter{it.kind in listOf("mastery","evidence")}.map{it.payload.s("concept")}.filter{it.isNotBlank()}.forEach{concepts.add(it)}
  source.filter{it.kind=="question"}.flatMap{it.payload.strings("concepts")}.forEach{concepts.add(it)}
  if(concepts.isEmpty())return (if(scope=="signals")Curriculum.diagnosticTargets(scope)else listOf("数列极限","导数与微分的定义","定积分的几何与物理应用")).map{buildJsonObject{put("concept",it);put("state","diagnostic");put("reason","证据不足，先做诊断，不判断为薄弱");put("priority",3);put("confidence",0.0);put("sourceIds",JsonArray(emptyList()))}}
  return concepts.map{concept->val rows=source.filter{it.payload.s("concept")==concept||it.kind=="question"&&concept in it.payload.strings("concepts")};val evidence=rows.filter{it.kind=="evidence"};val recent=evidence.filter{runCatching{Duration.between(Instant.parse(it.updatedAt),Instant.now()).toDays()<=30}.getOrDefault(false)};val errors=recent.count{it.payload.s("outcome")=="incorrect"};val state=rows.lastOrNull{it.kind=="mastery"}?.payload?.s("state")?:"needs_evidence";val asked=rows.count{it.kind=="question"};val priority=if(errors>0)8+errors.coerceAtMost(3)else when(state){"mastered"->1;"needs_refresh"->5;"developing"->4;else->6}
   buildJsonObject{put("concept",concept);put("state",state);put("priority",priority);put("confidence",if(errors>0).8 else if(evidence.isNotEmpty()).6 else .3);put("reason",if(errors>0)"近期有 $errors 条错误证据，需要针对性巩固"else when(state){"mastered"->"已有独立证据，安排间隔复习";"needs_refresh"->"旧证据需要更新";"developing"->"已有进展，继续迁移练习";else->if(asked>0)"近期在此知识点有提问，先通过独立作答验证，尚不判断为薄弱"else "尚缺稳定的独立作答证据"});put("sourceIds",JsonArray(rows.map{JsonPrimitive(it.id)}))}
  }.sortedByDescending{it.i("priority")}.take(6)
 }
 fun candidate(row:JsonObject,sources:List<QuestionSource>,scope:String,date:String,slot:Int):PracticeItem?{
  val source=sources.firstOrNull{it.id==row.s("sourceId")}?:return null;val quote=row.s("quote").trim()
  if(quote.contains("[无法辨认]")||quote.contains('\uFFFD')||quote.contains('\uFFFC')||Regex("【答案】|【解析】|参考答案|答案[:：]|(?:^|\\n)\\s*解[:：]").containsMatchIn(quote))return null
  if(!row.b("complete")||quote.length !in 16..5000||quote.endsWith("…")||quote.endsWith("...")||!normalized(source.text).contains(normalized(quote))||!Regex("求|证明|计算|设|已知|判断|若|选择|[Ff]ind|[Ee]valuate").containsMatchIn(quote))return null
  if(Regex("\\\\iiint|\\\\iint").containsMatchIn(quote)&&Regex("一元|微分|导数").containsMatchIn(row.s("concept")))return null
  val label=row.s("examLabel").take(100);val year=Regex("(?:19|20)\\d{2}").find(label)?.value;val claimed=year!=null&&(source.title+source.text).contains(year)&&Regex("真题|考研|研究生|全国|858|数学[一二三]").containsMatchIn(source.title+source.text)
  val answer=row.s("answerQuote").takeIf{it.isNotBlank()&&normalized(source.text).contains(normalized(it))}.orEmpty()
  val identity=identity(quote);val qid="practice-question:"+identity.take(40)
  return PracticeItem(date,slot,scope,row.s("concept").take(80),quote,answer,source.type,source.title,source.url,quote,source.documentId,source.page,identity,if(claimed)label else "",if(claimed)"source_claim"else "practice_source",qid,"ready",false,"","","")
 }
 fun independent(work:String,selfReported:Boolean,helpUsed:Boolean,review:JsonObject)=selfReported&&!helpUsed&&work.trim().length>=8&&review.b("reasoningComplete")&&((review["confidence"] as? JsonPrimitive)?.doubleOrNull?:0.0)>=.8
}
