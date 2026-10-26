package cn.jieti.ai

import cn.jieti.core.*
import cn.jieti.data.*

object Curriculum {
 private val separator=Regex("[\\s·（）()，,。.:：-]")
 private val byScope=CurriculumCatalog.chapters.groupBy{it.scope}
 private val names=CurriculumCatalog.chapters.flatMap{c->c.topics.map{t->Triple(c.scope,t.id,(listOf(t.title)+t.aliases).map{normalized(it)})}}.groupBy{it.first}
 // Bound arbitrary model/user labels; do not retain learning records in this cache.
 private val matches=object:LinkedHashMap<Pair<String,String>,String?>(512,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Pair<String,String>,String?>?)=size>512}
 fun chapters(scope:String)=byScope[scope].orEmpty()
 fun normalized(value:String)=value.lowercase().replace(separator,"")
 @Synchronized fun topicFor(scope:String,concept:String):String?{
  val value=normalized(concept);if(value.isBlank())return null
  val key=scope to value;if(matches.containsKey(key))return matches[key]
  var best=0;var winner:String?=null;var tied=false
  for(topic in names[scope].orEmpty()){
   var score=0
   for(alias in topic.third){val candidate=if(alias==value)10000+alias.length else if(alias.length>=3&&value.contains(alias))alias.length else 0;if(candidate>score)score=candidate}
   if(score>best){best=score;winner=topic.second;tied=false}else if(score>0&&score==best)tied=true
  }
  val result=winner.takeUnless{tied};matches[key]=result;return result
 }
 data class ChapterStats(val total:Int,val mastered:Int,val refresh:Int,val developing:Int)
 data class Progress(val scope:String,val chapters:List<CourseChapter>,val states:Map<String,String>,val stats:Map<String,ChapterStats>,val unassigned:List<String>){
  fun state(id:String)=states[id]?:"diagnostic"
  val total:Int=stats.values.sumOf{it.total}
  val mastered:Int=stats.values.sumOf{it.mastered}
  val refresh:Int=stats.values.sumOf{it.refresh}
 }
 private fun rank(state:String)=when(state){"needs_refresh"->4;"diagnostic","needs_evidence"->3;"developing"->2;else->1}
 /** Resolve each distinct concept once, then aggregate all chapters in a single pass. */
 data class MasteryEntry(val scope:String,val concept:String,val state:String)
 fun progress(records:List<SyncRecord>,scope:String)=progressFromMastery(records.filter{!it.deleted&&it.kind=="mastery"}.map{MasteryEntry(it.payload.s("scope"),it.payload.s("concept"),it.payload.s("state"))},scope)
 fun progressFromMastery(entries:List<MasteryEntry>,scope:String):Progress{
  val states=HashMap<String,String>();val unknown=linkedSetOf<String>();val concepts=HashMap<String,String?>()
  for(row in entries)if(row.scope==scope){
   val concept=row.concept;val id=if(concepts.containsKey(concept))concepts[concept]else topicFor(scope,concept).also{concepts[concept]=it}
   if(id==null){if(concept.isNotBlank())unknown.add(concept);continue}
   val raw=row.state;val next=if(raw in listOf("mastered","developing","needs_refresh"))raw else "needs_evidence"
   if(states[id]==null||rank(next)>rank(states.getValue(id)))states[id]=next
  }
  val chapters=chapters(scope);val stats=chapters.associate{c->
   var mastered=0;var refresh=0;var developing=0
   for(topic in c.topics)when(states[topic.id]){"mastered"->mastered++;"needs_refresh"->refresh++;"developing"->developing++}
   c.id to ChapterStats(c.topics.size,mastered,refresh,developing)
  }
  return Progress(scope,chapters,states.toMap(),stats,unknown.toList())
 }
 fun state(records:List<SyncRecord>,scope:String,id:String)=progress(records,scope).state(id)
 fun label(state:String)=when(state){"mastered"->"已掌握";"developing"->"巩固中";"needs_refresh"->"待复习";else->"待诊断"}
 fun diagnosticTargets(scope:String)=chapters(scope).filter{it.number!="00"}.take(3).map{it.topics.first().title}
}
