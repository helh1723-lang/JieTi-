package cn.jieti.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.jieti.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

val wire = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun now(): String = Instant.now().toString()
fun uid(): String = UUID.randomUUID().toString()
fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun JsonObject.s(k: String): String = this[k]?.jsonPrimitive?.contentOrNull ?: ""
fun JsonObject.b(k: String): Boolean = this[k]?.jsonPrimitive?.booleanOrNull ?: false
fun JsonObject.i(k: String): Int = this[k]?.jsonPrimitive?.intOrNull ?: 0
fun JsonObject.strings(k: String): List<String> = (this[k] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
inline fun <reified T> payload(value:T): JsonObject = wire.encodeToJsonElement(value).jsonObject
inline fun <reified T> SyncRecord.value():T = wire.decodeFromJsonElement(payload)

@Entity(tableName="records",indices=[Index("kind"),Index("parentId")]) data class RecordRow(@PrimaryKey val id:String,val kind:String,val rev:String,val json:String,@ColumnInfo(defaultValue="''")val parentId:String="")
data class RecordHead(val id:String,val kind:String,val rev:String)
@Entity(tableName="history",primaryKeys=["id","rev"]) data class HistoryRow(val id:String,val rev:String,val json:String)
@Entity(tableName="conflicts") data class ConflictRow(@PrimaryKey val id:String,val recordId:String,val local:String,val incoming:String,val fields:String)
@Entity(tableName="chunks",indices=[Index("documentId")]) data class ChunkRow(@PrimaryKey val id:String,val documentId:String,val scope:String,val page:Int,val text:String,val searchText:String,val embeddingModel:String="")
@Fts4(contentEntity=ChunkRow::class) @Entity(tableName="chunks_fts") data class ChunkFts(val searchText:String)
@Dao interface LearningDao {
 @Query("SELECT * FROM records") fun observe():Flow<List<RecordRow>>
 @Query("SELECT * FROM records") suspend fun all():List<RecordRow>
 @Query("SELECT * FROM records WHERE kind=:kind") suspend fun allByKind(kind:String):List<RecordRow>
 @Query("SELECT * FROM records WHERE kind IN (:kinds)") fun observeKinds(kinds:List<String>):Flow<List<RecordRow>>
 @Query("SELECT * FROM records WHERE id IN (:ids)") suspend fun rowsByIds(ids:List<String>):List<RecordRow>
 @Query("SELECT id,kind,rev FROM records") suspend fun heads():List<RecordHead>
 @Query("SELECT * FROM records WHERE kind NOT IN ('message','artifact','citation','evidence','plan','practice','attempt') OR parentId=:questionId") fun observeScreen(questionId:String):Flow<List<RecordRow>>
 @Query("SELECT * FROM records WHERE kind='plan' OR kind='practice' AND id LIKE :pattern") fun observeCoach(pattern:String):Flow<List<RecordRow>>
 @Query("SELECT * FROM records WHERE kind='message' AND parentId=:questionId") fun observeMessages(questionId:String):Flow<List<RecordRow>>
 @Query("SELECT * FROM records WHERE kind='message' AND parentId=:questionId ORDER BY rowid") suspend fun messageRows(questionId:String):List<RecordRow>
 @Query("SELECT * FROM records WHERE id=:id") suspend fun get(id:String):RecordRow?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(row:RecordRow)
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun history(row:HistoryRow)
 @Query("SELECT * FROM history WHERE id=:id AND rev=:rev") suspend fun history(id:String,rev:String):HistoryRow?
 @Query("SELECT * FROM history WHERE id=:id AND rev>:after ORDER BY rev LIMIT 10") suspend fun historyPage(id:String,after:String):List<HistoryRow>
 @Insert suspend fun conflict(row:ConflictRow)
 @Query("SELECT * FROM conflicts") suspend fun conflicts():List<ConflictRow>
 @Query("DELETE FROM conflicts WHERE id=:id") suspend fun removeConflict(id:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun chunk(row:ChunkRow)
 @Query("SELECT * FROM chunks WHERE documentId=:id AND page=:page") suspend fun chunks(id:String,page:Int):List<ChunkRow>
 @Query("SELECT * FROM chunks WHERE id IN (:ids)") suspend fun chunksById(ids:List<String>):List<ChunkRow>
 @Query("DELETE FROM chunks WHERE documentId=:id") suspend fun deleteChunks(id:String)
 @Query("DELETE FROM chunks WHERE documentId=:id AND page=:page") suspend fun deletePage(id:String,page:Int)
 @Query("UPDATE chunks SET scope=:scope WHERE documentId=:id") suspend fun moveChunks(id:String,scope:String)
 @Query("SELECT chunks.* FROM chunks JOIN chunks_fts ON chunks.rowid = chunks_fts.rowid WHERE chunks.scope=:scope AND chunks_fts MATCH :query AND chunks.id>:after ORDER BY chunks.id LIMIT 256") suspend fun search(scope:String,query:String,after:String):List<ChunkRow>
}
@Database(entities=[RecordRow::class,HistoryRow::class,ConflictRow::class,ChunkRow::class,ChunkFts::class],version=2,exportSchema=true)
abstract class LearningDatabase:RoomDatabase(){abstract fun dao():LearningDao}

class LearningStore(val context:Context,databaseName:String="learning.sqlite"){
 val db=Room.databaseBuilder(context,LearningDatabase::class.java,databaseName).addMigrations(object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE records ADD COLUMN parentId TEXT NOT NULL DEFAULT ''");db.execSQL("CREATE INDEX IF NOT EXISTS index_records_kind ON records(kind)");db.execSQL("CREATE INDEX IF NOT EXISTS index_records_parentId ON records(parentId)");db.query("SELECT id,json FROM records").use{cursor->while(cursor.moveToNext()){val parent=runCatching{wire.decodeFromString<SyncRecord>(cursor.getString(1)).payload.s("questionId")}.getOrDefault("");if(parent.isNotBlank())db.execSQL("UPDATE records SET parentId=? WHERE id=?",arrayOf(parent,cursor.getString(0)))}}}}).build()
 val dao=db.dao()
 private val prefs=context.getSharedPreferences("identity",Context.MODE_PRIVATE)
 val deviceId:String=prefs.getString("deviceId",null)?:uid().also{prefs.edit().putString("deviceId",it).apply()}
 val assetDir=File(context.filesDir,"assets").apply{mkdirs()}
 suspend fun get(id:String)=dao.get(id)?.let{wire.decodeFromString<SyncRecord>(it.json)}
 suspend fun all(kind:String?=null,deleted:Boolean=false)=(if(kind==null)dao.all()else dao.allByKind(kind)).map{wire.decodeFromString<SyncRecord>(it.json)}.filter{deleted||!it.deleted}.sortedBy{it.updatedAt}
 suspend fun write(r:SyncRecord){
  require(Regex("[a-zA-Z0-9_.:-]{1,160}").matches(r.id)&&r.rev.length<=80)
  when(r.kind){"memory"->r.value<MemoryItem>();"mastery"->r.value<MasterySnapshot>();"policy"->r.value<TeachingPolicyVersion>();"evaluation"->r.value<PolicyEvaluation>();"forget"->r.value<ForgetRule>();"question"->r.value<Question>();"message"->r.value<Message>();"evidence"->r.value<Evidence>();"document"->r.value<Document>();"citation"->r.value<Citation>();"artifact"->r.value<Artifact>();"note"->r.value<Note>();"setting"->r.value<Setting>();"plan"->r.value<LearningPlan>();"practice"->r.value<PracticeItem>();"attempt"->r.value<PracticeAttempt>();else->error("未知记录类型")}
  if(r.kind=="document"&&get(r.id)?.payload?.s("scope")!=r.payload.s("scope"))dao.moveChunks(r.id,r.payload.s("scope"))
  if(r.kind=="question"){val previous=get(r.id);if(previous!=null&&cn.jieti.ai.StudyContext.inputFields.any{previous.payload[it]!=r.payload[it]}&&r.payload.s("analysisState")!="current")invalidateDerived(r.id)}
  val json=wire.encodeToString(r);dao.history(HistoryRow(r.id,r.rev,json));dao.save(RecordRow(r.id,r.kind,r.rev,json,r.payload.s("questionId")))
 }
 suspend fun remember(records:List<SyncRecord>)=db.withTransaction{for(r in records){if(get(r.id)?.deleted==true||forgotten(r))continue;require(Regex("[a-zA-Z0-9_.:-]{1,160}").matches(r.id)&&r.rev.length<=80);dao.history(HistoryRow(r.id,r.rev,wire.encodeToString(r)))}}
 suspend fun lineage(id:String,rev:String,limit:Int=10):Pair<List<SyncRecord>,String>{val records=mutableListOf<SyncRecord>();val seen=mutableSetOf<String>();var next=rev;while(next.isNotBlank()&&records.size<limit&&seen.add(next)){val r=dao.history(id,next)?.let{wire.decodeFromString<SyncRecord>(it.json)}?:break;records.add(r);next=r.baseRev};return records to if(records.size==limit)next else ""}
 suspend fun historyPage(id:String,after:String):Pair<List<SyncRecord>,String>{if(get(id)?.deleted==true)return emptyList<SyncRecord>() to "";val rows=dao.historyPage(id,after).map{wire.decodeFromString<SyncRecord>(it.json)};return rows to if(rows.size==10)rows.last().rev else ""}
 private suspend fun commonBase(a:SyncRecord,b:SyncRecord):SyncRecord?{suspend fun ancestors(head:SyncRecord):Map<String,SyncRecord>{val found=linkedMapOf<String,SyncRecord>();val pending=java.util.ArrayDeque<SyncRecord>();pending.add(head);while(pending.isNotEmpty()){val r=pending.removeFirst();if(r.rev in found)continue;found[r.rev]=r;for(rev in listOf(r.baseRev)+r.mergeParents)dao.history(r.id,rev)?.let{pending.add(wire.decodeFromString<SyncRecord>(it.json))}};return found};val left=ancestors(a);return ancestors(b).values.firstOrNull{it.rev in left}}
 suspend fun importDocument(doc:Document,mode:String="reuse",versionOf:String=""):SyncRecord=db.withTransaction{val existing=all("document").firstOrNull{it.payload.strings("assetIds").firstOrNull()==doc.assetIds.firstOrNull()};if(existing!=null&&mode=="reuse")existing else put("document",payload(doc.copy(quality="pending",versionOf=if(mode=="version")versionOf else "")))}
 suspend fun messages(questionId:String)=dao.messageRows(questionId).map{wire.decodeFromString<SyncRecord>(it.json)}.filter{!it.deleted&&it.payload.s("completion") in listOf("","complete")}.sortedBy{it.updatedAt}
 suspend fun completedConversation(questionId:String):List<SyncRecord>{val rows=messages(questionId);val last=rows.indexOfLast{it.payload.s("role")=="assistant"};return if(last<0)emptyList()else rows.take(last+1)}
 suspend fun put(kind:String,p:JsonObject,id:String=uid()):SyncRecord=db.withTransaction{val old=get(id);check(old?.deleted!=true){"已删除记录不能覆盖"};val parent=p.s("questionId");check(parent.isBlank()||get(parent)?.deleted!=true){"题目已删除"};val r=SyncRecord(id,kind,uid(),old?.rev?:"",p,false,now(),deviceId);write(r);r}
 suspend fun edit(id:String,changes:JsonObject):SyncRecord =db.withTransaction{val r=get(id)?:error("记录不存在");val merged=(r.payload+changes).toMutableMap();if(r.kind=="question"&&cn.jieti.ai.StudyContext.inputFields.any{it in changes&&r.payload[it]!=changes[it]}){merged["inputChangedAt"]=JsonPrimitive(now());merged["analysisState"]=JsonPrimitive("stale");merged["analysisSourceHash"]=JsonPrimitive("");for(k in listOf("diagnosis","solution"))if(k !in r.payload.strings("userEditedFields")&&k !in changes)merged[k]=JsonPrimitive("")};put(r.kind,JsonObject(merged),id)}
 suspend fun saveArtifact(questionId:String,inputHash:String,p:JsonObject):SyncRecord?=db.withTransaction{val q=get(questionId)?.takeUnless{it.deleted}?:return@withTransaction null;if(cn.jieti.ai.StudyContext.inputHash(q,messages(questionId))!=inputHash)return@withTransaction null;put("artifact",p)}
 suspend fun updateConditions(id:String,source:String,text:String,temporary:Boolean)=db.withTransaction{val q=get(id)?:return@withTransaction;if(source in q.payload.strings("conditionSourceIds")||q.payload.s("assumptionSourceId")==source)return@withTransaction
  edit(id,buildJsonObject{if(temporary){put("temporaryAssumption",text);put("assumptionSourceId",source)}else{put("effectiveConditions",(q.payload.s("effectiveConditions").takeIf{it.isNotBlank()}?.plus("\n")?:"")+text);put("conditionSourceIds",JsonArray((q.payload.strings("conditionSourceIds")+source).map(::JsonPrimitive)));put("conditionsOrigin","conversation")}})
 }
 suspend fun saveTutorResponse(id:String,hash:String,p:JsonObject,responseId:String):SyncRecord?=db.withTransaction{val q=get(id)?.takeUnless{it.deleted}?:return@withTransaction null;if(cn.jieti.ai.StudyContext.inputHash(q,messages(id))!=hash)return@withTransaction null;put("message",p,responseId)}
 suspend fun commitAnalysis(id:String,expected:String,result:JsonObject,review:JsonObject,evidence:List<Evidence>):Boolean=db.withTransaction{
  val q=get(id)?.takeUnless{it.deleted}?:return@withTransaction false
  if(cn.jieti.ai.StudyContext.inputHash(q,messages(id))!=expected)return@withTransaction false
  val changes=buildJsonObject{for(k in listOf("title","concepts","transcription","studentWork","classification","errorTypes","diagnosis","solution"))if(result[k]!=null&&!(k=="transcription"&&q.payload.s("effectiveConditions").isNotBlank()&&q.payload.s("transcription").isNotBlank())&&k !in q.payload.strings("userEditedFields")&&!(k=="classification"&&q.payload.s("classificationOrigin")=="user")){put(k,if(k=="solution"&&result.s(k).isNotBlank())JsonPrimitive(result.s(k)+"\n\n核验："+(if(review.b("correct"))"模型复核通过（仍可人工修正）"else "待核验 / 存在疑问")+"\n"+review.s("notes"))else result[k]!!);if(k=="classification")put("classificationOrigin","ai")};put("analysisState","current")}
  val saved=put("question",JsonObject(q.payload+changes),id);val hash=cn.jieti.ai.StudyContext.inputHash(saved,messages(id));edit(id,buildJsonObject{put("analysisSourceHash",hash)})
  for(old in all("evidence").filter{it.payload.s("questionId")==id&&it.payload.s("sourceHash")!=hash})tombstone(old)
  for(e in evidence){val value=e.copy(sourceHash=hash);put("evidence",payload(value),"evidence:"+sha((id+":"+hash+":"+e.concept+":"+e.sourceIds.joinToString()).toByteArray()).take(48))};true
 }
 suspend fun saveAgentNote(questionId:String,content:String,hash:String,expected:SyncRecord?,inputHash:String="")=db.withTransaction{
  val question=get(questionId)?.takeUnless{it.deleted}?:return@withTransaction
  if(inputHash.isNotBlank()&&cn.jieti.ai.StudyContext.inputHash(question,messages(questionId))!=inputHash)return@withTransaction
  val latest=cn.jieti.ai.StudyContext.note(all("note"),questionId)
  if(latest?.rev!=expected?.rev||latest!=null&&latest.payload.s("origin")!="agent")return@withTransaction
  put("note",payload(Note(questionId,content,"agent",hash)),latest?.id?:"study-note:$questionId")
 }
 suspend fun saveStudyNote(questionId:String,content:String,expectedRev:String):SyncRecord=db.withTransaction{
  val latest=cn.jieti.ai.StudyContext.note(all("note"),questionId)
  check((latest?.rev?:"")==expectedRev){"笔记已在另一端更新，当前草稿仍保留，请先复制后重新打开"}
  put("note",payload(Note(questionId,content,"user","")),latest?.id?:"study-note:$questionId")
 }
 suspend fun editUser(id:String,changes:JsonObject,expectedRev:String):SyncRecord=db.withTransaction{
  val r=get(id)?:error("记录不存在");if(r.rev!=expectedRev){val base=dao.history(id,expectedRev)?.let{wire.decodeFromString<SyncRecord>(it.json)};check(base!=null&&changes.keys.all{r.payload[it]==base.payload[it]}){"同一字段已更新，草稿已保留，请复制后重新打开最新版本"}}
  val marked=changes.toMutableMap();if(r.kind=="question"){if("classification" in changes)marked["classificationOrigin"]=JsonPrimitive("user");if("scope" in changes)marked["scopeOrigin"]=JsonPrimitive("user");if("effectiveConditions" in changes){marked["conditionsOrigin"]=JsonPrimitive("user");marked["conditionSourceIds"]=JsonArray(emptyList())};marked["userEditedFields"]=JsonArray((r.payload.strings("userEditedFields")+changes.keys).distinct().map(::JsonPrimitive))}
  edit(id,JsonObject(marked))
 }
 private suspend fun invalidateDerived(id:String){for(child in all().filter{it.payload.s("questionId")==id&&(it.kind in listOf("evidence","artifact")||it.kind=="note"&&it.payload.s("origin")=="agent")}){if(child.kind=="evidence")tombstone(child)else if(child.kind=="artifact")put("artifact",JsonObject(child.payload+mapOf("status" to JsonPrimitive("stale"))),child.id)else put("note",JsonObject(child.payload+mapOf("content" to JsonPrimitive(""),"sourceHash" to JsonPrimitive(""))),child.id)};for(m in all("mastery"))if(m.payload.strings("evidenceIds").any{get(it)?.deleted==true})put("mastery",JsonObject(m.payload+mapOf("state" to JsonPrimitive("needs_evidence"))),m.id)}
 private suspend fun tombstone(r:SyncRecord){if(!r.deleted)write(r.copy(deleted=true,baseRev=r.rev,rev=uid(),updatedAt=now(),deviceId=deviceId))}
 private suspend fun cascade(r:SyncRecord){
  if(r.kind=="document")dao.deleteChunks(r.id)
  if(r.kind in listOf("question","document"))for(child in all())if(child.payload.s("questionId")==r.id||child.payload.s("documentId")==r.id)tombstone(child)
 }
 suspend fun delete(id:String)=db.withTransaction{val r=get(id)?:return@withTransaction;check(r.kind in listOf("question","document")){"此记录不能删除"};tombstone(r);cascade(r)}
 private suspend fun parentDeleted(r:SyncRecord):Boolean= listOf(r.payload.s("questionId"),r.payload.s("documentId")).filter{it.isNotBlank()}.any{get(it)?.deleted==true}
 suspend fun forget(id:String){db.withTransaction {val r=get(id)?:return@withTransaction;check(r.kind=="memory");val m=r.value<MemoryItem>();write(r.copy(deleted=true,baseRev=r.rev,rev=uid(),updatedAt=now()));put("forget",payload(ForgetRule(id,m.scope,m.key,m.sourceIds,"用户明确遗忘")))}}
 suspend fun forgotten(r:SyncRecord):Boolean {if(r.kind!="memory")return false;return all("forget").any{it.payload.s("memoryId")==r.id||(it.payload.s("scope")==r.payload.s("scope")&&(it.payload.s("key")==r.payload.s("key")||it.payload.strings("sourceIds").any{s->s in r.payload.strings("sourceIds")}))}}
 suspend fun ingest(incoming:SyncRecord):SyncRecord=db.withTransaction {
  val local=get(incoming.id)
  var candidate=if(forgotten(incoming)||parentDeleted(incoming))incoming.copy(deleted=true,rev=uid())else incoming
  if(local!=null){
   require(local.kind==candidate.kind){"记录类型冲突"}
   if(local.deleted)return@withTransaction local
   if(local.rev==candidate.rev)return@withTransaction local
   if(!candidate.deleted&&candidate.baseRev!=local.rev){
    if(local.baseRev==candidate.rev)return@withTransaction local
    val base=commonBase(local,candidate)
    if(base?.rev==candidate.rev)return@withTransaction local
    if(base?.rev==local.rev){write(candidate);return@withTransaction candidate}
    val fields=mutableListOf<String>();val merged=local.payload.toMutableMap()
    if(base==null)fields.add("无共同版本")else for(k in local.payload.keys+candidate.payload.keys){val l=local.payload[k];val r=candidate.payload[k];val b=base.payload[k];if(k=="userEditedFields"){merged[k]=JsonArray((local.payload.strings(k)+candidate.payload.strings(k)).distinct().map(::JsonPrimitive));continue};if(l==r)continue;if(l==b){if(r==null)merged.remove(k)else merged[k]=r}else if(r!=b)fields.add(k)}
    if(local.kind=="question"&&cn.jieti.ai.StudyContext.inputFields.any{local.payload[it]!=candidate.payload[it]}){merged["analysisState"]=JsonPrimitive("stale");merged["analysisSourceHash"]=JsonPrimitive("");for(k in listOf("diagnosis","solution"))if(k !in (local.payload.strings("userEditedFields")+candidate.payload.strings("userEditedFields")))merged[k]=JsonPrimitive("")}
    if(fields.isNotEmpty()){dao.conflict(ConflictRow(uid(),local.id,wire.encodeToString(local),wire.encodeToString(candidate),fields.joinToString()));return@withTransaction local}
    write(candidate);candidate=local.copy(payload=JsonObject(merged),baseRev=local.rev,mergeParents=listOf(candidate.rev),rev=uid(),updatedAt=now())
   }
  }
  write(candidate)
  if(candidate.deleted)cascade(candidate)
  if(candidate.kind=="forget")for(m in all("memory"))if(forgotten(m))write(m.copy(deleted=true,baseRev=m.rev,rev=uid(),updatedAt=now()))
  candidate
 }
 suspend fun acceptServer(r:SyncRecord,expectedLocalRev:String):SyncRecord=db.withTransaction {
  val local=get(r.id)
  if(local!=null&&local.rev!=expectedLocalRev)return@withTransaction ingest(r)
  if(local?.deleted==true&&!r.deleted)return@withTransaction local
  val accepted=if(!r.deleted&&(forgotten(r)||parentDeleted(r)))r.copy(deleted=true,baseRev=r.rev,rev=uid())else r
  write(accepted);if(accepted.deleted)cascade(accepted);accepted
 }
 fun asset(hash:String):File{require(Regex("[a-f0-9]{64}").matches(hash));return File(assetDir,hash)}
 fun addAsset(bytes:ByteArray):String=sha(bytes).also{val target=asset(it);if(!target.exists()){val tmp=File(assetDir,"$it.tmp");tmp.writeBytes(bytes);check(tmp.renameTo(target)){"附件写入失败"}}}
 fun addAsset(input:java.io.InputStream):String {
  val tmp=File(assetDir,uid()+".import");val digest=MessageDigest.getInstance("SHA-256")
  try{input.use{source->tmp.outputStream().use{out->val buffer=ByteArray(1048576);while(true){val n=source.read(buffer);if(n<0)break;digest.update(buffer,0,n);out.write(buffer,0,n)}}};val hash=digest.digest().joinToString(""){"%02x".format(it)};val target=asset(hash);if(target.exists())tmp.delete()else check(tmp.renameTo(target));return hash}catch(e:Exception){tmp.delete();throw e}
 }
 suspend fun setting(key:String,default:String=""):String=all("setting").firstOrNull{it.payload.s("key")==key}?.payload?.s("value")?:default
 suspend fun setSetting(key:String,value:String)=put("setting",payload(Setting(key,value)),"setting:$key")
}
