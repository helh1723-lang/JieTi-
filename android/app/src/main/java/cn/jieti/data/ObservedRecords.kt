package cn.jieti.data

import cn.jieti.core.SyncRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*

/** Room emits on its executor; JSON decoding and sorting must also stay off main. */
fun Flow<List<RecordRow>>.decodedRecords():Flow<List<SyncRecord>> = distinctUntilChanged().map { rows ->
 val context=currentCoroutineContext()
 rows.map { context.ensureActive();wire.decodeFromString<SyncRecord>(it.json) }.filter{!it.deleted}.sortedByDescending{it.updatedAt}
}.distinctUntilChanged().flowOn(Dispatchers.Default)

fun LearningStore.observeWorkspace(page:String,questionId:String):Flow<List<SyncRecord>> {
 val rows=when {
  page=="学习规划" -> return flowOf(emptyList()) // This page owns its two narrow subscriptions.
  page=="题目"&&questionId.isNotBlank() -> dao.observeScreen(questionId)
  page=="题目" -> dao.observeKinds(listOf("question"))
  page=="记忆" -> dao.observeKinds(listOf("memory","mastery"))
  page=="资料库" -> dao.observeKinds(listOf("document"))
  page=="导师进化" -> dao.observeKinds(listOf("policy"))
  else -> return flowOf(emptyList())
 }
 return rows.decodedRecords()
}
