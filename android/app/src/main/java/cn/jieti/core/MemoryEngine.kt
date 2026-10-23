package cn.jieti.core

import cn.jieti.data.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object MemoryRules {
 fun promotesPreference(sourceIds:List<String>)=sourceIds.distinct().size>=2
 fun mastery(evidence:List<Pair<String,Evidence>>,dates:Map<String,String>,at:Instant=Instant.now()):String {
  val successes=evidence.filter{it.second.outcome=="correct"&&it.second.independent&&it.second.hintLevel==0}
  if(evidence.isEmpty())return "unknown"
  val last=evidence.mapNotNull{dates[it.first]?.let(Instant::parse)}.maxOrNull()?:return "unknown"
  if(ChronoUnit.DAYS.between(last,at)>30)return "needs_refresh"
  val days=successes.mapNotNull{dates[it.first]?.let{d->Instant.parse(d).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()}}.distinct()
  return if(successes.map{it.second.questionId}.distinct().size>=3&&days.size>=2)"mastered" else if(successes.isNotEmpty())"developing" else "needs_evidence"
 }
 fun context(r:SyncRecord)=buildJsonObject {put("id",r.id);for(k in listOf("scope","category","value","origin","confidence","observedAt"))r.payload[k]?.let{put(k,it)};put("sourceIds",JsonArray(r.payload.strings("sourceIds").takeLast(3).map(::JsonPrimitive)))}
 fun recall(records:List<SyncRecord>,scope:String,query:String,maxChars:Int=3000):List<SyncRecord>{
  val keys=query.lowercase().filterNot{it.isWhitespace()}.windowed(2).toSet()
  var budget=0
  return records.filter{!it.deleted&&it.payload.s("status")=="active"&&(it.payload.s("scope")==scope||it.payload.s("scope")=="global")}
   .sortedByDescending{r->(if(r.payload.s("origin")=="explicit")10000 else 0)+(if(r.payload.b("locked"))1000 else 0)+keys.count{r.payload.s("value").lowercase().contains(it)}}
   .filter{r->val cost=context(r).toString().toByteArray(Charsets.UTF_8).size;if(budget+cost>maxChars)false else{budget+=cost;true}}
 }
}
class MemoryEngine(private val store:LearningStore){
 suspend fun observeStyle(scope:String,family:String,choice:String,sourceIds:List<String>):Boolean {
  val value=TeachingStyle.value(family,choice)?:return false
  val valid=mutableListOf<String>();for(id in sourceIds.distinct()){val m=store.get(id);if(m!=null&&!m.deleted&&m.kind=="message"&&m.payload.s("role")=="user"&&store.get(m.payload.s("questionId"))?.deleted!=true)valid.add(id)}
  if(valid.isEmpty())return false
  val related=store.all("memory").filter{TeachingStyle.family(it.payload.s("key"))==family&&(it.payload.s("scope")==scope||it.payload.s("scope")=="global")}
  if(related.any{it.payload.b("locked")||it.payload.s("status")=="paused"||it.payload.s("origin")=="explicit"})return false
  val key="$family:observed:$choice";val old=related.firstOrNull{it.payload.s("key")==key&&it.payload.s("scope")==scope}
  if(old!=null&&valid.all{it in old.payload.strings("sourceIds")})return false
  upsert(MemoryItem("preference",scope,key,value,valid,"inferred",0.4,now(),"candidate",false))
  val saved=store.all("memory").firstOrNull{it.payload.s("scope")==scope&&it.payload.s("key")==key}?:return false
  if(saved.payload.s("status")=="active")for(r in related)if(r.id!=saved.id&&r.payload.s("scope")==scope&&r.payload.s("origin")=="inferred"&&r.payload.s("status")=="active")store.edit(r.id,buildJsonObject{put("status","superseded")})
  return true
 }
 suspend fun seed(){
  val historical=store.get("seed:background")
  if(historical!=null&&!historical.deleted&&historical.payload.s("origin")=="imported"&&historical.payload.s("scope")=="global")store.edit(historical.id,buildJsonObject{put("scope","math")})
  if(store.setting("seeded")=="yes")return
  for((key,value) in listOf("goal" to "学习目标以用户提供的教材、大纲和当前需求为准。","teaching" to "优先渐进式启发，默认只提供最小必要提示。")){
   store.put("memory",payload(MemoryItem("profile",if(key=="background")"math"else "global",key,value,listOf("imported-system-prompt"),"imported",0.7,now(),"active",false)),"seed:$key")
  };store.setSetting("seeded","yes")
 }
 suspend fun refreshStale(){for(r in store.all("mastery")){val observed=runCatching{Instant.parse(r.payload.s("observedAt"))}.getOrNull()?:continue;if(ChronoUnit.DAYS.between(observed,Instant.now())>30&&r.payload.s("state")!="needs_refresh")store.edit(r.id,buildJsonObject{put("state","needs_refresh")})}}
 suspend fun explicit(text:String,scope:String,sourceId:String){
  if(text.contains("记住")||text.contains("以后")||text.contains("忘掉")||text.contains("不要记录")){
   if(text.contains("忘掉")||text.contains("不要记录")){
    val needle=text.replace(Regex("请|忘掉|这条|以后|不要记录|记忆|[，。！ ]"),"")
    val matches=store.all("memory").filter{needle.isNotBlank()&&it.payload.s("value").contains(needle)}
    for(m in matches)store.forget(m.id)
   }else{
    val content=text.replace(Regex("^.*?(?:记住[：:,， ]*|以后[：:,， ]*)"),"").trim()
    var key=when{Regex("简短|啰嗦|详细|精简|回答.*长|回答.*短").containsMatchIn(content)->"response_length";Regex("提示.*步|提示.*多|提示.*少|提示.*深").containsMatchIn(content)->"hint_granularity";Regex("图解|画图|可视化|动画").containsMatchIn(content)->"visualization";Regex("符号|归一化|变换定义").containsMatchIn(content)->"notation";else->"explicit:${sha(content.toByteArray()).take(20)}"}
    val targetScope=if(Regex("所有学科|全局|默认回答|一律|以后都").containsMatchIn(text))"global"else scope
    if(store.all("forget").any{it.payload.s("scope")==targetScope&&it.payload.s("key")==key}){
     val prefix="$key:renewed:"
     key=store.all("memory").lastOrNull{it.payload.s("scope")==targetScope&&it.payload.s("origin")=="explicit"&&it.payload.s("key").startsWith(prefix)}?.payload?.s("key")?:prefix+sourceId
    }
    if(content.isNotBlank())upsert(MemoryItem("preference",targetScope,key,content,listOf(sourceId),"explicit",1.0,now(),"active",false))
   }
  }
 }
 suspend fun upsert(m:MemoryItem){
  require(m.value.length<=1200&&m.key.length<=200)
  val blocked=store.all("forget").any{it.payload.s("scope")==m.scope&&(it.payload.s("key")==m.key||it.payload.strings("sourceIds").any{source->source in m.sourceIds})}
  if(blocked)return
  val old=store.all("memory").firstOrNull{it.payload.s("scope")==m.scope&&it.payload.s("key")==m.key}
  if(old?.payload?.b("locked")==true)return
  if(old?.payload?.s("status")=="paused"&&m.origin!="explicit")return
  if(old?.payload?.s("origin")=="explicit"&&m.origin!="explicit")return
  val prior=if(old?.payload?.s("status")=="superseded")emptyList()else old?.payload?.strings("sourceIds").orEmpty()
  val sources=(prior+m.sourceIds).distinct()
  val status=if(m.origin=="inferred"&&!MemoryRules.promotesPreference(sources))"candidate"else "active"
  store.put("memory",payload(m.copy(sourceIds=sources,status=status,confidence=if(m.origin=="explicit")1.0 else if(status=="active")0.8 else 0.4)),old?.id?:uid())
 }
 suspend fun recall(scope:String,query:String):List<SyncRecord>{val allowed=mutableListOf<SyncRecord>();for(m in store.all("memory"))if(!store.forgotten(m))allowed.add(m);return MemoryRules.recall(allowed,scope,query)}
 suspend fun retireDeletedSources(){for(m in store.all("memory").filter{it.payload.s("origin")=="inferred"&&!it.payload.b("locked")&&it.payload.s("status")!="paused"}){
  val previous=m.payload.strings("sourceIds");val remaining=mutableListOf<String>();for(id in previous)if(store.get(id)?.deleted!=true)remaining.add(id)
  if(remaining.size!=previous.size)store.edit(m.id,buildJsonObject{put("sourceIds",JsonArray(remaining.map(::JsonPrimitive)));put("status",if(remaining.isEmpty())"superseded"else if(remaining.size<2)"candidate"else m.payload.s("status"))})
 }}
 suspend fun refreshMastery(scope:String,concept:String){val all=store.all("evidence");val evidence=all.filter{it.payload.s("scope")==scope&&it.payload.s("concept")==concept};val state=MemoryRules.mastery(evidence.map{it.id to it.value<Evidence>()},all.associate{it.id to it.updatedAt});store.put("mastery",payload(MasterySnapshot(scope,concept,state,evidence.map{it.id},now())),"mastery:${sha("$scope:$concept".toByteArray()).take(24)}")}
}
