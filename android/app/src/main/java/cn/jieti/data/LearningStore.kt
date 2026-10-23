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
 @Query("SELECT id,kind,rev FROM records") suspend fun heads():List<RecordHead>
 @Query("SELECT * FROM records WHERE kind NOT IN ('message','artifact','citation','evidence') OR parentId=:questionId") fun observeScreen(questionId:String):Flow<List<RecordRow>>
 @Query("SELECT * FROM records WHERE kind='message' AND parentId=:questionId") fun observeMessages(questionId:String):Flow<List<RecordRow>>
 @Query("SELECT * FROM records WHERE kind='message' AND parentId=:questionId ORDER BY rowid") suspend fun messageRows(questionId:String):List<RecordRow>
 @Query("SELECT * FROM records WHERE id=:id") suspend fun get(id:String):RecordRow?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(row:RecordRow)
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun history(row:HistoryRow)
 @Query("SELECT * FROM history WHERE id=:id AND rev=:rev") suspend fun history(id:String,rev:String):HistoryRow?
 @Insert suspend fun conflict(row:ConflictRow)
 @Query("SELECT * FROM conflicts") suspend fun conflicts():List<ConflictRow>
 @Query("DELETE FROM conflicts WHERE id=:id") suspend fun removeConflict(id:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun chunk(row:ChunkRow)
 @Query("SELECT * FROM chunks WHERE documentId=:id AND page=:page") suspend fun chunks(id:String,page:Int):List<ChunkRow>
 @Query("SELECT * FROM chunks WHERE id IN (:ids)") suspend fun chunksById(ids:List<String>):List<ChunkRow>
 @Query("DELETE FROM chunks WHERE documentId=:id") suspend fun deleteChunks(id:String)
 @Query("DELETE FROM chunks WHERE documentId=:id AND page=:page") suspend fun deletePage(id:String,page:Int)
 @Query("UPDATE chunks SET scope=:scope WHERE documentId=:id") suspend fun moveChunks(id:String,scope:String)
 @Query("SELECT chunks.* FROM chunks JOIN chunks_fts ON chunks.rowid = chunks_fts.rowid WHERE chunks.scope=:scope AND chunks_fts MATCH :query LIMIT 200") suspend fun search(scope:String,query:String):List<ChunkRow>
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
  when(r.kind){"memory"->r.value<MemoryItem>();"mastery"->r.value<MasterySnapshot>();"policy"->r.value<TeachingPolicyVersion>();"evaluation"->r.value<PolicyEvaluation>();"forget"->r.value<ForgetRule>();"question"->r.value<Question>();"message"->r.value<Message>();"evidence"->r.value<Evidence>();"document"->r.value<Document>();"citation"->r.value<Citation>();"artifact"->r.value<Artifact>();"note"->r.value<Note>();"setting"->r.value<Setting>();else->error("未知记录类型")}
  if(r.kind=="document"&&get(r.id)?.payload?.s("scope")!=r.payload.s("scope"))dao.moveChunks(r.id,r.payload.s("scope"))
  val json=wire.encodeToString(r);dao.history(HistoryRow(r.id,r.rev,json));dao.save(RecordRow(r.id,r.kind,r.rev,json,r.payload.s("questionId")))
 }
 suspend fun messages(questionId:String)=dao.messageRows(questionId).map{wire.decodeFromString<SyncRecord>(it.json)}.filter{!it.deleted}.sortedBy{it.updatedAt}
 suspend fun put(kind:String,p:JsonObject,id:String=uid()):SyncRecord=db.withTransaction{val old=get(id);check(old?.deleted!=true){"已删除记录不能覆盖"};val parent=p.s("questionId");check(parent.isBlank()||get(parent)?.deleted!=true){"题目已删除"};val r=SyncRecord(id,kind,uid(),old?.rev?:"",p,false,now(),deviceId);write(r);r}
 suspend fun edit(id:String,changes:JsonObject):SyncRecord =db.withTransaction{val r=get(id)?:error("记录不存在");put(r.kind,JsonObject(r.payload+changes),id)}
 suspend fun editUser(id:String,changes:JsonObject,expectedRev:String):SyncRecord=db.withTransaction{
  val r=get(id)?:error("记录不存在");if(r.rev!=expectedRev){val base=dao.history(id,expectedRev)?.let{wire.decodeFromString<SyncRecord>(it.json)};check(base!=null&&changes.keys.all{r.payload[it]==base.payload[it]}){"同一字段已更新，草稿已保留，请复制后重新打开最新版本"}}
  val marked=changes.toMutableMap();if(r.kind=="question"){if("classification" in changes)marked["classificationOrigin"]=JsonPrimitive("user");marked["userEditedFields"]=JsonArray((r.payload.strings("userEditedFields")+changes.keys).distinct().map(::JsonPrimitive))}
  edit(id,JsonObject(marked))
 }
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
    val base=dao.history(candidate.id,candidate.baseRev)?.let{wire.decodeFromString<SyncRecord>(it.json)}
    val fields=mutableListOf<String>();val merged=local.payload.toMutableMap()
    if(base==null)fields.add("无共同版本")else for(k in local.payload.keys+candidate.payload.keys){val l=local.payload[k];val r=candidate.payload[k];val b=base.payload[k];if(l==r)continue;if(l==b){if(r==null)merged.remove(k)else merged[k]=r}else if(r!=b)fields.add(k)}
    if(fields.isNotEmpty()){dao.conflict(ConflictRow(uid(),local.id,wire.encodeToString(local),wire.encodeToString(candidate),fields.joinToString()));return@withTransaction local}
    write(candidate);candidate=local.copy(payload=JsonObject(merged),baseRev=local.rev,rev=uid(),updatedAt=now())
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
