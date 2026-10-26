package cn.jieti.data

import cn.jieti.ai.Curriculum
import cn.jieti.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

/** Only fields affecting a lecture link. Indexing progress never invalidates this value. */
data class CourseDocument(val id:String,val scope:String,val title:String,val pages:Int)
fun courseDocuments(records:List<SyncRecord>)=records.filter{!it.deleted&&it.kind=="document"}.map{
 CourseDocument(it.id,it.payload.s("scope"),it.payload.s("title"),it.payload.i("pages"))
}.sortedWith(compareBy({it.scope},{Curriculum.normalized(it.title.removeSuffix(".pdf"))}))

class CourseSources(documents:List<CourseDocument>){
 private data class Binding(val direct:CourseDocument?,val combined:CourseDocument?,val offset:Int?)
 private fun name(value:String)=Curriculum.normalized(value.removeSuffix(".pdf"))
 private val byChapter=buildMap {
  val docs=documents.groupBy{it.scope}.mapValues{(_,rows)->rows.groupBy{name(it.title)}}
  val offsets=mapOf("signals-1" to 2,"signals-2" to 33,"signals-3" to 52,"signals-4" to 91,"signals-5" to 110)
  for(scope in listOf("math","signals"))for(chapter in Curriculum.chapters(scope)){
   val scoped=docs[scope].orEmpty()
   if(chapter.availability=="lecture")put(chapter.id,Binding(scoped[name(chapter.source)]?.firstOrNull(),scoped[name("1-5章讲义.pdf")]?.firstOrNull(),offsets[chapter.id]))
  }
 }
 fun resolve(chapterId:String,page:Int):Pair<String,Int>?{
  if(page<=0)return null
  val source=byChapter[chapterId]?:return null
  source.direct?.let{if(page<=it.pages)return it.id to page}
  val offset=source.offset?:return null
  return source.combined?.takeIf{page+offset<=it.pages}?.let{it.id to page+offset}
 }
}

/** One subscription per input kind for both courses, independent of the selected subject. */
class CourseData(store:LearningStore,scope:CoroutineScope){
 val progress=store.dao.observeKinds(listOf("mastery")).decodedRecords()
  .map{rows->rows.map{Curriculum.MasteryEntry(it.payload.s("scope"),it.payload.s("concept"),it.payload.s("state"))}}
  .distinctUntilChanged()
  .map{entries->listOf("math","signals").associateWith{Curriculum.progressFromMastery(entries,it)}}
  .distinctUntilChanged().flowOn(Dispatchers.Default)
  .stateIn(scope,SharingStarted.WhileSubscribed(5000),emptyMap())
 val sources=store.dao.observeKinds(listOf("document")).decodedRecords()
  .map(::courseDocuments).distinctUntilChanged().map(::CourseSources).flowOn(Dispatchers.Default)
  .stateIn(scope,SharingStarted.WhileSubscribed(5000),CourseSources(emptyList()))
}
