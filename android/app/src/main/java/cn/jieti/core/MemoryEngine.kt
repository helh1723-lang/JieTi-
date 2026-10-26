package cn.jieti.core

import cn.jieti.data.*
import androidx.room.withTransaction
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object MemoryRules {
 fun promotesPreference(sourceIds:List<String>)=sourceIds.distinct().size>=2
 fun mastery(evidence:List<Pair<String,Evidence>>,dates:Map<String,String>,at:Instant=Instant.now()):String {
  val dated=evidence.mapNotNull{item->runCatching{dates[item.first]?.let(java.time.Instant::parse)?.let{item to it}}.getOrNull()}.filter{!it.second.isAfter(at)}
  if(dated.isEmpty())return "unknown"
  if(ChronoUnit.DAYS.between(dated.maxOf{it.second},at)>30)return "needs_refresh"
  // A revised result for the same question replaces its older result.
  val recent=dated.filter{ChronoUnit.DAYS.between(it.second,at)<=30}.groupBy{it.first.second.questionId}.values.map{rows->rows.maxBy{it.second}}.sortedBy{it.second}
  val lastError=recent.lastOrNull{it.first.second.outcome=="incorrect"}?.second
  val successes=recent.filter{it.first.second.let{e->e.outcome=="correct"&&e.independent&&e.hintLevel==0}&&(lastError==null||it.second>lastError)}
  val days=successes.map{it.second.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()}.distinct()
  return if(successes.size>=3&&days.size>=2)"mastered" else if(successes.isNotEmpty()||recent.any{it.first.second.outcome=="correct"&&it.first.second.independent&&it.first.second.hintLevel==0})"developing" else "needs_evidence"
 }
 enum class Intent { REMEMBER, FORGET, SKIP, NONE }
 fun directText(text:String)=text.replace(Regex("(?s)```.*?```|[“\"].*?[”\"]|‘.*?’|「.*?」|『.*?』"),"").lineSequence().filterNot{it.trimStart().startsWith(">")}.joinToString("\n")
 fun intent(text:String):Intent {
  val direct=directText(text)
  if(Regex("(?:不要|不用|别|无需|不必|不许|不能|不想|别再)(?:再|帮我|替我)?(?:记住|记录|保存.*记忆|记下)|不.*(?:加入|写入).*记忆").containsMatchIn(direct))return Intent.SKIP
  if(Regex("(?:^|[。！\n；])\\s*(?:请|帮我)?(?:忘掉|忘记|遗忘|删除记忆)").containsMatchIn(direct))return Intent.FORGET
  if(Regex("(?:^|[。！\n；])\\s*(?:请|帮我)?记住|^以后(?:都|请|回答|提示|讲解|解释|默认)").containsMatchIn(direct))return Intent.REMEMBER
  return Intent.NONE
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
  val suppressed=store.all("forget").flatMap{it.payload.strings("sourceIds")}.toSet();valid.removeAll(suppressed);if(valid.isEmpty())return false
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
  val intent=MemoryRules.intent(text);val direct=if(intent==MemoryRules.Intent.REMEMBER)text.trim()else MemoryRules.directText(text)
  when(intent){
   MemoryRules.Intent.NONE->return
   MemoryRules.Intent.SKIP->{store.put("forget",payload(ForgetRule("",scope,"source:$sourceId",listOf(sourceId),"本轮不记录长期记忆")),"skip-memory:$sourceId");return}
   MemoryRules.Intent.FORGET->{
    val needle=direct.replace(Regex("^(?:请|帮我)?(?:忘掉|忘记|遗忘|删除记忆)[：:,， ]*"),"").trim().trimEnd('。','！')
    if(needle.length>=2)for(m in store.all("memory").filter{it.payload.s("scope") in listOf(scope,"global")&&it.payload.s("value").contains(needle)})store.forget(m.id)
    return
   }
   MemoryRules.Intent.REMEMBER->{
    val content=direct.replace(Regex("^.*?(?:记住[：:,， ]*|以后[：:,， ]*)"),"").trim()
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
 suspend fun refreshMastery(scope:String,concept:String):Unit=store.db.withTransaction{
  val evidence=store.all("evidence").filter{it.payload.s("scope")==scope&&it.payload.s("concept")==concept}
  val dates=mutableMapOf<String,String>()
  for(e in evidence){val sourceDates=mutableListOf<String>();for(id in e.payload.strings("sourceIds"))store.get(id)?.takeUnless{it.deleted}?.updatedAt?.let{sourceDates.add(it)};dates[e.id]=sourceDates.maxOrNull()?:e.updatedAt}
  saveMastery(scope,concept,evidence,dates);Unit
 }
 /** Startup/sync maintenance reads evidence and source dates once, not once per concept. */
 suspend fun refreshAllMastery():Int=store.db.withTransaction{
  val masteries=store.all("mastery");if(masteries.isEmpty())return@withTransaction 0
  val evidence=store.all("evidence");val grouped=evidence.groupBy{it.payload.s("scope") to it.payload.s("concept")}
  val dates=HashMap<String,String>()
  val ids=evidence.flatMap{it.payload.strings("sourceIds")}.distinct()
  for(batch in ids.chunked(400))for(row in store.dao.rowsByIds(batch)){val r=wire.decodeFromString<SyncRecord>(row.json);if(!r.deleted)dates[r.id]=r.updatedAt}
  var changed=0
  for(m in masteries.distinctBy{it.payload.s("scope") to it.payload.s("concept")}){val scope=m.payload.s("scope");val concept=m.payload.s("concept");val rows=grouped[scope to concept].orEmpty();val observed=rows.associate{e->e.id to (e.payload.strings("sourceIds").mapNotNull{dates[it]}.maxOrNull()?:e.updatedAt)}
   if(saveMastery(scope,concept,rows,observed))changed++
  }
  changed
 }
 private suspend fun saveMastery(scope:String,concept:String,evidence:List<SyncRecord>,dates:Map<String,String>):Boolean{
  val state=MemoryRules.mastery(evidence.map{it.id to it.value<Evidence>()},dates);val id="mastery:${sha("$scope:$concept".toByteArray()).take(24)}";val old=store.get(id)
  val newest=dates.values.mapNotNull{runCatching{Instant.parse(it)}.getOrNull()}.filter{!it.isAfter(Instant.now())}.maxOrNull()
  val observed=old?.payload?.s("observedAt")?.let{runCatching{Instant.parse(it)}.getOrNull()}
  if(old?.deleted==false&&old.payload.s("state")==state&&old.payload.strings("evidenceIds").toSet()==evidence.map{it.id}.toSet()&&(newest==null||observed!=null&&!observed.isBefore(newest)))return false
  store.put("mastery",payload(MasterySnapshot(scope,concept,state,evidence.map{it.id},now())),id);return true
 }
}
